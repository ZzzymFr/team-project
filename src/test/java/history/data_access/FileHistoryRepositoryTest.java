package history.data_access;

import history.domain.HistoryEntry;
import history.use_case.HistoryInteractor;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class FileHistoryRepositoryTest {
    @TempDir Path temp;
    private HistoryEntry entry(String formula, String result) {
        return new HistoryEntry(java.util.UUID.randomUUID().toString(), HistoryEntry.Kind.CALCULATION,
                "Calculation", formula, result, null, 0, 0, Instant.now());
    }
    @Test void noDirectoryMeansEmptyHistory() throws Exception {
        assertTrue(new FileHistoryRepository(temp.resolve("missing")).findAll().isEmpty());
    }
    @Test void restartLoadsUnicodeFormulaAndResult() throws Exception {
        Path dir = temp.resolve("history");
        HistoryInteractor first = new HistoryInteractor(new FileHistoryRepository(dir));
        first.recordCalculation("\\int_0^1 x\\,dx 中文", "答案=0.5");
        HistoryInteractor restarted = new HistoryInteractor(new FileHistoryRepository(dir));
        assertEquals(1, restarted.list().size());
        assertEquals("答案=0.5", restarted.list().get(0).result());
        assertEquals("\\int_0^1 x\\,dx 中文", restarted.list().get(0).formula());
    }
    @Test void restartRetainsOneCopyOnly() throws Exception {
        Path dir = temp.resolve("history");
        assertTrue(new FileHistoryRepository(dir).saveIfAbsent(entry("x", "2")));
        assertFalse(new FileHistoryRepository(dir).saveIfAbsent(entry("x", "2")));
        assertEquals(1, new FileHistoryRepository(dir).findAll().size());
    }
    @Test void differingResultsGetSeparateFiles() throws Exception {
        FileHistoryRepository repo = new FileHistoryRepository(temp);
        repo.saveIfAbsent(entry("x", "2"));repo.saveIfAbsent(entry("x", "3"));
        assertEquals(2, repo.findAll().size());
    }
    @Test void deletionIsDurableAndDoesNotDeleteOtherHistory() throws Exception {
        FileHistoryRepository repo = new FileHistoryRepository(temp);
        HistoryEntry a = entry("a", "1"), b = entry("b", "2");
        repo.saveIfAbsent(a);repo.saveIfAbsent(b);
        assertTrue(repo.deleteById(a.id()));
        assertFalse(repo.deleteById(a.id()));
        List<HistoryEntry> remaining = new FileHistoryRepository(temp).findAll();
        assertEquals(1, remaining.size());assertEquals(b.id(), remaining.get(0).id());
    }
    @Test void ignoresCorruptedFileWhileLoadingGoodHistory() throws Exception {
        FileHistoryRepository repo = new FileHistoryRepository(temp);
        repo.saveIfAbsent(entry("valid", "yes"));
        Files.writeString(temp.resolve("a".repeat(64) + ".properties"), "nonsense-not-properties");
        assertEquals(1, repo.findAll().size());
    }
    @Test void ignoresUnrecognizedVersion() throws Exception {
        Path bad = temp.resolve("f".repeat(64) + ".properties");
        Files.writeString(bad, "version=99\nid=x\n");
        assertTrue(new FileHistoryRepository(temp).findAll().isEmpty());
    }
    @Test void ignoresUnrelatedFilesAndInterruptedTemp() throws Exception {
        Files.writeString(temp.resolve("notes.txt"), "hi");
        Files.writeString(temp.resolve("history-leftover.tmp"), "partial");
        assertTrue(new FileHistoryRepository(temp).findAll().isEmpty());
    }
    @Test void storesOneFilePerEntryNotIndexThatCanBeCorrupted() throws Exception {
        FileHistoryRepository repo = new FileHistoryRepository(temp);
        HistoryEntry entry = entry("x", "2");
        repo.saveIfAbsent(entry);
        assertTrue(Files.isRegularFile(temp.resolve(entry.contentKey() + ".properties")));
    }
    @Test void rejectsNullEntryAndDoesNotDeleteNonexistent() throws Exception {
        FileHistoryRepository repo = new FileHistoryRepository(temp);
        assertThrows(NullPointerException.class, () -> repo.saveIfAbsent(null));
        assertFalse(repo.deleteById("missing"));
    }
    @Test void concurrentWritesStillStoreOneRecord() throws Exception {
        FileHistoryRepository repo = new FileHistoryRepository(temp);
        java.util.concurrent.ExecutorService pool = java.util.concurrent.Executors.newFixedThreadPool(5);
        try {
            List<java.util.concurrent.Future<Boolean>> results = new java.util.ArrayList<>();
            for (int i = 0; i < 16; i++) results.add(pool.submit(() -> repo.saveIfAbsent(entry("x", "1"))));
            int saved = 0;
            for (var result : results) if (result.get()) saved++;
            assertEquals(1, saved);
            assertEquals(1, repo.findAll().size());
        } finally {pool.shutdownNow();}
    }
    @Test void tamperedContentIsRejectedByFingerprint() throws Exception {
        FileHistoryRepository repo = new FileHistoryRepository(temp);
        HistoryEntry valid = entry("original", "4");
        repo.saveIfAbsent(valid);
        Files.move(temp.resolve(valid.contentKey() + ".properties"),
                temp.resolve("b".repeat(64) + ".properties"));
        assertTrue(repo.findAll().isEmpty());
    }
    @Test void storagePathIsFileFailsWithoutDeletingIt() throws Exception {
        Path file = temp.resolve("not-a-directory");
        Files.writeString(file, "leave me alone");
        FileHistoryRepository repo = new FileHistoryRepository(file);
        assertThrows(IOException.class, () -> repo.saveIfAbsent(entry("x", "1")));
        assertEquals("leave me alone", Files.readString(file));
    }
    @Test void graphBinaryRoundTrip() throws Exception {
        Path dir = temp.resolve("graph");
        HistoryInteractor uc = new HistoryInteractor(new FileHistoryRepository(dir));
        byte[] graph = new byte[] {(byte)137,80,78,71,13,10,26,10,1,2,3,4,5,6};
        uc.recordGraph("y=x^2", graph, 640, 480);
        HistoryEntry restored = new HistoryInteractor(new FileHistoryRepository(dir)).list().get(0);
        assertArrayEquals(graph, restored.png());
        assertEquals(640, restored.imageWidth());
        assertEquals(480, restored.imageHeight());
        assertEquals("y=x^2", restored.formula());
    }
    @Test void repeatedRestartSaveStillDeduplicatesGraph() throws Exception {
        Path dir = temp.resolve("graph");byte[] graph = new byte[] {(byte)137,80,78,71,13,10,26,10,1,2,3};
        assertTrue(new HistoryInteractor(new FileHistoryRepository(dir)).recordGraph("x", graph, 5, 6));
        assertFalse(new HistoryInteractor(new FileHistoryRepository(dir)).recordGraph("x", graph, 5, 6));
    }
    @Test void formulaUpgradeSurvivesRestart() throws Exception {
        Path dir = temp.resolve("upgraded");
        new HistoryInteractor(new FileHistoryRepository(dir)).recordFormula("x+1");
        new HistoryInteractor(new FileHistoryRepository(dir)).recordCalculation("x+1", "4");
        var actual = new HistoryInteractor(new FileHistoryRepository(dir)).list();
        assertEquals(1, actual.size());assertEquals("4", actual.get(0).result());
    }
}
