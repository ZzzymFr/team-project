package view.glass;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.FontMetrics;
import java.awt.GradientPaint;
import java.awt.Graphics2D;
import java.awt.LinearGradientPaint;
import java.awt.Paint;
import java.awt.RenderingHints;
import java.awt.geom.Area;
import java.awt.geom.RoundRectangle2D;

/**
 * Paints the "liquid glass" material used by the glass controls, in the layers of WWDC25 "Meet
 * Liquid Glass": a soft shadow, a barely tinted body so ink shows through, a gloss on the upper
 * half, a dark hairline, and a specular rim brightest at the top-left and bottom-right. The
 * lensing itself is pixel work, see {@link GlassOptics}; color is kept for primary elements only.
 *
 * <p>Apple does not publish numeric values; every constant here is an estimate.
 */
public final class Glass {
    /** The window's background, secondarySystemBackground (light). */
    public static final Color CANVAS = new Color(0xF2F2F7);
    /** Apple-style system blue, used for the selected state. */
    public static final Color ACCENT = new Color(0x007AFF);
    static final Color TEXT = new Color(0, 0, 0, 220);
    /** Secondary text on glass. */
    public static final Color TEXT_MUTED = new Color(51, 65, 85);
    static final Color TEXT_DISABLED = new Color(0, 0, 0, 70);
    static final Color PLATTER = new Color(0, 0, 0, 18);    // selection: a solid fill, not a second glass layer
    static final Color TINT = new Color(255, 255, 255, 38);  // low, so strokes show through
    static final Color EDGE = new Color(0, 0, 0, 24);
    static final Color HALO = new Color(242, 242, 247, 170);  // legibility halo behind labels and icons
    static final int GLOSS_ALPHA = 28;
    static final int RIM_TOP_ALPHA = 230;
    static final int RIM_BOTTOM_ALPHA = 110;
    static final float DROP_SHADOW = 0.12f;

    /** Room around a glass shape for its shadow. */
    static final int SHADOW = 3;

    private static final Color[] INK_COLORS = {new Color(0x1C1C1E), new Color(255, 59, 48), new Color(0x007AFF),
        new Color(52, 199, 89), new Color(255, 149, 0)};

    private Glass() {
    }

    /** The ink colours on offer: the default ink first, then systemRed, Blue, Green and Orange. */
    public static Color[] inkColors() {
        return INK_COLORS.clone();
    }

    /** A capsule (fully rounded ends) filling the rectangle. */
    static RoundRectangle2D capsule(double x, double y, double w, double h) {
        return new RoundRectangle2D.Double(x, y, w, h, h, h);
    }

