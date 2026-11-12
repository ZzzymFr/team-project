package view;

import interface_adapter.AnswerKind;
import org.junit.jupiter.api.Test;
import java.awt.event.MouseEvent;
import java.awt.image.BufferedImage;
import static org.junit.jupiter.api.Assertions.*;

/** Box-selected formulas should carry their existing evaluated result into the Library. */
class CanvasSelectedResultTest {
    private static void event(DrawingCanvas c,int type,int x,int y){
        int button=type==MouseEvent.MOUSE_DRAGGED?MouseEvent.NOBUTTON:MouseEvent.BUTTON1;
        c.dispatchEvent(new MouseEvent(c,type,0,0,x,y,1,false,button));
    }
    private static void stroke(DrawingCanvas c,int a,int b,int x,int y){
        event(c,MouseEvent.MOUSE_PRESSED,a,b);
        event(c,MouseEvent.MOUSE_DRAGGED,x,y);
        event(c,MouseEvent.MOUSE_RELEASED,x,y);
    }
    @Test void selectedFormulaRetainsAnswerAndImageIncludesIt() {
        DrawingCanvas canvas=new DrawingCanvas();canvas.setSize(900,500);
        stroke(canvas,100,100,100,140);
        stroke(canvas,130,115,170,115);
        stroke(canvas,130,125,170,125);
        var capture=canvas.takeFormulasToCalculate().get(0);
        assertTrue(canvas.showAnswer(capture.getId(),capture.getRevision(),AnswerKind.AFTER_EQUALS,"2",false));
        canvas.setTool(DrawingCanvas.Tool.RECTANGLE);
        event(canvas,MouseEvent.MOUSE_PRESSED,60,60);
        event(canvas,MouseEvent.MOUSE_DRAGGED,195,160);
        event(canvas,MouseEvent.MOUSE_RELEASED,195,160);
        assertEquals("2",canvas.selectedAnswer());
        BufferedImage original=canvas.selectedInkImage();
        BufferedImage full=canvas.selectedInkColorImage();
        assertTrue(full.getWidth()>original.getWidth());
    }
    @Test void uncalculatedSelectedFormulaHasNoResult() {
        DrawingCanvas canvas=new DrawingCanvas();canvas.setSize(400,350);
        stroke(canvas,100,100,100,145);
        canvas.setTool(DrawingCanvas.Tool.RECTANGLE);
        event(canvas,MouseEvent.MOUSE_PRESSED,70,70);
        event(canvas,MouseEvent.MOUSE_DRAGGED,130,180);
        event(canvas,MouseEvent.MOUSE_RELEASED,130,180);
        assertEquals("",canvas.selectedAnswer());
    }
}
