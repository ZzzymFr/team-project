package view.canvas;

import data_access.InMemoryFunctionDataAccessObject;
import interface_adapter.MathLabViewModel;
import interface_adapter.evaluate.EvaluateController;
import interface_adapter.evaluate.EvaluatePresenter;
import org.junit.jupiter.api.Test;
import use_case.evaluate.EvaluateInteractor;
import view.DrawingCanvas;

import javax.swing.SwingUtilities;
import java.awt.event.MouseEvent;
import java.awt.image.BufferedImage;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BooleanSupplier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

// calculate -> recognize -> evaluate -> answer/error
class AnswerCanvasPageTest {
    private final DrawingCanvas canvas = new DrawingCanvas();
    private final SharedCanvas shared = new SharedCanvas(canvas);
    private final MathLabViewModel viewModel = new MathLabViewModel();
    private AnswerCanvasPage page;

    private void openPage() throws Exception {
        EvaluateController controller = new EvaluateController(new EvaluateInteractor(
                new InMemoryFunctionDataAccessObject(), new EvaluatePresenter(viewModel)));
        SwingUtilities.invokeAndWait(() -> {
            page = new AnswerCanvasPage(shared, controller, viewModel);
            canvas.setSize(900, 500);
            shared.attachTo(page);
        });
    }

    private void write() {
        drag(100, 100, 200, 150);
    }

    private void drag(int... xy) {
        canvas.dispatchEvent(new MouseEvent(canvas, MouseEvent.MOUSE_PRESSED, 0, 0, xy[0], xy[1], 1, false, MouseEvent.BUTTON1));
        for (int i = 2; i < xy.length; i += 2) {
            canvas.dispatchEvent(new MouseEvent(canvas, MouseEvent.MOUSE_DRAGGED, 0, 0, xy[i], xy[i + 1], 1, false, MouseEvent.BUTTON1));
        }
        canvas.dispatchEvent(new MouseEvent(canvas, MouseEvent.MOUSE_RELEASED, 0, 0,
                xy[xy.length - 2], xy[xy.length - 1], 1, false, MouseEvent.BUTTON1));
    }

    private void clickCalculate() throws Exception {
        SwingUtilities.invokeAndWait(() -> page.calculate());
    }

    // wait up to 5s for bg work, check on EDT
    private void waitFor(BooleanSupplier condition, String what) throws Exception {
        AtomicBoolean met = new AtomicBoolean();
        for (int i = 0; i < 250 && !met.get(); i++) {
            Thread.sleep(20);
            SwingUtilities.invokeAndWait(() -> met.set(condition.getAsBoolean()));
        }
        assertTrue(met.get(), "timed out waiting for " + what);
    }

    private boolean errorShown(String text) {
        return page.isMessageError() && page.currentMessage().contains(text);
    }

    @Test
    void withoutRecognitionItSaysSoAndShowsNoAnswer() throws Exception {
        openPage();
        write();
        clickCalculate();

        waitFor(() -> errorShown("not connected"), "the not-connected message");
        assertEquals("", page.currentAnswer());
    }

    @Test
    void recognisedFormulaIsEvaluated() throws Exception {
        shared.setFormulaRecognizer(image -> "\\frac{1}{2}+2^{3}");
        openPage();
        write();
        clickCalculate();

        waitFor(() -> "= 8.5".equals(page.currentAnswer()), "the answer");
        assertFalse(page.isAnswerError());
        waitFor(() -> page.currentMessage() == null, "the progress message to go away");
    }

    @Test
    void recognisedEquationIsSolved() throws Exception {
        shared.setFormulaRecognizer(image -> "2x+1=7");
        openPage();
        write();
        clickCalculate();

        waitFor(() -> "x = 3".equals(page.currentAnswer()), "the solution");
    }

    @Test
    void anExpressionThatCannotBeEvaluatedIsShownInRed() throws Exception {
        shared.setFormulaRecognizer(image -> "\\int");
        openPage();
        write();
        clickCalculate();

        waitFor(page::isAnswerError, "the error");
        assertEquals("Uncalculable", page.currentAnswer());
    }

    @Test
    void onlyTheFormulaCircledWithTheLassoIsRecognised() throws Exception {
        AtomicReference<BufferedImage> seen = new AtomicReference<>();
        shared.setFormulaRecognizer(image -> {
            seen.set(image);
            return "1+1";
        });
        openPage();
        write();                                    // formula A: (100,100)-(200,150)
        drag(400, 400, 420, 420);                   // formula B, far away
        canvas.setTool(DrawingCanvas.Tool.LASSO);
        drag(80, 80, 220, 80, 220, 170, 80, 170);   // circle A
        clickCalculate();

        waitFor(() -> "= 2".equals(page.currentAnswer()), "the answer");
        assertEquals(51 + 2 * 18, seen.get().getHeight(), "the image holds only A");
    }

    @Test
    void emptyCanvasIsReported() throws Exception {
        AtomicBoolean recognised = new AtomicBoolean();
        shared.setFormulaRecognizer(image -> {
            recognised.set(true);
            return "1+1";
        });
        openPage();
        clickCalculate();

        waitFor(() -> errorShown("The canvas is empty"), "the empty-canvas message");
        assertFalse(recognised.get(), "nothing was sent to recognition");
    }
}
