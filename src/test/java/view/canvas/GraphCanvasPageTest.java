package view.canvas;

import interface_adapter.graph.GraphController;
import interface_adapter.graph.GraphPresenter;
import interface_adapter.graph.GraphViewModel;
import org.junit.jupiter.api.Test;
import use_case.graph_function.GraphFunctionInputBoundary;
import use_case.graph_function.GraphFunctionInputData;
import use_case.graph_function.GraphFunctionOutputData;
import view.DrawingCanvas;

import javax.imageio.ImageIO;
import javax.swing.SwingUtilities;
import java.awt.event.MouseEvent;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.BooleanSupplier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The Graph page: Graph button -> recognition -> graph use case -> card or error message. */
class GraphCanvasPageTest {

    /** Stands in for the graph use case: records the LaTeX and answers through the real presenter. */
    private static class FakeGraphUseCase implements GraphFunctionInputBoundary {
        final GraphPresenter presenter;
        volatile String received;
        volatile GraphFunctionInputData input;
        volatile boolean fail;

        FakeGraphUseCase(GraphPresenter presenter) {
            this.presenter = presenter;
        }

        @Override
        public void execute(GraphFunctionInputData input) {
            this.input = input;
            received = input.getLatex();
            if (fail) {
                presenter.prepareFailView("Cannot graph \"" + received + "\": Write the function explicitly.");
            } else {
                presenter.prepareSuccessView(new GraphFunctionOutputData(received, png()));
            }
        }
    }

    private final DrawingCanvas canvas = new DrawingCanvas();
    private final SharedCanvas shared = new SharedCanvas(canvas);
    private final GraphViewModel viewModel = new GraphViewModel();
    private final FakeGraphUseCase useCase = new FakeGraphUseCase(new GraphPresenter(viewModel));
    private GraphCanvasPage page;

