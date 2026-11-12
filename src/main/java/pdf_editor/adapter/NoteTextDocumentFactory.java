package pdf_editor.adapter;

import pdf_editor.domain.PdfDocument;
import javax.swing.JTextPane;
import javax.swing.text.BadLocationException;
import javax.swing.text.SimpleAttributeSet;
import javax.swing.text.StyleConstants;
import javax.swing.text.StyledDocument;
import javax.swing.text.Element;
import javax.swing.text.AttributeSet;
import java.util.ArrayList;
import java.util.List;
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
    /** Convert Swing styled text to immutable domain spans at the view-adapter boundary. */
    public static List<PdfDocument.NoteSpan> toSpans(JTextPane pane) {
        StyledDocument doc = pane.getStyledDocument();
        List<PdfDocument.NoteSpan> spans = new ArrayList<>();
        try {
            for (int i = 0; i < doc.getLength(); ) {
                Element element = doc.getCharacterElement(i);
                int end = Math.min(doc.getLength(), element.getEndOffset());
                AttributeSet attr = element.getAttributes();
                spans.add(new PdfDocument.NoteSpan(doc.getText(i, end - i),
                        StyleConstants.isBold(attr), StyleConstants.isItalic(attr),
                        StyleConstants.isUnderline(attr), Math.max(8, StyleConstants.getFontSize(attr))));
                i = end;
            }
        } catch (BadLocationException e) { throw new IllegalStateException(e); }
        return spans;
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
