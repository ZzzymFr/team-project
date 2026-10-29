package view.stack;

import org.junit.jupiter.api.Test;

import javax.swing.JPanel;
import javax.swing.SwingUtilities;
import java.awt.Color;
import java.awt.Cursor;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;

class StackedPagesTest {

    @Test
    void emptyStackHasNoSelection() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            StackedPages stack = new StackedPages();
            assertEquals(-1, stack.getSelectedIndex());
            assertNull(stack.getSelectedComponent());
        });
    }

    @Test
    void selectingAPageMakesItTheFrontAndNotifiesListeners() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            StackedPages stack = new StackedPages();
            stack.setSize(980, 660);
            JPanel a = new JPanel();
            JPanel b = new JPanel();
            JPanel c = new JPanel();
            stack.addPage("A", Color.BLUE, a);
            stack.addPage("B", Color.ORANGE, b);
            stack.addPage("C", Color.GREEN, c);
            List<Integer> events = new ArrayList<>();
            stack.addChangeListener(e -> events.add(stack.getSelectedIndex()));

            assertEquals(0, stack.getSelectedIndex());
            assertSame(a, stack.getSelectedComponent());

            stack.select(2);
            assertEquals(2, stack.getSelectedIndex());
            assertSame(c, stack.getSelectedComponent());

            stack.select(2);   // already in front: no event
            stack.select(7);   // out of range: ignored
            assertEquals(List.of(2), events);
        });
    }

    /** Counts what the stack does while the mouse hovers, without needing a screen. */
    private static final class CountingStack extends StackedPages {
        int repaints;
        int cursorChanges;

        @Override
        public void repaint(long tm, int x, int y, int width, int height) {
            repaints++;
        }

        @Override
        public void setCursor(Cursor cursor) {
            cursorChanges++;
            super.setCursor(cursor);
        }
    }

    private static void move(StackedPages stack, int x, int y) {
        stack.dispatchEvent(new MouseEvent(stack, MouseEvent.MOUSE_MOVED, 0, 0, x, y, 0, false));
    }

    @Test
    void hoveringATitleRepaintsAndSetsTheCursorOnlyWhenTheHoverChanges() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            CountingStack stack = new CountingStack();
            stack.setSize(980, 660);
            stack.addPage("A", Color.BLUE, new JPanel());
            stack.addPage("B", Color.ORANGE, new JPanel());
            stack.addPage("C", Color.GREEN, new JPanel());
            stack.repaints = 0;
            stack.cursorChanges = 0;

            // The back card "C" sticks out at the top (y 14..54); wiggle the mouse inside its title.
            for (int x = 400; x < 600; x += 5) {
                move(stack, x, 30);
            }
            assertEquals(1, stack.repaints, "one repaint when the hover starts, none while it stays");
            assertEquals(1, stack.cursorChanges);
            assertEquals(Cursor.HAND_CURSOR, stack.getCursor().getType());

            move(stack, 490, 400);   // onto the front card: the hover ends
            assertEquals(2, stack.repaints);
            assertEquals(2, stack.cursorChanges);
            assertEquals(Cursor.DEFAULT_CURSOR, stack.getCursor().getType());
        });
    }
}
