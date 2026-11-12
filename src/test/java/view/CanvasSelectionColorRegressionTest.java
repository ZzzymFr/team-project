package view;

import org.junit.jupiter.api.Test;
import java.awt.Color;
import java.awt.event.MouseEvent;
import java.awt.image.BufferedImage;
import static org.junit.jupiter.api.Assertions.*;

/** Recognition keeps its monochrome capture; PDF ink capture preserves user stroke colour. */
class CanvasSelectionColorRegressionTest {
    private static void event(DrawingCanvas canvas,int type,int x,int y){
        int button=type==MouseEvent.MOUSE_DRAGGED?MouseEvent.NOBUTTON:MouseEvent.BUTTON1;
        MouseEvent event=new MouseEvent(canvas,type,1,0,x,y,1,false,button);
        if(type==MouseEvent.MOUSE_DRAGGED)
            for(var l:canvas.getMouseMotionListeners())l.mouseDragged(event);
        else if(type==MouseEvent.MOUSE_PRESSED)
            for(var l:canvas.getMouseListeners())l.mousePressed(event);
        else for(var l:canvas.getMouseListeners())l.mouseReleased(event);
    }
    private static DrawingCanvas selected(){
        DrawingCanvas canvas=new DrawingCanvas();canvas.setSize(400,400);
        canvas.setInkColor(new Color(235,40,50));
        event(canvas,MouseEvent.MOUSE_PRESSED,80,80);
        event(canvas,MouseEvent.MOUSE_DRAGGED,93,94);
        event(canvas,MouseEvent.MOUSE_RELEASED,93,94);
        canvas.setTool(DrawingCanvas.Tool.RECTANGLE);
        event(canvas,MouseEvent.MOUSE_PRESSED,40,40);
        event(canvas,MouseEvent.MOUSE_DRAGGED,130,130);
        event(canvas,MouseEvent.MOUSE_RELEASED,130,130);
        return canvas;
    }
    @Test void selectedColorImagePreservesUserPenColour(){
        BufferedImage image=selected().selectedInkColorImage();
        int redPixels=0;
        for(int y=0;y<image.getHeight();y++)for(int x=0;x<image.getWidth();x++){
            Color color=new Color(image.getRGB(x,y),true);
            if(color.getRed()>color.getBlue()+60 && color.getRed()>color.getGreen()+60)redPixels++;
        }
        assertTrue(redPixels>0);
    }
    @Test void recognitionImageRemainsNeutralForModel(){
        BufferedImage image=selected().selectedInkImage();
        for(int y=0;y<image.getHeight();y++)for(int x=0;x<image.getWidth();x++){
            Color color=new Color(image.getRGB(x,y));
            assertEquals(color.getRed(),color.getBlue());
        }
    }
    @Test void selectedColorImageRejectsEmptySelection(){
        DrawingCanvas canvas=new DrawingCanvas();canvas.setSize(400,400);
        assertThrows(IllegalStateException.class,canvas::selectedInkColorImage);
    }
}
