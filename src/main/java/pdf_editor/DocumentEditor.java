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
