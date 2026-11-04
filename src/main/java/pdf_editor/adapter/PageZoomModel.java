package pdf_editor.adapter;

/** View-only zoom shared across all A4 pages. Does not change document point dimensions. */
public final class PageZoomModel {
    public static final double MIN_ZOOM = .35;
    public static final double MAX_ZOOM = 3.0;
    private double zoom = 1.0;
    public double zoom() { return zoom; }
    public double set(double value) {
        if (!Double.isFinite(value)) throw new IllegalArgumentException("Zoom must be finite");
        zoom = Math.max(MIN_ZOOM, Math.min(MAX_ZOOM, value));
        return zoom;
    }
    public double adjustWheel(double rotation) { return set(zoom * Math.pow(1.12, -rotation)); }
    public double fit(int viewportWidth, int viewportHeight, int pageWidth, int pageHeight) {
        if (viewportWidth <= 0 || viewportHeight <= 0 || pageWidth <= 0 || pageHeight <= 0)
            throw new IllegalArgumentException("Dimensions must be positive");
        return set(Math.min((double) viewportWidth / pageWidth,
                (double) viewportHeight / pageHeight));
    }
}
