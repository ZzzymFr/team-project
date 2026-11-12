package pdf_editor;

import org.junit.jupiter.api.Test;
import java.util.Set;
import static org.junit.jupiter.api.Assertions.*;
class FunctionDefinitionLibraryTest {
    @Test void expressionWithoutNumericResultStillVisible() {
        MathLibrary library = new MathLibrary();
        var item = library.addSourceCalculation(17,"y=3x","");
        assertEquals("y=3x",item.formula());
        assertEquals("",item.result());
        library.retainActiveSources(Set.of(17L));
        assertNotNull(library.find(item.id()));
    }
    @Test void erasedDefinitionRestoredWithOriginalIdentity() {
        MathLibrary library=new MathLibrary();
        var item=library.addSourceCalculation(23,"y=3x","");
        library.retainActiveSources(Set.of());
        assertNull(library.find(item.id()));
        library.retainActiveSources(Set.of(23L));
        assertSame(item,library.find(item.id()));
    }
}
