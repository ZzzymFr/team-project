package view.board;

import interface_adapter.history.HistoryViewModel;
import org.junit.jupiter.api.Test;
import view.DrawingCanvas;
import javax.swing.JPanel;
import java.awt.geom.Rectangle2D;
import static org.junit.jupiter.api.Assertions.*;

class BoardViewportInvariantTest {
    @Test void resizingSplitMovesBoundaryNotInkWorldCoordinates() {
        JPanel page=new JPanel(),pdf=new JPanel();
        DrawingCanvas canvas=new DrawingCanvas();
        BoardWindow board=new BoardWindow(page,canvas,h->new HistoryPanel(new HistoryViewModel(),h),pdf);
        board.setSize(1300,740);board.doLayout();
        Rectangle2D probe=new Rectangle2D.Double(100,80,20,20);
        Rectangle2D before=canvas.worldToScreen(probe);
        board.setPdfOpen(true);board.setPdfOverlay(false);board.doLayout();
        Rectangle2D after=canvas.worldToScreen(probe);
        assertEquals(before.getX(),after.getX(),0.0001);
        assertEquals(before.getY(),after.getY(),0.0001);
        assertEquals(page.getX()+page.getWidth(),pdf.getX());
    }
    @Test void splitAndHistoryProduceNoOverlapEvenAfterResize() {
        JPanel page=new JPanel(),pdf=new JPanel();
        BoardWindow board=new BoardWindow(page,new DrawingCanvas(),
                h->new HistoryPanel(new HistoryViewModel(),h),pdf);
        board.setSize(1250,740);board.setHistoryOpen(true);
        board.setPdfOpen(true);board.setPdfOverlay(false);board.doLayout();
        assertEquals(page.getX()+page.getWidth(),pdf.getX());
        assertTrue(page.getX()>0);
        board.setSize(1470,740);board.doLayout();
        assertEquals(page.getX()+page.getWidth(),pdf.getX());
    }
    @Test void overlayIsStillAnExplicitDifferentMode() {
        JPanel page=new JPanel(),pdf=new JPanel();
        BoardWindow board=new BoardWindow(page,new DrawingCanvas(),
                h->new HistoryPanel(new HistoryViewModel(),h),pdf);
        board.setSize(1250,740);board.setPdfOverlay(true);board.setPdfOpen(true);board.doLayout();
        assertEquals(1250,page.getWidth());
        assertTrue(pdf.getX()<page.getWidth());
    }
}
