package pdf_editor.view;

import org.junit.jupiter.api.Test;
import pdf_editor.domain.PdfDocument;
import pdf_editor.use_case.DocumentEditor;
import pdf_editor.adapter.NoteTextDocumentFactory;
import javax.swing.text.StyleConstants;
import java.awt.event.MouseEvent;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class WordTextBoxRegressionTest {
    private PdfDocument.Placement note(){return new DocumentEditor().addNote(0,"Hello",20,30);}
    @Test void zoomHalfScalesEditorFontNotPersistedFontSize() {
        var pane = new InlineNoteEditor(note(),0.5,s-> {}, ()-> {});
        assertEquals(8,StyleConstants.getFontSize(pane.getStyledDocument().getCharacterElement(0).getAttributes()));
    }
    @Test void savingZoomedTextRestoresLogicalFontSize() {
        final List<PdfDocument.NoteSpan>[] result=new List[]{null};
        var pane = new InlineNoteEditor(note(),0.5,s->result[0]=s,()->{});
        pane.finish();
        assertEquals(16,result[0].get(0).size());
    }
    @Test void editingAtDoubleZoomStoresOriginalTextSize() {
        final List<PdfDocument.NoteSpan>[] result=new List[]{null};
        var pane = new InlineNoteEditor(note(),2,s->result[0]=s,()->{});
        pane.finish();assertEquals(16,result[0].get(0).size());
    }
    @Test void rendererAndEditorShareMatchingTextMargins() {
        var pane = NoteTextDocumentFactory.createPane(note());
        assertEquals(6,pane.getMargin().top);
        assertEquals(7,pane.getMargin().left);
    }
    @Test void finishCannotDuplicateSavedParagraphs() {
        int[] commits={0};
        var pane = new InlineNoteEditor(note(),1,s->commits[0]++,()->{});
        pane.setText("PPT style");pane.finish();pane.finish();
        assertEquals(1,commits[0]);
    }
    @Test void formattingAtZoomPreservesLogicalSize() {
        final List<PdfDocument.NoteSpan>[] result=new List[]{null};
        var pane = new InlineNoteEditor(note(),2,s->result[0]=s,()->{});
        pane.select(0,pane.getText().length());pane.fontSize(24);pane.finish();
        assertEquals(24,result[0].get(0).size());
    }
}
