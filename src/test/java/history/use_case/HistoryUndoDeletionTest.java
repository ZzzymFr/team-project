package history.use_case;

import history.data_access.FileHistoryRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.Path;
import static org.junit.jupiter.api.Assertions.*;

class HistoryUndoDeletionTest {
    @TempDir Path dir;
    private HistoryInteractor session(){return new HistoryInteractor(new FileHistoryRepository(dir));}
    @Test void deletionCanBeUndoneAndKeepsOriginalIdentity() throws Exception {
        HistoryInteractor history=session();history.recordCalculation("x","2");var original=history.list().get(0);
        history.delete(original.id());assertTrue(history.undoDelete());
        assertEquals(original.id(),history.list().get(0).id());
    }
    @Test void undoneDeletionSurvivesApplicationRestart() throws Exception {
        HistoryInteractor history=session();history.recordCalculation("x","2");var saved=history.list().get(0);
        history.delete(saved.id());history.undoDelete();assertEquals(saved.id(),session().list().get(0).id());
    }
    @Test void unknownDeleteDoesNotModifyHistory() throws Exception {
        HistoryInteractor history=session();history.recordCalculation("x","2");
        assertFalse(history.delete("unknown"));assertEquals(1,history.list().size());
    }
    @Test void undoWhenNothingWasDeletedReturnsFalse() throws Exception {assertFalse(session().undoDelete());}
    @Test void deletedRecordsRestoredMostRecentFirst() throws Exception {
        HistoryInteractor history=session();history.recordCalculation("x","1");history.recordCalculation("x","2");
        for (var item:history.list())history.delete(item.id());
        assertTrue(history.undoDelete());assertEquals(1,history.list().size());
        assertTrue(history.undoDelete());assertEquals(2,history.list().size());
    }
    @Test void deletedGraphIsRestoredWithOriginalPng() throws Exception {
        HistoryInteractor history=session();
        var picture=new java.awt.image.BufferedImage(10,11,2);
        var bytes=new java.io.ByteArrayOutputStream();javax.imageio.ImageIO.write(picture,"png",bytes);
        history.recordGraph("x",bytes.toByteArray(),10,11);
        String id=history.list().get(0).id();history.delete(id);history.undoDelete();
        assertArrayEquals(bytes.toByteArray(),history.find(id).png());
    }
}
