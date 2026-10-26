package view.stack;

import javax.swing.JComponent;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.geom.Line2D;
import java.awt.geom.Path2D;
import java.awt.geom.RoundRectangle2D;

/** A flat Windows-style caption button: minimize, maximize/restore or close. */
public final class WindowButton extends JComponent {
    public enum Kind { MINIMIZE, MAXIMIZE, RESTORE, CLOSE }

    private static final Color GLYPH = new Color(51, 65, 85);
    private static final Color CLOSE_RED = new Color(232, 17, 35);

    private Kind kind;
    private boolean hover;
    private boolean pressed;

    public WindowButton(Kind kind, Runnable action) {
        this.kind = kind;
        setPreferredSize(new Dimension(42, 30));
        setToolTipText(tooltipFor(kind));
        addMouseListener(new MouseAdapter() {
            @Override
            public void mouseEntered(MouseEvent e) {
                hover = true;
                repaint();
            }

            @Override
            public void mouseExited(MouseEvent e) {
                hover = false;
                pressed = false;
                repaint();
            }

            @Override
            public void mousePressed(MouseEvent e) {
                pressed = true;
                repaint();
            }

            @Override
            public void mouseReleased(MouseEvent e) {
                boolean run = pressed && hover;
                pressed = false;
                repaint();
                if (run) {
                    action.run();
                }
            }
        });
    }

    public void setKind(Kind kind) {
        this.kind = kind;
        setToolTipText(tooltipFor(kind));
        repaint();
    }

    private static String tooltipFor(Kind kind) {
        switch (kind) {
            case MINIMIZE:
                return "Minimize";
            case MAXIMIZE:
                return "Maximize";
            case RESTORE:
                return "Restore";
            default:
                return "Close";
        }
    }

    @Override
    protected void paintComponent(Graphics g) {
        Graphics2D g2 = (Graphics2D) g.create();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        int w = getWidth();
        int h = getHeight();
        if (hover) {
            if (kind == Kind.CLOSE) {
                g2.setColor(pressed ? CLOSE_RED.darker() : CLOSE_RED);
            } else {
                g2.setColor(new Color(15, 23, 42, pressed ? 40 : 22));
            }
            g2.fill(new RoundRectangle2D.Double(1, 1, w - 2, h - 2, 9, 9));
        }
        g2.setColor(hover && kind == Kind.CLOSE ? Color.WHITE : GLYPH);
        g2.setStroke(new BasicStroke(1.3f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        double cx = w / 2.0;
        double cy = h / 2.0;
        double s = 5;
        switch (kind) {
            case MINIMIZE:
                g2.draw(new Line2D.Double(cx - s, cy, cx + s, cy));
                break;
            case MAXIMIZE:
                g2.draw(new RoundRectangle2D.Double(cx - s, cy - s, 2 * s, 2 * s, 3, 3));
                break;
            case RESTORE:
                g2.draw(new RoundRectangle2D.Double(cx - s, cy - s + 2.5, 2 * s - 2.5, 2 * s - 2.5, 3, 3));
                Path2D back = new Path2D.Double();
                back.moveTo(cx - s + 2.5, cy - s + 2.5);
                back.lineTo(cx - s + 2.5, cy - s);
                back.lineTo(cx + s, cy - s);
                back.lineTo(cx + s, cy + s - 2.5);
                back.lineTo(cx + s - 2.5, cy + s - 2.5);
                g2.draw(back);
                break;
            default:
                g2.draw(new Line2D.Double(cx - s, cy - s, cx + s, cy + s));
                g2.draw(new Line2D.Double(cx - s, cy + s, cx + s, cy - s));
                break;
        }
        g2.dispose();
    }
}
