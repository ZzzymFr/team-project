package view.canvas;

import data_access.InMemoryFunctionDataAccessObject;
import interface_adapter.MathLabViewModel;
import interface_adapter.evaluate.EvaluateController;
import interface_adapter.evaluate.EvaluatePresenter;
import interface_adapter.graph.GraphController;
import interface_adapter.graph.GraphPresenter;
import interface_adapter.graph.GraphViewModel;
import org.junit.jupiter.api.Test;
import use_case.evaluate.EvaluateInteractor;
import use_case.graph_function.GraphFunctionOutputData;
import view.DrawingCanvas;

import javax.imageio.ImageIO;
import javax.swing.SwingUtilities;
import java.awt.event.ActionEvent;
import java.awt.event.MouseEvent;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BooleanSupplier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

// lasso around ink -> popup with Graph and Calculate for just that ink
class LassoPopupTest {
    private static final int NOTHING_HAPPENS_MILLIS = 250;

    private final DrawingCanvas canvas = new DrawingCanvas();
    private final SharedCanvas shared = new SharedCanvas(canvas);
    private final GraphViewModel graphs = new GraphViewModel();
    private final AtomicReference<String> graphed = new AtomicReference<>();
    private final AtomicReference<BufferedImage> seen = new AtomicReference<>();
    private AnswerCanvasPage page;

