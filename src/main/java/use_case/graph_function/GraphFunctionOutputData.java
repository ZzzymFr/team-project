package use_case.graph_function;

/**
 * The rendered graph as encoded image bytes (PNG), with the LaTeX it shows.
 */
public class GraphFunctionOutputData {
    private final String latex;
    private final byte[] imageBytes;

    public GraphFunctionOutputData(String latex, byte[] imageBytes) {
        this.latex = latex;
        this.imageBytes = imageBytes.clone();
    }

    public String getLatex() {
        return latex;
    }

    public byte[] getImageBytes() {
        return imageBytes.clone();
    }
}
