package view.glass;

import view.stack.Fonts;

import javax.swing.JComponent;
import javax.swing.Timer;
import java.awt.Color;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.geom.RoundRectangle2D;
import java.util.ArrayList;
import java.util.List;
import java.util.function.IntConsumer;

/**
 * A liquid-glass segmented control: a glass track holding equal segments, with a tinted glass
 * lens over the chosen one. Choosing another segment slides the lens across; it stretches like
 * a drop of liquid while it moves and settles back into shape when it arrives.
 */
public class GlassSegmentedControl extends JComponent {
    private static final int HEIGHT = 32;
    private static final int INSET = 3;          // gap between the track edge and the lens
    private static final int SEGMENT_PADDING = 14;
    private static final int DURATION_MS = 340;
    private static final Color LABEL_SELECTED = new Color(0, 98, 210);

    private final String[] labels;
    private final List<IntConsumer> listeners = new ArrayList<>();
    private final Timer animator = new Timer(15, e -> step());
    private int selected;
    private int hoverIndex = -1;
    private double lens;      // lens position in segments: 0 = first segment, 1 = second, ...
    private double lensFrom;
    private long animationStart;

    public GlassSegmentedControl(String... labels) {
        if (labels.length == 0) {
            throw new IllegalArgumentException("A segmented control needs at least one segment.");
        }
        this.labels = labels.clone();
        setFont(Fonts.ui(Font.PLAIN, 13f));
        setOpaque(false);
        setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        MouseAdapter mouse = new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent e) {
                int index = segmentAt(e.getX());
                if (index >= 0 && index != selected) {
                    setSelectedIndex(index);
                    for (IntConsumer listener : new ArrayList<>(listeners)) {
                        listener.accept(index);
                    }
                }
            }

            @Override
            public void mouseMoved(MouseEvent e) {
                int index = segmentAt(e.getX());
                if (index != hoverIndex) {
                    hoverIndex = index;
                    repaint();
                }
            }

            @Override
            public void mouseExited(MouseEvent e) {
                hoverIndex = -1;
                repaint();
            }
        };
        addMouseListener(mouse);
        addMouseMotionListener(mouse);
    }

    /** Called with the new index when the user picks a segment (not when it is set in code). */
    public void addSelectionListener(IntConsumer listener) {
        listeners.add(listener);
    }

    public int getSelectedIndex() {
        return selected;
    }

    /** Moves the lens to the segment, sliding it if the control is on screen. Does not notify listeners. */
    public void setSelectedIndex(int index) {
        if (index < 0 || index >= labels.length || index == selected && !animator.isRunning()) {
            return;
        }
        selected = index;
        if (isShowing()) {
            lensFrom = lens;
            animationStart = System.nanoTime();
            animator.start();
        } else {
            lens = index;
            repaint();
        }
    }

    private double progress() {
        return Math.min(1, (System.nanoTime() - animationStart) / (DURATION_MS * 1_000_000.0));
    }

    private void step() {
        double t = progress();
        double eased = 1 - Math.pow(1 - t, 3);
        lens = lensFrom + (selected - lensFrom) * eased;
        if (t >= 1) {
            lens = selected;
            animator.stop();
        }
        repaint();
    }

    // ---------- geometry ----------

    private double segmentWidth() {
        FontMetrics fm = getFontMetrics(getFont());
        int widest = 0;
        for (String label : labels) {
            widest = Math.max(widest, fm.stringWidth(label));
        }
        return widest + 2.0 * SEGMENT_PADDING;
    }

    private RoundRectangle2D track() {
        double s = Glass.SHADOW;
        return Glass.capsule(s, s, getWidth() - 2 * s, getHeight() - 2 * s);
    }

    /** Segments share the track's inner width equally. */
    private double innerSegmentWidth() {
        return (track().getWidth() - 2 * INSET) / labels.length;
    }

    private int segmentAt(int x) {
        RoundRectangle2D track = track();
        double offset = x - track.getX() - INSET;
        int index = (int) Math.floor(offset / innerSegmentWidth());
        return offset < 0 || index >= labels.length ? -1 : index;
    }

    @Override
    public Dimension getPreferredSize() {
        int width = (int) Math.ceil(labels.length * segmentWidth()) + 2 * INSET + 2 * Glass.SHADOW;
        return new Dimension(width, HEIGHT + 2 * Glass.SHADOW);
    }

    @Override
    public Dimension getMinimumSize() {
        return getPreferredSize();
    }

    @Override
    public Dimension getMaximumSize() {
        return getPreferredSize();
    }

    // ---------- painting ----------

    @Override
    protected void paintComponent(Graphics g) {
        Graphics2D g2 = (Graphics2D) g.create();
        Glass.setUp(g2);
        RoundRectangle2D track = track();
        double seg = innerSegmentWidth();
        double lensHeight = track.getHeight() - 2 * INSET;

        // the track: clear glass with a faint cool tint, so the lens stands out on it
        Glass.paint(g2, track, new Color(148, 163, 184), 0.3, 0, 0, 0);

        // a soft glow under the segment the mouse is over
        if (hoverIndex >= 0 && hoverIndex != selected && !animator.isRunning()) {
            g2.setColor(new Color(255, 255, 255, 120));
            g2.fill(Glass.capsule(track.getX() + INSET + hoverIndex * seg, track.getY() + INSET, seg, lensHeight));
        }

        // the lens: stretches sideways and flattens a little mid-slide, like a moving drop
        double stretch = animator.isRunning() ? Math.sin(Math.PI * progress()) : 0;
        double lensWidth = seg + seg * 0.32 * stretch;
        double squash = 2.5 * stretch;
        double lensX = track.getX() + INSET + lens * seg - (lensWidth - seg) / 2;
        lensX = Math.max(track.getX() + 1, Math.min(lensX, track.getX() + track.getWidth() - lensWidth - 1));
        RoundRectangle2D lensShape = Glass.capsule(lensX, track.getY() + INSET + squash / 2,
                lensWidth, lensHeight - squash);
        Glass.paint(g2, lensShape, Glass.ACCENT, 0.55, 0, 0, 1);

        // labels: they take the accent colour as the lens passes over them
        g2.setFont(getFont());
        FontMetrics fm = g2.getFontMetrics();
        int textY = (int) Math.round((getHeight() + fm.getAscent() - fm.getDescent()) / 2.0);
        for (int i = 0; i < labels.length; i++) {
            double closeness = Math.max(0, 1 - Math.abs(i - lens));
            double centre = track.getX() + INSET + (i + 0.5) * seg;
            Glass.drawText(g2, labels[i], centre, textY, Glass.mix(Glass.TEXT_MUTED, LABEL_SELECTED, closeness));
        }
        g2.dispose();
    }
}
