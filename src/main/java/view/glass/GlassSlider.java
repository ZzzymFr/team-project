package view.glass;

import javax.swing.JComponent;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Line2D;
import java.util.ArrayList;
import java.util.List;
import java.util.function.DoubleConsumer;

/**
 * A horizontal slider made of liquid glass: a clear capsule holding a thin track, filled in the
 * accent colour up to a white knob. The value is a fraction from 0 (left) to 1 (right). Click or
 * drag to move it; it snaps to its default value (marked with a tick) when close, and a double-click
 * goes back to it. Only the user's moves reach the listeners, not {@link #setValue}.
 */
public class GlassSlider extends JComponent {
    private static final int TRACK_INSET = 18;   // from the capsule ends to the ends of the track
    private static final float TRACK_WIDTH = 4f;
    private static final int KNOB_RADIUS = 9;
    private static final double SNAP = 0.02;
    private static final Color TRACK = new Color(0, 0, 0, 38);
    private static final Color TICK = new Color(0, 0, 0, 70);

    private final int width;
    private final int height;
    private final double defaultValue;
    private final List<DoubleConsumer> listeners = new ArrayList<>();
    private double value;

    /** A slider of the given capsule size (its shadow adds a little around it), at its default value. */
    public GlassSlider(int width, int height, double defaultValue) {
        this.width = width;
        this.height = height;
        this.defaultValue = clamp(defaultValue);
        this.value = this.defaultValue;
        setOpaque(false);
        MouseAdapter mouse = new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent e) {
                if (e.getButton() != MouseEvent.BUTTON1) {
                    return;
                }
                userMoved(e.getClickCount() == 2 ? GlassSlider.this.defaultValue : valueAt(e.getX()));
            }

            @Override
            public void mouseDragged(MouseEvent e) {
                userMoved(valueAt(e.getX()));
            }
        };
        addMouseListener(mouse);
        addMouseMotionListener(mouse);
    }

    public double getValue() {
        return value;
    }

    /** Moves the knob without telling the listeners, e.g. to follow a change made elsewhere. */
    public void setValue(double value) {
        double v = clamp(value);
        if (v != this.value) {
            this.value = v;
            repaint();
        }
    }

    /** Called with the new value whenever the user moves the slider. */
    public void addValueListener(DoubleConsumer listener) {
        listeners.add(listener);
    }

    /** The value under an x position, snapped to the default when close to it. */
    public double valueAt(int x) {
        double left = trackLeft();
        double v = clamp((x - left) / (trackRight() - left));
        return Math.abs(v - defaultValue) < SNAP ? defaultValue : v;
    }

    private void userMoved(double v) {
        setValue(v);
        for (DoubleConsumer l : new ArrayList<>(listeners)) {
            l.accept(value);
        }
    }

    private double trackLeft() {
        return Glass.SHADOW + TRACK_INSET;
    }

    private double trackRight() {
        return getWidth() - Glass.SHADOW - TRACK_INSET;
    }

    private static double clamp(double v) {
        return Math.max(0, Math.min(1, v));
    }

    @Override
    public Dimension getPreferredSize() {
        return new Dimension(width + 2 * Glass.SHADOW, height + 2 * Glass.SHADOW);
    }

    @Override
    protected void paintComponent(Graphics g) {
        Graphics2D g2 = (Graphics2D) g.create();
        Glass.setUp(g2);
        double s = Glass.SHADOW;
        Glass.paint(g2, Glass.rounded(s, s, getWidth() - 2 * s, getHeight() - 2 * s), null, 0, 0, 0, 0);

        double left = trackLeft();
        double right = trackRight();
        double y = getHeight() / 2.0;
        double knobX = left + value * (right - left);
        double tickX = left + defaultValue * (right - left);
        g2.setStroke(new BasicStroke(1.2f));
        g2.setColor(TICK);
        g2.draw(new Line2D.Double(tickX, y - 7, tickX, y + 7));
        g2.setStroke(new BasicStroke(TRACK_WIDTH, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        g2.setColor(TRACK);
        g2.draw(new Line2D.Double(left, y, right, y));
        g2.setColor(Glass.ACCENT);
        g2.draw(new Line2D.Double(left, y, knobX, y));

        double r = KNOB_RADIUS;
        for (int i = 1; i <= 3; i++) {
            g2.setColor(new Color(0, 0, 0, 12));
            g2.fill(new Ellipse2D.Double(knobX - r - i * 0.5, y - r + i * 0.6, 2 * r + i, 2 * r + i * 0.4));
        }
        Ellipse2D knob = new Ellipse2D.Double(knobX - r, y - r, 2 * r, 2 * r);
        g2.setColor(Color.WHITE);
        g2.fill(knob);
        g2.setStroke(new BasicStroke(1f));
        g2.setColor(Glass.EDGE);
        g2.draw(knob);
        g2.dispose();
    }
}
