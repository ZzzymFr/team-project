package view.canvas;

/**
 * Answer page: evaluates or solves the expression written on the canvas (US2).
 *
 * <p>The function area is a placeholder for now; it will hold this page's controls.
 * Use {@link #captureFormula()} or {@link #recognizeFormula()} to get what was written.
 */
public class AnswerCanvasPage extends CanvasPage {
    public AnswerCanvasPage(SharedCanvas sharedCanvas) {
        super(sharedCanvas);
        setFunctionArea(placeholder("Answer tools go here: evaluate or solve the expression on the canvas."));
    }
}
