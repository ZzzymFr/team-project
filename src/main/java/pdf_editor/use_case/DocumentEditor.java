package pdf_editor.use_case;

import pdf_editor.domain.PdfDocument;
import pdf_editor.domain.PdfMaterial;
import java.util.List;
import java.util.Objects;

/**
 * Application interactor: edits documents and enforces placement constraints. No UI references. Undo
 * lives with the canvas, which keeps {@link #snapshot()}s and puts them back with {@link #restore}.
 */
public final class DocumentEditor {
    private PdfDocument document = new PdfDocument();
    private boolean inBatch;

    public PdfDocument snapshot() { return document.copy(); }
    /** Puts the document back as it was in {@code snapshot}, for an undo kept outside this editor. */
    public void restore(PdfDocument snapshot) { document = Objects.requireNonNull(snapshot, "snapshot").copy(); }
    public int pageCount() { return document.pageCount(); }
    public List<PdfDocument.Placement> placements(int page) { return document.placements(page); }
    public PdfDocument.Placement find(int page, String id) { return document.find(page, id); }
    /** Atomic multi-placement edit: Auto Layout either happens whole or not at all. */
    public void transaction(Runnable action) {
        Objects.requireNonNull(action, "action");
        if (inBatch) throw new IllegalStateException("Nested transactions are not allowed");
        PdfDocument before = document.copy();
        inBatch = true;
        try {
            action.run();
        } catch (RuntimeException | Error ex) {
            document = before;
            throw ex;
        } finally { inBatch = false; }
    }
    public int addPage() { return document.addPage(); }
    public PdfDocument.Placement place(int page, PdfMaterial material, float x, float y, float width, float height) {
        Objects.requireNonNull(material, "material");
        validatePage(page);
        float w = validSize(width, 45f, PdfDocument.PAGE_WIDTH);
        float h = validSize(height, 35f, PdfDocument.PAGE_HEIGHT);
        PdfDocument.Placement p = PdfDocument.Placement.material(material,
                clamp(x, 0, PdfDocument.PAGE_WIDTH - w), clamp(y, 0, PdfDocument.PAGE_HEIGHT - h), w, h);
        document.add(page, p);
        return p;
    }
    public PdfDocument.Placement addNote(int page, String text, float x, float y) {
        validatePage(page);
        PdfDocument.Placement p = PdfDocument.Placement.note(text,
                clamp(x, 0, PdfDocument.PAGE_WIDTH - 260), clamp(y, 0, PdfDocument.PAGE_HEIGHT - 95), 260, 95);
        document.add(page, p);
        return p;
    }
    public PdfDocument.Placement updateNote(int page, String id, String text, int style, int size) {
        PdfDocument.Placement p = findRequired(page, id);
        if (!p.isNote()) throw new IllegalArgumentException("Not a note");
        PdfDocument.Placement updated = p.withNote(text, style, size);
        document.replace(page, updated);
        return updated;
    }
    public PdfDocument.Placement updateRichNote(int page, String id, List<PdfDocument.NoteSpan> spans) {
        PdfDocument.Placement p = findRequired(page, id);
        if (!p.isNote()) throw new IllegalArgumentException("Not a note");
        PdfDocument.Placement updated = p.withSpans(spans);
        document.replace(page, updated);
        return updated;
    }
    public void arrange(int page, String id, int direction) {
        validatePage(page);
        int index = document.indexOf(page, id);
        if (index < 0) return;
        int count = placements(page).size();
        int target = direction == 2 ? count - 1 : direction == -2 ? 0 : index + direction;
        target = Math.max(0, Math.min(count - 1, target));
        if (target == index) return;
        document.reorder(page, id, target);
    }
    public PdfDocument.Placement[] splitCalculation(int page, String id) {
        PdfDocument.Placement original = find(page, id);
        if (original == null || original.isNote() || original.material().isGraph()
                || !original.material().hasResult()) return null;
        int index = document.indexOf(page, id);
        float leftWidth = Math.max(45, original.width() * .52f);
        float rightWidth = Math.max(45, original.width() - leftWidth);
        // Leave the sole equals sign on the result half. Formula has none.
        PdfDocument.Placement formula = PdfDocument.Placement.material(
                PdfMaterial.formulaPart(original.material()), original.x(), original.y(),
                leftWidth, original.height());
        PdfDocument.Placement result = PdfDocument.Placement.material(
                PdfMaterial.resultPart(original.material()), original.x() + leftWidth,
                original.y(), rightWidth, original.height());
        document.remove(page, id);
        document.insert(page, index, formula);
        document.insert(page, index + 1, result);
        return new PdfDocument.Placement[] { formula, result };
    }
    public boolean moveToPage(int from, int to, String id) {
        validatePage(from); validatePage(to);
        PdfDocument.Placement p = document.find(from, id);
        if (from == to || p == null) return false;
        document.remove(from, id);
        document.add(to, p.withBounds(
                clamp(p.x(), 0, PdfDocument.PAGE_WIDTH - p.width()),
                clamp(p.y(), 0, PdfDocument.PAGE_HEIGHT - p.height()), p.width(), p.height()));
        return true;
    }
    public boolean delete(int page, String id) {
        if (document.find(page, id) == null) return false;
        return document.remove(page, id);
    }
    public PdfDocument.Placement transform(int page, String id, float x, float y, float width, float height) {
        PdfDocument.Placement p = findRequired(page, id);
        float w = validSize(width, 45, PdfDocument.PAGE_WIDTH);
        float h = validSize(height, 35, PdfDocument.PAGE_HEIGHT);
        PdfDocument.Placement updated = p.withBounds(
                clamp(x, 0, PdfDocument.PAGE_WIDTH - w),
                clamp(y, 0, PdfDocument.PAGE_HEIGHT - h), w, h);
        document.replace(page, updated);
        return updated;
    }
    private PdfDocument.Placement findRequired(int page, String id) {
        PdfDocument.Placement p = document.find(page, id);
        if (p == null) throw new IllegalArgumentException("Unknown placement: " + id);
        return p;
    }
    private void validatePage(int page) { document.placements(page); }
    private static float validSize(float v, float min, float max) {
        if (!Float.isFinite(v)) throw new IllegalArgumentException("Size must be finite");
        return clamp(v, min, max);
    }
    private static float clamp(float v, float low, float high) {
        if (!Float.isFinite(v)) throw new IllegalArgumentException("Coordinate must be finite");
        return Math.max(low, Math.min(high, v));
    }
}
