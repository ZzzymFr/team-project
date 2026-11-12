package pdf_editor.adapter;

import org.junit.jupiter.api.Test;
import pdf_editor.MathLibrary;
import view.DrawingCanvas;
import java.awt.event.MouseEvent;
import static org.junit.jupiter.api.Assertions.*;

class SelectedInkRegistryTest {
    private static void ink(DrawingCanvas canvas){
        for(var l:canvas.getMouseListeners())l.mousePressed(new MouseEvent(canvas,MouseEvent.MOUSE_PRESSED,1,0,50,50,1,false,1));
        for(var l:canvas.getMouseMotionListeners())l.mouseDragged(new MouseEvent(canvas,MouseEvent.MOUSE_DRAGGED,2,0,65,70,1,false,0));
        for(var l:canvas.getMouseListeners())l.mouseReleased(new MouseEvent(canvas,MouseEvent.MOUSE_RELEASED,3,0,65,70,1,false,1));
    }
    @Test void eraseOrClearRemovesTrackedMaterial() {
        DrawingCanvas c=new DrawingCanvas();c.setSize(500,400);ink(c);
        MathLibrary library=new MathLibrary();SelectedInkRegistry tracker=new SelectedInkRegistry(c,library);
        var item=library.addFormula("x");tracker.track(item.id(),c.getStrokes());c.clear();assertNull(library.find(item.id()));
    }
    @Test void trackedMaterialsSurviveUnrelatedInkChanges() {
        DrawingCanvas c=new DrawingCanvas();c.setSize(500,400);ink(c);
        MathLibrary library=new MathLibrary();SelectedInkRegistry tracker=new SelectedInkRegistry(c,library);
        var item=library.addFormula("x");tracker.track(item.id(),c.getStrokes());ink(c);assertNotNull(library.find(item.id()));
    }
    @Test void invalidationDoesNotOfferUndoOfStaleInk() {
        DrawingCanvas c=new DrawingCanvas();c.setSize(500,400);ink(c);
        MathLibrary library=new MathLibrary();SelectedInkRegistry tracker=new SelectedInkRegistry(c,library);
        var item=library.addFormula("x");tracker.track(item.id(),c.getStrokes());c.clear();assertFalse(library.undoDelete());
    }
    @Test void untrackedItemsAreNotPruned() {
        DrawingCanvas c=new DrawingCanvas();c.setSize(500,400);ink(c);
        MathLibrary library=new MathLibrary();new SelectedInkRegistry(c,library);
        var item=library.addFormula("manual");c.clear();assertNotNull(library.find(item.id()));
    }
}
