package entity;

import java.util.ArrayList;
import java.util.List;

/**
 * Editing operations on handwritten ink: erasing part of the strokes, selecting strokes with a
 * lasso loop, and moving strokes. Strokes are never changed in place; new strokes are returned.
 */
public final class InkEditor {
    /** Erasing walks along touched segments in steps of this many pixels. */
    private static final double ERASE_STEP = 1.5;

    private InkEditor() {
    }

    /**
     * Erases the ink inside the circle. A stroke that the circle cuts through is split into the
     * pieces left on either side; pieces too small to draw a line are dropped.
     *
     * @return the strokes after erasing; the same list object if nothing was erased
     */
    public static List<Stroke> erase(List<Stroke> strokes, double cx, double cy, double radius) {
        List<Stroke> result = new ArrayList<>();
        boolean changed = false;
        for (Stroke stroke : strokes) {
            if (!touches(stroke, cx, cy, radius)) {
                result.add(stroke);
                continue;
            }
            changed = true;
            result.addAll(split(stroke, cx, cy, radius));
        }
        return changed ? result : strokes;
    }

    /**
     * Erases along the path of the eraser from (x1, y1) to (x2, y2), so a fast drag leaves no gaps.
     *
     * @return the strokes after erasing; the same list object if nothing was erased
     */
    public static List<Stroke> eraseAlong(List<Stroke> strokes, double x1, double y1,
                                          double x2, double y2, double radius) {
        double length = Math.hypot(x2 - x1, y2 - y1);
        int steps = Math.max(1, (int) Math.ceil(length / (radius / 2)));
        List<Stroke> current = strokes;
        for (int i = 0; i <= steps; i++) {
            double t = (double) i / steps;
            current = erase(current, x1 + (x2 - x1) * t, y1 + (y2 - y1) * t, radius);
        }
        return current;
    }

    /**
     * Whole-stroke erasing: removes every stroke the eraser touches anywhere on its path from
     * (x1, y1) to (x2, y2). Untouched strokes are kept as they are.
     *
     * @return the strokes that are left; the same list object if nothing was touched
     */
    public static List<Stroke> removeTouchedAlong(List<Stroke> strokes, double x1, double y1,
                                                  double x2, double y2, double radius) {
        double length = Math.hypot(x2 - x1, y2 - y1);
        int steps = Math.max(1, (int) Math.ceil(length / (radius / 2)));
        List<Stroke> result = new ArrayList<>();
        for (Stroke stroke : strokes) {
            boolean touched = false;
            for (int i = 0; i <= steps && !touched; i++) {
                double t = (double) i / steps;
                touched = touches(stroke, x1 + (x2 - x1) * t, y1 + (y2 - y1) * t, radius);
            }
            if (!touched) {
                result.add(stroke);
            }
        }
        return result.size() == strokes.size() ? strokes : result;
    }

    /**
     * The strokes the lasso loop goes around: more than half of a stroke's points must be inside.
     *
     * @param loop the lasso path; it is closed automatically from its last point back to its first
     */
    public static List<Stroke> selectInside(List<Stroke> strokes, List<InkPoint> loop) {
        List<Stroke> selected = new ArrayList<>();
        if (loop.size() < 3) {
            return selected;
        }
        for (Stroke stroke : strokes) {
            int inside = 0;
            for (InkPoint p : stroke.getPoints()) {
                if (contains(loop, p.getX(), p.getY())) {
                    inside++;
                }
            }
            if (!stroke.isEmpty() && inside * 2 > stroke.getPoints().size()) {
                selected.add(stroke);
            }
        }
        return selected;
    }

    /** A copy of the stroke shifted by (dx, dy). */
    public static Stroke translate(Stroke stroke, int dx, int dy) {
        Stroke moved = new Stroke();
        for (InkPoint p : stroke.getPoints()) {
            moved.addPoint(new InkPoint(p.getX() + dx, p.getY() + dy, p.getTimeMillis()));
        }
        return moved;
    }

