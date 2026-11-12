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
    private final double zoom;
    private final Runnable textChanged;

    public InlineNoteEditor(PdfDocument.Placement note, Consumer<List<PdfDocument.NoteSpan>> commit) {
        this(note, 1.0, commit, () -> { });
    }
    public InlineNoteEditor(PdfDocument.Placement note, double zoom,
                            Consumer<List<PdfDocument.NoteSpan>> commit, Runnable textChanged) {
        this.commit = Objects.requireNonNull(commit);
        if (!Double.isFinite(zoom) || zoom <= 0) throw new IllegalArgumentException("Invalid zoom");
        this.zoom = zoom;
        this.textChanged = Objects.requireNonNull(textChanged);
        setStyledDocument(NoteTextDocumentFactory.createEditable(note, zoom).getStyledDocument());
        setFont(new Font("Segoe UI", Font.PLAIN, Math.max(1, (int) Math.round(note.noteSize() * zoom))));
        setOpaque(true);
        setBackground(Color.WHITE);
        // PPT-like text frame: border marks the box, with the same inner text margins as export.
        setBorder(BorderFactory.createLineBorder(new Color(60, 126, 216, 180), 1, true));
        setMargin(new Insets(5, 6, 5, 6));
        getDocument().addDocumentListener(new javax.swing.event.DocumentListener() {
            private void refresh() { SwingUtilities.invokeLater(() -> { if (!finished) InlineNoteEditor.this.textChanged.run(); }); }
            @Override public void insertUpdate(javax.swing.event.DocumentEvent e) { refresh(); }
            @Override public void removeUpdate(javax.swing.event.DocumentEvent e) { refresh(); }
            @Override public void changedUpdate(javax.swing.event.DocumentEvent e) { refresh(); }
        });
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
        StyleConstants.setFontSize(attrs, Math.max(1, (int) Math.round(size * zoom)));
        NoteTextFormatting.apply(this, attrs);
    }
    public void finish() {
        if (finished) return;
        finished = true;
        commit.accept(NoteTextDocumentFactory.toSpans(this, zoom));
    }
}
