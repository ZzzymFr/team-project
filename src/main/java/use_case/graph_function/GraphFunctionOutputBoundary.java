package use_case.graph_function;

/**
 * How the graph use case reports its result.
 */
public interface GraphFunctionOutputBoundary {
    void prepareSuccessView(GraphFunctionOutputData outputData);

    void prepareFailView(String errorMessage);
}
