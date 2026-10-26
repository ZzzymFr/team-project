package view.stack;

import org.junit.jupiter.api.Test;

import javax.swing.JPanel;
import javax.swing.SwingUtilities;
import java.awt.Color;
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
}
