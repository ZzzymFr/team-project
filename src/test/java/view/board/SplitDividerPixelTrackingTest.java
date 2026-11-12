package view.board;

import interface_adapter.history.HistoryViewModel;
import org.junit.jupiter.api.Test;
import view.DrawingCanvas;
import javax.swing.JPanel;
import java.awt.event.MouseEvent;
import static org.junit.jupiter.api.Assertions.*;

/** Dragging the PDF divider by N pixels must change the Canvas/PDF boundary by N pixels. */
class SplitDividerPixelTrackingTest {
    @Test void horizontalDragTracksBoundaryOneToOne() {
        JPanel canvasPage=new JPanel(); JPanel pdf=new JPanel();
        BoardWindow board=new BoardWindow(canvasPage,new DrawingCanvas(),
                host->new HistoryPanel(new HistoryViewModel(),host),pdf);
        board.setSize(1450,750);board.setPdfOpen(true);board.setPdfOverlay(false);board.doLayout();
        SplitDivider handle=null;
        for(var component:board.getComponents())
            if(component instanceof SplitDivider divider && divider.isVisible())handle=divider;
        assertNotNull(handle);
        int original=pdf.getX();
        MouseEvent pressed=new MouseEvent(handle,MouseEvent.MOUSE_PRESSED,1,0,4,200,1,false,MouseEvent.BUTTON1);
        for(var listener:handle.getMouseListeners())listener.mousePressed(pressed);
        MouseEvent drag=new MouseEvent(handle,MouseEvent.MOUSE_DRAGGED,2,0,-96,200,0,false,MouseEvent.BUTTON1);
        for(var listener:handle.getMouseMotionListeners())listener.mouseDragged(drag);
        board.doLayout();
        assertEquals(original-100,pdf.getX(),2.0);
        assertEquals(pdf.getX(),canvasPage.getX()+canvasPage.getWidth());
    }
}
