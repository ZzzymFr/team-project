package view.glass;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.FontMetrics;
import java.awt.GradientPaint;
import java.awt.Graphics2D;
import java.awt.LinearGradientPaint;
import java.awt.RenderingHints;
import java.awt.Shape;
import java.awt.geom.Area;
import java.awt.geom.Rectangle2D;
import java.awt.geom.RoundRectangle2D;

/**
 * Paints the "liquid glass" material used by the glass controls: a soft shadow, a translucent
 * body, a bright specular sheen across the top, a light rim that is brightest at the top edge,
 * and a faint refracted glow along the bottom edge.
 */
public final class Glass {
    /** Apple-style system blue, used for the selected state. */
    public static final Color ACCENT = new Color(0, 122, 255);
    static final Color TEXT = new Color(15, 23, 42);
    /** Secondary text on glass. */
    public static final Color TEXT_MUTED = new Color(51, 65, 85);

    /** Room around a glass shape for its shadow. */
    static final int SHADOW = 3;

    private Glass() {
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
            g2.setColor(new Color(15, 23, 42, (int) (5 + 3 * lift)));
            g2.fill(layer);
        }

        // the body: almost clear, so whatever is behind shows through
        g2.setPaint(new GradientPaint(0, (float) y, new Color(255, 255, 255, 70),
                0, (float) (y + h), new Color(236, 242, 250, 40)));
        g2.fill(shape);
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
            g2.setColor(new Color(15, 23, 42, (int) (18 * press)));
            g2.fill(shape);
        }

        Graphics2D inside = (Graphics2D) g2.create();
        inside.clip(shape);
        // thickness: the glass looks a touch darker just inside its edge
        inside.setStroke(new BasicStroke(4f));
        inside.setColor(new Color(15, 23, 42, 9));
        inside.draw(shape);
        // a curved highlight across the top, the reflection of the light above
        double inset = Math.min(h * 0.32, w * 0.2);
        inside.setPaint(new GradientPaint(0, (float) (y + 1), new Color(255, 255, 255, 200),
                0, (float) (y + h * 0.48), new Color(255, 255, 255, 0)));
        inside.fill(new RoundRectangle2D.Double(x + inset, y + 1.5, w - 2 * inset, h * 0.46, h * 0.46, h * 0.46));
        // light refracted through the glass, gathering along the bottom edge
        inside.setPaint(new GradientPaint(0, (float) (y + h * 0.68), new Color(255, 255, 255, 0),
                0, (float) (y + h), new Color(255, 255, 255, 140)));
        inside.fill(new Rectangle2D.Double(x, y + h * 0.68, w, h * 0.32));
        inside.dispose();

        // outer hairline, then the inner rim: brightest on top where light enters, bright again at the bottom
        g2.setStroke(new BasicStroke(1f));
        g2.setColor(new Color(15, 23, 42, tint != null && tintAmount > 0 ? 34 : 28));
        g2.draw(shape);
        RoundRectangle2D rim = new RoundRectangle2D.Double(x + 1, y + 1, w - 2, h - 2,
                Math.max(0, arc - 2), Math.max(0, arc - 2));
        g2.setPaint(new LinearGradientPaint(0, (float) y, 0, (float) (y + h),
                new float[]{0f, 0.45f, 1f},
                new Color[]{new Color(255, 255, 255, 255), new Color(255, 255, 255, 60),
                        new Color(255, 255, 255, 190)}));
        g2.setStroke(new BasicStroke(1.2f));
        g2.draw(rim);
        g2.dispose();
    }

    /**
     * Draws text centred at (centreX, baseline) with a soft white halo, so it stays readable
     * on clear glass even when ink shows through behind it.
     */
    static void drawText(Graphics2D g2, String text, double centreX, int baseline, Color color) {
        FontMetrics fm = g2.getFontMetrics();
        int x = (int) Math.round(centreX - fm.stringWidth(text) / 2.0);
        g2.setColor(new Color(255, 255, 255, 110));
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

    /** The part of a shape inside another one, e.g. to clip a lens to its track. */
    static Shape intersect(Shape a, Shape b) {
        Area area = new Area(a);
        area.intersect(new Area(b));
        return area;
    }

    static Color withAlpha(Color c, int alpha) {
        return new Color(c.getRed(), c.getGreen(), c.getBlue(), Math.max(0, Math.min(255, alpha)));
    }

    static Color mix(Color a, Color b, double t) {
        t = Math.max(0, Math.min(1, t));
        return new Color((int) Math.round(a.getRed() + (b.getRed() - a.getRed()) * t),
                (int) Math.round(a.getGreen() + (b.getGreen() - a.getGreen()) * t),
                (int) Math.round(a.getBlue() + (b.getBlue() - a.getBlue()) * t));
    }
}
