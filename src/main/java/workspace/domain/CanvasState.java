package workspace.domain;

import java.util.List;
import java.util.Objects;

/** Immutable, framework-independent representation of an editable whiteboard. */
public record CanvasState(List<Ink> ink, List<Formula> formulas, List<Asset> assets,
                          double zoom, double offsetX, double offsetY) {
    public CanvasState {
        ink = List.copyOf(Objects.requireNonNull(ink));
        formulas = List.copyOf(Objects.requireNonNull(formulas));
        assets = List.copyOf(Objects.requireNonNull(assets));
        if (!Double.isFinite(zoom) || zoom < .25 || zoom > 4
                || !Double.isFinite(offsetX) || !Double.isFinite(offsetY))
            throw new IllegalArgumentException("Invalid canvas viewport");
    }
    public record Point(int x, int y, long timeMillis) { }
    public record Ink(List<Point> points, int argb) {
        public Ink { points = List.copyOf(points); if (points.isEmpty()) throw new IllegalArgumentException("Empty stroke"); }
    }
    public record Formula(List<Integer> strokes, String answer, boolean error,
                          double x, double y, double width, double height, float fontSize, int color,
                          List<TextPart> textParts) {
        public Formula(List<Integer> strokes, String answer, boolean error,
                       double x, double y, double width, double height, float fontSize, int color) {
            this(strokes, answer, error, x, y, width, height, fontSize, color, List.of());
        }
        public Formula {
            strokes = List.copyOf(strokes);
            textParts = List.copyOf(Objects.requireNonNull(textParts));
            answer = Objects.requireNonNull(answer);
            if (strokes.isEmpty() || !Double.isFinite(x) || !Double.isFinite(y)
                    || !Double.isFinite(width) || !Double.isFinite(height) || width < 0 || height < 0
                    || !Float.isFinite(fontSize) || fontSize < 0) throw new IllegalArgumentException("Invalid formula");
        }
    }
    public record TextPart(String text, boolean superscript) {
        public TextPart { Objects.requireNonNull(text); }
    }
    /** Image may be a graph or Math/History import; source LaTeX is retained for editing. */
    public record Asset(String latex, String result, byte[] png,
                        double x, double y, double width, double height, boolean graph) {
        public Asset(String latex, String result, byte[] png,
                     double x, double y, double width, double height) {
            this(latex, result, png, x, y, width, height, false);
        }
        public Asset {
            latex = Objects.requireNonNull(latex);
            result = Objects.requireNonNull(result);
            png = Objects.requireNonNull(png).clone();
            if (png.length == 0 || png.length > 12 * 1024 * 1024
                    || !Double.isFinite(x) || !Double.isFinite(y)
                    || !Double.isFinite(width) || !Double.isFinite(height)
                    || width <= 0 || height <= 0) throw new IllegalArgumentException("Invalid canvas asset");
        }
        @Override public byte[] png() { return png.clone(); }
    }
}
