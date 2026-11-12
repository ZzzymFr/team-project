package pdf_editor.adapter;

import org.junit.jupiter.api.Test;
import pdf_editor.MathLibrary;
import view.DrawingCanvas;
import java.awt.event.MouseEvent;
import static org.junit.jupiter.api.Assertions.*;

class SelectedInkRestoreTest {
    private static void ink(DrawingCanvas canvas) {
        for(var m: canvas.getMouseListeners()) m.mousePressed(new MouseEvent(canvas,MouseEvent.MOUSE_PRESSED,1,0,40,40,1,false,MouseEvent.BUTTON1));
        for(var m: canvas.getMouseMotionListeners()) m.mouseDragged(new MouseEvent(canvas,MouseEvent.MOUSE_DRAGGED,2,0,72,68,1,false,MouseEvent.BUTTON1));
        for(var m: canvas.getMouseListeners()) m.mouseReleased(new MouseEvent(canvas,MouseEvent.MOUSE_RELEASED,3,0,72,68,1,false,MouseEvent.BUTTON1));
    }
    @Test void clearHidesSelectedImageAndUndoRestoresSameMaterial() {
        DrawingCanvas canvas = new DrawingCanvas();canvas.setSize(400,400);ink(canvas);
        MathLibrary library = new MathLibrary();SelectedInkRegistry registry = new SelectedInkRegistry(canvas,library);
        var image = library.addFormula("x");registry.track(image.id(),canvas.getStrokes());
        canvas.clear();assertNull(library.find(image.id()));
        canvas.undo();assertSame(image,library.find(image.id()));
    }
    @Test void repeatedUndoAndPruningDoNotCreateDuplicates() {
        DrawingCanvas canvas = new DrawingCanvas();canvas.setSize(400,400);ink(canvas);
        MathLibrary library = new MathLibrary();SelectedInkRegistry registry = new SelectedInkRegistry(canvas,library);
        var item = library.addFormula("x");registry.track(item.id(),canvas.getStrokes());
        canvas.clear();registry.synchronize();canvas.undo();registry.synchronize();
        assertEquals(1,library.items().size());
    }
}
