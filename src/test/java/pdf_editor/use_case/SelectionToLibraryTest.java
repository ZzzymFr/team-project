package pdf_editor.use_case;

import org.junit.jupiter.api.Test;
import pdf_editor.MathLibrary;
import java.awt.image.BufferedImage;
import static org.junit.jupiter.api.Assertions.*;

class SelectionToLibraryTest {
    private final BufferedImage selected=new BufferedImage(25,15,BufferedImage.TYPE_INT_RGB);
    @Test void explicitRecognitionAddsEditableFormula() throws Exception {
        MathLibrary library=new MathLibrary();SelectionToLibrary useCase=new SelectionToLibrary(i->"x^2",library);
        assertEquals("x^2",useCase.recognize(selected));
        var material=useCase.addFormula("x^2");assertFalse(material.isGraph());assertEquals("x^2",material.formula());
    }
    @Test void previewDoesNotAddAnything() throws Exception {
        MathLibrary library=new MathLibrary();SelectionToLibrary useCase=new SelectionToLibrary(i->"x",library);
        useCase.recognize(selected);assertTrue(library.items().isEmpty());
    }
    @Test void rejectedBlankRecognitionDoesNotAdd() {
        MathLibrary library=new MathLibrary();SelectionToLibrary useCase=new SelectionToLibrary(i->"  ",library);
        assertThrows(IllegalArgumentException.class,()->useCase.recognize(selected));assertTrue(library.items().isEmpty());
    }
    @Test void recognitionErrorsPropagateWithoutAdding() {
        MathLibrary library=new MathLibrary();SelectionToLibrary useCase=new SelectionToLibrary(i->{throw new IllegalStateException("offline");},library);
        assertThrows(IllegalStateException.class,()->useCase.recognize(selected));assertTrue(library.items().isEmpty());
    }
    @Test void explicitInkCapturePreservesDimensions() {
        MathLibrary library=new MathLibrary();SelectionToLibrary useCase=new SelectionToLibrary(i->"x",library);
        var img=useCase.addInk(selected);assertTrue(img.isGraph());assertEquals(25,img.imageWidth());assertEquals(15,img.imageHeight());
    }
    @Test void nullSelectionsCannotBeRecognized() {
        SelectionToLibrary useCase=new SelectionToLibrary(i->"x",new MathLibrary());
        assertThrows(NullPointerException.class,()->useCase.recognize(null));
    }
    @Test void multipleRecognitionsOfSameFormulaDoNotDuplicateMaterials() {
        MathLibrary library=new MathLibrary();SelectionToLibrary useCase=new SelectionToLibrary(i->"x^2",library);
        useCase.addFormula("x^2");useCase.addFormula("x^2");assertEquals(1,library.items().size());
    }
}
