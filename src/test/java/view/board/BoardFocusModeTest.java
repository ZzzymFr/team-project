package view.board;

import org.junit.jupiter.api.Test;
import interface_adapter.history.HistoryViewModel;
import view.DrawingCanvas;
import javax.swing.JPanel;
import static org.junit.jupiter.api.Assertions.*;

class BoardFocusModeTest {
    @Test void fullScreenPdfHidesOtherPanesAndRestoresSplit() {
        var page=new JPanel();var pdf=new JPanel();
        var board=new BoardWindow(page,new DrawingCanvas(),
                host->new HistoryPanel(new HistoryViewModel(),host),pdf);
        board.setSize(1200,750);board.setHistoryOpen(true);board.setPdfOpen(true);board.doLayout();
        assertEquals(page.getX()+page.getWidth(),pdf.getX());
        board.setPdfFullscreen(true);board.doLayout();
        assertTrue(board.isPdfFullscreen());assertFalse(page.isVisible());
        assertEquals(1200,pdf.getWidth());assertEquals(0,pdf.getX());
        board.setPdfFullscreen(false);board.doLayout();
        assertTrue(page.isVisible());assertEquals(page.getX()+page.getWidth(),pdf.getX());
    }
    @Test void closingPdfExitsFocusMode() {
        var board=new BoardWindow(new JPanel(),new DrawingCanvas(),
                host->new HistoryPanel(new HistoryViewModel(),host),new JPanel());
        board.setSize(950,600);board.setPdfFullscreen(true);board.setPdfOpen(false);board.doLayout();
        assertFalse(board.isPdfFullscreen());assertFalse(board.isPdfOpen());
    }
}
