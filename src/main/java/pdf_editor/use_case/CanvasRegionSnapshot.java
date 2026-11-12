package pdf_editor.use_case;

import java.awt.Rectangle;
import java.awt.image.BufferedImage;
import java.util.Objects;

/** Pure cropping and validation: no Swing state or document mutation. */
public final class CanvasRegionSnapshot {
    private CanvasRegionSnapshot() { }
    /** Copy the selected screen region without resampling its original stroke colours. */
    public static BufferedImage crop(BufferedImage source, Rectangle selection) {
        Objects.requireNonNull(source, "source");
        Objects.requireNonNull(selection, "selection");
        Rectangle image = new Rectangle(0, 0, source.getWidth(), source.getHeight());
        Rectangle clipped = selection.intersection(image);
        if (clipped.width < 8 || clipped.height < 8) {
            throw new IllegalArgumentException("Select an area at least 8×8 pixels");
        }
        BufferedImage copy = new BufferedImage(clipped.width, clipped.height, BufferedImage.TYPE_INT_RGB);
        java.awt.Graphics2D g = copy.createGraphics();
        try {
            g.drawImage(source, 0, 0, clipped.width, clipped.height,
                    clipped.x, clipped.y, clipped.x + clipped.width, clipped.y + clipped.height, null);
        } finally { g.dispose(); }
        return copy;
    }
    /** Blank crops would produce misleading Library cards. */
    public static boolean hasVisibleInk(BufferedImage image) {
        Objects.requireNonNull(image, "image");
        for (int y = 0; y < image.getHeight(); y++) {
            for (int x = 0; x < image.getWidth(); x++) {
                int argb = image.getRGB(x, y);
                int r = (argb >>> 16) & 255, g = (argb >>> 8) & 255, b = argb & 255;
                if (Math.min(r, Math.min(g, b)) < 225) return true;
            }
        }
        return false;
    }
}
