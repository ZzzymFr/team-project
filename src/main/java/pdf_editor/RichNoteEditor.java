package pdf_editor;

import javax.swing.*;
import javax.swing.text.*;
import java.awt.*;
import java.awt.event.*;
import java.util.ArrayList;
import java.util.List;

public final class RichNoteEditor {
    private RichNoteEditor() { }

    public static List<PdfDocument.NoteSpan> edit(Component parent, PdfDocument.Placement note) {
        JTextPane pane = createPane(note);
        pane.setPreferredSize(new Dimension(540, 270));
        JPanel panel = new JPanel(new BorderLayout(8, 8));
        JToolBar bar = new JToolBar(); bar.setFloatable(false);
        addToggle(bar, pane, "B", StyleConstants.Bold);
        addToggle(bar, pane, "I", StyleConstants.Italic);
        addToggle(bar, pane, "U", StyleConstants.Underline);
        bar.addSeparator();
        JComboBox<Integer> sizes = new JComboBox<>(new Integer[]{8, 10, 12, 14, 16, 18, 20, 24, 28, 32, 36, 48, 60, 72});
        sizes.setSelectedItem(note.noteSize);
        sizes.addActionListener(e -> {
            Integer size = (Integer) sizes.getSelectedItem();
            if (size == null) return;
            SimpleAttributeSet attrs = new SimpleAttributeSet();
            StyleConstants.setFontSize(attrs, size);
            apply(pane, attrs);
        });
        bar.add(new JLabel("Size ")); bar.add(sizes);
        panel.add(bar, BorderLayout.NORTH);
        panel.add(new JScrollPane(pane), BorderLayout.CENTER);
        panel.add(new JLabel("Select text, then apply formatting. Ctrl+B / Ctrl+I / Ctrl+U supported."), BorderLayout.SOUTH);
        installShortcut(pane, "control B", StyleConstants.Bold);
        installShortcut(pane, "control I", StyleConstants.Italic);
        installShortcut(pane, "control U", StyleConstants.Underline);
        int choice = JOptionPane.showConfirmDialog(parent, panel, "Edit Note", JOptionPane.OK_CANCEL_OPTION,
                JOptionPane.PLAIN_MESSAGE);
        if (choice != JOptionPane.OK_OPTION) return null;
        return readSpans(pane.getStyledDocument());
    }

    static JTextPane createPane(PdfDocument.Placement note) {
        JTextPane pane = new JTextPane();
        pane.setFont(new Font("Segoe UI", Font.PLAIN, 16));
        StyledDocument doc = pane.getStyledDocument();
        try {
            if (note.noteSpans.isEmpty()) {
                SimpleAttributeSet attrs = new SimpleAttributeSet();
                StyleConstants.setFontSize(attrs, note.noteSize);
                StyleConstants.setBold(attrs, (note.noteStyle & Font.BOLD) != 0);
                StyleConstants.setItalic(attrs, (note.noteStyle & Font.ITALIC) != 0);
                doc.insertString(0, note.noteText == null ? "" : note.noteText, attrs);
            } else {
                for (PdfDocument.NoteSpan span : note.noteSpans)
                    doc.insertString(doc.getLength(), span.text, attributes(span));
            }
        } catch (BadLocationException ex) { throw new IllegalStateException(ex); }
        return pane;
    }

    private static AttributeSet attributes(PdfDocument.NoteSpan span) {
        SimpleAttributeSet attrs = new SimpleAttributeSet();
        StyleConstants.setFontFamily(attrs, "Segoe UI");
        StyleConstants.setFontSize(attrs, span.size);
        StyleConstants.setBold(attrs, span.bold);
        StyleConstants.setItalic(attrs, span.italic);
        StyleConstants.setUnderline(attrs, span.underline);
        StyleConstants.setForeground(attrs, new Color(31, 48, 72));
        return attrs;
    }

    private static void addToggle(JToolBar bar, JTextPane pane, String label, Object attribute) {
        JButton button = new JButton(label);
        if (attribute == StyleConstants.Bold) button.setFont(button.getFont().deriveFont(Font.BOLD));
        if (attribute == StyleConstants.Italic) button.setFont(button.getFont().deriveFont(Font.ITALIC));
        button.addActionListener(e -> toggle(pane, attribute));
        bar.add(button);
    }

    private static void installShortcut(JTextPane pane, String key, Object attribute) {
        String action = "note-" + key;
        pane.getInputMap().put(KeyStroke.getKeyStroke(key), action);
        pane.getActionMap().put(action, new AbstractAction() {
            @Override public void actionPerformed(ActionEvent e) { toggle(pane, attribute); }
        });
    }

    private static void toggle(JTextPane pane, Object attribute) {
        NoteTextFormatting.toggle(pane, attribute);
    }

    private static void apply(JTextPane pane, AttributeSet attrs) {
        NoteTextFormatting.apply(pane, attrs);
    }

    static List<PdfDocument.NoteSpan> readSpans(StyledDocument doc) {
        List<PdfDocument.NoteSpan> spans = new ArrayList<>();
        try {
            int i = 0;
            while (i < doc.getLength()) {
                Element element = doc.getCharacterElement(i);
                int end = Math.min(doc.getLength(), element.getEndOffset());
                AttributeSet a = element.getAttributes();
                spans.add(new PdfDocument.NoteSpan(doc.getText(i, end - i),
                        StyleConstants.isBold(a), StyleConstants.isItalic(a),
                        StyleConstants.isUnderline(a), Math.max(8, StyleConstants.getFontSize(a))));
                i = end;
            }
        } catch (BadLocationException ex) { throw new IllegalStateException(ex); }
        return spans;
    }
}
