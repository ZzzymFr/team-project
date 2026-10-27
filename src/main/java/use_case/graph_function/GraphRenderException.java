package use_case.graph_function;

/**
 * Thrown when the graph image could not be produced (e.g. the chart service is unreachable).
 */
public class GraphRenderException extends Exception {
    public GraphRenderException(String message) {
        super(message);
    }

    public GraphRenderException(String message, Throwable cause) {
        super(message, cause);
    }
}
