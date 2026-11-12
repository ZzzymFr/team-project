package use_case.graph_check;

// can this formula be plotted? asked before the Graph button is shown
public interface GraphCheckInputBoundary {
    boolean canGraph(String latex);
}
