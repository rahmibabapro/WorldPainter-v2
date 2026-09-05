package org.pepsoft.worldpainter.tools.scripts;

import org.junit.Test;

import javax.swing.JComponent;
import javax.swing.SwingUtilities;
import java.io.File;
import java.text.NumberFormat;

import static org.junit.Assert.*;

public class ScriptParameterDescriptorTest {
    @Test public void repeatedEditorAccessPreservesEveryPresetInsteadOfReapplyingDefaults() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            assertPreset(new ScriptRunner.StringParameterDescriptor(), "default", "preset");
            assertPreset(new ScriptRunner.IntegerParameterDescriptor(), 7, 23);
            assertPreset(new ScriptRunner.PercentageParameterDescriptor(), 40, 20);
            assertPreset(new ScriptRunner.FloatParameterDescriptor(), 1.25f, 2.5f);
            assertPreset(new ScriptRunner.BooleanParameterDescriptor(), true, false);
            assertPreset(new ScriptRunner.FileParameterDescriptor(), new File("default.bp"), new File("preset.bp").getAbsoluteFile());
        });
    }

    @Test public void optionalFloatMayBeBlankButCannotBeMalformedOrNonFinite() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            final ScriptRunner.FloatParameterDescriptor descriptor = new ScriptRunner.FloatParameterDescriptor();
            descriptor.name = "optionalFloat";
            descriptor.optional = true;
            descriptor.getEditor();
            assertTrue(descriptor.isEditorValid());
            assertNull(descriptor.getValue());
            descriptor.editor.setText("not-a-number");
            assertFalse(descriptor.isEditorValid());
            descriptor.setValue(Float.NaN);
            assertFalse(descriptor.isEditorValid());
            descriptor.setValue(Float.POSITIVE_INFINITY);
            assertFalse(descriptor.isEditorValid());
            descriptor.setValue(Float.NEGATIVE_INFINITY);
            assertFalse(descriptor.isEditorValid());
        });
    }

    @Test public void clearingOptionalFloatDoesNotResubmitItsOldPresetValue() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            final ScriptRunner.FloatParameterDescriptor descriptor = new ScriptRunner.FloatParameterDescriptor();
            descriptor.name = "optionalFloat";
            descriptor.optional = true;
            descriptor.defaultValue = 12.5f;
            descriptor.getEditor();
            assertEquals(12.5f, descriptor.getValue(), 0);
            descriptor.editor.setText(" ");
            assertTrue(descriptor.isEditorValid());
            assertNull("Blank optional input must not retain the formatted field's stale value", descriptor.getValue());
            final ScriptRunner.ScriptDescriptor script = new ScriptRunner.ScriptDescriptor();
            script.parameterDescriptors.add(descriptor);
            assertFalse(script.getValues().containsKey("optionalFloat"));
        });
    }

    @Test public void floatValidationCommitsEditedLocaleFormattedText() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            final ScriptRunner.FloatParameterDescriptor descriptor = new ScriptRunner.FloatParameterDescriptor();
            descriptor.getEditor();
            descriptor.setValue(1.0f);
            descriptor.editor.setText(NumberFormat.getNumberInstance().format(12.5));
            assertTrue(descriptor.isEditorValid());
            assertEquals(12.5f, descriptor.getValue(), 0);
            descriptor.editor.setText("");
            assertFalse("Mandatory fields cannot be empty", descriptor.isEditorValid());
        });
    }

    @Test public void floatMetadataCannotSmuggleNaNInfinityOrOverflow() {
        final ScriptRunner.FloatParameterDescriptor descriptor = new ScriptRunner.FloatParameterDescriptor();
        descriptor.name = "metadataFloat";
        for (String value : new String[] { "NaN", "Infinity", "-Infinity", "1e100" }) {
            assertThrows(value, IllegalArgumentException.class, () -> descriptor.toObject(value));
        }
        assertEquals(1.25f, descriptor.toObject("1.25"), 0);
    }

    private static <T, E extends JComponent> void assertPreset(ScriptRunner.ParameterDescriptor<T, E> descriptor,
                                                               T defaultValue, T preset) {
        descriptor.defaultValue = defaultValue;
        final E originalEditor = descriptor.getEditor();
        descriptor.setValue(preset);
        assertSame(originalEditor, descriptor.getEditor());
        assertEquals(preset, descriptor.getValue());
        assertSame(originalEditor, descriptor.getEditor());
        assertEquals(preset, descriptor.getValue());
    }
}
