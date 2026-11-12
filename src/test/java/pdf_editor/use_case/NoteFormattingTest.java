package pdf_editor.use_case;

import org.junit.jupiter.api.Test;
import pdf_editor.MathLibrary;
import pdf_editor.adapter.PdfEditorController;
import pdf_editor.domain.PdfDocument;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class NoteFormattingTest {
    @Test void togglingBoldTwiceRestoresOriginalNote() {
        var first = NoteFormatting.toggle(List.of(), "hello", 16, NoteFormatting.Style.BOLD);
        assertTrue(first.get(0).bold());
        var second = NoteFormatting.toggle(first, "hello", 16, NoteFormatting.Style.BOLD);
        assertFalse(second.get(0).bold());
        assertEquals("hello", second.get(0).text());
    }
    @Test void italicAndUnderlineDoNotModifyBold() {
        var bold = NoteFormatting.toggle(List.of(), "x", 16, NoteFormatting.Style.BOLD);
        var italic = NoteFormatting.toggle(bold, "x", 16, NoteFormatting.Style.ITALIC);
        var underline = NoteFormatting.toggle(italic, "x", 16, NoteFormatting.Style.UNDERLINE);
        assertTrue(underline.get(0).bold());
        assertTrue(underline.get(0).italic());
        assertTrue(underline.get(0).underline());
    }
    @Test void fontSizeRetainsIndependentStylesAndText() {
        var spans = List.of(new PdfDocument.NoteSpan("a", true, false, true, 14),
                new PdfDocument.NoteSpan("b", false, true, false, 18));
        var changed = NoteFormatting.resize(spans, "unused", 14, 30);
        assertTrue(changed.get(0).bold());
        assertTrue(changed.get(1).italic());
        assertEquals("ab", changed.get(0).text() + changed.get(1).text());
        assertEquals(30, changed.get(0).size());
        assertEquals(30, changed.get(1).size());
        assertEquals(14, spans.get(0).size());
    }
    @Test void rejectsInvalidFontsAndNullStyle() {
        assertThrows(IllegalArgumentException.class, () -> NoteFormatting.resize(List.of(), "x", 16, 7));
        assertThrows(IllegalArgumentException.class, () -> NoteFormatting.resize(List.of(), "x", 16, 73));
        assertThrows(NullPointerException.class, () -> NoteFormatting.toggle(List.of(), "x", 16, null));
    }
    @Test void controllerFormattingSupportsUndo() {
        DocumentEditor editor = new DocumentEditor();
        PdfEditorController controller = new PdfEditorController(new MathLibrary(), editor,
                new AutoLayoutInteractor(editor),
                new PdfExportInteractor((document, destination) -> {}, (document, dpi) -> List.of()));
        controller.addNote();
        assertTrue(controller.toggleSelectedNoteStyle(NoteFormatting.Style.BOLD));
        assertTrue(controller.selected().noteSpans().get(0).bold());
        assertTrue(controller.undo());
        assertTrue(controller.placements().get(0).noteSpans().isEmpty());
        assertFalse(controller.resizeSelectedNote(20)); // selected ID cleared on undo
        controller.select(controller.placements().get(0).id());
        assertTrue(controller.resizeSelectedNote(24));
        assertEquals(24, controller.selected().noteSpans().get(0).size());
    }
}
