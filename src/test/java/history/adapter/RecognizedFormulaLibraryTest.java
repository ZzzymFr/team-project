package history.adapter;

import history.data_access.FileHistoryRepository;
import history.use_case.HistoryInteractor;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import pdf_editor.MathLibrary;
import pdf_editor.use_case.SelectionToLibrary;
import pdf_editor.adapter.MathLabCollector;
import javax.swing.SwingUtilities;
import java.awt.image.BufferedImage;
import java.nio.file.Path;
import static org.junit.jupiter.api.Assertions.*;

/** Preview recognition is deliberately ephemeral until Calculate/Graph is invoked. */
class RecognizedFormulaLibraryTest {
    @TempDir Path dir;
    @Test void previewDoesNotPopulateHistoryOrLibrary() throws Exception {
        HistoryInteractor history = new HistoryInteractor(new FileHistoryRepository(dir));
        MathLibrary library = new MathLibrary();
        // Recognizer can preview an arbitrary tiny mark many times; no listener auto-records it.
        var recognize = (pdf_editor.use_case.SelectedInkRecognitionGateway) image -> ".";
        BufferedImage picture = new BufferedImage(5, 5, BufferedImage.TYPE_INT_RGB);
        assertEquals(".", recognize.recognize(picture));
        assertEquals(".", recognize.recognize(picture));
        assertTrue(library.items().isEmpty());
        assertTrue(history.list().isEmpty());
    }
    @Test void userExplicitlyAddsSelectionOnlyToCurrentLibrary() throws Exception {
        HistoryInteractor history = new HistoryInteractor(new FileHistoryRepository(dir));
        MathLibrary library = new MathLibrary();
        SelectionToLibrary command = new SelectionToLibrary(image -> "x+2", library);
        var image = new BufferedImage(10, 10, BufferedImage.TYPE_INT_RGB);
        command.addFormula(command.recognize(image));
        command.addFormula(command.recognize(image));
        assertEquals(1, library.items().size());
        assertTrue(history.list().isEmpty());
        new MathLabCollector(library, history).acceptCalculation(5L, "x+2", "3");
        SwingUtilities.invokeAndWait(() -> {});
        assertEquals(1, history.list().size());
        assertEquals("3", history.list().get(0).result());
    }
}
