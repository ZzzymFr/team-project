package history.adapter;

import history.data_access.FileHistoryRepository;
import history.use_case.HistoryInteractor;
import history.domain.HistoryEntry;
import interface_adapter.history.HistoryViewModel;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import pdf_editor.MathLibrary;
import pdf_editor.adapter.MathLabCollector;
import javax.swing.SwingUtilities;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Path;
import static org.junit.jupiter.api.Assertions.*;

class OneBoardHistoryIntegrationTest {
    @TempDir Path storage;
    private void flush() throws Exception { SwingUtilities.invokeAndWait(() -> {}); }

    @Test void recognizesOnceAndReloadsAcrossSession() throws Exception {
        HistoryInteractor history = new HistoryInteractor(new FileHistoryRepository(storage));
        RecordingFormulaRecognizer recognizer = new RecordingFormulaRecognizer(i -> "x^2+1", history);
        recognizer.recognize(new BufferedImage(1, 1, BufferedImage.TYPE_INT_ARGB));
        recognizer.recognize(new BufferedImage(1, 1, BufferedImage.TYPE_INT_ARGB));
        assertEquals(1, new HistoryInteractor(new FileHistoryRepository(storage)).list().size());
    }
    @Test void brokenRecognizerNeverWritesHistory() throws Exception {
        HistoryInteractor history = new HistoryInteractor(new FileHistoryRepository(storage));
        RecordingFormulaRecognizer recognizer = new RecordingFormulaRecognizer(i -> { throw new IOException("bad model"); }, history);
        assertThrows(IOException.class, () -> recognizer.recognize(new BufferedImage(1,1,2)));
        assertTrue(history.list().isEmpty());
    }
    @Test void autosavedCalculationUpgradesFormulaWithoutDuplicates() throws Exception {
        HistoryInteractor history = new HistoryInteractor(new FileHistoryRepository(storage));
        MathLibrary library = new MathLibrary();
        MathLabCollector collector = new MathLabCollector(library, history);
        collector.acceptRecognizedFormula("x+2"); flush();
        collector.acceptCalculation(12L, "x+2", "3"); flush();
        collector.acceptCalculation(12L, "x+2", "3"); flush();
        assertEquals(1, history.list().size());
        assertEquals("3", history.list().get(0).result());
        library.clear();
        assertEquals(1, new HistoryInteractor(new FileHistoryRepository(storage)).list().size());
    }
    @Test void graphSavesAsPngAndCanBeMappedBack() throws Exception {
        HistoryInteractor history = new HistoryInteractor(new FileHistoryRepository(storage));
        BufferedImage graph = new BufferedImage(120, 60, BufferedImage.TYPE_INT_RGB);
        new MathLabCollector(new MathLibrary(), history).acceptGraph("y=x^2", graph); flush();
        HistoryEntry saved = history.list().get(0);
        assertEquals(HistoryEntry.Kind.GRAPH, saved.kind());
        assertEquals(120, HistoryMaterialMapper.toPdfMaterial(saved).imageWidth());
        assertArrayEquals(saved.png(), HistoryMaterialMapper.toPdfMaterial(saved).imagePng());
    }
    @Test void deletionNotifiesBoardPresentationAndPersists() throws Exception {
        HistoryInteractor history = new HistoryInteractor(new FileHistoryRepository(storage));
        HistoryViewModel model = new HistoryViewModel();
        HistoryBoardPresenter presenter = new HistoryBoardPresenter(history, model);
        presenter.refresh();
        history.recordCalculation("2+2", "4"); flush();
        assertEquals(1, model.getItems().size());
        assertTrue(presenter.delete(model.getItems().get(0).getId()));flush();
        assertTrue(model.getItems().isEmpty());
        assertTrue(new HistoryInteractor(new FileHistoryRepository(storage)).list().isEmpty());
    }
}
