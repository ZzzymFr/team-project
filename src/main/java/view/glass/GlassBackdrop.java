package view.glass;

import java.awt.Graphics2D;

/** What lies under a piece of glass; the glass samples it to bend and frost it. */
public interface GlassBackdrop {
    /**
     * Paints the picture, opaque, in the coordinates of the glass's parent. The graphics may be
     * scaled up, so paint vectors rather than a bitmap to keep what the glass magnifies sharp.
     */
    void paintBackdrop(Graphics2D g);

    /** Changes whenever the picture may have changed; while it stays the same, cached copies are good. */
    default long backdropVersion() {
        return System.nanoTime();
    }
}
