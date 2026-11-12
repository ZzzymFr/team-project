package view.board.doc;

import org.junit.jupiter.api.Test;
import javax.swing.JPanel;
import javax.swing.JViewport;
import java.awt.Dimension;
import static org.junit.jupiter.api.Assertions.*;

class CenteredDeskTest {
    @Test void a4SheetHasWhitespaceAroundIt() {
        JPanel paper = new JPanel();paper.setPreferredSize(new Dimension(400, 570));
        CenteredDesk desk = new CenteredDesk(paper);
        assertEquals(420, desk.getPreferredSize().width);
        assertEquals(594, desk.getPreferredSize().height);
    }
    @Test void deskTracksWideViewportButScrollsOnNarrowViewport() {
        JPanel paper = new JPanel();paper.setPreferredSize(new Dimension(400, 570));
        CenteredDesk desk = new CenteredDesk(paper);
        JViewport viewport = new JViewport();viewport.setView(desk);
        viewport.setSize(700, 400);
        assertTrue(desk.getScrollableTracksViewportWidth());
        viewport.setSize(230, 400);
        assertFalse(desk.getScrollableTracksViewportWidth());
        assertFalse(desk.getScrollableTracksViewportHeight());
    }
}
