package view.glass;

import javax.swing.JPanel;
import java.awt.Component;
import java.awt.FlowLayout;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.geom.RoundRectangle2D;
import java.awt.image.BufferedImage;

/**
 * A row of glass controls that floats over other content. Behind each control it draws what lies
 * underneath (the backdrop), barely blurred and bent along the rim like a lens, as with real glass.
 * Gaps between the controls let mouse clicks through to what is below.
 */
public class GlassDock extends JPanel {
    private GlassBackdrop backdrop;
    private BufferedImage lensed;  // the backdrop through every control's glass, at device resolution
    private long lensedKey;

    public GlassDock() {
        super(new FlowLayout(FlowLayout.LEFT, 4, 0));
        setOpaque(false);
    }

    /** What lies under the dock, painted in the coordinates of the dock's parent (the dock shifts it into place). */
    public void setBackdrop(GlassBackdrop backdrop) {
        this.backdrop = backdrop;
        lensed = null;
        repaint();
    }

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        if (backdrop == null || getWidth() <= 0 || getHeight() <= 0) {
            return;
        }
        Graphics2D g2 = (Graphics2D) g.create();
        double scale = g2.getTransform().getScaleX();
        long key = lensedKey(scale);
        if (lensed == null || key != lensedKey) {
            lensed = renderLensed(scale);
            lensedKey = key;
        }
        GlassOptics.drawPixels(g2, lensed, scale, 0, 0);
        g2.dispose();
    }

    /** Fingerprint of everything the lensed picture depends on. */
    private long lensedKey(double scale) {
        long key = Double.doubleToLongBits(scale) * 31 + backdrop.backdropVersion();
        key = key * 31 + getX();
        key = key * 31 + getY();
        key = key * 31 + getWidth();
        key = key * 31 + getHeight();
        for (Component child : getComponents()) {
            key = key * 31 + (child.isVisible() ? child.getBounds().hashCode() : 0);
        }
        return key;
    }

    private BufferedImage renderLensed(double scale) {
        int pw = (int) Math.ceil(getWidth() * scale);
        int ph = (int) Math.ceil(getHeight() * scale);
        BufferedImage sharp = new BufferedImage(pw, ph, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = sharp.createGraphics();
        g.scale(scale, scale);
        g.translate(-getX(), -getY());
        backdrop.paintBackdrop(g);
        g.dispose();
        BufferedImage frosted = GlassOptics.gaussian(sharp, (float) (GlassOptics.BLUR_SIGMA * scale));

        BufferedImage result = new BufferedImage(pw, ph, BufferedImage.TYPE_INT_ARGB);
        Graphics2D out = result.createGraphics();
        for (Component child : getComponents()) {
            if (child.isVisible() && child.getWidth() > 2 * Glass.SHADOW && child.getHeight() > 2 * Glass.SHADOW) {
                // the same shape the control paints its glass in
                RoundRectangle2D glass = Glass.rounded(child.getX() + Glass.SHADOW, child.getY() + Glass.SHADOW,
                        child.getWidth() - 2.0 * Glass.SHADOW, child.getHeight() - 2.0 * Glass.SHADOW);
                out.drawImage(GlassOptics.refract(frosted, scale, glass, 1f, 0, 0), 0, 0, null);
            }
        }
        out.dispose();
        return result;
    }
}
