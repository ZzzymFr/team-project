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
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BooleanSupplier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

// d/dy(3y^2)= -> 6y written on the canvas
class AnswerCanvasPageDerivativeTest {
    private final DrawingCanvas canvas = new DrawingCanvas();
    private final SharedCanvas shared = new SharedCanvas(canvas);
    private final MathLabViewModel viewModel = new MathLabViewModel();
    private final AtomicReference<String> pdfResult = new AtomicReference<>();
    private AnswerCanvasPage page;

    private void openPage(String latex) throws Exception {
        shared.setFormulaRecognizer(image -> latex);
        EvaluateController controller = new EvaluateController(new EvaluateInteractor(
                new InMemoryFunctionDataAccessObject(), new EvaluatePresenter(viewModel)));
        SwingUtilities.invokeAndWait(() -> {
            page = new AnswerCanvasPage(shared, controller, viewModel, (l, result) -> pdfResult.set(result));
            page.setPopupDelay(100);
            shared.attachTo(page);
            page.setSize(900, 560);
            layout(page);
        });
    }

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
        SwingUtilities.invokeAndWait(() -> {
            canvas.dispatchEvent(new MouseEvent(canvas, MouseEvent.MOUSE_PRESSED, 0, 0, 100, 100, 1, false, MouseEvent.BUTTON1));
            canvas.dispatchEvent(new MouseEvent(canvas, MouseEvent.MOUSE_DRAGGED, 0, 0, 200, 150, 1, false, MouseEvent.BUTTON1));
            canvas.dispatchEvent(new MouseEvent(canvas, MouseEvent.MOUSE_RELEASED, 0, 0, 200, 150, 1, false, MouseEvent.BUTTON1));
        });
    }

    private void waitFor(BooleanSupplier condition, String what) throws Exception {
        AtomicBoolean met = new AtomicBoolean();
        for (int i = 0; i < 250 && !met.get(); i++) {
            Thread.sleep(20);
            SwingUtilities.invokeAndWait(() -> met.set(condition.getAsBoolean()));
        }
        assertTrue(met.get(), "timed out waiting for " + what);
    }

    private CanvasAnswer calculate(String latex) throws Exception {
        openPage(latex);
        write();
        waitFor(() -> page.visiblePopup() != null && latex.equals(page.visiblePopup().getLatex()), "popup");
        SwingUtilities.invokeAndWait(() -> page.visiblePopup().clickCalculate());
        waitFor(() -> canvas.getAnswers().size() == 1, "answer");
        return canvas.getAnswers().get(0);
    }

    @Test
    void derivativeIsWrittenOnTheCanvas() throws Exception {
        CanvasAnswer a = calculate("\\frac{d}{dy}(3y^{2})=");
        assertEquals("6y", a.getText());
        assertNotNull(a.getRuns());
        assertTrue(a.getX() > 200, "x " + a.getX());
        assertEquals("= 6y", pdfResult.get());
    }

    @Test
    void noEqualsGetsAnEqualsRun() throws Exception {
        CanvasAnswer a = calculate("\\frac{d}{dx}(x^3)");
        assertEquals("= 3x2", a.getText());
        assertTrue(a.getRuns().get(a.getRuns().size() - 1).isRaised());
    }

    @Test
    void numericAnswerStaysPlain() throws Exception {
        CanvasAnswer a = calculate("1+1=");
        assertEquals("2", a.getText());
        assertNull(a.getRuns());
    }

    @Test
    void negativeLeadsWithMinusSign() throws Exception {
        CanvasAnswer a = calculate("\\frac{d}{dx}(\\cos x)=");
        assertEquals("−sin x", a.getText());
        List<?> runs = a.getRuns();
        assertEquals(1, runs.size());
    }
}
