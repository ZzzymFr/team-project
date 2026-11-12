package view.board;

import org.junit.jupiter.api.Test;
import java.awt.event.MouseEvent;
import java.util.concurrent.atomic.AtomicInteger;
import static org.junit.jupiter.api.Assertions.*;

class SplitDividerRegressionTest {
    @Test void draggingDividerMovesBoundaryAndDoubleClickResets() {
        AtomicInteger location = new AtomicInteger(-1);
        AtomicInteger reset = new AtomicInteger();
        SplitDivider divider = new SplitDivider(location::set, reset::incrementAndGet);
        divider.setBounds(300, 0, SplitDivider.HIT, 600);
        divider.dispatchEvent(new MouseEvent(divider, MouseEvent.MOUSE_PRESSED, 1, 0, 3, 10, 1, false,
                MouseEvent.BUTTON1));
        divider.dispatchEvent(new MouseEvent(divider, MouseEvent.MOUSE_DRAGGED, 2, 0, 43, 10, 0, false,
                MouseEvent.BUTTON1));
        assertEquals(343, location.get());
        divider.dispatchEvent(new MouseEvent(divider, MouseEvent.MOUSE_RELEASED, 3, 0, 43, 10, 1, false,
                MouseEvent.BUTTON1));
        divider.dispatchEvent(new MouseEvent(divider, MouseEvent.MOUSE_CLICKED, 4, 0, 3, 10, 2, false,
                MouseEvent.BUTTON1));
        assertEquals(1, reset.get());
    }
    @Test void splitSizeClampsToMinCanvasAndPanelAtLargeWidths() {
        assertTrue(SplitMath.panelWidth(0.99, 1200) <= 1200 - SplitMath.MIN_CANVAS);
        assertTrue(SplitMath.panelWidth(0.01, 1200) >= SplitMath.MIN_PANEL);
        assertTrue(SplitMath.ratioFor(500, 1200) > 0);
    }
}