    private static byte[] png() {
        try {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            ImageIO.write(new BufferedImage(160, 100, BufferedImage.TYPE_INT_RGB), "png", out);
            return out.toByteArray();
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    private void openPage() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            page = new GraphCanvasPage(shared, new GraphController(useCase), viewModel);
            canvas.setSize(900, 500);
            shared.attachTo(page);
        });
    }

    private void write() {
        canvas.dispatchEvent(new MouseEvent(canvas, MouseEvent.MOUSE_PRESSED, 0, 0, 100, 100, 1, false, MouseEvent.BUTTON1));
        canvas.dispatchEvent(new MouseEvent(canvas, MouseEvent.MOUSE_DRAGGED, 0, 0, 200, 150, 1, false, MouseEvent.BUTTON1));
        canvas.dispatchEvent(new MouseEvent(canvas, MouseEvent.MOUSE_RELEASED, 0, 0, 200, 150, 1, false, MouseEvent.BUTTON1));
    }

    private void clickGraph() throws Exception {
        SwingUtilities.invokeAndWait(() -> page.graph());
    }

    /** Waits (up to 5 s) for something the background work will make true, checking on the event thread. */
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
    void withoutRecognitionItSaysSoAndGraphsNothing() throws Exception {
        openPage();
        write();
        clickGraph();

        waitFor(() -> errorShown("not connected"), "the not-connected message");
        assertNull(useCase.received, "nothing was sent to the graph use case");
        assertFalse(shared.hasGraph());
    }

    @Test
    void recognisedFormulaIsGraphedBesideIt() throws Exception {
        shared.setFormulaRecognizer(image -> "y=x^2");
        openPage();
        write();
        clickGraph();

        waitFor(shared::hasGraph, "the graph card");
        assertEquals("y=x^2", useCase.received);
        waitFor(() -> !page.isMessageError() && "Graph of y=x^2".equals(page.currentMessage()), "the success message");
    }

    @Test
    void onlyTheFormulaCircledWithTheLassoIsRecognisedAndGraphed() throws Exception {
        java.util.concurrent.atomic.AtomicReference<BufferedImage> seen = new java.util.concurrent.atomic.AtomicReference<>();
        shared.setFormulaRecognizer(image -> {
            seen.set(image);
            return "y=2x";
        });
        openPage();
        write();                                    // formula A: (100,100)-(200,150)
        drag(400, 400, 420, 420);                   // formula B, far away
        canvas.setTool(DrawingCanvas.Tool.LASSO);
        drag(80, 80, 220, 80, 220, 170, 80, 170);   // circle A
        clickGraph();

        waitFor(shared::hasGraph, "the graph card");
        assertEquals(51 + 2 * 18, seen.get().getHeight(), "the image holds only A");
        assertEquals(2 * (51 + 2 * 18), seen.get().getWidth());
        assertTrue(canvas.getGraphCardOnScreen().getMinX() >= 200 + 40, "the card is beside A");
    }

    private void drag(int... xy) {
        canvas.dispatchEvent(new MouseEvent(canvas, MouseEvent.MOUSE_PRESSED, 0, 0, xy[0], xy[1], 1, false, MouseEvent.BUTTON1));
        for (int i = 2; i < xy.length; i += 2) {
            canvas.dispatchEvent(new MouseEvent(canvas, MouseEvent.MOUSE_DRAGGED, 0, 0, xy[i], xy[i + 1], 1, false, MouseEvent.BUTTON1));
        }
        canvas.dispatchEvent(new MouseEvent(canvas, MouseEvent.MOUSE_RELEASED, 0, 0,
                xy[xy.length - 2], xy[xy.length - 1], 1, false, MouseEvent.BUTTON1));
    }

    @Test
    void emptyRangeBoxesMeanTheDefaults() throws Exception {
        shared.setFormulaRecognizer(image -> "y=x");
        openPage();
        write();
        clickGraph();

        waitFor(shared::hasGraph, "the graph card");
        assertEquals(-10, useCase.input.getXMin());
        assertEquals(10, useCase.input.getXMax());
        assertTrue(useCase.input.isYAutomatic());
    }

    @Test
    void typedRangesAreUsed() throws Exception {
        shared.setFormulaRecognizer(image -> "y=x^2");
        openPage();
        write();
        SwingUtilities.invokeAndWait(() -> page.setRangeText("[-3, 3]", "0 to 9"));
        clickGraph();

        waitFor(shared::hasGraph, "the graph card");
        assertEquals(-3, useCase.input.getXMin());
        assertEquals(3, useCase.input.getXMax());
        assertEquals(0, useCase.input.getYMin());
        assertEquals(9, useCase.input.getYMax());
    }

    @Test
    void aTypoInARangeIsReportedBeforeAnythingElse() throws Exception {
        java.util.concurrent.atomic.AtomicBoolean recognised = new java.util.concurrent.atomic.AtomicBoolean();
        shared.setFormulaRecognizer(image -> {
            recognised.set(true);
            return "y=x";
        });
        openPage();
        write();
        SwingUtilities.invokeAndWait(() -> page.setRangeText("5, -5", ""));
        clickGraph();

        waitFor(() -> errorShown("x range must go from a smaller number"), "the range error");
        assertFalse(recognised.get(), "nothing was sent to recognition");
        assertNull(useCase.received);
    }

    @Test
    void useCaseErrorIsShownAndNoCardAppears() throws Exception {
        shared.setFormulaRecognizer(image -> "8x+4=20");
        useCase.fail = true;
        openPage();
        write();
        clickGraph();

        waitFor(() -> errorShown("Cannot graph \"8x+4=20\""), "the error message");
        assertFalse(shared.hasGraph());
    }

    @Test
    void emptyCanvasIsReported() throws Exception {
        shared.setFormulaRecognizer(image -> "y=x");
        openPage();
        clickGraph();

        waitFor(() -> errorShown("The canvas is empty"), "the empty-canvas message");
        assertNull(useCase.received);
    }
}
