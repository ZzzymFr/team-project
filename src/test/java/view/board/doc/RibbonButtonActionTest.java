package view.board.doc;

import org.junit.jupiter.api.Test;
import java.awt.event.MouseEvent;
import java.util.concurrent.atomic.AtomicInteger;
import static org.junit.jupiter.api.Assertions.*;

/** UI buttons are actual commands, not decorative placeholders. */
class RibbonButtonActionTest {
    @Test void buttonExecutesActionOnClickAndNotOnHover() {
        AtomicInteger count = new AtomicInteger();
        RibbonButton button = RibbonButton.letter("B", java.awt.Font.BOLD, false, false, "Bold")
                .onClick(count::incrementAndGet);
        button.setSize(button.getPreferredSize());
        button.dispatchEvent(new MouseEvent(button, MouseEvent.MOUSE_ENTERED, 1L, 0, 8, 8, 0, false));
        assertEquals(0, count.get());
        button.dispatchEvent(new MouseEvent(button, MouseEvent.MOUSE_PRESSED, 2L, 0, 8, 8, 1, false));
        button.dispatchEvent(new MouseEvent(button, MouseEvent.MOUSE_RELEASED, 3L, 0, 8, 8, 1, false));
        assertEquals(1, count.get());
    }
    @Test void buttonDoesNotExecuteIfReleasedOutsideBounds() {
        AtomicInteger count = new AtomicInteger();
        RibbonButton button = RibbonButton.letter("I", java.awt.Font.ITALIC, false, false, "Italic")
                .onClick(count::incrementAndGet);
        button.setSize(button.getPreferredSize());
        button.dispatchEvent(new MouseEvent(button, MouseEvent.MOUSE_PRESSED, 2L, 0, 7, 7, 1, false));
        button.dispatchEvent(new MouseEvent(button, MouseEvent.MOUSE_RELEASED, 3L, 0, -8, -8, 1, false));
        assertEquals(0, count.get());
    }
}
