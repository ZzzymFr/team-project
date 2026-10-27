package view.canvas;

import data_access.InMemoryFunctionDataAccessObject;
import entity.CanvasAnswer;
import interface_adapter.MathLabViewModel;
import interface_adapter.evaluate.EvaluateController;
import interface_adapter.evaluate.EvaluatePresenter;
import org.junit.jupiter.api.Test;
import use_case.evaluate.EvaluateInteractor;
import view.DrawingCanvas;

import javax.swing.SwingUtilities;
import java.awt.event.MouseEvent;
import java.awt.image.BufferedImage;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BooleanSupplier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

// calculate -> recognize -> evaluate -> answer on the canvas
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
            page.setRecalculateDelay(100);
            canvas.setSize(900, 500);
            shared.attachTo(page);
        });
    }

    // ink box (100,100)-(200,150)
    private void write() throws Exception {
        drag(100, 100, 200, 150);
    }

    // a stroke right on the formula
    private void editFormula() throws Exception {
        drag(205, 100, 205, 150);
    }

    private void drag(int... xy) throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            canvas.dispatchEvent(new MouseEvent(canvas, MouseEvent.MOUSE_PRESSED, 0, 0, xy[0], xy[1], 1, false, MouseEvent.BUTTON1));
            for (int i = 2; i < xy.length; i += 2) {
                canvas.dispatchEvent(new MouseEvent(canvas, MouseEvent.MOUSE_DRAGGED, 0, 0, xy[i], xy[i + 1], 1, false, MouseEvent.BUTTON1));
            }
            canvas.dispatchEvent(new MouseEvent(canvas, MouseEvent.MOUSE_RELEASED, 0, 0,
                    xy[xy.length - 2], xy[xy.length - 1], 1, false, MouseEvent.BUTTON1));
        });
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

    private List<CanvasAnswer> answers() throws Exception {
        AtomicReference<List<CanvasAnswer>> seen = new AtomicReference<>();
        SwingUtilities.invokeAndWait(() -> seen.set(shared.getAnswers()));
        return seen.get();
    }

    private boolean answerIs(String text) {
        List<CanvasAnswer> all = canvas.getAnswers();
        return all.size() == 1 && text.equals(all.get(0).getText());
    }

    private boolean errorShown(String text) {
        return page.isMessageError() && page.currentMessage() != null && page.currentMessage().contains(text);
    }

    @Test
    void recognisedFormulaIsEvaluated() throws Exception {
        shared.setFormulaRecognizer(image -> "\\frac{1}{2}+2^{3}");
        openPage();
        write();
        clickCalculate();

        waitFor(() -> answerIs("= 8.5"), "the answer");
        CanvasAnswer a = answers().get(0);
        assertFalse(a.isError());
        assertTrue(a.getX() > 200, "right of the ink");
        waitFor(() -> page.currentMessage() == null, "the progress message to go away");
    }

    @Test
    void trailingEqualsAnswerHasNoEqualsSign() throws Exception {
        shared.setFormulaRecognizer(image -> "1+1=");
        openPage();
        write();
        clickCalculate();

        waitFor(() -> answerIs("2"), "the answer");
        assertTrue(answers().get(0).getX() > 200);
    }

    @Test
    void recognisedEquationIsSolvedBelow() throws Exception {
        shared.setFormulaRecognizer(image -> "2x+1=7");
        openPage();
        write();
        clickCalculate();

        waitFor(() -> answerIs("x = 3"), "the solution");
        assertTrue(answers().get(0).getY() > 150, "below the ink");
    }

    @Test
    void uncalculableShowsARedQuestionMark() throws Exception {
        shared.setFormulaRecognizer(image -> "\\int");
        openPage();
        write();
        clickCalculate();

        waitFor(() -> answerIs("?"), "the red ?");
        assertTrue(answers().get(0).isError());
        waitFor(() -> errorShown("Uncalculable"), "the reason");
    }

    @Test
    void withoutRecognitionItSaysSo() throws Exception {
        openPage();
        write();
        clickCalculate();

        waitFor(() -> errorShown("not connected"), "the not-connected message");
        assertTrue(answerIs("?"));
        assertTrue(answers().get(0).isError());
    }

    @Test
    void editingTheFormulaRecalculates() throws Exception {
        AtomicReference<String> latex = new AtomicReference<>("1+1=");
        shared.setFormulaRecognizer(image -> latex.get());
        openPage();
        write();
        clickCalculate();
        waitFor(() -> answerIs("2"), "the first answer");

        latex.set("1+2=");
        editFormula();
        assertTrue(answers().isEmpty(), "old answer gone at once");
        waitFor(() -> answerIs("3"), "the new answer");
    }

    @Test
    void staleResultIsDropped() throws Exception {
        CountDownLatch first = new CountDownLatch(1);
        CountDownLatch second = new CountDownLatch(1);
        AtomicInteger calls = new AtomicInteger();
        shared.setFormulaRecognizer(image -> {
            if (calls.incrementAndGet() == 1) {
                first.await();
                return "1+1=";
            }
            second.await();
            return "1+2=";
        });
        openPage();
        write();
        clickCalculate();
        waitFor(() -> calls.get() == 1, "the first recognition to start");

        editFormula();
        Thread.sleep(300);  // recalc queued behind the first job
        first.countDown();
        waitFor(() -> calls.get() == 2, "the second recognition to start");
        SwingUtilities.invokeAndWait(() -> { });
        assertTrue(answers().isEmpty(), "the stale answer was not drawn");

        second.countDown();
        waitFor(() -> answerIs("3"), "the fresh answer");
    }

    @Test
    void undoToAnsweredStateDoesNotRecalculate() throws Exception {
        AtomicInteger calls = new AtomicInteger();
        shared.setFormulaRecognizer(image -> {
            calls.incrementAndGet();
            return "1+1=";
        });
        openPage();
        write();
        clickCalculate();
        waitFor(() -> answerIs("2"), "the answer");

        editFormula();
        SwingUtilities.invokeAndWait(shared::undo);
        Thread.sleep(300);
        assertEquals(1, calls.get());
        assertTrue(answerIs("2"));
    }

    @Test
    void functionDefinitionOnlyShowsAMessage() throws Exception {
        shared.setFormulaRecognizer(image -> "f(x)=x^{2}");
        openPage();
        write();
        clickCalculate();

        waitFor(() -> page.currentMessage() != null && page.currentMessage().startsWith("Defined"), "the message");
        assertFalse(page.isMessageError());
        assertTrue(answers().isEmpty());
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
        SwingUtilities.invokeAndWait(() -> canvas.setTool(DrawingCanvas.Tool.LASSO));
        drag(80, 80, 220, 80, 220, 170, 80, 170);   // circle A
        clickCalculate();

        waitFor(() -> answerIs("= 2"), "the answer");
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

    @Test
    void anErrorFromTheModelStillEndsTheJob() throws Exception {
        shared.setFormulaRecognizer(image -> {
            throw new UnsatisfiedLinkError("no native lib");
        });
        openPage();
        write();
        clickCalculate();

        waitFor(() -> answerIs("?"), "the red ?");
        waitFor(() -> errorShown("no native lib"), "the reason");
    }
}
