package pdf_editor.adapter;

import pdf_editor.domain.PdfDocument;
import javax.swing.JTextPane;
import javax.swing.text.BadLocationException;
import javax.swing.text.SimpleAttributeSet;
import javax.swing.text.StyleConstants;
import javax.swing.text.StyledDocument;
import java.awt.Color;
import java.awt.Font;

/** Swing adapter shared by the note editor and preview renderer; domain contains only NoteSpan values. */
public final class NoteTextDocumentFactory {
    private NoteTextDocumentFactory() { }
    public static JTextPane createPane(PdfDocument.Placement note) {
        JTextPane pane = new JTextPane();
        pane.setFont(new Font("Segoe UI", Font.PLAIN, 16));
        StyledDocument doc = pane.getStyledDocument();
        try {
            if (note.noteSpans().isEmpty()) {
                SimpleAttributeSet attrs = new SimpleAttributeSet();
                StyleConstants.setFontSize(attrs, note.noteSize());
                StyleConstants.setBold(attrs, (note.noteStyle() & Font.BOLD) != 0);
                StyleConstants.setItalic(attrs, (note.noteStyle() & Font.ITALIC) != 0);
                doc.insertString(0, note.noteText(), attrs);
            } else {
                for (PdfDocument.NoteSpan span : note.noteSpans()) {
                    doc.insertString(doc.getLength(), span.text(), attributes(span));
                }
            }
        } catch (BadLocationException ex) { throw new IllegalStateException(ex); }
        return pane;
    }
    private static SimpleAttributeSet attributes(PdfDocument.NoteSpan span) {
        SimpleAttributeSet attrs = new SimpleAttributeSet();
        StyleConstants.setFontFamily(attrs, "Segoe UI");
        StyleConstants.setFontSize(attrs, span.size());
        StyleConstants.setBold(attrs, span.bold());
        StyleConstants.setItalic(attrs, span.italic());
        StyleConstants.setUnderline(attrs, span.underline());
        StyleConstants.setForeground(attrs, new Color(31, 48, 72));
        return attrs;
    }
}
