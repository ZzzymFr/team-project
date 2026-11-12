package view.board;

import org.junit.jupiter.api.Test;
import interface_adapter.history.HistoryViewModel;
import view.DrawingCanvas;
import javax.swing.JPanel;
import static org.junit.jupiter.api.Assertions.*;

class BoardWindowLayoutTest {
    private static final class Fixture {
        final JPanel page = new JPanel();
        final JPanel pdf = new JPanel();
        final BoardWindow board = new BoardWindow(page, new DrawingCanvas(),
                h -> new HistoryPanel(new HistoryViewModel(), h), pdf);
        Fixture() { board.setSize(1280, 760); board.doLayout(); }
    }
    @Test void initialCanvasFillsWidthAndPdfIsClosed() {
        Fixture f = new Fixture();
        assertFalse(f.board.isPdfOpen());
        assertEquals(1280, f.page.getWidth());
    }
    @Test void splitReservesWidthForPdf() {
        Fixture f = new Fixture();
        f.board.setPdfOpen(true); f.board.setPdfOverlay(false); f.board.doLayout();
        assertTrue(f.pdf.getWidth() >= 280);
        assertEquals(f.page.getWidth(), f.pdf.getX());
    }
    @Test void overlayKeepsCanvasWidthAndPlacesPdfOnRight() {
        Fixture f = new Fixture();
        f.board.setPdfOpen(true); f.board.setPdfOverlay(true); f.board.doLayout();
        assertTrue(f.board.isPdfOverlay());
        assertEquals(1280, f.page.getWidth());
        assertEquals(1280, f.pdf.getX() + f.pdf.getWidth());
    }
    @Test void historyAndPdfMayRemainOpenTogether() {
        Fixture f = new Fixture();
        f.board.setHistoryOpen(true); f.board.setPdfOpen(true); f.board.doLayout();
        assertTrue(f.board.isHistoryOpen()); assertTrue(f.board.isPdfOpen());
        assertTrue(f.page.getX() > 0);
        assertTrue(f.page.getWidth() > 0);
    }
    @Test void closingPdfRestoresCanvasWhileHistoryStaysOpen() {
        Fixture f = new Fixture();
        f.board.setHistoryOpen(true); f.board.setPdfOpen(true); f.board.setPdfOpen(false); f.board.doLayout();
        assertFalse(f.board.isPdfOpen()); assertTrue(f.board.isHistoryOpen());
        assertEquals(1280, f.page.getX() + f.page.getWidth());
    }
}
