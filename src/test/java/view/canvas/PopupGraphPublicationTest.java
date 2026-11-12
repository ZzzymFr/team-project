package view.canvas;

import history.data_access.FileHistoryRepository;
import history.domain.HistoryEntry;
import history.use_case.HistoryInteractor;
import interface_adapter.graph.GraphController;
import interface_adapter.graph.GraphPresenter;
import interface_adapter.graph.GraphViewModel;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import pdf_editor.MathLibrary;
import pdf_editor.adapter.MathLabCollector;
import use_case.graph_function.GraphFunctionInputBoundary;
import use_case.graph_function.GraphFunctionOutputData;
import view.DrawingCanvas;
import view.FormulaCapture;

import javax.imageio.ImageIO;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;
import java.awt.Rectangle;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.nio.file.Path;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import static org.junit.jupiter.api.Assertions.*;

/** Tests Graph's real presenter/callback path, without an external chart service or ML model. */
class PopupGraphPublicationTest {
    @TempDir Path storage;
    private static byte[] png() throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ImageIO.write(new BufferedImage(60, 40, BufferedImage.TYPE_INT_RGB), "png", out);
        return out.toByteArray();
    }

    @Test void successfulGraphPublishesItsSourceFormulaAsEditableLibraryAndHistoryMaterial() throws Exception {
        var library = new MathLibrary();
        var history = new HistoryInteractor(new FileHistoryRepository(storage));
        var collector = new MathLabCollector(library, history);
        var viewModel = new GraphViewModel();
        var presenter = new GraphPresenter(viewModel);
        byte[] png = png();
        GraphFunctionInputBoundary graph = input ->
                presenter.prepareSuccessView(new GraphFunctionOutputData(input.getLatex(), png));
        var shared = new SharedCanvas(new DrawingCanvas());
        CountDownLatch saved = new CountDownLatch(1);
        var popup = new PopupGraphing(shared, new GraphController(graph), viewModel,
                (message, error, duration) -> { }, new JPanel(), (capture, latex) -> {
                    collector.acceptCalculation(capture.getId(), latex, "");
                    saved.countDown();
                });
        popup.graph("y=x", new Rectangle(10, 10, 60, 20),
                new FormulaCapture(42, 1, new BufferedImage(4, 4, BufferedImage.TYPE_INT_RGB)));
        assertTrue(saved.await(4, TimeUnit.SECONDS));
        SwingUtilities.invokeAndWait(() -> { });
        assertEquals(1, library.items().size());
        assertEquals("y=x", library.items().get(0).formula());
        assertFalse(library.items().get(0).isGraph());
        assertEquals(1, history.list().size());
        assertEquals(HistoryEntry.Kind.FORMULA, history.list().get(0).kind());
    }

    @Test void graphFailureMustNotPersistTheFormula() throws Exception {
        var library = new MathLibrary();
        var history = new HistoryInteractor(new FileHistoryRepository(storage));
        var collector = new MathLabCollector(library, history);
        var viewModel = new GraphViewModel();
        var presenter = new GraphPresenter(viewModel);
        GraphFunctionInputBoundary graph = input -> presenter.prepareFailView("Invalid graph");
        AtomicInteger callbacks = new AtomicInteger();
        var popup = new PopupGraphing(new SharedCanvas(new DrawingCanvas()), new GraphController(graph),
                viewModel, (message, error, duration) -> { }, new JPanel(), (capture, latex) -> {
                    callbacks.incrementAndGet();
                    collector.acceptCalculation(capture.getId(), latex, "");
                });
        popup.graph("y=1/0", new Rectangle(10, 10, 60, 20),
                new FormulaCapture(3, 1, new BufferedImage(4, 4, BufferedImage.TYPE_INT_RGB)));
        // Let the background graph worker and the EDT complete the failure event.
        for (int attempt = 0; attempt < 60 && viewModel.getState().getError() == null; attempt++)
            Thread.sleep(20);
        SwingUtilities.invokeAndWait(() -> { });
        assertEquals(0, callbacks.get());
        assertTrue(library.items().isEmpty());
        assertTrue(history.list().isEmpty());
    }
}
