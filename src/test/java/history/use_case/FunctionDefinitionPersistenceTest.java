package history.use_case;

import history.data_access.FileHistoryRepository;
import history.domain.HistoryEntry;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.Path;
import java.util.Set;
import static org.junit.jupiter.api.Assertions.*;

class FunctionDefinitionPersistenceTest {
    @TempDir Path folder;
    @Test void completedDefinitionWithoutNumericalResultIsSavedAndLinked() throws Exception {
        var history=new HistoryInteractor(new FileHistoryRepository(folder));
        var lifecycle=new CanvasHistoryLifecycle(history);
        assertTrue(lifecycle.recordCalculation(27,"y=3x",""));
        assertEquals(1,history.list().size());
        assertEquals(HistoryEntry.Kind.FORMULA,history.list().get(0).kind());
        var id=history.list().get(0).id();
        lifecycle.synchronize(Set.of());
        assertEquals(0,history.list().size());
        lifecycle.synchronize(Set.of(27L));
        assertEquals(id,history.list().get(0).id());
    }
    @Test void repeatedDefinitionIsDeduplicated() throws Exception {
        var history=new HistoryInteractor(new FileHistoryRepository(folder));
        var lifecycle=new CanvasHistoryLifecycle(history);
        assertTrue(lifecycle.recordCalculation(4,"y=3x",""));
        assertFalse(lifecycle.recordCalculation(4,"y=3x",""));
        assertEquals(1,history.list().size());
    }
    @Test void earlierHistoryCanBeLoadedFromDisk() throws Exception {
        var saved=new HistoryInteractor(new FileHistoryRepository(folder));
        assertTrue(saved.recordFormula("y=3x"));
        var reopened=new HistoryInteractor(new FileHistoryRepository(folder));
        assertEquals("y=3x",reopened.list().get(0).formula());
    }
}
