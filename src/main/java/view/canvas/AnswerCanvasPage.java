package view.canvas;

import javax.swing.JComponent;

/**
 * Answer page: evaluates or solves the expression written on the canvas (US2).
 *
 * <p>The function area holds the answer tools, or a placeholder until they are given.
 * Use {@link #captureFormula()} or {@link #recognizeFormula()} to get what was written.
 */
public class AnswerCanvasPage extends CanvasPage {
    public AnswerCanvasPage(SharedCanvas sharedCanvas) {
        super(sharedCanvas);
        setFunctionArea(placeholder("Answer tools go here: evaluate or solve the expression on the canvas."));
    }

    /** An answer page whose function area shows the given tools, e.g. the expression evaluator. */
    public AnswerCanvasPage(SharedCanvas sharedCanvas, JComponent answerTools) {
        super(sharedCanvas);
        setFunctionArea(answerTools);
    }
}
