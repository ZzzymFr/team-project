package view;

import entity.InkPoint;
import org.junit.jupiter.api.Test;

import java.awt.event.InputEvent;
import java.awt.event.MouseEvent;
import java.awt.event.MouseWheelEvent;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The infinite canvas: moving and zooming the view never changes the ink. */
class DrawingCanvasViewTest {
    private static final double EPS = 1e-9;
    private final DrawingCanvas canvas = new DrawingCanvas();

    private void drag(int button, int... xy) {
        send(MouseEvent.MOUSE_PRESSED, button, xy[0], xy[1]);
        for (int i = 2; i < xy.length; i += 2) {
            send(MouseEvent.MOUSE_DRAGGED, button, xy[i], xy[i + 1]);
        }
        send(MouseEvent.MOUSE_RELEASED, button, xy[xy.length - 2], xy[xy.length - 1]);
    }

    private void send(int id, int button, int x, int y) {
        canvas.dispatchEvent(new MouseEvent(canvas, id, 0, 0, x, y, 1, false, button));
    }

    private void wheel(int modifiers, int x, int y, int notches) {
        canvas.dispatchEvent(new MouseWheelEvent(canvas, MouseEvent.MOUSE_WHEEL, 0, modifiers, x, y, 0, false,
                MouseWheelEvent.WHEEL_UNIT_SCROLL, 3, notches));
    }

    private InkPoint lastStrokeStart() {
        List<entity.Stroke> strokes = canvas.getStrokes();
        return strokes.get(strokes.size() - 1).getPoints().get(0);
    }

    @Test
    void scrollingMovesTheViewSoNewInkGoesFurtherDown() {
        wheel(0, 50, 50, 1);                          // one notch down: the view moves 48 px
        drag(MouseEvent.BUTTON1, 10, 10, 20, 20);
        assertEquals(10, lastStrokeStart().getX());
        assertEquals(58, lastStrokeStart().getY());
    }

    @Test
    void shiftScrollMovesSideways() {
        wheel(InputEvent.SHIFT_DOWN_MASK, 50, 50, 2);
        drag(MouseEvent.BUTTON1, 10, 10, 20, 20);
        assertEquals(106, lastStrokeStart().getX());
        assertEquals(10, lastStrokeStart().getY());
    }

    @Test
    void ctrlScrollZoomsAroundThePointer() {
        wheel(InputEvent.CTRL_DOWN_MASK, 100, 100, -1);   // zoom in one notch at (100, 100)
        assertEquals(1.1, canvas.getZoom(), EPS);

        drag(MouseEvent.BUTTON1, 100, 100, 210, 100);
        List<InkPoint> points = canvas.getStrokes().get(0).getPoints();
        assertEquals(100, points.get(0).getX(), "the point under the pointer stays put");
        assertEquals(200, points.get(1).getX(), "110 px on screen is 100 px of world at 110%");
    }

    @Test
    void zoomStaysWithinLimits() {
        for (int i = 0; i < 100; i++) {
            wheel(InputEvent.CTRL_DOWN_MASK, 0, 0, -1);
        }
        assertEquals(DrawingCanvas.MAX_ZOOM, canvas.getZoom(), EPS);
        for (int i = 0; i < 100; i++) {
            wheel(InputEvent.CTRL_DOWN_MASK, 0, 0, 1);
        }
        assertEquals(DrawingCanvas.MIN_ZOOM, canvas.getZoom(), EPS);
    }

    @Test
    void rightDragMovesTheViewAndDrawsNothing() {
        drag(MouseEvent.BUTTON3, 100, 100, 130, 140);
        assertTrue(canvas.isBlank());

        drag(MouseEvent.BUTTON1, 30, 40, 50, 40);
        assertEquals(0, lastStrokeStart().getX());
        assertEquals(0, lastStrokeStart().getY());
    }

    @Test
    void formulaImageIsTheSameAtEveryZoom() {
        drag(MouseEvent.BUTTON1, 100, 50, 140, 80);
        BufferedImage before = canvas.toFormulaImage();
        wheel(InputEvent.CTRL_DOWN_MASK, 0, 0, -5);
        wheel(0, 0, 0, 3);
        BufferedImage after = canvas.toFormulaImage();
        assertEquals(before.getWidth(), after.getWidth());
        assertEquals(before.getHeight(), after.getHeight());
    }

    @Test
    void eraserKeepsItsScreenSizeWhenZoomedOut() {
        drag(MouseEvent.BUTTON1, 0, 100, 400, 100);
        canvas.zoomAt(0, 0, 0.5);                     // the line is now at y = 50 on screen
        canvas.setTool(DrawingCanvas.Tool.ERASER);
        canvas.setEraserMode(DrawingCanvas.EraserMode.STROKE);
        drag(MouseEvent.BUTTON1, 100, 54, 100, 54);   // 4 px from it on screen, inside the 6 px eraser
        assertTrue(canvas.isBlank());
    }

    @Test
    void resetViewGoesBackTo100PercentAndAnnouncesIt() {
        List<Object> zooms = new ArrayList<>();
        canvas.addPropertyChangeListener(DrawingCanvas.ZOOM_PROPERTY, e -> zooms.add(e.getNewValue()));
        canvas.zoomAt(0, 0, 2);
        canvas.resetView();
        assertEquals(1, canvas.getZoom(), EPS);
        assertEquals(List.of(2.0, 1.0), zooms);
    }
}
