package application;

import data_access.QuickChartGraphRenderer;
import entity.LatexFunctionParser;
import interface_adapter.graph.GraphController;
import interface_adapter.graph.GraphPresenter;
import interface_adapter.graph.GraphViewModel;
import use_case.graph_function.GraphFunctionInteractor;
import view.GraphView;

/**
 * Wires the graph feature (US3) together: view -> controller -> interactor -> QuickChart -> presenter -> view.
 */
public final class GraphFeatureFactory {
    private GraphFeatureFactory() {
    }

    public static GraphView createGraphView() {
        GraphViewModel viewModel = new GraphViewModel();
        GraphPresenter presenter = new GraphPresenter(viewModel);
        GraphFunctionInteractor interactor = new GraphFunctionInteractor(
                new LatexFunctionParser(), new QuickChartGraphRenderer(), presenter);
        return new GraphView(viewModel, new GraphController(interactor));
    }
}
