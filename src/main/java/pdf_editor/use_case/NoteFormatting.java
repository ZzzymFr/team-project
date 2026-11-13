package pdf_editor.use_case;

import pdf_editor.domain.PdfDocument;
import java.util.List;
import java.util.Objects;

/** Pure formatting policy. The Word-style UI chooses a command; it does not mutate domain spans. */
public final class NoteFormatting {
    public enum Style { BOLD, ITALIC, UNDERLINE }
    private NoteFormatting() { }

    public static List<PdfDocument.NoteSpan> toggle(List<PdfDocument.NoteSpan> spans,
                                                       String fallbackText, int size, Style style) {
        Objects.requireNonNull(spans);
        Objects.requireNonNull(style);
        List<PdfDocument.NoteSpan> source = initial(spans, fallbackText, size);
        boolean active = switch (style) {
            case BOLD -> source.stream().allMatch(PdfDocument.NoteSpan::bold);
            case ITALIC -> source.stream().allMatch(PdfDocument.NoteSpan::italic);
            case UNDERLINE -> source.stream().allMatch(PdfDocument.NoteSpan::underline);
        };
        return source.stream().map(s -> switch (style) {
            case BOLD -> new PdfDocument.NoteSpan(s.text(), !active, s.italic(), s.underline(), s.size(), s.fontFamily());
            case ITALIC -> new PdfDocument.NoteSpan(s.text(), s.bold(), !active, s.underline(), s.size(), s.fontFamily());
            case UNDERLINE -> new PdfDocument.NoteSpan(s.text(), s.bold(), s.italic(), !active, s.size(), s.fontFamily());
        }).toList();
    }
    public static List<PdfDocument.NoteSpan> resize(List<PdfDocument.NoteSpan> spans,
                                                       String fallbackText, int originalSize, int size) {
        if (size < 8 || size > 72) throw new IllegalArgumentException("Font size must be between 8 and 72");
        return initial(spans, fallbackText, originalSize).stream()
                .map(s -> new PdfDocument.NoteSpan(s.text(), s.bold(), s.italic(), s.underline(), size, s.fontFamily()))
                .toList();
    }
    public static List<PdfDocument.NoteSpan> family(List<PdfDocument.NoteSpan> spans,
                                                      String fallbackText, int size, String font) {
        if (font == null || font.isBlank() || font.length() > 100)
            throw new IllegalArgumentException("Invalid font family");
        return initial(spans, fallbackText, size).stream()
                .map(s -> new PdfDocument.NoteSpan(s.text(), s.bold(), s.italic(),
                        s.underline(), s.size(), font)).toList();
    }
    private static List<PdfDocument.NoteSpan> initial(List<PdfDocument.NoteSpan> spans,
                                                       String text, int size) {
        return spans.isEmpty() ? List.of(new PdfDocument.NoteSpan(
                Objects.requireNonNull(text, "note text"), false, false, false, size)) : List.copyOf(spans);
    }
}
