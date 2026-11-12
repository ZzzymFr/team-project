package view;

import org.junit.jupiter.api.Test;
import java.awt.event.MouseEvent;
import static org.junit.jupiter.api.Assertions.*;

class DrawingCanvasRectangleSelectionTest {
    private static void fire(DrawingCanvas canvas,int id,int x,int y){
        int button=id==MouseEvent.MOUSE_DRAGGED?MouseEvent.NOBUTTON:MouseEvent.BUTTON1;
        var e=new MouseEvent(canvas,id,System.currentTimeMillis(),0,x,y,1,false,button);
        if(id==MouseEvent.MOUSE_DRAGGED)for(var h:canvas.getMouseMotionListeners())h.mouseDragged(e);
        else if(id==MouseEvent.MOUSE_PRESSED)for(var h:canvas.getMouseListeners())h.mousePressed(e);
        else for(var h:canvas.getMouseListeners())h.mouseReleased(e);
    }
    private static void stroke(DrawingCanvas c,int x,int y){
        fire(c,MouseEvent.MOUSE_PRESSED,x,y);fire(c,MouseEvent.MOUSE_DRAGGED,x+12,y+12);fire(c,MouseEvent.MOUSE_RELEASED,x+12,y+12);
    }
    private static DrawingCanvas canvas(){DrawingCanvas c=new DrawingCanvas();c.setSize(600,400);return c;}
    @Test void rectangleToolIsAvailable(){DrawingCanvas c=canvas();c.setTool(DrawingCanvas.Tool.RECTANGLE);assertEquals(DrawingCanvas.Tool.RECTANGLE,c.getTool());}
    @Test void boxSelectsOnlyContainedInk(){
        DrawingCanvas c=canvas();stroke(c,70,70);stroke(c,260,260);c.setTool(DrawingCanvas.Tool.RECTANGLE);
        fire(c,MouseEvent.MOUSE_PRESSED,45,45);fire(c,MouseEvent.MOUSE_DRAGGED,110,110);fire(c,MouseEvent.MOUSE_RELEASED,110,110);
        assertEquals(1,c.selectedInkSnapshot().size());
    }
    @Test void explicitSelectionImageContainsOnlySelectedInk(){
        DrawingCanvas c=canvas();stroke(c,40,45);stroke(c,260,260);c.setTool(DrawingCanvas.Tool.RECTANGLE);
        fire(c,MouseEvent.MOUSE_PRESSED,20,20);fire(c,MouseEvent.MOUSE_DRAGGED,90,90);fire(c,MouseEvent.MOUSE_RELEASED,90,90);
        assertTrue(c.selectedInkImage().getWidth()>0);assertEquals(1,c.selectedInkSnapshot().size());
    }
    @Test void captureWithoutSelectionIsRefused(){DrawingCanvas c=canvas();stroke(c,40,45);assertThrows(IllegalStateException.class,c::selectedInkImage);}
    @Test void erasingSelectionInvalidatesSource(){
        DrawingCanvas c=canvas();stroke(c,40,45);c.setTool(DrawingCanvas.Tool.RECTANGLE);
        fire(c,MouseEvent.MOUSE_PRESSED,20,20);fire(c,MouseEvent.MOUSE_DRAGGED,90,90);fire(c,MouseEvent.MOUSE_RELEASED,90,90);
        var selected=c.selectedInkSnapshot();assertTrue(c.containsAllInk(selected));c.clear();assertFalse(c.containsAllInk(selected));
    }
}
