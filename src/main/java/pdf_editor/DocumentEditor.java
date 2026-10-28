package pdf_editor;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;

public final class DocumentEditor {
    private PdfDocument document = new PdfDocument();
    private final Deque<PdfDocument> undo = new ArrayDeque<>();
    private final Deque<PdfDocument> redo = new ArrayDeque<>();

    public PdfDocument snapshot() { return document.copy(); }
    public int pageCount() { return document.pages.size(); }
    public java.util.List<PdfDocument.Placement> placements(int page) {
        checkPage(page);
        return java.util.Collections.unmodifiableList(document.pages.get(page));
    }
    public void checkpoint() {
        undo.push(document.copy());
        if (undo.size() > 60) undo.removeLast();
        redo.clear();
    }
    public int addPage() { checkpoint(); document.pages.add(new ArrayList<>()); return pageCount() - 1; }
    public void removePage(int page) {
        checkPage(page);
        if (pageCount() == 1) throw new IllegalStateException("At least one page is required");
        checkpoint(); document.pages.remove(page);
    }
    public PdfDocument.Placement place(int page, MathLibrary.Material material, float x, float y, float w, float h) {
        checkPage(page);
        if (material == null) throw new IllegalArgumentException("Material is required");
        checkpoint();
        PdfDocument.Placement placement = new PdfDocument.Placement(material,
            clamp(x, 0, PdfDocument.PAGE_WIDTH - w), clamp(y, 0, PdfDocument.PAGE_HEIGHT - h), w, h);
        document.pages.get(page).add(placement);
        return placement;
    }
    public PdfDocument.Placement addNote(int page, String text, float x, float y) {
        checkPage(page);
        checkpoint();
        PdfDocument.Placement note = new PdfDocument.Placement(null, x, y, 260, 95);
        note.noteText = text; document.pages.get(page).add(note);
        return note;
    }
    public void updateNote(int page, PdfDocument.Placement note, String text, int style, int size) {
        checkPage(page);
        if (!document.pages.get(page).contains(note) || !note.isNote()) return;
        checkpoint(); note.noteText = text; note.noteStyle = style; note.noteSize = size; note.noteSpans.clear();
    }
    public void updateRichNote(int page, PdfDocument.Placement note,
                               java.util.List<PdfDocument.NoteSpan> spans) {
        checkPage(page);
        if (!document.pages.get(page).contains(note) || !note.isNote()) return;
        checkpoint();
        note.noteSpans = new java.util.ArrayList<>();
        StringBuilder text = new StringBuilder();
        for (PdfDocument.NoteSpan span : spans) {
            note.noteSpans.add(span.copy()); text.append(span.text);
        }
        note.noteText = text.toString();
    }
    public void arrange(int page, PdfDocument.Placement item, int direction) {
        checkPage(page);
        java.util.List<PdfDocument.Placement> items = document.pages.get(page);
        int index = items.indexOf(item);
        if (index < 0) return;
        int destination = direction == 2 ? items.size() - 1 : direction == -2 ? 0 : index + direction;
        destination = Math.max(0, Math.min(items.size() - 1, destination));
        if (destination == index) return;
        checkpoint(); items.remove(index); items.add(destination, item);
    }
    public PdfDocument.Placement[] splitCalculation(int page, PdfDocument.Placement original) {
        checkPage(page);
        if (original == null || original.isNote() || original.material.isGraph()
                || original.material.result == null || original.material.result.isBlank()) return null;
        java.util.List<PdfDocument.Placement> items = document.pages.get(page);
        int index = items.indexOf(original);
        if (index < 0) return null;
        checkpoint();
        float half = Math.max(35, original.height / 2);
        PdfDocument.Placement formula = new PdfDocument.Placement(
                MathLibrary.detachedFormula(original.material), original.x, original.y, original.width, half);
        PdfDocument.Placement result = new PdfDocument.Placement(
                MathLibrary.detachedResult(original.material), original.x,
                Math.min(PdfDocument.PAGE_HEIGHT - half, original.y + half + 12), original.width, half);
        items.remove(index); items.add(index, formula); items.add(index + 1, result);
        return new PdfDocument.Placement[] { formula, result };
    }
    public void delete(int page, PdfDocument.Placement placement) {
        checkPage(page);
        if (!document.pages.get(page).contains(placement)) return;
        checkpoint(); document.pages.get(page).remove(placement);
    }

    public void beginTransform(int page, PdfDocument.Placement placement) {
        checkPage(page);
        if (!document.pages.get(page).contains(placement)) throw new IllegalArgumentException("Unknown placement");
        checkpoint();
    }
    public void transform(int page, PdfDocument.Placement p, float x, float y, float w, float h) {
        checkPage(page);
        if (!document.pages.get(page).contains(p)) throw new IllegalArgumentException("Unknown placement");
        p.width = clamp(w, 45, PdfDocument.PAGE_WIDTH);
        p.height = clamp(h, 35, PdfDocument.PAGE_HEIGHT);
        p.x = clamp(x, 0, PdfDocument.PAGE_WIDTH - p.width);
        p.y = clamp(y, 0, PdfDocument.PAGE_HEIGHT - p.height);
    }
    public boolean undo() {
        if (undo.isEmpty()) return false;
        redo.push(document.copy()); document = undo.pop(); return true;
    }
    public boolean redo() {
        if (redo.isEmpty()) return false;
        undo.push(document.copy()); document = redo.pop(); return true;
    }
    private void checkPage(int page) {
        if (page < 0 || page >= pageCount()) throw new IndexOutOfBoundsException("Invalid page: " + page);
    }
    private static float clamp(float v, float min, float max) { return Math.max(min, Math.min(max, v)); }
}
