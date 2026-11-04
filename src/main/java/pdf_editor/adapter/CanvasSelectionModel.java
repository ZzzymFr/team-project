package pdf_editor.adapter;

import java.awt.Point;
import java.awt.Rectangle;
import java.awt.Shape;
import java.awt.geom.Path2D;
import java.util.ArrayList;
import java.util.List;

/** Selection gesture state expressed in original image coordinates; no dependency on Swing. */
public final class CanvasSelectionModel {
    private boolean freehand;
    private final List<Point> points = new ArrayList<>();
    private boolean selecting;
    public void setFreehand(boolean value) { freehand = value; clear(); }
    public boolean isFreehand() { return freehand; }
    public void start(Point sourcePoint) { points.clear(); points.add(new Point(sourcePoint)); selecting = true; }
    public void drag(Point sourcePoint) {
        if (!selecting) return;
        if (freehand) points.add(new Point(sourcePoint));
        else { if (points.size() > 1) points.remove(1); points.add(new Point(sourcePoint)); }
    }
    public void finish(Point sourcePoint) { drag(sourcePoint); selecting = false; }
    public void clear() { points.clear(); selecting = false; }
    public Shape shape() {
        if (points.size() < (freehand ? 3 : 2)) return null;
        if (!freehand) {
            Point a = points.get(0), b = points.get(1);
            return new Rectangle(Math.min(a.x, b.x), Math.min(a.y, b.y), Math.abs(a.x - b.x), Math.abs(a.y - b.y));
        }
        Path2D path = new Path2D.Double();
        path.moveTo(points.get(0).x, points.get(0).y);
        for (int i=1; i<points.size(); i++) path.lineTo(points.get(i).x, points.get(i).y);
        if (!selecting) path.closePath();
        return path;
    }
    public List<Point> points() { return List.copyOf(points); }
}
