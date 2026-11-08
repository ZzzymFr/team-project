package view.glass;

import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.plaf.basic.BasicScrollBarUI;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.RenderingHints;

/** A slim scroll bar: a thin rounded thumb that darkens under the mouse and while dragged, and no track or arrows. */
public final class SlimScrollBarUI extends BasicScrollBarUI {
    @Override
    protected void configureScrollBarColors() {
        thumbColor = new Color(0, 0, 0, 60);
        trackColor = new Color(0, 0, 0, 0);
    }

    @Override
    protected JButton createDecreaseButton(int orientation) {
        return invisibleButton();
    }

    @Override
    protected JButton createIncreaseButton(int orientation) {
        return invisibleButton();
    }

    private static JButton invisibleButton() {
        JButton b = new JButton();
        b.setPreferredSize(new Dimension(0, 0));
        b.setMinimumSize(new Dimension(0, 0));
        b.setMaximumSize(new Dimension(0, 0));
        return b;
    }

    @Override
    protected void paintTrack(Graphics g, JComponent c, Rectangle bounds) {
    }

    @Override
    protected void paintThumb(Graphics g, JComponent c, Rectangle bounds) {
        if (bounds.isEmpty()) {
            return;
        }
        Graphics2D g2 = (Graphics2D) g.create();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2.setColor(new Color(0, 0, 0, isDragging ? 110 : isThumbRollover() ? 85 : 60));
        g2.fillRoundRect(bounds.x + 2, bounds.y + 3, Math.max(3, bounds.width - 4), Math.max(4, bounds.height - 6), 9, 9);
        g2.dispose();
    }
}