    static void setUp(Graphics2D g2) {
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);
        g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g2.setRenderingHint(RenderingHints.KEY_FRACTIONALMETRICS, RenderingHints.VALUE_FRACTIONALMETRICS_ON);
    }

    /**
     * Paints one piece of glass.
     *
     * @param tint     colour mixed into the body (null for clear glass)
     * @param tintAmount 0..1, how strongly the tint shows
     * @param hover    0..1, brightens the glass under the mouse
     * @param press    0..1, darkens it while pressed
     * @param lift     0..1, a stronger shadow for glass that floats above other glass
     */
    static void paint(Graphics2D g, RoundRectangle2D shape, Color tint, double tintAmount,
                      double hover, double press, double lift) {
        Graphics2D g2 = (Graphics2D) g.create();
        setUp(g2);
        double x = shape.getX();
        double y = shape.getY();
        double w = shape.getWidth();
        double h = shape.getHeight();
        double arc = shape.getArcWidth();

        // soft shadow around the glass, but not under it: clear glass must not look grey inside
        int layers = 4 + (int) Math.round(2 * lift);
        for (int i = 1; i <= layers; i++) {
            Area layer = new Area(new RoundRectangle2D.Double(x - i * 0.5, y + i * 0.7, w + i, h + i * 0.4,
                    arc + i, arc + i));
            layer.subtract(new Area(shape));
            g2.setColor(new Color(0, 0, 0, (int) (5 + 3 * lift)));
            g2.fill(layer);
        }

        fillBody(g2, shape);
        if (tint != null && tintAmount > 0) {
            g2.setPaint(new GradientPaint(0, (float) y, withAlpha(tint, (int) (26 * tintAmount)),
                    0, (float) (y + h), withAlpha(tint, (int) (56 * tintAmount))));
            g2.fill(shape);
        }
        if (hover > 0) {
            g2.setColor(new Color(255, 255, 255, (int) (70 * hover)));
            g2.fill(shape);
        }
        if (press > 0) {
            g2.setColor(new Color(0, 0, 0, (int) (18 * press)));
            g2.fill(shape);
        }
        drawRim(g2, shape);
        g2.dispose();
    }

    /** The almost clear body: a faint white tint and a gloss fading out over the upper half. */
    static void fillBody(Graphics2D g2, RoundRectangle2D shape) {
        g2.setColor(TINT);
        g2.fill(shape);
        g2.setPaint(new GradientPaint(0, (float) shape.getY(), new Color(255, 255, 255, GLOSS_ALPHA),
                0, (float) (shape.getY() + shape.getHeight() * 0.55), new Color(255, 255, 255, 0)));
        g2.fill(shape);
    }

    /** The dark outer hairline, then the specular rim just inside it. */
    static void drawRim(Graphics2D g2, RoundRectangle2D shape) {
        g2.setStroke(new BasicStroke(1f));
        g2.setColor(EDGE);
        g2.draw(inset(shape, 0.5));
        g2.setStroke(new BasicStroke(1.4f));
        g2.setPaint(rimPaint(shape, RIM_TOP_ALPHA, RIM_BOTTOM_ALPHA));
        g2.draw(inset(shape, 1.2));
    }

    /** White along the diagonal: bright at the top-left and the bottom-right, clear in between. */
    static Paint rimPaint(RoundRectangle2D r, int topAlpha, int bottomAlpha) {
        return new LinearGradientPaint((float) r.getX(), (float) r.getY(),
                (float) (r.getX() + r.getWidth() * 0.35), (float) (r.getY() + r.getHeight() * 2.2),
                new float[]{0f, 0.35f, 0.7f, 1f},
                new Color[]{new Color(255, 255, 255, topAlpha), new Color(255, 255, 255, 0),
                    new Color(255, 255, 255, 0), new Color(255, 255, 255, bottomAlpha)});
    }

    static RoundRectangle2D inset(RoundRectangle2D r, double d) {
        return new RoundRectangle2D.Double(r.getX() + d, r.getY() + d, r.getWidth() - 2 * d, r.getHeight() - 2 * d,
                Math.max(0, r.getArcWidth() - 2 * d), Math.max(0, r.getArcHeight() - 2 * d));
    }

    /**
     * Draws text centred at (centreX, baseline) with a soft halo, so it stays readable
     * on clear glass even when ink shows through behind it.
     */
    static void drawText(Graphics2D g2, String text, double centreX, int baseline, Color color) {
        FontMetrics fm = g2.getFontMetrics();
        int x = (int) Math.round(centreX - fm.stringWidth(text) / 2.0);
        g2.setColor(withAlpha(HALO, 110));
        for (int dx = -1; dx <= 1; dx++) {
            for (int dy = -1; dy <= 1; dy++) {
                if (dx != 0 || dy != 0) {
                    g2.drawString(text, x + dx, baseline + dy);
                }
            }
        }
        g2.setColor(color);
        g2.drawString(text, x, baseline);
    }

    static Color withAlpha(Color c, int alpha) {
        return new Color(c.getRed(), c.getGreen(), c.getBlue(), Math.max(0, Math.min(255, alpha)));
    }
}
