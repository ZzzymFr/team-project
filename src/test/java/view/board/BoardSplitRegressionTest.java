package view.board;

import org.junit.jupiter.api.Test;
import interface_adapter.history.HistoryViewModel;
import view.DrawingCanvas;
import javax.swing.JPanel;
import static org.junit.jupiter.api.Assertions.*;

class BoardSplitRegressionTest {
    private static final class Fixture {
        JPanel page=new JPanel(),pdf=new JPanel();
        BoardWindow window=new BoardWindow(page,new DrawingCanvas(),h->new HistoryPanel(new HistoryViewModel(),h),pdf);
        Fixture(int width){window.setSize(width,720);window.doLayout();}
    }
    @Test void splitDoesNotOverlayCanvas() {
        Fixture f=new Fixture(1440);f.window.setPdfOpen(true);f.window.setPdfOverlay(false);f.window.doLayout();
        assertEquals(f.page.getX()+f.page.getWidth(),f.pdf.getX());
        assertTrue(f.page.getWidth()<1440);
    }
    @Test void historyCanvasPdfOccupyThreeNonOverlappingColumns() {
        Fixture f=new Fixture(1500);f.window.setHistoryOpen(true);f.window.setPdfOpen(true);f.window.setPdfOverlay(false);f.window.doLayout();
        assertTrue(f.page.getX()>0);assertTrue(f.page.getWidth()>0);assertEquals(f.pdf.getX(),f.page.getX()+f.page.getWidth());
    }
    @Test void overlayIntentionallyKeepsCanvasUnderPdf() {
        Fixture f=new Fixture(1440);f.window.setPdfOpen(true);f.window.setPdfOverlay(true);f.window.doLayout();
        assertEquals(1440,f.page.getWidth());assertTrue(f.pdf.getX()<f.page.getWidth());
    }
    @Test void disablingPdfRestoresCanvasWidth() {
        Fixture f=new Fixture(1440);f.window.setPdfOpen(true);f.window.doLayout();
        f.window.setPdfOpen(false);f.window.doLayout();assertEquals(1440,f.page.getWidth());
    }
    @Test void smallWindowKeepsCanvasNonNegative() {
        Fixture f=new Fixture(520);f.window.setHistoryOpen(true);f.window.setPdfOpen(true);f.window.doLayout();
        assertTrue(f.page.getWidth()>=0);assertTrue(f.pdf.getWidth()>=0);
    }
    @Test void enablingHistoryDoesNotClosePdf() {
        Fixture f=new Fixture(1440);f.window.setPdfOpen(true);f.window.setHistoryOpen(true);f.window.doLayout();
        assertTrue(f.window.isHistoryOpen());assertTrue(f.window.isPdfOpen());
    }
}
