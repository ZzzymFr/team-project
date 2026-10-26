package entity;

/**
 * Sampled points of a function ready to be drawn, plus the window to draw them in.
 * A NaN y value marks a gap (the function is undefined or jumps there).
 */
public class GraphData {
    private final String title;
    private final double[] xs;
    private final double[] ys;
    private final double xMin;
    private final double xMax;
    private final double yMin;
    private final double yMax;

    public GraphData(String title, double[] xs, double[] ys,
                     double xMin, double xMax, double yMin, double yMax) {
        if (xs.length != ys.length) {
            throw new IllegalArgumentException("xs and ys must have the same length");
        }
        this.title = title;
        this.xs = xs.clone();
        this.ys = ys.clone();
        this.xMin = xMin;
        this.xMax = xMax;
        this.yMin = yMin;
        this.yMax = yMax;
    }

    public String getTitle() {
        return title;
    }

    public double[] getXs() {
        return xs.clone();
    }

    public double[] getYs() {
        return ys.clone();
    }

    public double getXMin() {
        return xMin;
    }

    public double getXMax() {
        return xMax;
    }

    public double getYMin() {
        return yMin;
    }

    public double getYMax() {
        return yMax;
    }
}