    /**
     * A copy of the stroke resized by a factor around the anchor point: the anchor stays where it
     * is and every point moves towards it (factor below 1) or away from it (above 1).
     */
    public static Stroke scale(Stroke stroke, double anchorX, double anchorY, double factor) {
        Stroke scaled = new Stroke();
        for (InkPoint p : stroke.getPoints()) {
            scaled.addPoint(new InkPoint((int) Math.round(anchorX + (p.getX() - anchorX) * factor),
                    (int) Math.round(anchorY + (p.getY() - anchorY) * factor), p.getTimeMillis()));
        }
        return scaled;
    }

    /** Ray casting: is (x, y) inside the closed polygon? */
    static boolean contains(List<InkPoint> polygon, double x, double y) {
        boolean inside = false;
        for (int i = 0, j = polygon.size() - 1; i < polygon.size(); j = i++) {
            double xi = polygon.get(i).getX();
            double yi = polygon.get(i).getY();
            double xj = polygon.get(j).getX();
            double yj = polygon.get(j).getY();
            if ((yi > y) != (yj > y) && x < (xj - xi) * (y - yi) / (yj - yi) + xi) {
                inside = !inside;
            }
        }
        return inside;
    }

    private static boolean touches(Stroke stroke, double cx, double cy, double radius) {
        List<InkPoint> points = stroke.getPoints();
        if (points.size() == 1) {
            return within(points.get(0).getX(), points.get(0).getY(), cx, cy, radius);
        }
        for (int i = 0; i + 1 < points.size(); i++) {
            if (segmentDistance(points.get(i), points.get(i + 1), cx, cy) <= radius) {
                return true;
            }
        }
        return false;
    }

    private static List<Stroke> split(Stroke stroke, double cx, double cy, double radius) {
        List<InkPoint> points = stroke.getPoints();
        List<Stroke> pieces = new ArrayList<>();
        List<InkPoint> piece = new ArrayList<>();
        for (int i = 0; i < points.size(); i++) {
            InkPoint p = points.get(i);
            if (i + 1 == points.size() || segmentDistance(p, points.get(i + 1), cx, cy) > radius) {
                // This segment is clear of the eraser (or p is the last point): keep p as it is.
                if (within(p.getX(), p.getY(), cx, cy, radius)) {
                    finishPiece(piece, pieces);
                } else {
                    piece.add(p);
                }
                continue;
            }
            // The eraser crosses this segment: walk along it and keep only the points outside.
            InkPoint q = points.get(i + 1);
            double length = Math.hypot(q.getX() - p.getX(), q.getY() - p.getY());
            int steps = Math.max(1, (int) Math.ceil(length / ERASE_STEP));
            for (int k = 0; k < steps; k++) {
                double t = (double) k / steps;
                double x = p.getX() + (q.getX() - p.getX()) * t;
                double y = p.getY() + (q.getY() - p.getY()) * t;
                if (within(x, y, cx, cy, radius)) {
                    finishPiece(piece, pieces);
                } else {
                    long time = p.getTimeMillis() + Math.round((q.getTimeMillis() - p.getTimeMillis()) * t);
                    piece.add(new InkPoint((int) Math.round(x), (int) Math.round(y), time));
                }
            }
        }
        finishPiece(piece, pieces);
        return pieces;
    }

    private static void finishPiece(List<InkPoint> piece, List<Stroke> pieces) {
        if (piece.size() >= 2) {
            Stroke stroke = new Stroke();
            for (InkPoint p : piece) {
                stroke.addPoint(p);
            }
            pieces.add(stroke);
        }
        piece.clear();
    }

    private static boolean within(double x, double y, double cx, double cy, double radius) {
        return Math.hypot(x - cx, y - cy) <= radius;
    }

    private static double segmentDistance(InkPoint a, InkPoint b, double cx, double cy) {
        double dx = b.getX() - a.getX();
        double dy = b.getY() - a.getY();
        double lengthSquared = dx * dx + dy * dy;
        double t = lengthSquared == 0 ? 0
                : Math.max(0, Math.min(1, ((cx - a.getX()) * dx + (cy - a.getY()) * dy) / lengthSquared));
        return Math.hypot(a.getX() + t * dx - cx, a.getY() + t * dy - cy);
    }
}
