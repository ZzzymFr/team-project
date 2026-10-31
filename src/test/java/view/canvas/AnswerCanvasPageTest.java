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
import java.awt.Point;
import java.awt.Rectangle;
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
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

// pause -> popup with the formula -> Calculate -> answer on the canvas
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
            page.setPopupDelay(100);
            shared.attachTo(page);
            page.setSize(900, 560);
            layout(page);
        });
    }

    // validate() is a no-op without a window, lay out by hand
    private static void layout(java.awt.Container c) {
        c.doLayout();
        for (java.awt.Component child : c.getComponents()) {
            if (child instanceof java.awt.Container) {
                layout((java.awt.Container) child);
            }
        }
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

    private void onEdt(Runnable r) throws Exception {
        SwingUtilities.invokeAndWait(r);
    }

    private AnswerPopup popup() throws Exception {
        AtomicReference<AnswerPopup> seen = new AtomicReference<>();
        onEdt(() -> seen.set(page.visiblePopup()));
        return seen.get();
    }

    private void waitPopup(String latex) throws Exception {
        waitFor(() -> page.visiblePopup() != null && latex.equals(page.visiblePopup().getLatex()), "popup with " + latex);
    }

    private void clickCalculate() throws Exception {
        onEdt(() -> page.visiblePopup().clickCalculate());
    }

    // wait up to 5s for bg work, check on EDT
    private void waitFor(BooleanSupplier condition, String what) throws Exception {
        AtomicBoolean met = new AtomicBoolean();
        for (int i = 0; i < 250 && !met.get(); i++) {
            Thread.sleep(20);
            onEdt(() -> met.set(condition.getAsBoolean()));
        }
        assertTrue(met.get(), "timed out waiting for " + what);
    }

    private List<CanvasAnswer> answers() throws Exception {
        AtomicReference<List<CanvasAnswer>> seen = new AtomicReference<>();
        onEdt(() -> seen.set(shared.getAnswers()));
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
    void popupShowsFormulaAndWaitsForCalculate() throws Exception {
        shared.setFormulaRecognizer(image -> "5\\times7");
        openPage();
        write();
        assertNull(popup(), "not before the pause");

        waitPopup("5\\times7");
        AnswerPopup p = popup();
        assertTrue(p.isTypeset());
        assertTrue(p.hasCalculate());
        Rectangle box = p.getBounds();
        assertFalse(box.intersects(new Rectangle(100, 100, 101, 51)), "does not cover the formula");
        assertTrue(new Rectangle(0, 0, p.getParent().getWidth(), p.getParent().getHeight()).contains(box), "stays in view");
        Thread.sleep(300);
        assertTrue(answers().isEmpty(), "nothing until Calculate");
    }

    @Test
    void calculateClickWritesTheAnswer() throws Exception {
        shared.setFormulaRecognizer(image -> "\\frac{1}{2}+2^{3}");
        openPage();
        write();
        waitPopup("\\frac{1}{2}+2^{3}");
        clickCalculate();
        assertNull(popup(), "popup goes when clicked");

        waitFor(() -> answerIs("= 8.5"), "the answer");
        CanvasAnswer a = answers().get(0);
        assertFalse(a.isError());
        assertTrue(a.getX() > 200, "right of the ink");
        waitFor(() -> page.currentMessage() == null, "the progress message to go away");
    }

    @Test
    void trailingEqualsAnswerGoesRight() throws Exception {
        shared.setFormulaRecognizer(image -> "1+1=");
        openPage();
        write();
        waitPopup("1+1=");
        clickCalculate();

        waitFor(() -> answerIs("2"), "the answer");
        assertTrue(answers().get(0).getX() > 200);
    }

    @Test
    void equationIsSolvedBelow() throws Exception {
        shared.setFormulaRecognizer(image -> "2x+1=7");
        openPage();
        write();
        waitPopup("2x+1=7");
        clickCalculate();

        waitFor(() -> answerIs("x = 3"), "the solution");
        assertTrue(answers().get(0).getY() > 150, "below the ink");
    }

    @Test
    void penOrToolChangeClosesThePopup() throws Exception {
        shared.setFormulaRecognizer(image -> "1+1");
        openPage();
        write();
        waitPopup("1+1");

        drag(500, 400, 520, 420);  // writes somewhere else
        assertNull(popup());
        waitPopup("1+1");          // the new ink gets its own
        onEdt(() -> canvas.setTool(DrawingCanvas.Tool.ERASER));
        assertNull(popup());
        Thread.sleep(300);
        assertNull(popup());
        assertTrue(answers().isEmpty());
    }

    @Test
    void panAndZoomKeepThePopup() throws Exception {
        shared.setFormulaRecognizer(image -> "1+1");
        openPage();
        write();
        waitPopup("1+1");
        Point before = popup().getLocation();

        onEdt(() -> canvas.panBy(30, -20));  // up, so it stays below the ink
        assertNotNull(popup());
        Point after = popup().getLocation();
        assertEquals(before.x + 30, after.x);
        assertEquals(before.y - 20, after.y);
        onEdt(() -> canvas.zoomAt(450, 250, 1.5));
        assertNotNull(popup());
    }

    @Test
    void editingAnAnsweredFormulaPopsUpAgain() throws Exception {
        AtomicReference<String> latex = new AtomicReference<>("1+1=");
        shared.setFormulaRecognizer(image -> latex.get());
        openPage();
        write();
        waitPopup("1+1=");
        clickCalculate();
        waitFor(() -> answerIs("2"), "the first answer");

        latex.set("1+2=");
        editFormula();
        assertTrue(answers().isEmpty(), "old answer gone at once");
        waitPopup("1+2=");
        Thread.sleep(300);
        assertTrue(answers().isEmpty(), "no auto recalc");
        clickCalculate();
        waitFor(() -> answerIs("3"), "the new answer");
    }

    @Test
    void lateRecognitionAfterAnotherActionIsDropped() throws Exception {
        CountDownLatch release = new CountDownLatch(1);
        AtomicInteger calls = new AtomicInteger();
        shared.setFormulaRecognizer(image -> {
            calls.incrementAndGet();
            release.await();
            return "1+1";
        });
        openPage();
        write();
        waitFor(() -> calls.get() == 1, "recognition to start");
        onEdt(() -> canvas.setTool(DrawingCanvas.Tool.LASSO));
        release.countDown();
        Thread.sleep(300);
        assertNull(popup(), "stale popup never shows");
    }

    @Test
    void recognitionProblemShowsReasonWithoutCalculate() throws Exception {
        openPage();
        write();
        waitFor(() -> page.visiblePopup() != null && page.visiblePopup().getProblem() != null
                && page.visiblePopup().getProblem().contains("not connected"), "the reason");
        assertFalse(popup().hasCalculate());
        assertTrue(answers().isEmpty());
    }

    @Test
    void anErrorFromTheModelShowsInThePopup() throws Exception {
        shared.setFormulaRecognizer(image -> {
            throw new UnsatisfiedLinkError("no native lib");
        });
        openPage();
        write();
        waitFor(() -> page.visiblePopup() != null && "no native lib".equals(page.visiblePopup().getProblem()), "the reason");
        assertFalse(popup().hasCalculate());
    }

    @Test
    void uncalculableOnlyShowsTheReason() throws Exception {
        shared.setFormulaRecognizer(image -> "\\int");
        openPage();
        write();
        waitPopup("\\int");
        clickCalculate();

        waitFor(() -> errorShown("Uncalculable — Integrals not supported"), "the reason");
        assertTrue(answers().isEmpty(), "no red ?");
    }

    @Test
    void functionDefinitionOnlyShowsAMessage() throws Exception {
        shared.setFormulaRecognizer(image -> "f(x)=x^{2}");
        openPage();
        write();
        waitPopup("f(x)=x^{2}");
        clickCalculate();

        waitFor(() -> page.currentMessage() != null && page.currentMessage().startsWith("Defined"), "the message");
        assertFalse(page.isMessageError());
        assertTrue(answers().isEmpty());
    }

    @Test
    void undoToAnsweredStateDoesNotPopUp() throws Exception {
        AtomicInteger calls = new AtomicInteger();
        shared.setFormulaRecognizer(image -> {
            calls.incrementAndGet();
            return "1+1=";
        });
        openPage();
        write();
        waitPopup("1+1=");
        clickCalculate();
        waitFor(() -> answerIs("2"), "the answer");

        editFormula();
        onEdt(shared::undo);
        Thread.sleep(400);
        assertEquals(1, calls.get());
        assertNull(popup());
        assertTrue(answerIs("2"));
    }

    @Test
    void undoWithUnansweredInkDoesNotPopUp() throws Exception {
        AtomicInteger calls = new AtomicInteger();
        shared.setFormulaRecognizer(image -> {
            calls.incrementAndGet();
            return "1+1";
        });
        openPage();
        write();
        waitPopup("1+1");
        editFormula();
        onEdt(shared::undo);
        Thread.sleep(400);
        assertNull(popup());
        assertEquals(1, calls.get());
    }

    @Test
    void graphPageInFrontNeverRecognises() throws Exception {
        AtomicInteger calls = new AtomicInteger();
        shared.setFormulaRecognizer(image -> {
            calls.incrementAndGet();
            return "1+1";
        });
        openPage();
        GraphCanvasPage[] graph = new GraphCanvasPage[1];
        onEdt(() -> {
            graph[0] = new GraphCanvasPage(shared);
            shared.attachTo(graph[0]);
        });
        write();
        Thread.sleep(400);
        assertEquals(0, calls.get());
        assertNull(popup());

        onEdt(() -> shared.attachTo(page));
        Thread.sleep(300);
        assertEquals(0, calls.get(), "coming back does not pop up by itself");
        assertNull(popup());
    }

    @Test
    void switchingPageClosesThePopup() throws Exception {
        shared.setFormulaRecognizer(image -> "1+1");
        openPage();
        write();
        waitPopup("1+1");
        onEdt(() -> shared.attachTo(new GraphCanvasPage(shared)));
        assertNull(popup());
    }

    @Test
    void secondFormulaGetsItsOwnPopup() throws Exception {
        AtomicReference<BufferedImage> seen = new AtomicReference<>();
        shared.setFormulaRecognizer(image -> {
            seen.set(image);
            return "1+1";
        });
        openPage();
        write();                                     // A: 50 tall
        waitPopup("1+1");
        clickCalculate();
        waitFor(() -> answerIs("= 2"), "A's answer");

        drag(400, 300, 420, 330);                    // B: 30 tall, far away
        waitFor(() -> page.visiblePopup() != null && seen.get().getHeight() == 30 + 1 + 2 * 18, "B's popup");
        assertTrue(popup().getX() > 300, "popup sits by B");
        clickCalculate();
        waitFor(() -> canvas.getAnswers().size() == 2, "both answers");
    }
}
