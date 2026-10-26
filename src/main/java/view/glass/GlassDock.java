package view.glass;

import javax.swing.JPanel;
import java.awt.Component;
import java.awt.FlowLayout;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.Shape;
import java.awt.image.BufferedImage;
import java.awt.image.ConvolveOp;
import java.awt.image.Kernel;
import java.util.function.Consumer;

/**
 * A row of glass controls that floats over other content. Behind each control it draws a blurred
 * copy of what lies underneath (the backdrop), so content passing under the glass shows through
 * frosted, as with real glass. Gaps between the controls let mouse clicks through to what is below.
 */
public class GlassDock extends JPanel {
    private static final int BLUR_RADIUS = 3;
    private static final double MAGNIFY = 1.06;  // the glass bends light like a lens: what is behind looks a bit bigger
    private static final float[] BLUR_KERNEL = gaussian(BLUR_RADIUS);

    private Consumer<Graphics2D> backdrop;

    public GlassDock() {
        super(new FlowLayout(FlowLayout.LEFT, 4, 0));
        setOpaque(false);
    }

    /**
     * What lies under the dock, painted in the coordinates of the dock's parent (the dock shifts it
     * into place). It is drawn over white and blurred.
     */
    public void setBackdrop(Consumer<Graphics2D> backdrop) {
        this.backdrop = backdrop;
        repaint();
    }

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        if (backdrop == null || getWidth() <= 0 || getHeight() <= 0) {
            return;
        }
        BufferedImage frosted = blurredBackdrop();
        Graphics2D g2 = (Graphics2D) g.create();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        for (Component child : getComponents()) {
            if (!child.isVisible()) {
                continue;
            }
            // the same capsule the control paints its glass in
            Shape glass = Glass.capsule(child.getX() + Glass.SHADOW, child.getY() + Glass.SHADOW,
                    child.getWidth() - 2.0 * Glass.SHADOW, child.getHeight() - 2.0 * Glass.SHADOW);
            Graphics2D clip = (Graphics2D) g2.create();
            clip.clip(glass);
            clip.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
            double cx = glass.getBounds2D().getCenterX();
            double cy = glass.getBounds2D().getCenterY();
            clip.translate(cx, cy);
            clip.scale(MAGNIFY, MAGNIFY);
            clip.translate(-cx, -cy);
            clip.drawImage(frosted, -BLUR_RADIUS * 2, -BLUR_RADIUS * 2, null);
            clip.dispose();
        }
        g2.dispose();
    }

    /** The backdrop under the dock (plus a margin so the edges blur cleanly), blurred both ways. */
    private BufferedImage blurredBackdrop() {
        int pad = BLUR_RADIUS * 2;
        BufferedImage sharp = new BufferedImage(getWidth() + 2 * pad, getHeight() + 2 * pad,
                BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = sharp.createGraphics();
        g.setColor(java.awt.Color.WHITE);
        g.fillRect(0, 0, sharp.getWidth(), sharp.getHeight());
        g.translate(pad - getX(), pad - getY());
        backdrop.accept(g);
        g.dispose();
        BufferedImage across = new ConvolveOp(new Kernel(BLUR_KERNEL.length, 1, BLUR_KERNEL),
                ConvolveOp.EDGE_NO_OP, null).filter(sharp, null);
        return new ConvolveOp(new Kernel(1, BLUR_KERNEL.length, BLUR_KERNEL),
                ConvolveOp.EDGE_NO_OP, null).filter(across, null);
    }

    private static float[] gaussian(int radius) {
        float[] kernel = new float[2 * radius + 1];
        double sigma = radius / 2.0;
        double sum = 0;
        for (int i = -radius; i <= radius; i++) {
            kernel[i + radius] = (float) Math.exp(-(i * i) / (2 * sigma * sigma));
            sum += kernel[i + radius];
        }
        for (int i = 0; i < kernel.length; i++) {
            kernel[i] /= sum;
        }
        return kernel;
    }
}
