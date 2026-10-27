package view;

import entity.InkPoint;
import entity.Stroke;
import org.junit.jupiter.api.Test;

import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DrawingCanvasToolsTest {
    private final DrawingCanvas canvas = new DrawingCanvas();

    /** Presses at the first point, drags through the rest, releases at the last, like the mouse. */
    private void drag(int... xy) {
        send(MouseEvent.MOUSE_PRESSED, xy[0], xy[1]);
        for (int i = 2; i < xy.length; i += 2) {
            send(MouseEvent.MOUSE_DRAGGED, xy[i], xy[i + 1]);
        }
        send(MouseEvent.MOUSE_RELEASED, xy[xy.length - 2], xy[xy.length - 1]);
    }

    private void send(int id, int x, int y) {
        canvas.dispatchEvent(new MouseEvent(canvas, id, 0, 0, x, y, 1, false, MouseEvent.BUTTON1));
    }

    private int firstX(int stroke) {
        return canvas.getStrokes().get(stroke).getPoints().get(0).getX();
    }

    @Test
    void penIsTheDefaultTool() {
        assertEquals(DrawingCanvas.Tool.PEN, canvas.getTool());
        drag(10, 10, 50, 10);
        assertEquals(1, canvas.getStrokes().size());
    }

    @Test
    void toolChangeIsAnnounced() {
        List<Object> seen = new ArrayList<>();
        canvas.addPropertyChangeListener(DrawingCanvas.TOOL_PROPERTY, e -> seen.add(e.getNewValue()));
        canvas.setTool(DrawingCanvas.Tool.ERASER);
        canvas.setTool(DrawingCanvas.Tool.ERASER);
        canvas.setTool(DrawingCanvas.Tool.LASSO);
        assertEquals(List.of(DrawingCanvas.Tool.ERASER, DrawingCanvas.Tool.LASSO), seen);
    }

    @Test
    void eraserCutsAStrokeAndUndoBringsItBack() {
        drag(0, 50, 200, 50);
        canvas.setTool(DrawingCanvas.Tool.ERASER);
        drag(100, 0, 100, 100);  // one sweep across the middle

        assertEquals(2, canvas.getStrokes().size());

        canvas.undo();  // the whole sweep is one step
        assertEquals(1, canvas.getStrokes().size());
        assertEquals(200, canvas.getStrokes().get(0).getPoints().get(1).getX());
    }

    @Test
    void strokeEraserRemovesTheWholeStroke() {
        drag(0, 50, 200, 50);
        drag(0, 150, 200, 150);
        canvas.setTool(DrawingCanvas.Tool.ERASER);
        canvas.setEraserMode(DrawingCanvas.EraserMode.STROKE);
        drag(100, 45, 100, 55);  // just touch the first line

        assertEquals(1, canvas.getStrokes().size());
        assertEquals(150, canvas.getStrokes().get(0).getPoints().get(0).getY());
        canvas.undo();
        assertEquals(2, canvas.getStrokes().size());
    }

    @Test
    void eraserModeChangeIsAnnounced() {
        List<Object> seen = new ArrayList<>();
        canvas.addPropertyChangeListener(DrawingCanvas.ERASER_MODE_PROPERTY, e -> seen.add(e.getNewValue()));
        assertEquals(DrawingCanvas.EraserMode.PRECISE, canvas.getEraserMode());
        canvas.setEraserMode(DrawingCanvas.EraserMode.STROKE);
        canvas.setEraserMode(DrawingCanvas.EraserMode.STROKE);
        assertEquals(List.of(DrawingCanvas.EraserMode.STROKE), seen);
    }

    @Test
    void eraserMissingTheInkAddsNoUndoStep() {
        drag(0, 50, 200, 50);
        canvas.setTool(DrawingCanvas.Tool.ERASER);
        drag(100, 150, 120, 150);
        canvas.undo();  // undoes the pen stroke, not an empty erase
        assertTrue(canvas.isBlank());
    }

    @Test
    void lassoSelectsAndMovesStrokes() {
        drag(20, 20, 40, 40);       // inside the loop below
        drag(300, 300, 320, 320);   // outside it
        canvas.setTool(DrawingCanvas.Tool.LASSO);
        drag(0, 0, 100, 0, 100, 100, 0, 100);
        assertTrue(canvas.hasSelection());

        drag(30, 30, 80, 30);       // drag the selection 50 px right

        assertEquals(70, firstX(0));
        assertEquals(300, firstX(1), "unselected ink stays put");
        canvas.undo();
        assertEquals(20, firstX(0));
    }

    /** Selects the stroke (20,20)-(40,40); its selection box is (12,12)-(48,48) with 8 px padding. */
    private void selectSmallStroke() {
        drag(20, 20, 40, 40);
        drag(300, 300, 320, 320);   // stays unselected
        canvas.setTool(DrawingCanvas.Tool.LASSO);
        drag(0, 0, 100, 0, 100, 100, 0, 100);
    }

    private int point(int stroke, int index, boolean x) {
        InkPoint p = canvas.getStrokes().get(stroke).getPoints().get(index);
        return x ? p.getX() : p.getY();
    }

    @Test
    void draggingACornerResizesTheSelectionAroundTheOppositeCorner() {
        selectSmallStroke();
        drag(48, 48, 84, 84);       // bottom-right handle, twice as far from the top-left corner

        // scaled 2x around (12, 12): (20,20) -> (28,28), (40,40) -> (68,68)
        assertEquals(28, point(0, 0, true));
        assertEquals(28, point(0, 0, false));
        assertEquals(68, point(0, 1, true));
        assertEquals(68, point(0, 1, false));
        assertEquals(300, firstX(1), "unselected ink is not resized");
        assertTrue(canvas.hasSelection(), "the resized ink stays selected");

        canvas.undo();
        assertEquals(40, point(0, 1, true));
    }

    @Test
    void resizingKeepsTheShape() {
        selectSmallStroke();
        drag(12, 12, 30, 20);       // top-left handle dragged unevenly: still one factor for x and y

        int width = point(0, 1, true) - point(0, 0, true);
        int height = point(0, 1, false) - point(0, 0, false);
        assertEquals(width, height);
        assertTrue(width < 20, "dragging the corner inwards makes it smaller");
    }

    @Test
    void resizeCannotShrinkToNothing() {
        selectSmallStroke();
        drag(48, 48, -500, -500);   // far past the opposite corner
        assertTrue(point(0, 1, true) > point(0, 0, true), "the stroke is not flipped or collapsed");
    }

    @Test
    void lassoDeleteRemovesOnlyTheSelection() {
        drag(20, 20, 40, 40);
        drag(300, 300, 320, 320);
        canvas.setTool(DrawingCanvas.Tool.LASSO);
        drag(0, 0, 100, 0, 100, 100, 0, 100);

        canvas.deleteSelection();

        assertEquals(1, canvas.getStrokes().size());
        assertEquals(300, firstX(0));
        assertFalse(canvas.hasSelection());
    }

    @Test
    void lassoAroundNothingSelectsNothing() {
        drag(20, 20, 40, 40);
        canvas.setTool(DrawingCanvas.Tool.LASSO);
        drag(200, 200, 250, 200, 250, 250);
        assertFalse(canvas.hasSelection());
    }

    @Test
    void clearCanBeUndone() {
        drag(0, 0, 10, 10);
        drag(20, 20, 30, 30);
        canvas.clear();
        assertTrue(canvas.isBlank());
        canvas.undo();
        assertEquals(2, canvas.getStrokes().size());
    }

    @Test
    void undoStillRemovesTheLastPenStroke() {
        drag(0, 0, 10, 10);
        drag(20, 20, 30, 30);
        canvas.undo();
        List<Stroke> left = canvas.getStrokes();
        assertEquals(1, left.size());
        assertEquals(0, left.get(0).getPoints().get(0).getX());
    }
}
