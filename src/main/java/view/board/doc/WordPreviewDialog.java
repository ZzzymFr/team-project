package view.board.doc;

import view.board.BoardTheme;
import javax.swing.*;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.util.List;
import java.util.Objects;

/** Read-only styled preview. Its page navigation never mutates the PDF document. */
public final class WordPreviewDialog {
    private WordPreviewDialog() { }
    public static void show(Component parent, List<BufferedImage> images) {
        Objects.requireNonNull(images);
        if (images.isEmpty()) return;
        Window owner = SwingUtilities.getWindowAncestor(parent);
        JDialog dialog = new JDialog(owner, "PDF preview", Dialog.ModalityType.APPLICATION_MODAL);
        dialog.setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);
        JPanel root = new JPanel(new BorderLayout(0, 12));
        root.setBackground(new Color(241, 244, 249));
        root.setBorder(BorderFactory.createEmptyBorder(15, 17, 15, 17));
        JPanel header = new JPanel(new BorderLayout()); header.setOpaque(false);
        JLabel title = new JLabel("PDF preview");
        title.setFont(BoardTheme.font(17f)); header.add(title, BorderLayout.WEST);
        JLabel counter = new JLabel(); counter.setFont(BoardTheme.font(12f));
        header.add(counter, BorderLayout.EAST); root.add(header, BorderLayout.NORTH);
        JLabel sheet = new JLabel(); sheet.setVerticalAlignment(SwingConstants.TOP);
        sheet.setHorizontalAlignment(SwingConstants.CENTER);
        JScrollPane scroll = new JScrollPane(sheet);
        scroll.setBorder(BorderFactory.createLineBorder(new Color(214, 224, 238)));
        scroll.getViewport().setBackground(new Color(233, 238, 245));
        root.add(scroll, BorderLayout.CENTER);
        JPanel navigation = new JPanel(new FlowLayout(FlowLayout.CENTER, 9, 0));
        navigation.setOpaque(false);
        JButton back = pill("‹ Previous"); JButton next = pill("Next ›");
        JLabel current = new JLabel();current.setFont(BoardTheme.font(12f));
        navigation.add(back);navigation.add(current);navigation.add(next);
        root.add(navigation, BorderLayout.SOUTH);
        final int[] index = {0};
        Runnable render = () -> {
            sheet.setIcon(new ImageIcon(images.get(index[0])));
            current.setText("Page " + (index[0] + 1) + " / " + images.size());
            counter.setText(images.size() + (images.size() == 1 ? " page" : " pages"));
            back.setEnabled(index[0] > 0);next.setEnabled(index[0] < images.size() - 1);
            scroll.getViewport().setViewPosition(new Point(0, 0));
        };
        back.addActionListener(e -> {index[0]--;render.run();});
        next.addActionListener(e -> {index[0]++;render.run();});
        render.run();
        dialog.setContentPane(root);
        dialog.setSize(780, 760);dialog.setLocationRelativeTo(parent);dialog.setVisible(true);
    }
    private static JButton pill(String text) {
        JButton button = new JButton(text);
        button.setFont(BoardTheme.font(12f));button.setFocusPainted(false);
        button.setBackground(Color.WHITE);
        button.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(new Color(205, 215, 229), 1, true),
                BorderFactory.createEmptyBorder(8, 15, 8, 15)));
        return button;
    }
}
