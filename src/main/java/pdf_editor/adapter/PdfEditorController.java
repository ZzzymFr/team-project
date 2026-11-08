package pdf_editor.adapter;

import pdf_editor.MathLibrary;
import pdf_editor.domain.PdfDocument;
import pdf_editor.domain.PdfMaterial;
import pdf_editor.use_case.AutoLayoutInteractor;
import pdf_editor.use_case.DocumentEditor;
import pdf_editor.use_case.PdfExportInteractor;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.Objects;

/** Interface adapter translating view events into application use-case commands. No Swing dependency. */
public final class PdfEditorController {
    private final MathLibrary library;
    private final DocumentEditor editor;
    private final AutoLayoutInteractor layout;
    private final PdfExportInteractor output;
    private int page;
    private String selectedId;

    public PdfEditorController(MathLibrary library, DocumentEditor editor,
                               AutoLayoutInteractor layout, PdfExportInteractor output) {
        this.library = Objects.requireNonNull(library);
        this.editor = Objects.requireNonNull(editor);
        this.layout = Objects.requireNonNull(layout);
        this.output = Objects.requireNonNull(output);
    }
    public MathLibrary library() { return library; }
    public int currentPage() { return page; }
    public int pageCount() { return editor.pageCount(); }
    public List<PdfDocument.Placement> placements() { return editor.placements(page); }
    public PdfDocument.Placement selected() { return editor.find(page, selectedId); }
    public void select(String id) { selectedId = editor.find(page, id) == null ? null : id; }
    public void clearSelection() { selectedId = null; }
    public void switchPage(int target) {
        if (target < 0 || target >= pageCount()) return;
        page = target; selectedId = null;
    }
    public PdfDocument.Placement place(PdfMaterial material, float x, float y, float width, float height) {
        PdfDocument.Placement placed = editor.place(page, material, x, y, width, height);
        select(placed.id()); return placed;
    }
    public PdfDocument.Placement addNote() {
        PdfDocument.Placement placed = editor.addNote(page, "Double-click to edit note", 75, 95);
        select(placed.id()); return placed;
    }
    public void updateRichNote(List<PdfDocument.NoteSpan> spans) {
        if (selected() != null && selected().isNote()) editor.updateRichNote(page, selectedId, spans);
    }
    public boolean deleteSelected() {
        if (selected() == null) return false;
        boolean changed = editor.delete(page, selectedId);
        selectedId = null; return changed;
    }
    public void arrange(int direction) { if (selected() != null) editor.arrange(page, selectedId, direction); }
    public boolean splitSelected() {
        if (selected() == null) return false;
        PdfDocument.Placement[] split = editor.splitCalculation(page, selectedId);
        if (split == null) return false;
        selectedId = split[0].id(); return true;
    }
    public void transform(float x, float y, float width, float height) {
        if (selected() != null) editor.transform(page, selectedId, x, y, width, height);
    }
    public boolean moveSelectedToPage(int target) {
        if (selected() == null) return false;
        if (target == -1) {
            String id = selectedId;
            final int[] newPage = {-1};
            editor.transaction(() -> {
                newPage[0] = editor.addPage();
                editor.moveToPage(page, newPage[0], id);
            });
            switchPage(newPage[0]); select(id); return true;
        }
        if (!editor.moveToPage(page, target, selectedId)) return false;
        String id = selectedId;
        switchPage(target); select(id); return true;
    }
    public int autoLayout() {
        if (library.items().isEmpty()) return 0;
        int count = library.items().size();
        switchPage(layout.importMaterials(page, library.items()));
        return count;
    }
    /** Exports {@code document} with a picture drawn over each page (e.g. the handwriting on it). */
    public void export(PdfDocument document, List<BufferedImage> pageOverlays, Path destination) throws IOException {
        output.export(document, pageOverlays, destination);
    }
    public PdfDocument documentSnapshot() { return editor.snapshot(); }
    /** Puts the document back as it was (an undo kept elsewhere); nothing stays selected. */
    public void restore(PdfDocument snapshot) {
        editor.restore(snapshot);
        page = Math.min(page, pageCount() - 1);
        selectedId = null;
    }
    /** Adds blank pages at the end until there are at least {@code count}. */
    public void ensurePages(int count) {
        while (pageCount() < count) editor.addPage();
    }
}
