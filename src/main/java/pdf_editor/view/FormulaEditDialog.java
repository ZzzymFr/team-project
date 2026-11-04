package pdf_editor.view;

import pdf_editor.domain.PdfMaterial;
import pdf_editor.data_access.MaterialRenderer;
import view.glass.GlassButton;
import view.stack.Fonts;

import javax.swing.*;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import java.awt.*;

/** Swing-only text editing adapter. The domain validates material type and the use case applies changes. */
public final class FormulaEditDialog {
    public record Result(String formula, String result) { }
    private FormulaEditDialog() { }

    public static Result show(Window owner, PdfMaterial item) {
        if (item.isGraph()) throw new IllegalArgumentException("Graph images are not typeset formulas");
        JDialog dialog = new JDialog(owner, "Edit Formula", Dialog.ModalityType.APPLICATION_MODAL);
        dialog.setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);
        JPanel root = new JPanel(new BorderLayout(0, 12));
        root.setBackground(Color.WHITE);
        root.setBorder(BorderFactory.createEmptyBorder(22, 26, 22, 26));
        JLabel heading = new JLabel("Edit formula or type LaTeX");
        heading.setFont(Fonts.ui(Font.BOLD, 19f));
        heading.setForeground(new Color(35, 52, 77));
        root.add(heading, BorderLayout.NORTH);
        JPanel fields = new JPanel(new GridLayout(2, 1, 0, 10));
        fields.setOpaque(false);
        JTextArea formula = new JTextArea(item.formula(), 3, 38);
        JTextArea answer = new JTextArea(item.result(), 2, 38);
        formula.setLineWrap(true); answer.setLineWrap(true);
        formula.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 15));
        answer.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 15));
        fields.add(field("Formula / LaTeX (e.g. \\frac{1}{2} or x^2)", formula));
        fields.add(field("Result (optional)", answer));
        JLabel preview = new JLabel();
        preview.setOpaque(true); preview.setBackground(new Color(248, 251, 255));
        preview.setHorizontalAlignment(SwingConstants.CENTER);
        preview.setBorder(BorderFactory.createLineBorder(new Color(216, 226, 241)));
        Runnable render = () -> preview.setIcon(new ImageIcon(MaterialRenderer.render(
                item.withText(formula.getText(), answer.getText()), 530, 100)));
        DocumentListener refresh = new DocumentListener() {
            public void insertUpdate(DocumentEvent e) { render.run(); }
            public void removeUpdate(DocumentEvent e) { render.run(); }
            public void changedUpdate(DocumentEvent e) { render.run(); }
        };
        formula.getDocument().addDocumentListener(refresh);
        answer.getDocument().addDocumentListener(refresh);
        render.run();
        JPanel middle = new JPanel(new BorderLayout(0, 12));
        middle.setOpaque(false); middle.add(fields, BorderLayout.CENTER);
        middle.add(preview, BorderLayout.SOUTH);
        root.add(middle, BorderLayout.CENTER);
        JLabel help = new JLabel("Keyboard text and LaTeX are both accepted. Invalid LaTeX is shown as plain text.");
        help.setFont(Fonts.ui(Font.PLAIN, 12f)); help.setForeground(new Color(107, 124, 150));
        JButton cancel = new GlassButton("Cancel", 34);
        JButton save = new GlassButton("Apply Changes", 34);
        final Result[] chosen = {null};
        cancel.addActionListener(e -> dialog.dispose());
        save.addActionListener(e -> { chosen[0] = new Result(formula.getText(), answer.getText()); dialog.dispose(); });
        JPanel actions = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        actions.setOpaque(false); actions.add(cancel); actions.add(save);
        JPanel bottom = new JPanel(new BorderLayout()); bottom.setOpaque(false);
        bottom.add(help, BorderLayout.NORTH); bottom.add(actions, BorderLayout.SOUTH);
        root.add(bottom, BorderLayout.SOUTH);
        dialog.setContentPane(root); dialog.pack(); dialog.setMinimumSize(new Dimension(610, 430));
        dialog.setLocationRelativeTo(owner); dialog.getRootPane().setDefaultButton(save);
        dialog.setVisible(true);
        return chosen[0];
    }
    private static JPanel field(String label, JTextArea area) {
        JPanel group = new JPanel(new BorderLayout(0, 4)); group.setOpaque(false);
        JLabel name = new JLabel(label); name.setFont(Fonts.ui(Font.BOLD, 12f));
        group.add(name, BorderLayout.NORTH);
        JScrollPane scroll = new JScrollPane(area);
        scroll.setBorder(BorderFactory.createLineBorder(new Color(205, 219, 239)));
        group.add(scroll, BorderLayout.CENTER);
        return group;
    }
}
