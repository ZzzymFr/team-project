package pdf_editor.view;

import org.junit.jupiter.api.Test;
import pdf_editor.MathLibrary;
import pdf_editor.adapter.PdfEditorController;
import pdf_editor.use_case.AutoLayoutInteractor;
import pdf_editor.use_case.DocumentEditor;
import pdf_editor.use_case.PdfExportInteractor;
import javax.swing.SwingUtilities;
import java.awt.event.MouseEvent;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

/** Single-click commit and auto-expansion run on the Swing event thread. */
class PptTextExitTest {
    private static PdfEditorController controller(){
        DocumentEditor editor=new DocumentEditor();
        return new PdfEditorController(new MathLibrary(),editor,new AutoLayoutInteractor(editor),
                new PdfExportInteractor((d,p)->{},(d,dpi)->List.of()));
    }
    private static PdfPageCanvas page(PdfEditorController controller){
        return new PdfPageCanvas(controller,new PdfPageCanvas.Listener(){
            @Override public void contextMenu(MouseEvent e){}
            @Override public void editNote(){}
            @Override public void editFormula(){}
        });
    }
    private static void onEdt(Runnable check) throws Exception { SwingUtilities.invokeAndWait(check); }
    @Test void singleClickBlankPaperExitsTypingModeAndSaves() throws Exception {
        onEdt(() -> {
            PdfEditorController controller=controller();PdfPageCanvas paper=page(controller);
            controller.addNote();paper.editNoteInline();
            for(var child:paper.getComponents())if(child instanceof InlineNoteEditor editor)editor.setText("Hello PPT");
            MouseEvent click=new MouseEvent(paper,MouseEvent.MOUSE_PRESSED,1,0,500,580,1,false,MouseEvent.BUTTON1);
            for(var listener:paper.getMouseListeners())listener.mousePressed(click);
            assertFalse(paper.isEditingNote());
            assertEquals("Hello PPT",controller.placements().get(0).noteText());
        });
    }
    @Test void multiLineTypingExpandsThePersistentTextBoxBeforeClickAway() throws Exception {
        onEdt(() -> {
            PdfEditorController controller=controller();PdfPageCanvas paper=page(controller);
            controller.addNote();paper.editNoteInline();
            for(var child:paper.getComponents())if(child instanceof InlineNoteEditor editor)
                editor.setText("A\nB\nC\nD\nE\nF\nG\nH\nI\nJ\nK\nL\nM\nN\nO");
            paper.finishInlineEdit();
            assertTrue(controller.placements().get(0).height()>95);
        });
    }
    @Test void zoomChangeCommitsBeforeChangingTextScale() throws Exception {
        onEdt(() -> {
            PdfEditorController controller=controller();PdfPageCanvas paper=page(controller);
            controller.addNote();paper.editNoteInline();
            for(var child:paper.getComponents())if(child instanceof InlineNoteEditor editor)editor.setText("Zoom stable");
            paper.setZoom(1.75);
            assertFalse(paper.isEditingNote());
            assertEquals("Zoom stable",controller.placements().get(0).noteText());
        });
    }
}
