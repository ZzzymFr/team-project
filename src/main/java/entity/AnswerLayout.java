package entity;

import java.awt.Rectangle;
import java.awt.geom.Point2D;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;

// where answers go and how big, all in world coords
public final class AnswerLayout {
    public static final double GAP_RIGHT = 0.4;
    public static final double GAP_BELOW = 0.3;
    public static final double JOIN_MARGIN_X = 0.6;
    public static final double JOIN_MARGIN_Y = 0.15;
    public static final double MIN_CHAR_HEIGHT = 12;
    public static final double MAX_CHAR_HEIGHT = 400;
    private static final double DOT = 6;

    private AnswerLayout() {
    }

    // strokes overlapping in x merged into one box, left to right
    public static List<Rectangle> glyphs(Collection<Stroke> strokes) {
        List<Rectangle> boxes = new ArrayList<>();
        for (Stroke s : strokes) {
            Rectangle b = bounds(List.of(s));
            if (b != null) {
                boxes.add(b);
            }
        }
        boxes.sort(Comparator.comparingInt(b -> b.x));
        List<Rectangle> glyphs = new ArrayList<>();
        Rectangle current = null;
        for (Rectangle b : boxes) {
            if (current != null && b.x <= current.getMaxX()) {
                current.add(b);
            } else {
                current = new Rectangle(b);
                glyphs.add(current);
            }
        }
        return glyphs;
    }

    // median height of the tall glyphs, skips "-", "=", dots
    public static double charHeight(Collection<Stroke> strokes) {
        List<Integer> heights = new ArrayList<>();
        for (Rectangle g : glyphs(strokes)) {
            if (g.height >= DOT && g.height >= 0.5 * g.width) {
                heights.add(g.height);
            }
        }
        double h;
        if (heights.isEmpty()) {
            Rectangle all = bounds(strokes);
            h = all == null ? 0 : all.height;
        } else {
            heights.sort(null);
            int n = heights.size();
            h = n % 2 == 1 ? heights.get(n / 2) : (heights.get(n / 2 - 1) + heights.get(n / 2)) / 2.0;
        }
        return Math.max(MIN_CHAR_HEIGHT, Math.min(MAX_CHAR_HEIGHT, h));
    }

    // top-left of an answer right of the formula, centred on its last glyph
    public static Point2D.Double rightOf(Collection<Stroke> formula, double charHeight, double answerHeight) {
        Rectangle all = bounds(formula);
        List<Rectangle> glyphs = glyphs(formula);
        Rectangle last = glyphs.get(glyphs.size() - 1);
        return new Point2D.Double(all.getMaxX() + GAP_RIGHT * charHeight, last.getCenterY() - answerHeight / 2);
    }

    // top-left of an answer under the formula
    public static Point2D.Double below(Collection<Stroke> formula, double charHeight) {
        Rectangle all = bounds(formula);
        return new Point2D.Double(all.getMinX(), all.getMaxY() + GAP_BELOW * charHeight);
    }

    // does a new stroke land on the formula
    public static boolean joins(Rectangle formulaBounds, double charHeight, Rectangle strokeBounds) {
        double mx = JOIN_MARGIN_X * charHeight;
        double my = JOIN_MARGIN_Y * charHeight;
        // inclusive, so 0-wide lines count
        return strokeBounds.getMinX() <= formulaBounds.getMaxX() + mx
                && strokeBounds.getMaxX() >= formulaBounds.getMinX() - mx
                && strokeBounds.getMinY() <= formulaBounds.getMaxY() + my
                && strokeBounds.getMaxY() >= formulaBounds.getMinY() - my;
    }

    // median height of tall single strokes; lines stacked above each other don't add up
    public static double strokeHeight(Collection<Stroke> strokes) {
        List<Integer> heights = new ArrayList<>();
        for (Stroke s : strokes) {
            Rectangle b = bounds(List.of(s));
            if (b != null && b.height >= DOT && b.height >= 0.5 * b.width) {
                heights.add(b.height);
            }
        }
        if (heights.isEmpty()) {
            return charHeight(strokes);
        }
        heights.sort(null);
        int n = heights.size();
        double h = n % 2 == 1 ? heights.get(n / 2) : (heights.get(n / 2 - 1) + heights.get(n / 2)) / 2.0;
        return Math.max(MIN_CHAR_HEIGHT, Math.min(MAX_CHAR_HEIGHT, h));
    }

    public static final double NEW_INK_GAP_X = 1.5;
    public static final double NEW_INK_GAP_Y = 0.35;

    // fresh loose ink: same formula if on the same line, gaps up to 1.5 chars
    public static boolean sameNewFormula(Rectangle group, double charHeight, Rectangle stroke) {
        double mx = NEW_INK_GAP_X * charHeight;
        double my = NEW_INK_GAP_Y * charHeight;
        return stroke.getMinX() <= group.getMaxX() + mx
                && stroke.getMaxX() >= group.getMinX() - mx
                && stroke.getMinY() <= group.getMaxY() + my
                && stroke.getMaxY() >= group.getMinY() - my;
    }

    // box around every point, null if none
    public static Rectangle bounds(Iterable<Stroke> strokes) {
        Rectangle bounds = null;
        for (Stroke stroke : strokes) {
            for (InkPoint p : stroke.getPoints()) {
                if (bounds == null) {
                    bounds = new Rectangle(p.getX(), p.getY(), 0, 0);
                } else {
                    bounds.add(p.getX(), p.getY());
                }
            }
        }
        return bounds;
    }
}
