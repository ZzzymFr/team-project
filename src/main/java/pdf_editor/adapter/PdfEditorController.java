package pdf_editor.adapter;

import pdf_editor.MathLibrary;
import pdf_editor.domain.PdfDocument;
import pdf_editor.domain.PdfMaterial;
import pdf_editor.use_case.AutoLayoutInteractor;
import pdf_editor.use_case.DocumentEditor;
import pdf_editor.use_case.PdfExportInteractor;

import pdf_editor.domain.PdfPreviewPage;
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
    public void selectAt(float x, float y) {
        selectedId = null;
        List<PdfDocument.Placement> items = placements();
        for (int i=items.size()-1; i>=0; i--) {
            PdfDocument.Placement p = items.get(i);
            if (x >= p.x() && x <= p.x() + p.width() && y >= p.y() && y <= p.y() + p.height()) {
                selectedId = p.id(); break;
            }
        }
    }
    public void switchPage(int target) {
        if (target < 0 || target >= pageCount()) return;
        page = target; selectedId = null;
    }
    public void addPage() { switchPage(editor.addPage()); }
    public boolean deletePage() {
        if (pageCount() <= 1) return false;
        editor.removePage(page);
        switchPage(Math.min(page, pageCount()-1));
        return true;
    }
    public PdfDocument.Placement place(PdfMaterial material, float x, float y, float width, float height) {
        PdfDocument.Placement placed = editor.place(page, material, x, y, width, height);
        select(placed.id()); return placed;
    }
    public PdfDocument.Placement addNote() { return addNoteAt(75, 95); }
    public PdfDocument.Placement addNoteAt(float x, float y) {
        PdfDocument.Placement placed = editor.addNote(page, "", x, y);
        select(placed.id()); return placed;
    }
    public void updateRichNote(List<PdfDocument.NoteSpan> spans) {
        if (selected() != null && selected().isNote()) editor.updateRichNote(page, selectedId, spans);
    }
    /** Formatting is an application command, never state directly mutated by the view. */
    public boolean toggleSelectedNoteStyle(pdf_editor.use_case.NoteFormatting.Style style) {
        PdfDocument.Placement note = selected();
        if (note == null || !note.isNote()) return false;
        editor.updateRichNote(page, selectedId, pdf_editor.use_case.NoteFormatting.toggle(
                note.noteSpans(), note.noteText(), note.noteSize(), style));
        return true;
    }
    public boolean resizeSelectedNote(int fontSize) {
        PdfDocument.Placement note = selected();
        if (note == null || !note.isNote()) return false;
        editor.updateRichNote(page, selectedId, pdf_editor.use_case.NoteFormatting.resize(
                note.noteSpans(), note.noteText(), note.noteSize(), fontSize));
        return true;
    }
    public boolean changeSelectedNoteFont(String family) {
        PdfDocument.Placement note = selected();
        if (note == null || !note.isNote()) return false;
        editor.updateRichNote(page, selectedId, pdf_editor.use_case.NoteFormatting.family(
                note.noteSpans(), note.noteText(), note.noteSize(), family));
        return true;
    }
    public boolean deleteSelected() {
        if (selected() == null) return false;
        boolean changed = editor.delete(page, selectedId);
        selectedId = null; return changed;
    }
    public boolean editSelectedFormula(String formula, String result) {
        PdfDocument.Placement item = selected();
        if (item == null || item.isNote() || item.material().isGraph()) return false;
        editor.editFormula(page, selectedId, formula, result);
        return true;
    }
    public void arrange(int direction) { if (selected() != null) editor.arrange(page, selectedId, direction); }
    public boolean splitSelected() {
        if (selected() == null) return false;
        PdfDocument.Placement[] split = editor.splitCalculation(page, selectedId);
        if (split == null) return false;
        selectedId = split[0].id(); return true;
    }
    public void beginTransform() {
        if (selected() != null) editor.beginTransform(page, selectedId);
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
    public boolean undo() {
        if (!editor.undo()) return false;
        switchPage(Math.min(page, pageCount()-1)); return true;
    }
    public boolean redo() {
        if (!editor.redo()) return false;
        switchPage(Math.min(page, pageCount()-1)); return true;
    }
    public List<PdfPreviewPage> preview() throws IOException { return output.preview(editor.snapshot()); }
    public void export(Path destination) throws IOException { output.export(editor.snapshot(), destination); }
    public PdfDocument documentSnapshot() { return editor.snapshot(); }
}
