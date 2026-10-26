package use_case.graph_function;

/**
 * The LaTeX to graph (e.g. recognised from handwriting) and the x range to show.
 */
public class GraphFunctionInputData {
    public static final double DEFAULT_X_MIN = -10;
    public static final double DEFAULT_X_MAX = 10;

    private final String latex;
    private final double xMin;
    private final double xMax;

    public GraphFunctionInputData(String latex) {
        this(latex, DEFAULT_X_MIN, DEFAULT_X_MAX);
    }

    public GraphFunctionInputData(String latex, double xMin, double xMax) {
        this.latex = latex;
        this.xMin = xMin;
        this.xMax = xMax;
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
}
