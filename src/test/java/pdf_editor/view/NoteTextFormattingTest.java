package pdf_editor.view;

import org.junit.jupiter.api.Test;
import javax.swing.JTextPane;
import javax.swing.text.StyleConstants;
import static org.junit.jupiter.api.Assertions.*;

class NoteTextFormattingTest {
    @Test void boldItalicUnderlineToggleIndependently() {
        JTextPane pane = new JTextPane();
        pane.setText("nihao"); pane.select(0,5);
        NoteTextFormatting.toggle(pane, StyleConstants.Bold);
        NoteTextFormatting.toggle(pane, StyleConstants.Italic);
        NoteTextFormatting.toggle(pane, StyleConstants.Underline);
        var attrs = pane.getStyledDocument().getCharacterElement(0).getAttributes();
        assertTrue(StyleConstants.isBold(attrs));
        assertTrue(StyleConstants.isItalic(attrs));
        assertTrue(StyleConstants.isUnderline(attrs));
        NoteTextFormatting.toggle(pane, StyleConstants.Italic);
        NoteTextFormatting.toggle(pane, StyleConstants.Underline);
        attrs = pane.getStyledDocument().getCharacterElement(0).getAttributes();
        assertTrue(StyleConstants.isBold(attrs));
        assertFalse(StyleConstants.isItalic(attrs));
        assertFalse(StyleConstants.isUnderline(attrs));
    }
}
