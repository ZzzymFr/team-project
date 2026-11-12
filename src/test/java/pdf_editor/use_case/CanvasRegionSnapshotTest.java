package pdf_editor.use_case;

import org.junit.jupiter.api.Test;
import java.awt.Color;
import java.awt.Rectangle;
import java.awt.image.BufferedImage;
import static org.junit.jupiter.api.Assertions.*;

class CanvasRegionSnapshotTest {
    private BufferedImage image() {
        BufferedImage img = new BufferedImage(80, 65, BufferedImage.TYPE_INT_RGB);
        var g = img.createGraphics();g.setColor(Color.WHITE);g.fillRect(0, 0, 80, 65);g.dispose();
        img.setRGB(30, 24, Color.RED.getRGB());
        return img;
    }
    @Test void keepsOriginalColour() {
        BufferedImage cropped = CanvasRegionSnapshot.crop(image(), new Rectangle(20, 20, 30, 22));
        assertEquals(Color.RED.getRGB() & 0xffffff, cropped.getRGB(10, 4) & 0xffffff);
        assertTrue(CanvasRegionSnapshot.hasVisibleInk(cropped));
    }
    @Test void clipsOutsideBounds() {
        BufferedImage cropped = CanvasRegionSnapshot.crop(image(), new Rectangle(-5, -8, 42, 36));
        assertEquals(37, cropped.getWidth());assertEquals(28, cropped.getHeight());
    }
    @Test void doesNotMutateOriginal() {
        BufferedImage img = image();BufferedImage cropped = CanvasRegionSnapshot.crop(img, new Rectangle(20, 20, 30, 22));
        cropped.setRGB(10, 4, Color.GREEN.getRGB());
        assertEquals(Color.RED.getRGB() & 0xffffff, img.getRGB(30, 24) & 0xffffff);
    }
    @Test void rejectsTinyOrOutsideSelection() {
        assertThrows(IllegalArgumentException.class, () -> CanvasRegionSnapshot.crop(image(), new Rectangle(1,1,5,5)));
        assertThrows(IllegalArgumentException.class, () -> CanvasRegionSnapshot.crop(image(), new Rectangle(90,100,20,20)));
    }
    @Test void rejectsBlankScreenshot() {
        BufferedImage blank = new BufferedImage(20,20, BufferedImage.TYPE_INT_RGB);
        var g=blank.createGraphics();g.setColor(Color.WHITE);g.fillRect(0,0,20,20);g.dispose();
        assertFalse(CanvasRegionSnapshot.hasVisibleInk(blank));
    }
}
