package interface_adapter.graph;

/**
 * Supplies the LaTeX to graph when the user presses the Graph button.
 *
 * <p>The handwriting recogniser (US1) implements this: read the drawing canvas, run the model,
 * and return its LaTeX, e.g. {@code y=x^{2}+1}. Until then the view falls back to its text field.
 */
@FunctionalInterface
public interface LatexProvider {
    /**
     * Called off the Swing event thread, so it may take time (e.g. running the model).
     *
     * @return the recognised LaTeX
     * @throws Exception if recognition fails; its message is shown to the user
     */
    String provideLatex() throws Exception;
}
