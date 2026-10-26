package view.canvas;

/**
 * Graph page: plots the function written on the canvas (US3).
 *
 * <p>The function area is a placeholder for now; it will hold this page's controls.
 * Use {@link #captureFormula()} or {@link #recognizeFormula()} to get what was written.
 */
public class GraphCanvasPage extends CanvasPage {
    public GraphCanvasPage(SharedCanvas sharedCanvas) {
        super(sharedCanvas);
        setFunctionArea(placeholder("Graph tools go here: plot the function written on the canvas."));
    }
}
