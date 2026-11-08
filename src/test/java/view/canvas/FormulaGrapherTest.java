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
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Graph on the Math tab: circle -> Graph -> graph use case -> card or error message. */
class FormulaGrapherTest {

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
    private final CanvasDriver driver = new CanvasDriver(canvas);
    private final view.ribbon.Ribbon ribbon = new view.ribbon.Ribbon("Math Lab");
    private FormulaGrapher grapher;
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

    private void openPage() throws Exception {
        MathLabViewModel mathLab = new MathLabViewModel();
        EvaluateController evaluate = new EvaluateController(new EvaluateInteractor(
                new InMemoryFunctionDataAccessObject(), new EvaluatePresenter(mathLab)));
        SwingUtilities.invokeAndWait(() -> {
            grapher = new FormulaGrapher(shared, new GraphController(useCase), viewModel);
            page = new AnswerCanvasPage(shared, evaluate, mathLab, (latex, result) -> { }, grapher);
            canvas.setSize(900, 500);
            shared.attachTo(page);
            page.setSize(900, 500);
            page.addMathTab(ribbon);
        });
    }

    private void writeAndCircle(String latex) throws Exception {
        driver.write(100, 100, 200, 150);
        driver.lasso(80, 80, 220, 170);
        CanvasDriver.waitForReading(page, latex);
    }

    private void clickGraph() throws Exception {
        SwingUtilities.invokeAndWait(() -> page.getGraphButton().doClick());
    }

    private boolean enabled(view.ribbon.RibbonButton button) throws Exception {
        return CanvasDriver.check(() -> {
            ribbon.refresh();
            return button.isEnabled();
        });
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
    void theMathTabOffersBothGraphAndCalculate() throws Exception {
        shared.setFormulaRecognizer(image -> "y=x^2");
        openPage();
        writeAndCircle("y=x^2");
        assertTrue(enabled(page.getGraphButton()));
        assertTrue(enabled(page.getCalculateButton()));
    }

    @Test
    void graphDrawsTheCardBesideTheFormulaAndEndsTheSelection() throws Exception {
        shared.setFormulaRecognizer(image -> "y=x^2");
        openPage();
        writeAndCircle("y=x^2");
        clickGraph();

        waitFor(shared::hasGraph, "the graph card");
        assertEquals("y=x^2", useCase.received);
        assertFalse(CanvasDriver.check(canvas::hasSelection));
        assertTrue(canvas.getGraphCardOnScreen().getMinX() >= 200 + 40, "the card is beside the formula");
        waitFor(() -> !page.isMessageError() && "Graph of y=x^2".equals(page.currentMessage()), "the success message");
    }

    @Test
    void graphGoesBesideTheCircledFormulaNotBesideAllTheInk() throws Exception {
        shared.setFormulaRecognizer(image -> "y=2x");
        openPage();
        driver.write(100, 100, 200, 150);        // formula A
        driver.write(100, 380, 200, 420);        // formula B, far below it
        driver.lasso(80, 360, 220, 440);         // around B
        CanvasDriver.waitForReading(page, "y=2x");
        clickGraph();

        waitFor(shared::hasGraph, "the graph card");
        assertNotNull(canvas.getGraphCardOnScreen());
        assertTrue(canvas.getGraphCardOnScreen().getCenterY() > 150, "level with B, not with A");
    }

    @Test
    void emptyRangeBoxesMeanTheDefaults() throws Exception {
        shared.setFormulaRecognizer(image -> "y=x");
        openPage();
        writeAndCircle("y=x");
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
        writeAndCircle("y=x^2");
        SwingUtilities.invokeAndWait(() -> grapher.setRangeText("[-3, 3]", "0 to 9"));
        clickGraph();

        waitFor(shared::hasGraph, "the graph card");
        assertEquals(-3, useCase.input.getXMin());
        assertEquals(3, useCase.input.getXMax());
        assertEquals(0, useCase.input.getYMin());
        assertEquals(9, useCase.input.getYMax());
    }

    @Test
    void aTypoInARangeIsReportedAndTheSelectionStaysToTryAgain() throws Exception {
        shared.setFormulaRecognizer(image -> "y=x");
        openPage();
        writeAndCircle("y=x");
        SwingUtilities.invokeAndWait(() -> grapher.setRangeText("5, -5", ""));
        clickGraph();

        waitFor(() -> errorShown("x range must go from a smaller number"), "the range error");
        assertNull(useCase.received, "nothing was sent to the use case");
        assertTrue(CanvasDriver.check(canvas::hasSelection), "still circled, to press again once the box is fixed");

        SwingUtilities.invokeAndWait(() -> grapher.setRangeText("-5, 5", ""));
        clickGraph();
        waitFor(shared::hasGraph, "the graph card");
        assertEquals(-5, useCase.input.getXMin());
    }

    @Test
    void useCaseErrorIsShownAndNoCardAppears() throws Exception {
        shared.setFormulaRecognizer(image -> "8x+4=20");
        useCase.fail = true;
        openPage();
        writeAndCircle("8x+4=20");
        clickGraph();

        waitFor(() -> errorShown("Cannot graph \"8x+4=20\""), "the error message");
        assertFalse(shared.hasGraph());
    }

    @Test
    void enterInARangeBoxGraphsTheLastFormulaAgain() throws Exception {
        shared.setFormulaRecognizer(image -> "y=x");
        openPage();
        writeAndCircle("y=x");
        clickGraph();
        waitFor(shared::hasGraph, "the graph card");
        assertEquals(10, useCase.input.getXMax());

        SwingUtilities.invokeAndWait(() -> {
            grapher.setRangeText("-2, 2", "");
            ((javax.swing.JTextField) grapher.controls()[1]).postActionEvent();
        });
        waitFor(() -> useCase.input.getXMax() == 2, "the new range");
    }

    @Test
    void graphIsOfferedOnlyWithAGrapher() throws Exception {
        MathLabViewModel mathLab = new MathLabViewModel();
        EvaluateController evaluate = new EvaluateController(new EvaluateInteractor(
                new InMemoryFunctionDataAccessObject(), new EvaluatePresenter(mathLab)));
        shared.setFormulaRecognizer(image -> "1+1");
        SwingUtilities.invokeAndWait(() -> {
            page = new AnswerCanvasPage(shared, evaluate, mathLab);
            canvas.setSize(900, 500);
            shared.attachTo(page);
            page.setSize(900, 500);
            page.addMathTab(ribbon);
        });
        writeAndCircle("1+1");
        assertFalse(enabled(page.getGraphButton()));
        assertTrue(enabled(page.getCalculateButton()));
    }
}
