package interface_adapter.graph;

import use_case.graph_function.GraphFunctionInputBoundary;
import use_case.graph_function.GraphFunctionInputData;

/**
 * Receives a LaTeX string (e.g. from the handwriting recogniser) and starts the graph use case.
 * The result arrives in the {@link GraphViewModel}.
 */
public class GraphController {
    private final GraphFunctionInputBoundary interactor;

    public GraphController(GraphFunctionInputBoundary interactor) {
        this.interactor = interactor;
    }

    /** Graphs the LaTeX function over the default range x in [-10, 10]. */
    public void execute(String latex) {
        interactor.execute(new GraphFunctionInputData(latex));
    }

    public void execute(String latex, double xMin, double xMax) {
        interactor.execute(new GraphFunctionInputData(latex, xMin, xMax));
    }

    /**
     * Graphs the LaTeX function over the given ranges, as {min, max} pairs from {@link AxisRangeParser};
     * a null x range means the default [-10, 10], a null y range means choosing y automatically.
     */
    public void execute(String latex, double[] xRange, double[] yRange) {
        double xMin = xRange == null ? GraphFunctionInputData.DEFAULT_X_MIN : xRange[0];
        double xMax = xRange == null ? GraphFunctionInputData.DEFAULT_X_MAX : xRange[1];
        double yMin = yRange == null ? GraphFunctionInputData.AUTOMATIC : yRange[0];
        double yMax = yRange == null ? GraphFunctionInputData.AUTOMATIC : yRange[1];
        interactor.execute(new GraphFunctionInputData(latex, xMin, xMax, yMin, yMax));
    }
}
