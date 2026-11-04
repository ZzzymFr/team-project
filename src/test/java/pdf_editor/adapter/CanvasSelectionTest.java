package pdf_editor.adapter;

import org.junit.jupiter.api.Test;
import java.awt.Color;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.image.BufferedImage;
import static org.junit.jupiter.api.Assertions.*;

class CanvasSelectionTest {
    @Test void rectangleIsSelectedInSourceCoordinatesAtAnyZoom() {
        BufferedImage source = new BufferedImage(200, 120, BufferedImage.TYPE_INT_ARGB);
        source.setRGB(80, 50, Color.RED.getRGB());
        for (double zoom : new double[] {0.5, 1, 2, 4}) {
            CanvasSelectionModel model = new CanvasSelectionModel();
            model.start(CanvasCoordinateMapper.toSource(new Point((int)(60*zoom),(int)(30*zoom)), zoom, 200, 120));
            model.finish(CanvasCoordinateMapper.toSource(new Point((int)(100*zoom),(int)(70*zoom)), zoom, 200, 120));
            BufferedImage result = new CanvasRegionExtractor().extract(source, model.shape());
            assertNotNull(result);
            assertEquals(40, result.getWidth());
            assertEquals(Color.RED.getRGB(), result.getRGB(20, 20));
        }
    }
    @Test void freehandLassoMasksPixelsOutsideSelection() {
        BufferedImage source = new BufferedImage(100, 100, BufferedImage.TYPE_INT_ARGB);
        for (int y=0;y<100;y++) for (int x=0;x<100;x++) source.setRGB(x,y,Color.RED.getRGB());
        CanvasSelectionModel model = new CanvasSelectionModel();
        model.setFreehand(true);
        model.start(new Point(10,10));
        model.drag(new Point(70,10));
        model.drag(new Point(10,70));
        model.finish(new Point(10,10));
        BufferedImage result = new CanvasRegionExtractor().extract(source, model.shape());
        assertNotNull(result);
        assertEquals(Color.RED.getRGB(), result.getRGB(10,10));
        assertEquals(0, (result.getRGB(55,55) >>> 24));
        model.clear(); assertNull(model.shape());
    }
    @Test void invalidZoomIsRejected() {
        assertThrows(IllegalArgumentException.class,
                () -> CanvasCoordinateMapper.toSource(new Point(10,10), 0, 100,100));
    }
}
