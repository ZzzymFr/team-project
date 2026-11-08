package view.glass;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.util.Objects;

/**
 * Frosted glass: what lies under it, heavily blurred and whitened, as on a pane of sandblasted
 * glass. Paints itself as a backdrop too, so liquid glass laid on it (a {@link GlassBar}) bends the
 * frost rather than the sharp picture.
 *
 * <p>The blur is done on a quarter-size copy, which is both much faster and softer; drawn back at
 * full size it is smooth anyway.
 */
public final class GlassFrost implements GlassBackdrop {
    private static final int DOWNSAMPLE = 4;
    private static final float SIGMA = 3.5f;                    // in quarter-size pixels: ~14 px on screen
    private static final Color WHITEN = new Color(250, 250, 252, 178);

    private final GlassBackdrop under;
    private int width;
    private int height;
    private BufferedImage frost;
    private long frostKey = Long.MIN_VALUE;

    /** @param under what lies under the glass, in the glass's own coordinates */
    public GlassFrost(GlassBackdrop under) {
        this.under = Objects.requireNonNull(under);
    }

    /** The size of the pane, in its own coordinates. */
    public void setSize(int width, int height) {
        this.width = Math.max(1, width);
        this.height = Math.max(1, height);
    }

    @Override
    public void paintBackdrop(Graphics2D g) {
        Graphics2D g2 = (Graphics2D) g.create();
        g2.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        g2.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
        g2.drawImage(frost(), 0, 0, width, height, null);
        g2.dispose();
    }

    @Override
    public long backdropVersion() {
        return (under.backdropVersion() * 31 + width) * 31 + height;
    }

    private BufferedImage frost() {
        long key = backdropVersion();
        if (frost != null && key == frostKey) {
            return frost;
        }
        int w = Math.max(1, (width + DOWNSAMPLE - 1) / DOWNSAMPLE);
        int h = Math.max(1, (height + DOWNSAMPLE - 1) / DOWNSAMPLE);
        BufferedImage small = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = small.createGraphics();
        // opaque first: anything the backdrop leaves clear would blur in as a grey smear
        g.setColor(Glass.CANVAS);
        g.fillRect(0, 0, w, h);
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.scale(1.0 / DOWNSAMPLE, 1.0 / DOWNSAMPLE);
        g.setClip(0, 0, width, height);
        under.paintBackdrop(g);
        g.dispose();
        BufferedImage blurred = GlassOptics.gaussian(small, SIGMA);
        Graphics2D tint = blurred.createGraphics();
        tint.setColor(WHITEN);
        tint.fillRect(0, 0, w, h);
        tint.dispose();
        frost = blurred;
        frostKey = key;
        return frost;
    }
}
