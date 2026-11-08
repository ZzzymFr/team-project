package view.canvas;

import org.junit.jupiter.api.Test;
import view.DrawingCanvas;

import javax.swing.SwingUtilities;
import java.awt.Rectangle;
import java.awt.event.InputEvent;
import java.awt.event.MouseEvent;
import java.awt.event.MouseWheelEvent;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The zoom slider and percent field at the bottom right. */
class ZoomControlTest {
    private static final double EPS = 1e-6;
    private final DrawingCanvas drawing = new DrawingCanvas();
    private final SharedCanvas canvas = new SharedCanvas(drawing);
    private final ZoomControl zoom = new ZoomControl(canvas);

    ZoomControlTest() {
        drawing.setSize(900, 500);
        zoom.getSlider().setSize(zoom.getSlider().getPreferredSize());
    }

    private void type(String text) {
        zoom.getField().setText(text);
        zoom.getField().postActionEvent();
    }

    private void pressSlider(int x, int clicks) {
        zoom.getSlider().dispatchEvent(new MouseEvent(zoom.getSlider(), MouseEvent.MOUSE_PRESSED, 0,
                InputEvent.BUTTON1_DOWN_MASK, x, 20, clicks, false, MouseEvent.BUTTON1));
    }

    @Test
    void theSliderIsLogarithmicWith100PercentInTheMiddle() {
        assertEquals(0, ZoomControl.toFraction(DrawingCanvas.MIN_ZOOM), EPS);
        assertEquals(0.5, ZoomControl.toFraction(1), EPS);
        assertEquals(1, ZoomControl.toFraction(DrawingCanvas.MAX_ZOOM), EPS);
        assertEquals(2, ZoomControl.toZoom(ZoomControl.toFraction(2)), EPS);
    }

    @Test
    void percentsCanBeTypedWithOrWithoutTheSign() {
        assertEquals(1.5, ZoomControl.parsePercent("150"), EPS);
        assertEquals(0.75, ZoomControl.parsePercent(" 75 % "), EPS);
        assertEquals(1.255, ZoomControl.parsePercent("125.5%"), EPS);
        assertNull(ZoomControl.parsePercent("abc"));
        assertNull(ZoomControl.parsePercent("0"));
        assertNull(ZoomControl.parsePercent("-50"));
        assertNull(ZoomControl.parsePercent(""));
    }

    @Test
    void startsAt100Percent() {
        assertEquals("100%", zoom.getField().getText());
        assertEquals(0.5, zoom.getSlider().getValue(), EPS);
    }

    @Test
    void typingAPercentZoomsAndMovesTheSlider() {
        type("200");
        assertEquals(2, drawing.getZoom(), EPS);
        assertEquals("200%", zoom.getField().getText());
        assertEquals(ZoomControl.toFraction(2), zoom.getSlider().getValue(), EPS);
    }

    @Test
    void typedZoomIsKeptWithinTheLimitsAndNonsenseIsIgnored() {
        type("1000%");
        assertEquals(DrawingCanvas.MAX_ZOOM, drawing.getZoom(), EPS);
        assertEquals("400%", zoom.getField().getText());
        type("hello");
        assertEquals(DrawingCanvas.MAX_ZOOM, drawing.getZoom(), EPS);
        assertEquals("400%", zoom.getField().getText(), "shows the zoom again");
    }

    @Test
    void zoomingAroundTheMiddleOfTheView() {
        type("200");
        Rectangle centre = drawing.worldToScreen(new java.awt.geom.Rectangle2D.Double(450, 250, 0, 0)).getBounds();
        assertEquals(450, centre.x, 1, "the point in the middle stays put");
        assertEquals(250, centre.y, 1);
    }

    @Test
    void draggingTheSliderZoomsAndUpdatesTheField() {
        int right = zoom.getSlider().getWidth() - 3 - 18;  // the end of the track
        pressSlider(right, 1);
        assertEquals(DrawingCanvas.MAX_ZOOM, drawing.getZoom(), EPS);
        assertEquals("400%", zoom.getField().getText());

        pressSlider(right, 2);                              // double-click: back to 100%
        assertEquals(1, drawing.getZoom(), EPS);
        assertEquals("100%", zoom.getField().getText());
    }

    @Test
    void followsCtrlScroll() {
        drawing.dispatchEvent(new MouseWheelEvent(drawing, MouseEvent.MOUSE_WHEEL, 0, InputEvent.CTRL_DOWN_MASK,
                100, 100, 0, false, MouseWheelEvent.WHEEL_UNIT_SCROLL, 3, -1));
        assertEquals("110%", zoom.getField().getText());
        assertEquals(ZoomControl.toFraction(1.1), zoom.getSlider().getValue(), EPS);
    }

    @Test
    void sitsAtTheBottomRightOfTheShellAndNotAtTheTop() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            SharedCanvas shared = new SharedCanvas(new DrawingCanvas());
            CanvasShell shell = new CanvasShell(shared, new AnswerCanvasPage(shared));
            shell.setSize(1600, 900);
            shell.doLayout();
            Rectangle dock = shell.getZoomControl().getDock().getBounds();
            assertEquals(1600 - 22, dock.x + dock.width);
            assertEquals(900 - 26, dock.y + dock.height);
        });
    }
}
