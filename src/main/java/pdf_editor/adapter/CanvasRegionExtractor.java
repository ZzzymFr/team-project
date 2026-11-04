package pdf_editor.adapter;

import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.Shape;
import java.awt.image.BufferedImage;
import java.util.Objects;

/** Image adapter: selection is always specified in original source pixels, never zoomed pixels. */
public final class CanvasRegionExtractor {
    public BufferedImage extract(BufferedImage source, Shape selection) {
        Objects.requireNonNull(source); Objects.requireNonNull(selection);
        Rectangle bounds = selection.getBounds().intersection(new Rectangle(0, 0, source.getWidth(), source.getHeight()));
        if (bounds.width < 5 || bounds.height < 5) return null;
        BufferedImage out = new BufferedImage(bounds.width, bounds.height, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = out.createGraphics();
        try {
            g.translate(-bounds.x, -bounds.y);
            g.setClip(selection);
            g.drawImage(source, 0, 0, null);
        } finally { g.dispose(); }
        return out;
    }
}