    private static byte[] png() {
        try {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            ImageIO.write(new BufferedImage(160, 100, BufferedImage.TYPE_INT_RGB), "png", out);
            return out.toByteArray();
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    private void openPage(String latex) throws Exception {
        shared.setFormulaRecognizer(image -> {
            seen.set(image);
            return latex;
        });
        MathLabViewModel mathLab = new MathLabViewModel();
        EvaluateController evaluate = new EvaluateController(new EvaluateInteractor(
                new InMemoryFunctionDataAccessObject(), new EvaluatePresenter(mathLab)));
        GraphPresenter presenter = new GraphPresenter(graphs);
        GraphController graphing = new GraphController(input -> {
            graphed.set(input.getLatex());
            presenter.prepareSuccessView(new GraphFunctionOutputData(input.getLatex(), png()));
        });
        SwingUtilities.invokeAndWait(() -> {
            FormulaGrapher grapher = new FormulaGrapher(shared, graphing, graphs);
            page = new AnswerCanvasPage(shared, evaluate, mathLab, (l, result) -> { }, grapher);
            page.setPopupDelay(100);
            canvas.setSize(900, 500);
            shared.attachTo(page);
            page.setSize(900, 500);
        });
    }

    private void drag(int... xy) throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            send(MouseEvent.MOUSE_PRESSED, xy[0], xy[1]);
            for (int i = 2; i < xy.length; i += 2) {
                send(MouseEvent.MOUSE_DRAGGED, xy[i], xy[i + 1]);
            }
            send(MouseEvent.MOUSE_RELEASED, xy[xy.length - 2], xy[xy.length - 1]);
        });
    }

    private void send(int id, int x, int y) {
        canvas.dispatchEvent(new MouseEvent(canvas, id, 0, 0, x, y, 1, false, MouseEvent.BUTTON1));
    }

    private void lasso(int left, int top, int right, int bottom) throws Exception {
        SwingUtilities.invokeAndWait(() -> canvas.setTool(DrawingCanvas.Tool.LASSO));
        drag(left, top, right, top, right, bottom, left, bottom);
    }

    private void waitFor(BooleanSupplier condition, String what) throws Exception {
        AtomicBoolean met = new AtomicBoolean();
        for (int i = 0; i < 250 && !met.get(); i++) {
            Thread.sleep(20);
            SwingUtilities.invokeAndWait(() -> met.set(condition.getAsBoolean()));
        }
        assertTrue(met.get(), "timed out waiting for " + what);
    }

    private boolean popupIsUp() {
        return page.visiblePopup() != null;
    }

    /** Two formulas far apart, each offered its own popup and left alone. */
    private void writeTwoFormulas() throws Exception {
        drag(100, 100, 200, 150);                  // A
        waitFor(this::popupIsUp, "A's popup");
        drag(100, 380, 300, 420);                  // B
        waitFor(this::popupIsUp, "B's popup");
    }

    @Test
    void circlingAFormulaOffersGraphAndCalculateForJustThatInk() throws Exception {
        openPage("1+1");
        writeTwoFormulas();
        lasso(80, 80, 220, 170);                   // around A

        waitFor(this::popupIsUp, "the popup");
        SwingUtilities.invokeAndWait(() -> {
            assertTrue(page.visiblePopup().canGraph());
            assertTrue(page.visiblePopup().hasCalculate());
            assertEquals("1+1", page.visiblePopup().getLatex());
        });
        assertEquals(51 + 2 * 18, seen.get().getHeight(), "the image holds only A");
    }

    @Test
    void calculateAnswersBesideTheCircledFormulaAndEndsTheSelection() throws Exception {
        openPage("1+1");
        writeTwoFormulas();
        lasso(80, 80, 220, 170);
        waitFor(this::popupIsUp, "the popup");
        SwingUtilities.invokeAndWait(() -> page.visiblePopup().clickCalculate());

        waitFor(() -> !canvas.getAnswers().isEmpty(), "the answer");
        assertTrue(canvas.getAnswers().get(0).getBox().getCenterY() < 300, "beside A, not B");
        assertFalse(canvas.hasSelection());
        assertNull(page.visiblePopup());
    }

    @Test
    void graphDrawsTheCardBesideTheCircledFormulaAndEndsTheSelection() throws Exception {
        openPage("y=x");
        writeTwoFormulas();
        lasso(80, 360, 320, 440);                  // around B
        waitFor(this::popupIsUp, "the popup");
        SwingUtilities.invokeAndWait(() -> page.visiblePopup().clickGraph());

        waitFor(shared::hasGraph, "the graph card");
        assertEquals("y=x", graphed.get());
        assertTrue(canvas.getGraphCardOnScreen().getMinX() >= 300, "beside B");
        assertFalse(canvas.hasSelection());
        assertNull(page.visiblePopup());
    }

    @Test
    void justCirclingAFormulaKeepsItsAnswer() throws Exception {
        openPage("1+1");
        drag(100, 100, 200, 150);
        waitFor(this::popupIsUp, "the popup");
        SwingUtilities.invokeAndWait(() -> page.visiblePopup().clickCalculate());
        waitFor(() -> !canvas.getAnswers().isEmpty(), "the answer");

        lasso(80, 80, 220, 170);
        waitFor(this::popupIsUp, "the popup");
        assertEquals(1, canvas.getAnswers().size(), "only Calculate replaces it");
        assertEquals(1, canvas.getFormulas().size());
    }

    @Test
    void aLassoAroundNothingOffersNothing() throws Exception {
        openPage("1+1");
        drag(100, 100, 200, 150);
        waitFor(this::popupIsUp, "the popup");
        lasso(500, 300, 600, 400);

        Thread.sleep(NOTHING_HAPPENS_MILLIS);
        assertNull(page.visiblePopup());
    }

    @Test
    void movingTheSelectionDoesNotOfferAgain() throws Exception {
        openPage("1+1");
        writeTwoFormulas();
        lasso(80, 80, 220, 170);
        waitFor(this::popupIsUp, "the popup");

        drag(150, 125, 450, 125);                  // drag the selection away
        Thread.sleep(NOTHING_HAPPENS_MILLIS);
        assertNull(page.visiblePopup());
        assertTrue(canvas.hasSelection(), "still selected, to keep moving it");
    }

    @Test
    void escapeEndsTheSelectionAndThePopupWithIt() throws Exception {
        openPage("1+1");
        writeTwoFormulas();
        lasso(80, 80, 220, 170);
        waitFor(this::popupIsUp, "the popup");

        SwingUtilities.invokeAndWait(() -> canvas.getActionMap().get("clearSelection")
                .actionPerformed(new ActionEvent(canvas, ActionEvent.ACTION_PERFORMED, "")));
        assertNull(page.visiblePopup());
        assertFalse(canvas.hasSelection());
    }

    @Test
    void endingTheSelectionWhileItIsBeingReadShowsNoPopup() throws Exception {
        java.util.concurrent.CountDownLatch reading = new java.util.concurrent.CountDownLatch(1);
        java.util.concurrent.CountDownLatch release = new java.util.concurrent.CountDownLatch(1);
        openPage("1+1");
        drag(100, 100, 200, 150);
        waitFor(this::popupIsUp, "the first popup");
        shared.setFormulaRecognizer(image -> {
            reading.countDown();
            release.await();
            return "1+1";
        });

        lasso(80, 80, 220, 170);
        assertTrue(reading.await(5, java.util.concurrent.TimeUnit.SECONDS));
        SwingUtilities.invokeAndWait(() -> canvas.getActionMap().get("clearSelection")
                .actionPerformed(new ActionEvent(canvas, ActionEvent.ACTION_PERFORMED, "")));
        release.countDown();

        Thread.sleep(NOTHING_HAPPENS_MILLIS);
        assertNull(page.visiblePopup());
    }
}
