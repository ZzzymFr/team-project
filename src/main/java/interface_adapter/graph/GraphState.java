package interface_adapter.graph;

import java.awt.image.BufferedImage;

/**
 * What the graph canvas shows: the last LaTeX, its image, or an error message.
 */
public class GraphState {
    private String latex = "";
    private BufferedImage graphImage;
    private String error;

    public GraphState() {
    }

    public GraphState(GraphState copy) {
        this.latex = copy.latex;
        this.graphImage = copy.graphImage;
        this.error = copy.error;
    }

    public String getLatex() {
        return latex;
    }

    public void setLatex(String latex) {
        this.latex = latex;
    }

    public BufferedImage getGraphImage() {
        return graphImage;
    }

    public void setGraphImage(BufferedImage graphImage) {
        this.graphImage = graphImage;
    }

    public String getError() {
        return error;
    }

    public void setError(String error) {
        this.error = error;
    }
}
