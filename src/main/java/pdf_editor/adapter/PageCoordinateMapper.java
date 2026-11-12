package pdf_editor.adapter;

/** Converts view pixels to A4 document points, independently of Swing or the document entity. */
public final class PageCoordinateMapper {
    private PageCoordinateMapper() { }
    public static float screenToPage(double screenCoordinate, double pageMargin, double zoom) {
        if (!Double.isFinite(screenCoordinate) || !Double.isFinite(pageMargin)
                || !Double.isFinite(zoom) || zoom <= 0)
            throw new IllegalArgumentException("Invalid coordinate transform");
        return (float) ((screenCoordinate - pageMargin) / zoom);
    }
    public static float deltaToPage(double pixelDifference, double zoom) {
        if (!Double.isFinite(pixelDifference) || !Double.isFinite(zoom) || zoom <= 0)
            throw new IllegalArgumentException("Invalid zoom/drag delta");
        return (float) (pixelDifference / zoom);
    }
    public static double pageToScreen(double pageCoordinate, double pageMargin, double zoom) {
        if (!Double.isFinite(pageCoordinate) || !Double.isFinite(pageMargin)
                || !Double.isFinite(zoom) || zoom <= 0)
            throw new IllegalArgumentException("Invalid coordinate transform");
        return pageMargin + zoom * pageCoordinate;
    }
}
