package history.adapter;

import history.data_access.FileHistoryRepository;
import history.use_case.HistoryInteractor;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import pdf_editor.MathLibrary;
import pdf_editor.adapter.MathLabCollector;
import javax.swing.SwingUtilities;
import java.awt.image.BufferedImage;
import java.nio.file.Path;
import static org.junit.jupiter.api.Assertions.*;

class RecognizedFormulaLibraryTest {
    @TempDir Path dir;
    @Test void recognizesIntoBothHistoryAndCurrentSessionLibraryWithoutDuplicateCards() throws Exception {
        HistoryInteractor history = new HistoryInteractor(new FileHistoryRepository(dir));
        MathLibrary library = new MathLibrary();
        MathLabCollector collector = new MathLabCollector(library, history);
        RecordingFormulaRecognizer recognizer = new RecordingFormulaRecognizer(image -> "x+2", history,
                collector::acceptRecognizedFormula);
        var image = new BufferedImage(3, 3, BufferedImage.TYPE_INT_ARGB);
        recognizer.recognize(image);
        recognizer.recognize(image);
        SwingUtilities.invokeAndWait(() -> { });
        assertEquals(1, history.list().size());
        assertEquals(1, library.items().size());
        collector.acceptCalculation(5L, "x+2", "3");
        SwingUtilities.invokeAndWait(() -> { });
        assertEquals(1, history.list().size());
        assertEquals("3", history.list().get(0).result());
    }
}
