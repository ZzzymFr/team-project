package pdf_editor;

import java.util.ArrayList;
import java.util.List;

public final class PdfDocument {
    public static final float PAGE_WIDTH = 595f;
    public static final float PAGE_HEIGHT = 842f;
    public static final class Placement {
        public MathLibrary.Material material;
        public float x, y, width, height;
        public String noteText;
        public java.util.List<NoteSpan> noteSpans = new java.util.ArrayList<>();
        public int noteStyle = 0;
        public int noteSize = 16;
        public boolean isNote() { return noteText != null; }
        public Placement(MathLibrary.Material material, float x, float y, float width, float height) {
            this.material = material;
            this.x = x; this.y = y; this.width = width; this.height = height;
        }
        public Placement copy() {
            Placement copy = new Placement(material, x, y, width, height);
            copy.noteText = noteText;
            for (NoteSpan span : noteSpans) copy.noteSpans.add(span.copy());
            copy.noteStyle = noteStyle; copy.noteSize = noteSize;
            return copy;
        }
    }
    public static final class NoteSpan {
        public final String text;
        public final boolean bold, italic, underline;
        public final int size;
        public NoteSpan(String text, boolean bold, boolean italic, boolean underline, int size) {
            this.text = text; this.bold = bold; this.italic = italic;
            this.underline = underline; this.size = size;
        }
        public NoteSpan copy() { return new NoteSpan(text, bold, italic, underline, size); }
    }
    public final List<List<Placement>> pages = new ArrayList<>();
    public PdfDocument() { pages.add(new ArrayList<>()); }
    public PdfDocument copy() {
        PdfDocument out = new PdfDocument();
        out.pages.clear();
        for (List<Placement> page : pages) {
            List<Placement> next = new ArrayList<>();
            for (Placement p : page) next.add(p.copy());
            out.pages.add(next);
        }
        return out;
    }
}
