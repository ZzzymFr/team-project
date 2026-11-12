package pdf_editor.view;

import org.junit.jupiter.api.Test;
import pdf_editor.domain.PdfDocument;
import pdf_editor.use_case.DocumentEditor;
import pdf_editor.MathLibrary;
import pdf_editor.adapter.PdfEditorController;
import pdf_editor.use_case.AutoLayoutInteractor;
import pdf_editor.use_case.PdfExportInteractor;
import javax.swing.text.StyleConstants;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class InlineNoteEditorTest {
    private PdfDocument.Placement note(){return new DocumentEditor().addNote(0,"",10,10);}
    @Test void startsWithEmptyTextReadyToType() {
        var pane=new InlineNoteEditor(note(),s->{});assertEquals("",pane.getText());
    }
    @Test void commitsTextWithoutDialog() {
        final List<PdfDocument.NoteSpan>[] saved=new List[]{null};
        var pane=new InlineNoteEditor(note(),spans->saved[0]=spans);
        pane.setText("Hello Word");pane.finish();assertEquals("Hello Word",saved[0].get(0).text());
    }
    @Test void completionOnlyCommitsOnce() {
        int[] count={0};var pane=new InlineNoteEditor(note(),spans->count[0]++);
        pane.finish();pane.finish();assertEquals(1,count[0]);
    }
    @Test void formattingDoesNotDiscardText() {
        var pane=new InlineNoteEditor(note(),spans->{});
        pane.setText("hello");pane.select(0,5);pane.toggleStyle(StyleConstants.Bold);
        assertTrue(StyleConstants.isBold(pane.getStyledDocument().getCharacterElement(1).getAttributes()));
        assertEquals("hello",pane.getText());
    }
    @Test void italicAndUnderlineAreIndependent() {
        var pane=new InlineNoteEditor(note(),spans->{});pane.setText("x");pane.select(0,1);
        pane.toggleStyle(StyleConstants.Italic);pane.toggleStyle(StyleConstants.Underline);
        var attrs=pane.getStyledDocument().getCharacterElement(0).getAttributes();
        assertTrue(StyleConstants.isItalic(attrs));assertTrue(StyleConstants.isUnderline(attrs));assertFalse(StyleConstants.isBold(attrs));
    }
    @Test void fontSizeAppliesToSelection() {
        var pane=new InlineNoteEditor(note(),spans->{});pane.setText("x");pane.select(0,1);pane.fontSize(24);
        assertEquals(24,StyleConstants.getFontSize(pane.getStyledDocument().getCharacterElement(0).getAttributes()));
    }
    @Test void invalidFontSizeIsRejected() {
        var pane=new InlineNoteEditor(note(),spans->{});
        assertThrows(IllegalArgumentException.class,()->pane.fontSize(7));
        assertThrows(IllegalArgumentException.class,()->pane.fontSize(73));
    }
    @Test void pageCanvasCreatesInlineEditorForSelectedNote() {
        DocumentEditor editor=new DocumentEditor();
        var controller=new PdfEditorController(new MathLibrary(),editor,new AutoLayoutInteractor(editor),
                new PdfExportInteractor((d,f)->{},(d,dpi)->List.of()));
        var page=new PdfPageCanvas(controller,new PdfPageCanvas.Listener(){
            public void contextMenu(java.awt.event.MouseEvent e){}public void editNote(){}public void editFormula(){}
        });
        controller.addNote();assertTrue(page.editNoteInline());assertTrue(page.isEditingNote());
        for (java.awt.Component child:page.getComponents()) if(child instanceof InlineNoteEditor inline){
            inline.setText("Direct typing");inline.finish();
        }
        assertFalse(page.isEditingNote());assertEquals("Direct typing",controller.selected().noteText());
        assertTrue(controller.undo());assertEquals("",controller.placements().get(0).noteText());
    }
}
