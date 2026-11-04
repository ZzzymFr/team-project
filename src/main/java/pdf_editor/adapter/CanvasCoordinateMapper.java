package pdf_editor.adapter;

import java.awt.Point;

/** Converts displayed pixels to source pixels. Scroll position belongs to the viewport, not this view. */
public final class CanvasCoordinateMapper {
    private CanvasCoordinateMapper() { }
    public static Point toSource(Point viewPoint, double zoom, int width, int height) {
        if (!Double.isFinite(zoom) || zoom <= 0) throw new IllegalArgumentException("Invalid zoom");
        return new Point(Math.max(0, Math.min(width, (int) Math.floor(viewPoint.x / zoom))),
                         Math.max(0, Math.min(height, (int) Math.floor(viewPoint.y / zoom))));
    }
}
