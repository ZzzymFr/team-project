package pdf_editor;

import javax.swing.JTextPane;
import javax.swing.text.AttributeSet;
import javax.swing.text.SimpleAttributeSet;
import javax.swing.text.StyleConstants;
import javax.swing.text.StyledDocument;

final class NoteTextFormatting {
    private NoteTextFormatting() { }

    static void toggle(JTextPane pane, Object attribute) {
        if (attribute != StyleConstants.Bold && attribute != StyleConstants.Italic
                && attribute != StyleConstants.Underline) {
            throw new IllegalArgumentException("Unsupported text style: " + attribute);
        }
        int start = pane.getSelectionStart();
        int end = pane.getSelectionEnd();
        boolean enabled;
        if (start < end) {
            // Toggle OFF only if every selected character already has this style.
            // This is independent of the caret position and preserves other styles.
            enabled = true;
            StyledDocument doc = pane.getStyledDocument();
            for (int i = start; i < end; ) {
                var element = doc.getCharacterElement(i);
                if (!hasStyle(element.getAttributes(), attribute)) {
                    enabled = false;
                    break;
                }
                i = Math.min(end, element.getEndOffset());
            }
        } else {
            enabled = hasStyle(pane.getCharacterAttributes(), attribute);
        }
        SimpleAttributeSet attrs = new SimpleAttributeSet();
        if (attribute == StyleConstants.Bold) StyleConstants.setBold(attrs, !enabled);
        else if (attribute == StyleConstants.Italic) StyleConstants.setItalic(attrs, !enabled);
        else StyleConstants.setUnderline(attrs, !enabled);
        apply(pane, attrs);
    }

    static void apply(JTextPane pane, AttributeSet attrs) {
        int start = pane.getSelectionStart();
        int end = pane.getSelectionEnd();
        if (start != end) pane.getStyledDocument().setCharacterAttributes(start, end - start, attrs, false);
        else pane.setCharacterAttributes(attrs, false);
        pane.requestFocusInWindow();
        pane.select(start, end);
    }

    private static boolean hasStyle(AttributeSet attrs, Object attribute) {
        if (attribute == StyleConstants.Bold) return StyleConstants.isBold(attrs);
        if (attribute == StyleConstants.Italic) return StyleConstants.isItalic(attrs);
        return StyleConstants.isUnderline(attrs);
    }
}
