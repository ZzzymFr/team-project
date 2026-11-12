package pdf_editor.view;

import org.junit.jupiter.api.Test;
import view.DrawingCanvas;
import javax.swing.JPanel;
import java.awt.Color;
import java.awt.event.MouseEvent;
import java.awt.image.BufferedImage;
import java.util.concurrent.atomic.AtomicReference;
import static org.junit.jupiter.api.Assertions.*;

class CanvasRegionCaptureControllerTest {
    private static void mouse(java.awt.Component c, int type, int x, int y) {
        c.dispatchEvent(new MouseEvent(c, type, System.currentTimeMillis(), 0, x, y, 1, false,
                MouseEvent.BUTTON1));
    }
    @Test void capturesOnlySelectedRectangleWithoutAddingInkStrokes() {
        JPanel area = new JPanel(null); area.setSize(220, 180);
        DrawingCanvas canvas = new DrawingCanvas();
        canvas.setBounds(0, 0, 220, 180);area.add(canvas);
        canvas.setInkColor(Color.RED);
        mouse(canvas, MouseEvent.MOUSE_PRESSED, 60, 65);
        mouse(canvas, MouseEvent.MOUSE_DRAGGED, 100, 92);
        mouse(canvas, MouseEvent.MOUSE_RELEASED, 100, 92);
        int before = canvas.getStrokes().size();
        assertTrue(before > 0);
        AtomicReference<BufferedImage> captured = new AtomicReference<>();
        CanvasRegionCaptureController.begin(area, canvas, captured::set);
        java.awt.Component overlay = area.getComponent(0);
        mouse(overlay, MouseEvent.MOUSE_PRESSED, 55, 55);
        mouse(overlay, MouseEvent.MOUSE_DRAGGED, 120, 110);
        mouse(overlay, MouseEvent.MOUSE_RELEASED, 120, 110);
        assertNotNull(captured.get());
        assertEquals(65, captured.get().getWidth());
        assertEquals(55, captured.get().getHeight());
        assertEquals(before, canvas.getStrokes().size());
        assertEquals(1, area.getComponentCount());
    }
    @Test void emptyScreenshotDoesNotCreateLibraryMaterial() {
        JPanel area = new JPanel(null);area.setSize(150,130);
        DrawingCanvas canvas=new DrawingCanvas();canvas.setBounds(0,0,150,130);area.add(canvas);
        AtomicReference<BufferedImage> captured = new AtomicReference<>();
        CanvasRegionCaptureController.begin(area,canvas,captured::set);
        java.awt.Component overlay=area.getComponent(0);
        mouse(overlay,MouseEvent.MOUSE_PRESSED,10,10);
        mouse(overlay,MouseEvent.MOUSE_DRAGGED,100,100);
        mouse(overlay,MouseEvent.MOUSE_RELEASED,100,100);
        assertNull(captured.get());
        assertEquals(1,area.getComponentCount());
    }
}
