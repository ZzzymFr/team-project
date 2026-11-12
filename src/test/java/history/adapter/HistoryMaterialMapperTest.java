package history.adapter;

import history.domain.HistoryEntry;
import org.junit.jupiter.api.Test;
import java.time.Instant;
import static org.junit.jupiter.api.Assertions.*;

class HistoryMaterialMapperTest {
    @Test void formulaAndResultCopiedWithoutSharingMaterialIdentity() {
        HistoryEntry saved = new HistoryEntry("original", HistoryEntry.Kind.CALCULATION,
                "Calculation", "\\sqrt{9}", "3", null, 0, 0, Instant.EPOCH);
        var a = HistoryMaterialMapper.toPdfMaterial(saved);
        var b = HistoryMaterialMapper.toPdfMaterial(saved);
        assertNotEquals(a.id(), b.id());
        assertEquals("\\sqrt{9}", a.formula());
        assertEquals("3", a.result());
        var edited = a.withText("4+4", "8");
        assertEquals("\\sqrt{9}", saved.formula());
        assertEquals("\\sqrt{9}", b.formula());
        assertEquals("4+4", edited.formula());
    }
    @Test void graphMaterialIsNotEditableAsFormula() {
        HistoryEntry saved = new HistoryEntry("graph", HistoryEntry.Kind.GRAPH, "Graph",
                "x^2", "", new byte[]{(byte)137,80,78,71,13,10,26,10,1,2,3}, 600, 300, Instant.EPOCH);
        var material = HistoryMaterialMapper.toPdfMaterial(saved);
        assertTrue(material.isGraph());
        assertThrows(IllegalStateException.class, () -> material.withText("x", "2"));
    }
}
