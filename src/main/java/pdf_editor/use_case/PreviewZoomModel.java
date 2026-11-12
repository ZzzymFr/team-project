package pdf_editor.use_case;

/** Scale policy for a read-only document preview. Pixels and viewport sizes stay outside Swing. */
public final class PreviewZoomModel {
    public static final double MIN_ZOOM = 0.10;
    public static final double MAX_ZOOM = 3.0;
    private PreviewZoomModel() { }
    public static double clamp(double zoom) {
        if (!Double.isFinite(zoom)) throw new IllegalArgumentException("Invalid zoom");
        return Math.min(MAX_ZOOM, Math.max(MIN_ZOOM, zoom));
    }
    public static double fit(int pageWidth, int pageHeight, int viewportWidth, int viewportHeight) {
        if (pageWidth <= 0 || pageHeight <= 0) throw new IllegalArgumentException("Empty page");
        if (viewportWidth <= 0 || viewportHeight <= 0) return 1;
        double scale = Math.min((viewportWidth - 44.0) / pageWidth,
                (viewportHeight - 44.0) / pageHeight);
        return clamp(scale);
    }
}
