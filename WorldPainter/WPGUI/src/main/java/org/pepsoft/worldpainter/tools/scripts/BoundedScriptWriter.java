package org.pepsoft.worldpainter.tools.scripts;

import javax.swing.JTextArea;
import javax.swing.SwingUtilities;
import javax.swing.text.BadLocationException;
import java.io.Writer;
import java.util.Objects;

/** Bounded pending/output text; a noisy script cannot enqueue unlimited Swing work. */
final class BoundedScriptWriter extends Writer {
    BoundedScriptWriter(JTextArea output) { this(output, 64 * 1024, 512 * 1024); }

    BoundedScriptWriter(JTextArea output, int pendingLimit, int documentLimit) {
        if (pendingLimit < 1 || documentLimit < pendingLimit) throw new IllegalArgumentException("Invalid output limits");
        this.output = Objects.requireNonNull(output);
        this.pendingLimit = pendingLimit;
        this.documentLimit = documentLimit;
    }

    @Override
    public synchronized void write(char[] chars, int offset, int length) {
        Objects.checkFromIndexSize(offset, length, chars.length);
        if (length == 0) return;
        if (length >= pendingLimit) {
            pending.setLength(0);
            pending.append(chars, offset + length - pendingLimit, pendingLimit);
        } else {
            int excess = pending.length() + length - pendingLimit;
            if (excess > 0) pending.delete(0, excess);
            pending.append(chars, offset, length);
        }
        if (!scheduled) {
            scheduled = true;
            SwingUtilities.invokeLater(this::drain);
        }
    }

    private void drain() {
        final String text;
        synchronized (this) {
            text = pending.toString();
            pending.setLength(0);
            scheduled = false;
        }
        try {
            int excess = output.getDocument().getLength() + text.length() - documentLimit;
            if (excess > 0) output.getDocument().remove(0, excess);
            output.append(text);
        } catch (BadLocationException impossible) {
            throw new IllegalStateException(impossible);
        }
    }

    @Override public void flush() { }
    @Override public void close() { }

    private final JTextArea output;
    private final int pendingLimit, documentLimit;
    private final StringBuilder pending = new StringBuilder();
    private boolean scheduled;
}
