package view.glass;

import view.stack.Fonts;

import javax.swing.JComponent;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;

/** A short line of text on a glass capsule, e.g. a hint floating over a canvas. */
public class GlassLabel extends JComponent {
    private static final int HEIGHT = 28;
    private static final int PADDING = 14;

    private String text;

    public GlassLabel(String text) {
        this.text = text;
        setFont(Fonts.ui(Font.PLAIN, 12f));
        setForeground(Glass.TEXT_MUTED);
        setOpaque(false);
    }

    public void setText(String text) {
        this.text = text;
        revalidate();
        repaint();
    }

    public String getText() {
        return text;
    }

    @Override
    public Dimension getPreferredSize() {
        FontMetrics fm = getFontMetrics(getFont());
        return new Dimension(fm.stringWidth(text) + 2 * PADDING + 2 * Glass.SHADOW, HEIGHT + 2 * Glass.SHADOW);
    }

    @Override
    protected void paintComponent(Graphics g) {
        Graphics2D g2 = (Graphics2D) g.create();
        Glass.setUp(g2);
        double s = Glass.SHADOW;
        Glass.paint(g2, Glass.capsule(s, s, getWidth() - 2 * s, getHeight() - 2 * s), null, 0, 0, 0, 0);
        g2.setFont(getFont());
        FontMetrics fm = g2.getFontMetrics();
        int textY = (int) Math.round((getHeight() + fm.getAscent() - fm.getDescent()) / 2.0);
        Glass.drawText(g2, text, getWidth() / 2.0, textY, getForeground());
        g2.dispose();
    }
}
