package view.glass;

import view.stack.Fonts;

import javax.swing.JButton;
import javax.swing.Timer;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;

/**
 * A push button made of liquid glass: a translucent capsule that brightens under the mouse and
 * sinks slightly when pressed, with smooth transitions. Works like any JButton (addActionListener).
 */
public class GlassButton extends JButton {
    private static final int HEIGHT = 30;
    private static final int PADDING = 16;

    private double hover;
    private double press;
    private boolean hovering;
    private final Timer animator = new Timer(15, e -> step());

    public GlassButton(String text) {
        super(text);
        setFont(Fonts.ui(Font.PLAIN, 13f));
        setForeground(Glass.TEXT);
        setContentAreaFilled(false);
        setBorderPainted(false);
        setFocusPainted(false);
        setFocusable(false);
        setOpaque(false);
        setRolloverEnabled(true);
        addMouseListener(new MouseAdapter() {
            @Override
            public void mouseEntered(MouseEvent e) {
                hovering = true;
                animator.start();
            }

            @Override
            public void mouseExited(MouseEvent e) {
                hovering = false;
                animator.start();
            }

            @Override
            public void mousePressed(MouseEvent e) {
                animator.start();
            }

            @Override
            public void mouseReleased(MouseEvent e) {
                animator.start();
            }
        });
    }

    /** Eases hover and press towards their targets; stops once both arrive. */
    private void step() {
        double hoverTarget = hovering && isEnabled() ? 1 : 0;
        double pressTarget = getModel().isPressed() ? 1 : 0;
        hover += (hoverTarget - hover) * 0.25;
        press += (pressTarget - press) * 0.45;
        if (Math.abs(hoverTarget - hover) < 0.01 && Math.abs(pressTarget - press) < 0.01) {
            hover = hoverTarget;
            press = pressTarget;
            animator.stop();
        }
        repaint();
    }

    @Override
    public Dimension getPreferredSize() {
        FontMetrics fm = getFontMetrics(getFont());
        return new Dimension(fm.stringWidth(getText()) + 2 * PADDING + 2 * Glass.SHADOW,
                HEIGHT + 2 * Glass.SHADOW);
    }

    @Override
    protected void paintComponent(Graphics g) {
        Graphics2D g2 = (Graphics2D) g.create();
        Glass.setUp(g2);
        double sink = press * 0.6;  // the glass dips a little when pressed
        double s = Glass.SHADOW;
        double w = getWidth() - 2 * s - 2 * sink;
        double h = getHeight() - 2 * s - 2 * sink;
        Glass.paint(g2, Glass.capsule(s + sink, s + sink * 0.5, w, h), null, 0, hover, press, 0);

        g2.setFont(getFont());
        FontMetrics fm = g2.getFontMetrics();
        int textY = (int) Math.round((getHeight() + fm.getAscent() - fm.getDescent()) / 2.0 + sink * 0.5);
        Glass.drawText(g2, getText(), getWidth() / 2.0, textY,
                isEnabled() ? getForeground() : Glass.withAlpha(Glass.TEXT_MUTED, 110));
        g2.dispose();
    }
}
