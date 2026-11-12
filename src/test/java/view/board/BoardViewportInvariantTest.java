package view.board;

import interface_adapter.history.HistoryViewModel;
import org.junit.jupiter.api.Test;
import view.DrawingCanvas;
import javax.swing.JPanel;
import java.awt.geom.Rectangle2D;
import static org.junit.jupiter.api.Assertions.*;

/** The right PDF divider moves the view one-to-one, rather than clipping an unmoved drawing. */
class BoardViewportInvariantTest {
    @Test void splittingMovesInkWithPdfBoundaryAndClosingReversesIt() {
        JPanel page = new JPanel(), pdf = new JPanel();
        DrawingCanvas canvas = new DrawingCanvas();
        BoardWindow board = new BoardWindow(page, canvas,
                host -> new HistoryPanel(new HistoryViewModel(), host), pdf);
        board.setSize(1300, 740); board.doLayout();
        Rectangle2D probe = new Rectangle2D.Double(100, 80, 20, 20);
        double before = canvas.worldToScreen(probe).getX();
        board.setPdfOpen(true); board.setPdfOverlay(false); board.doLayout();
        assertEquals(pdf.getX() - 1300, canvas.worldToScreen(probe).getX() - before, 0.0001);
        assertEquals(page.getX() + page.getWidth(), pdf.getX());
        board.setPdfOpen(false); board.doLayout();
        assertEquals(before, canvas.worldToScreen(probe).getX(), 0.0001);
    }
    @Test void openingHistoryDoesNotPanInkWhenPdfRightEdgeIsFixed() {
        JPanel page = new JPanel(), pdf = new JPanel();
        DrawingCanvas canvas = new DrawingCanvas();
        BoardWindow board = new BoardWindow(page, canvas,
                host -> new HistoryPanel(new HistoryViewModel(), host), pdf);
        board.setSize(1400, 740); board.setPdfOpen(true); board.doLayout();
        double before = canvas.worldToScreen(new Rectangle2D.Double(45, 80, 10, 10)).getX();
        board.setHistoryOpen(true); board.doLayout();
        assertEquals(before, canvas.worldToScreen(new Rectangle2D.Double(45, 80, 10, 10)).getX(), 0.0001);
        assertEquals(page.getX() + page.getWidth(), pdf.getX());
    }
    @Test void splitAndHistoryProduceNoOverlapEvenAfterResize() {
        JPanel page = new JPanel(), pdf = new JPanel();
        BoardWindow board = new BoardWindow(page, new DrawingCanvas(),
                h -> new HistoryPanel(new HistoryViewModel(), h), pdf);
        board.setSize(1250, 740); board.setHistoryOpen(true);
        board.setPdfOpen(true); board.setPdfOverlay(false); board.doLayout();
        assertEquals(page.getX() + page.getWidth(), pdf.getX());
        assertTrue(page.getX() > 0);
        board.setSize(1470, 740); board.doLayout();
        assertEquals(page.getX() + page.getWidth(), pdf.getX());
    }
    @Test void overlayIsStillAnExplicitDifferentMode() {
        JPanel page = new JPanel(), pdf = new JPanel();
        BoardWindow board = new BoardWindow(page, new DrawingCanvas(),
                h -> new HistoryPanel(new HistoryViewModel(), h), pdf);
        board.setSize(1250, 740); board.setPdfOverlay(true); board.setPdfOpen(true); board.doLayout();
        assertEquals(1250, page.getWidth());
        assertTrue(pdf.getX() < page.getWidth());
    }
}
