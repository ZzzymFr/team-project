package history.adapter;

import history.domain.HistoryEntry;
import pdf_editor.domain.PdfMaterial;
import java.util.Objects;

/** Converts stored content to a *new* editable PDF material; history remains immutable. */
public final class HistoryMaterialMapper {
    private HistoryMaterialMapper() { }
    public static PdfMaterial toPdfMaterial(HistoryEntry history) {
        Objects.requireNonNull(history);
        return new RestoredMaterial(history);
    }
    private static final class RestoredMaterial extends PdfMaterial {
        private RestoredMaterial(HistoryEntry entry) {
            super(entry.kind() == HistoryEntry.Kind.GRAPH ? "Saved Graph" : "Saved " + entry.title(),
                    entry.formula(), entry.result(), entry.png(), entry.imageWidth(), entry.imageHeight());
        }
    }
}
