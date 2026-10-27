package use_case.graph_function;

/**
 * The LaTeX to graph (e.g. recognised from handwriting), the x range to show, and optionally the
 * y range (when it is not given, the y range is chosen automatically from the function's values).
 */
public class GraphFunctionInputData {
    public static final double DEFAULT_X_MIN = -10;
    public static final double DEFAULT_X_MAX = 10;
    /** Pass as both y bounds to have the y range chosen automatically. */
    public static final double AUTOMATIC = Double.NaN;

    private final String latex;
    private final double xMin;
    private final double xMax;
    private final double yMin;
    private final double yMax;

    public GraphFunctionInputData(String latex) {
        this(latex, DEFAULT_X_MIN, DEFAULT_X_MAX);
    }

    public GraphFunctionInputData(String latex, double xMin, double xMax) {
        this(latex, xMin, xMax, AUTOMATIC, AUTOMATIC);
    }

    /** @param yMin the bottom of the y range, or {@link #AUTOMATIC} (with yMax) to choose it from the values */
    public GraphFunctionInputData(String latex, double xMin, double xMax, double yMin, double yMax) {
        this.latex = latex;
        this.xMin = xMin;
        this.xMax = xMax;
        this.yMin = yMin;
        this.yMax = yMax;
    }

    public String getLatex() {
        return latex;
    }

    public double getXMin() {
        return xMin;
    }

    public double getXMax() {
        return xMax;
    }

    /** True when no y range was given, so it is chosen from the function's values. */
    public boolean isYAutomatic() {
        return Double.isNaN(yMin) && Double.isNaN(yMax);
    }

    public double getYMin() {
        return yMin;
    }

    public double getYMax() {
        return yMax;
    }
}
