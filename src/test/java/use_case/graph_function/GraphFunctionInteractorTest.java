package use_case.graph_function;

import entity.GraphData;
import entity.LatexFunctionParser;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GraphFunctionInteractorTest {

    /** Records what the use case reported. */
    private static class RecordingPresenter implements GraphFunctionOutputBoundary {
        GraphFunctionOutputData success;
        String error;

        @Override
        public void prepareSuccessView(GraphFunctionOutputData outputData) {
            success = outputData;
        }

        @Override
        public void prepareFailView(String errorMessage) {
            error = errorMessage;
        }
    }

    /** Stands in for QuickChart: remembers the data and returns fixed bytes. */
    private static class FakeRenderer implements GraphRendererGateway {
        GraphData received;
        boolean fail;

        @Override
        public byte[] render(GraphData graphData) throws GraphRenderException {
            if (fail) {
                throw new GraphRenderException("offline");
            }
            received = graphData;
            return new byte[]{1, 2, 3};
        }
    }

    private final RecordingPresenter presenter = new RecordingPresenter();
    private final FakeRenderer renderer = new FakeRenderer();
    private final GraphFunctionInteractor interactor =
            new GraphFunctionInteractor(new LatexFunctionParser(), renderer, presenter);

    @Test
    void successSendsSampledPointsAndReturnsImage() {
        interactor.execute(new GraphFunctionInputData("y=x^2", -2, 2));

        assertNull(presenter.error);
        assertNotNull(presenter.success);
        assertArrayEquals(new byte[]{1, 2, 3}, presenter.success.getImageBytes());
        assertEquals("y=x^2", presenter.success.getLatex());

        GraphData data = renderer.received;
        assertEquals(-2, data.getXMin(), 1e-9);
        assertEquals(2, data.getXMax(), 1e-9);
        assertEquals(GraphFunctionInteractor.SAMPLE_COUNT + 1, data.getXs().length);
        assertTrue(data.getYMin() < 0 && data.getYMax() > 4);
    }

    @Test
    void asymptoteIsClippedAndBroken() {
        interactor.execute(new GraphFunctionInputData("\\tan x"));

        GraphData data = renderer.received;
        assertTrue(data.getYMax() < 1000, "window should ignore values near the asymptotes");
        int gaps = 0;
        for (double y : data.getYs()) {
            if (Double.isNaN(y)) {
                gaps++;
            }
        }
        assertTrue(gaps >= 6, "tan x on [-10, 10] has 6 asymptotes");
    }

    @Test
    void constantFunctionGetsANonEmptyWindow() {
        interactor.execute(new GraphFunctionInputData("y=5"));
        assertTrue(renderer.received.getYMin() < 5 && renderer.received.getYMax() > 5);
    }

    @Test
    void parseErrorIsReported() {
        interactor.execute(new GraphFunctionInputData("y=ax+b"));
        assertNull(presenter.success);
        assertTrue(presenter.error.contains("Unknown variable 'a'"));
    }

    @Test
    void emptyInputIsReported() {
        interactor.execute(new GraphFunctionInputData("  "));
        assertEquals("No expression to graph.", presenter.error);
    }

    @Test
    void undefinedEverywhereIsReported() {
        interactor.execute(new GraphFunctionInputData("\\sqrt{x}", -5, -1));
        assertTrue(presenter.error.contains("undefined"));
    }

    @Test
    void invalidRangeIsReported() {
        interactor.execute(new GraphFunctionInputData("x", 3, 3));
        assertTrue(presenter.error.contains("x range"));
    }

    @Test
    void rendererFailureIsReported() {
        renderer.fail = true;
        interactor.execute(new GraphFunctionInputData("x"));
        assertEquals("Could not draw the graph: offline", presenter.error);
    }
}
