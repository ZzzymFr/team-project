package pdf_editor.use_case;

import pdf_editor.domain.PdfDocument;
import pdf_editor.domain.PdfMaterial;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/** Application use case: imports library materials with A4 pagination and one undo boundary. */
public final class AutoLayoutInteractor {
    private final DocumentEditor editor;
    public AutoLayoutInteractor(DocumentEditor editor) { this.editor = Objects.requireNonNull(editor); }

    public int importMaterials(int startPage, List<? extends PdfMaterial> materials) {
        Objects.requireNonNull(materials, "materials");
        if (materials.isEmpty()) return startPage;
        final int[] finalPage = {startPage};
        editor.transaction(() -> {
            int page = startPage;
            float y = 36;
            for (PdfDocument.Placement placement : editor.placements(page))
                y = Math.max(y, placement.y() + placement.height() + 14);
            for (List<PdfMaterial> group : relatedGroups(materials)) {
                float groupHeight = 0;
                for (PdfMaterial material : group) groupHeight += height(material) + 18;
                // A formula and graph originating from one graph action remain together if they fit on A4.
                if (y > 36 && y + groupHeight - 18 > PdfDocument.PAGE_HEIGHT - 36) {
                    page = editor.addPage(); y = 36;
                }
                for (PdfMaterial material : group) {
                    float height = height(material);
                    if (y + height > PdfDocument.PAGE_HEIGHT - 36 && y > 36) {
                        page = editor.addPage(); y = 36;
                    }
                    editor.place(page, material, 45, y, material.isGraph() ? 495 : 340, height);
                    y += height + 18;
                }
            }
            finalPage[0] = page;
        });
        return finalPage[0];
    }
    private static float height(PdfMaterial material) {
        if (!material.isGraph()) return 85;
        return Math.min(380, Math.max(100, 495f * material.imageHeight() / material.imageWidth()));
    }
    private static List<List<PdfMaterial>> relatedGroups(List<? extends PdfMaterial> materials) {
        List<List<PdfMaterial>> groups = new ArrayList<>();
        for (int i = 0; i < materials.size(); i++) {
            PdfMaterial current = Objects.requireNonNull(materials.get(i), "material");
            // MathLibrary appends a separate formula immediately after graph creation.
            if (current.isGraph() && i + 1 < materials.size()) {
                PdfMaterial following = Objects.requireNonNull(materials.get(i + 1), "material");
                if (!following.isGraph() && !current.formula().isBlank()
                        && current.formula().equals(following.formula()) && !following.hasResult()) {
                    groups.add(List.of(following, current));
                    i++;
                    continue;
                }
            }
            groups.add(List.of(current));
        }
        return groups;
    }
}
