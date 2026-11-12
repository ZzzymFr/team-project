package interface_adapter.graph_check;

import use_case.graph_check.GraphCheckInputBoundary;

public class GraphCheckController {
    private final GraphCheckInputBoundary interactor;

    public GraphCheckController(GraphCheckInputBoundary interactor) {
        this.interactor = interactor;
    }

    public boolean canGraph(String latex) {
        return interactor.canGraph(latex);
    }
}
