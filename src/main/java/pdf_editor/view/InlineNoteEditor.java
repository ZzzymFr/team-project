package pdf_editor.view;

import pdf_editor.adapter.NoteTextDocumentFactory;
import pdf_editor.domain.PdfDocument;
import javax.swing.*;
import javax.swing.text.SimpleAttributeSet;
import javax.swing.text.StyleConstants;
import java.awt.*;
import java.awt.event.FocusAdapter;
import java.awt.event.FocusEvent;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;

/** Page-local editor: type directly on A4, then commit immutable note spans via its callback. */
public final class InlineNoteEditor extends JTextPane {
    private final Consumer<List<PdfDocument.NoteSpan>> commit;
    private boolean finished;

    public InlineNoteEditor(PdfDocument.Placement note, Consumer<List<PdfDocument.NoteSpan>> commit) {
        this.commit = Objects.requireNonNull(commit);
        setStyledDocument(NoteTextDocumentFactory.createPane(note).getStyledDocument());
        setFont(new Font("Segoe UI", Font.PLAIN, note.noteSize()));
        setOpaque(true);
        setBackground(Color.WHITE);
        setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(new Color(60, 126, 216), 2, true),
                BorderFactory.createEmptyBorder(4, 5, 4, 5)));
        addFocusListener(new FocusAdapter() {
            @Override public void focusLost(FocusEvent e) { finish(); }
        });
        getInputMap().put(KeyStroke.getKeyStroke("control ENTER"), "finish-inline-note");
        getActionMap().put("finish-inline-note", new AbstractAction() {
            @Override public void actionPerformed(java.awt.event.ActionEvent e) { finish(); }
        });
        getInputMap().put(KeyStroke.getKeyStroke("ESCAPE"), "finish-inline-note");
        getActionMap().put("finish-inline-note", new AbstractAction() {
            @Override public void actionPerformed(java.awt.event.ActionEvent e) { finish(); }
        });
        bindStyle("control B", StyleConstants.Bold);
        bindStyle("control I", StyleConstants.Italic);
        bindStyle("control U", StyleConstants.Underline);
    }
    private void bindStyle(String key, Object style) {
        String name = "inline-" + key;
        getInputMap().put(KeyStroke.getKeyStroke(key), name);
        getActionMap().put(name, new AbstractAction() {
            @Override public void actionPerformed(java.awt.event.ActionEvent e) { toggleStyle(style); }
        });
    }
    public void toggleStyle(Object style) { NoteTextFormatting.toggle(this, style); }
    public void fontSize(int size) {
        if (size < 8 || size > 72) throw new IllegalArgumentException("Font size out of range");
        SimpleAttributeSet attrs = new SimpleAttributeSet();
        StyleConstants.setFontSize(attrs, size);
        NoteTextFormatting.apply(this, attrs);
    }
    public void finish() {
        if (finished) return;
        finished = true;
        commit.accept(NoteTextDocumentFactory.toSpans(this));
    }
}
