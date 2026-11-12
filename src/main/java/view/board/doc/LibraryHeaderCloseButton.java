package view.board.doc;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;

/** Clear, compact panel close control, distinct from each card's plain delete ×. */
final class LibraryHeaderCloseButton extends JButton {
    private boolean hovered;

    LibraryHeaderCloseButton(Runnable closeLibrary) {
        super();
        setName("libraryCloseButton");
        setToolTipText("Close Library");
        getAccessibleContext().setAccessibleName("Close Library");
        setPreferredSize(new Dimension(29, 27));
        setMinimumSize(new Dimension(29, 27));
        setContentAreaFilled(false);
        setFocusPainted(false);
        setOpaque(false);
        setBorder(BorderFactory.createEmptyBorder());
        setCursor(java.awt.Cursor.getPredefinedCursor(java.awt.Cursor.HAND_CURSOR));
        addActionListener(e -> closeLibrary.run());
        addMouseListener(new MouseAdapter() {
            @Override public void mouseEntered(MouseEvent e) { hovered = true; repaint(); }
            @Override public void mouseExited(MouseEvent e) { hovered = false; repaint(); }
        });
    }

    @Override protected void paintComponent(Graphics graphics) {
        Graphics2D g = (Graphics2D) graphics.create();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        int w = getWidth(), h = getHeight();
        g.setColor(hovered ? new Color(231, 236, 244) : new Color(246, 248, 251));
        g.fillRoundRect(1, 1, w - 3, h - 3, 11, 11);
        g.setColor(new Color(213, 218, 227));
        g.setStroke(new BasicStroke(1f));
        g.drawRoundRect(1, 1, w - 3, h - 3, 11, 11);
        g.setColor(new Color(79, 88, 103));
        g.setStroke(new BasicStroke(1.8f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        double cx = (w - 1) / 2.0, cy = (h - 1) / 2.0;
        g.drawLine((int)(cx - 4), (int)(cy - 4), (int)(cx + 4), (int)(cy + 4));
        g.drawLine((int)(cx + 4), (int)(cy - 4), (int)(cx - 4), (int)(cy + 4));
        g.dispose();
        super.paintComponent(graphics);
    }
}
