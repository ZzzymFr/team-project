package history.adapter;

import history.data_access.FileHistoryRepository;
import history.use_case.HistoryInteractor;
import interface_adapter.history.HistoryViewModel;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.Path;
import static org.junit.jupiter.api.Assertions.*;

/** Explicitly published graph formulas are visible even when no numerical solution exists. */
class GraphableFormulaHistoryTest {
    @TempDir Path directory;

    @Test void graphableFormulaAppearsWithoutAnAnswerAndSurvivesRestart() throws Exception {
        var history = new HistoryInteractor(new FileHistoryRepository(directory));
        assertTrue(history.recordFormula("y=x"));
        var model = new HistoryViewModel();
        var presenter = new HistoryBoardPresenter(history, model);
        presenter.refresh();
        assertEquals(1, model.getItems().size());
        assertEquals("y=x", model.getItems().get(0).getLatex());
        assertEquals("", model.getItems().get(0).getAnswer());
        var reopened = new HistoryViewModel();
        new HistoryBoardPresenter(new HistoryInteractor(new FileHistoryRepository(directory)), reopened).refresh();
        assertEquals(1, reopened.getItems().size());
    }

    @Test void legacyPunctuationIsNotShownButIsNotDeletedFromDisk() throws Exception {
        var history = new HistoryInteractor(new FileHistoryRepository(directory));
        history.recordFormula(".");
        history.recordFormula("y=x");
        var model = new HistoryViewModel();
        new HistoryBoardPresenter(history, model).refresh();
        assertEquals(1, model.getItems().size());
        assertEquals("y=x", model.getItems().get(0).getLatex());
        assertEquals(2, history.list().size());
    }
    @Test void savedGraphAndEditableFormulaAreSeparateHistoryMaterials() throws Exception {
        var history = new HistoryInteractor(new FileHistoryRepository(directory));
        var library = new pdf_editor.MathLibrary();
        var collector = new pdf_editor.adapter.MathLabCollector(library, history);
        var graph = new java.awt.image.BufferedImage(60, 40, java.awt.image.BufferedImage.TYPE_INT_RGB);
        collector.acceptGraph("y=x", graph);
        collector.acceptCalculation(23L, "y=x", "");
        javax.swing.SwingUtilities.invokeAndWait(() -> { });
        assertEquals(2, history.list().size());
        assertEquals(2, library.items().size());
        assertEquals(1L, library.items().stream().filter(x -> x.isGraph()).count());
        assertEquals(1L, library.items().stream().filter(x -> !x.isGraph()).count());
        var model = new HistoryViewModel();
        new HistoryBoardPresenter(history, model).refresh();
        assertEquals(2, model.getItems().size());
        assertTrue(model.getItems().stream().anyMatch(x -> "y=x".equals(x.getLatex()) && !x.isGraph()));
        assertTrue(model.getItems().stream().anyMatch(x -> x.isGraph()));
        assertFalse(history.recordFormula("y=x"));
    }
}
