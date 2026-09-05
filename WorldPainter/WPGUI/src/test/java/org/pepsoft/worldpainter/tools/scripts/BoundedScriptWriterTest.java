package org.pepsoft.worldpainter.tools.scripts;

import org.junit.Test;

import javax.swing.JTextArea;
import javax.swing.SwingUtilities;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.Assert.*;

/** No windows: exercise queued EDT work against a real Swing text document. */
public class BoundedScriptWriterTest {
    @Test public void busyEdtCoalescesWritesAndKeepsOnlyTheBoundedPendingTail() throws Exception {
        final JTextArea output = output();
        final AtomicInteger insertions = new AtomicInteger();
        final AtomicBoolean wrongThread = new AtomicBoolean();
        listen(output, insertions, wrongThread);
        final BoundedScriptWriter writer = new BoundedScriptWriter(output, 64, 128);
        final StringBuilder written = new StringBuilder();
        SwingUtilities.invokeAndWait(() -> {
            // The drain cannot run until this event returns: many writes must
            // become one queued document insertion, not one Swing task each.
            for (int i = 0; i < 1000; i++) {
                final String part = "message-" + i + "\n";
                written.append(part);
                final char[] chars = part.toCharArray();
                writer.write(chars, 0, chars.length);
            }
            assertEquals("No inline worker/EDT append", 0, output.getDocument().getLength());
        });
        drain();
        assertEquals(written.substring(written.length() - 64), text(output));
        assertEquals(1, insertions.get());
        assertFalse(wrongThread.get());
    }

    @Test public void oneHugeWriteIsTruncatedBeforeItReachesTheDocument() throws Exception {
        final JTextArea output = output();
        final BoundedScriptWriter writer = new BoundedScriptWriter(output, 16, 32);
        final String message = "x".repeat(100_000) + "0123456789abcdef";
        writer.write(message.toCharArray(), 0, message.length());
        drain();
        assertEquals("0123456789abcdef", text(output));
    }

    @Test public void repeatedDrainsCapTheDocumentAndPreserveNewestOutput() throws Exception {
        final JTextArea output = output();
        final AtomicInteger insertions = new AtomicInteger();
        final AtomicBoolean wrongThread = new AtomicBoolean();
        listen(output, insertions, wrongThread);
        final BoundedScriptWriter writer = new BoundedScriptWriter(output, 64, 96);
        for (char value : new char[] { 'A', 'B', 'C' }) {
            final char[] chars = String.valueOf(value).repeat(64).toCharArray();
            writer.write(chars, 0, chars.length);
            drain();
            assertTrue(text(output).length() <= 96);
        }
        assertEquals("B".repeat(32) + "C".repeat(64), text(output));
        assertEquals(3, insertions.get());
        assertFalse("All insert/remove document events must occur on EDT", wrongThread.get());
    }

    @Test public void oversizedExistingOutputIsTrimmedWithoutDiscardingNewText() throws Exception {
        final JTextArea output = output();
        SwingUtilities.invokeAndWait(() -> output.setText("old".repeat(100)));
        final BoundedScriptWriter writer = new BoundedScriptWriter(output, 16, 32);
        writer.write("latest".toCharArray(), 0, 6);
        drain();
        assertEquals(32, text(output).length());
        assertTrue(text(output).endsWith("latest"));
    }

    @Test public void flushAndCloseDoNotLoseAlreadyQueuedOutput() throws Exception {
        final JTextArea output = output();
        final BoundedScriptWriter writer = new BoundedScriptWriter(output, 16, 32);
        SwingUtilities.invokeAndWait(() -> {
            writer.write("queued".toCharArray(), 0, 6);
            writer.flush();
            writer.close();
        });
        drain();
        assertEquals("queued", text(output));
    }

    @Test public void invalidLimitsAndWriteRangesFailWithoutDocumentEdits() throws Exception {
        final JTextArea output = output();
        assertThrows(IllegalArgumentException.class, () -> new BoundedScriptWriter(output, 0, 32));
        assertThrows(IllegalArgumentException.class, () -> new BoundedScriptWriter(output, 64, 32));
        assertThrows(NullPointerException.class, () -> new BoundedScriptWriter(null, 16, 32));
        final BoundedScriptWriter writer = new BoundedScriptWriter(output, 16, 32);
        assertThrows(IndexOutOfBoundsException.class, () -> writer.write(new char[2], 1, 2));
        assertThrows(IndexOutOfBoundsException.class, () -> writer.write(new char[2], -1, 1));
        writer.write(new char[0], 0, 0);
        drain();
        assertEquals("", text(output));
    }

    private static JTextArea output() throws Exception {
        final AtomicReference<JTextArea> result = new AtomicReference<>();
        SwingUtilities.invokeAndWait(() -> result.set(new JTextArea()));
        return result.get();
    }

    private static void listen(JTextArea output, AtomicInteger insertions, AtomicBoolean wrongThread) throws Exception {
        SwingUtilities.invokeAndWait(() -> output.getDocument().addDocumentListener(new DocumentListener() {
            @Override public void insertUpdate(DocumentEvent event) {
                insertions.incrementAndGet();
                checkThread();
            }
            @Override public void removeUpdate(DocumentEvent event) { checkThread(); }
            @Override public void changedUpdate(DocumentEvent event) { checkThread(); }
            private void checkThread() { if (!SwingUtilities.isEventDispatchThread()) wrongThread.set(true); }
        }));
    }

    private static String text(JTextArea output) throws Exception {
        final AtomicReference<String> result = new AtomicReference<>();
        SwingUtilities.invokeAndWait(() -> result.set(output.getText()));
        return result.get();
    }

    private static void drain() throws Exception { SwingUtilities.invokeAndWait(() -> { }); }
}
