package pdf_editor.view;

import view.glass.GlassButton;
import view.stack.Fonts;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;
import javax.swing.WindowConstants;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.Window;

/** Small modal messages in the app's style: a white rounded card with glass buttons. */
final class GlassDialogs {
    private static final Color INK = new Color(28, 28, 30);
    private static final Color MUTED = new Color(99, 99, 102);

    private GlassDialogs() {
    }

    static void message(Component owner, String title, String message) {
        show(owner, title, message, null);
    }

    /** True if the user chose {@code confirmLabel}, false for Cancel or closing the dialog. */
    static boolean confirm(Component owner, String title, String message, String confirmLabel) {
        return show(owner, title, message, confirmLabel);
    }

    private static boolean show(Component owner, String title, String message, String confirmLabel) {
        Window window = owner == null ? null : SwingUtilities.getWindowAncestor(owner);
        JDialog dialog = new JDialog(window, title, java.awt.Dialog.ModalityType.APPLICATION_MODAL);
        dialog.setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);
        JPanel panel = new JPanel(new BorderLayout(0, 16)) {
            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setColor(Color.WHITE);
                g2.fillRect(0, 0, getWidth(), getHeight());
                g2.dispose();
            }
        };
        panel.setBorder(BorderFactory.createEmptyBorder(24, 26, 20, 26));
        JLabel heading = new JLabel(title);
        heading.setFont(Fonts.ui(Font.BOLD, 19f));
        heading.setForeground(INK);
        JLabel body = new JLabel("<html><div style='width:360px'>" + escapeHtml(message) + "</div></html>");
        body.setFont(Fonts.ui(Font.PLAIN, 13f));
        body.setForeground(MUTED);
        JPanel content = new JPanel(new BorderLayout(0, 10));
        content.setOpaque(false);
        content.add(heading, BorderLayout.NORTH);
        content.add(body, BorderLayout.CENTER);
        panel.add(content, BorderLayout.CENTER);

        JPanel actions = new JPanel(new FlowLayout(FlowLayout.RIGHT, 9, 0));
        actions.setOpaque(false);
        boolean[] accepted = {false};
        if (confirmLabel != null) {
            actions.add(button("Cancel", dialog::dispose));
        }
        JButton ok = button(confirmLabel == null ? "OK" : confirmLabel, () -> {
            accepted[0] = true;
            dialog.dispose();
        });
        actions.add(ok);
        panel.add(actions, BorderLayout.SOUTH);
        dialog.setContentPane(panel);
        dialog.pack();
        dialog.setMinimumSize(new Dimension(430, 175));
        dialog.setLocationRelativeTo(window);
        dialog.getRootPane().setDefaultButton(ok);
        dialog.setVisible(true);
        return accepted[0];
    }

    private static JButton button(String title, Runnable action) {
        JButton b = new GlassButton(title, 34);
        b.addActionListener(e -> action.run());
        return b;
    }

    private static String escapeHtml(String value) {
        return value.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;");
    }
}
