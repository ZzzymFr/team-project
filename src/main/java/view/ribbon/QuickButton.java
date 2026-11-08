package view.ribbon;

import view.glass.GlassIcons;

import javax.swing.JButton;
import java.awt.Color;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.AffineTransform;
import java.awt.geom.RoundRectangle2D;

/**
 * A button of the title row, like Word's Save: just a coloured icon, no border; the background
 * lights up under the mouse and darkens while pressed. Its name is its tooltip.
 */
final class QuickButton extends JButton {
    private static final int SIZE = 28;
    private static final int ICON = 17;
    private static final int ARC = 8;
    private static final Color HOVER = new Color(0, 0, 0, 16);
    private static final Color PRESS = new Color(0, 0, 0, 30);

    private final GlassIcons.Glyph icon;
    private final Color color;

    QuickButton(String name, GlassIcons.Glyph icon, Color color) {
        this.icon = icon;
        this.color = color;
        setToolTipText(name);
        getAccessibleContext().setAccessibleName(name);
        setContentAreaFilled(false);
        setBorderPainted(false);
        setFocusPainted(false);
        setFocusable(false);
        setOpaque(false);
        setRolloverEnabled(true);
        setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
    }

    Color getIconColor() {
        return color;
    }

    @Override
    public Dimension getPreferredSize() {
        return new Dimension(SIZE + 4, SIZE);
    }

    @Override
    protected void paintComponent(Graphics g) {
        Graphics2D g2 = (Graphics2D) g.create();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);
        if (getModel().isPressed()) {
            g2.setColor(PRESS);
            g2.fill(new RoundRectangle2D.Double(0, 0, getWidth(), getHeight(), ARC, ARC));
        } else if (getModel().isRollover()) {
            g2.setColor(HOVER);
            g2.fill(new RoundRectangle2D.Double(0, 0, getWidth(), getHeight(), ARC, ARC));
        }
        AffineTransform t = g2.getTransform();
        g2.translate(getWidth() / 2.0, getHeight() / 2.0);
        g2.scale(ICON / 20.0, ICON / 20.0);
        g2.setColor(isEnabled() ? color : new Color(0, 0, 0, 70));
        icon.paint(g2);
        g2.setTransform(t);
        g2.dispose();
    }
}
