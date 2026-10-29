package view;

import entity.Stroke;
import org.junit.jupiter.api.Test;

import java.awt.Rectangle;
import java.awt.event.MouseEvent;
import java.awt.geom.Rectangle2D;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

// the extra listeners the answer popup leans on
class DrawingCanvasHooksTest {
    private final DrawingCanvas canvas = new DrawingCanvas();

    DrawingCanvasHooksTest() {
        canvas.setSize(900, 500);
    }

    private void drag(int button, int... xy) {
        canvas.dispatchEvent(new MouseEvent(canvas, MouseEvent.MOUSE_PRESSED, 0, 0, xy[0], xy[1], 1, false, button));
        for (int i = 2; i < xy.length; i += 2) {
            canvas.dispatchEvent(new MouseEvent(canvas, MouseEvent.MOUSE_DRAGGED, 0, 0, xy[i], xy[i + 1], 1, false, button));
        }
        canvas.dispatchEvent(new MouseEvent(canvas, MouseEvent.MOUSE_RELEASED, 0, 0,
                xy[xy.length - 2], xy[xy.length - 1], 1, false, button));
    }

    private void write(int... xy) {
        drag(MouseEvent.BUTTON1, xy);
    }

    private Stroke last() {
        List<Stroke> all = canvas.getStrokes();
        return all.get(all.size() - 1);
    }

    @Test
    void strokeListenerGetsTheFinishedStroke() {
        List<Stroke> seen = new ArrayList<>();
        canvas.addStrokeListener(seen::add);
        write(100, 100, 150, 120);
        assertEquals(1, seen.size());
        assertSame(last(), seen.get(0));
    }

    @Test
    void pressToolAndEditsCountAsUserActions() {
        AtomicInteger actions = new AtomicInteger();
        canvas.addUserActionListener(actions::incrementAndGet);
        write(100, 100, 150, 120);
        assertEquals(1, actions.get());
        canvas.setTool(DrawingCanvas.Tool.LASSO);
        assertEquals(2, actions.get());
        canvas.setTool(DrawingCanvas.Tool.LASSO);
        assertEquals(2, actions.get(), "same tool is not an action");
        canvas.setEraserMode(DrawingCanvas.EraserMode.STROKE);
        assertEquals(3, actions.get());
        canvas.clear();
        assertEquals(4, actions.get());
    }

    @Test
    void rightDragPanIsNotAUserAction() {
        AtomicInteger actions = new AtomicInteger();
        AtomicInteger views = new AtomicInteger();
        canvas.addUserActionListener(actions::incrementAndGet);
        canvas.addViewListener(views::incrementAndGet);
        drag(MouseEvent.BUTTON3, 100, 100, 140, 130);
        assertEquals(0, actions.get());
        assertTrue(views.get() > 0);
    }

    @Test
    void deleteSelectionIsAUserAction() {
        write(100, 100, 150, 120);
        AtomicInteger actions = new AtomicInteger();
        canvas.setTool(DrawingCanvas.Tool.LASSO);
        write(80, 80, 180, 80, 180, 140, 80, 140);
        canvas.addUserActionListener(actions::incrementAndGet);
        canvas.deleteSelection();
        assertEquals(1, actions.get());
        assertTrue(canvas.isBlank());
    }

    @Test
    void undoFiresActionAfterFormulaChanges() {
        write(100, 100, 200, 150);
        assertNotNull(canvas.registerNewInk(last(), List.of(last())));
        write(205, 100, 205, 150);  // lands on the formula
        write(205, 100, 205, 150);  // and again, so undo leaves it unanswered
        List<String> order = new ArrayList<>();
        canvas.addFormulaChangeListener(id -> order.add("changed"));
        canvas.addUserActionListener(() -> order.add("action"));
        canvas.undo();
        assertEquals("action", order.get(order.size() - 1));
        assertTrue(order.contains("changed"), "undo still tells listeners, like before");
    }

    @Test
    void panAndZoomFireViewListener() {
        AtomicInteger views = new AtomicInteger();
        canvas.addViewListener(views::incrementAndGet);
        canvas.panBy(10, 0);
        canvas.zoomAt(100, 100, 2);
        canvas.resetView();
        assertEquals(3, views.get());
    }

    @Test
    void worldToScreenFollowsPanAndZoom() {
        canvas.panBy(30, 20);
        canvas.zoomAt(30, 20, 2);  // world (0,0) sits at (30,20)
        Rectangle2D r = canvas.worldToScreen(new Rectangle2D.Double(10, 10, 5, 5));
        assertEquals(50, r.getX(), 1e-9);
        assertEquals(40, r.getY(), 1e-9);
        assertEquals(10, r.getWidth(), 1e-9);
    }

    @Test
    void registerNewInkGroupsTouchingStrokesOnly() {
        write(100, 100, 130, 140);
        Stroke a = last();
        write(140, 100, 170, 140);  // right next to it
        Stroke b = last();
        write(500, 400, 530, 440);  // far off
        Stroke far = last();
        FormulaCapture capture = canvas.registerNewInk(b, List.of(a, b, far));
        assertNotNull(capture);
        assertEquals(2, canvas.getFormulas().stream()
                .filter(f -> f.getId() == capture.getId()).findFirst().orElseThrow().getStrokes().size());
        assertTrue(canvas.isBound(a) && canvas.isBound(b) && canvas.isBound(far));
        assertEquals(2, canvas.getFormulas().size(), "far ink is its own formula");
        Rectangle box = canvas.formulaBounds(capture.getId());
        assertEquals(new Rectangle(100, 100, 70, 40), box);
    }

    @Test
    void registerNewInkReturnsNullForGoneStroke() {
        write(100, 100, 130, 140);
        Stroke a = last();
        canvas.clear();
        assertNull(canvas.registerNewInk(a, List.of(a)));
        assertNull(canvas.formulaBounds(12345));
    }
}
