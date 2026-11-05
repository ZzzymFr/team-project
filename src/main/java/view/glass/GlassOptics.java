package view.glass;

import java.awt.Graphics2D;
import java.awt.geom.AffineTransform;
import java.awt.geom.RoundRectangle2D;
import java.awt.image.BufferedImage;
import java.awt.image.ConvolveOp;
import java.awt.image.Kernel;

/**
 * The pixel work behind the glass: a barely-there blur, lensing along the edge of a capsule, and a
 * soft halo that keeps icons and labels readable over ink.
 *
 * <p>All sizes are in logical pixels and are multiplied by the render scale here. Apple does not
 * publish numbers for Liquid Glass; these are estimates.
 */
final class GlassOptics {
    static final float BLUR_SIGMA = 0.5f;    // barely blurred, so strokes stay readable through the glass
    private static final float LENS_BAND = 14f;      // width of the refracting edge
    private static final float LENS_STRENGTH = 10f;  // how far the very edge pulls the picture

    private GlassOptics() {
    }

    /** Draws a device-resolution image 1:1, so its top-left lands on device pixel (ox, oy). */
    static void drawPixels(Graphics2D g, BufferedImage image, double scale, int ox, int oy) {
        g.drawImage(image, new AffineTransform(1 / scale, 0, 0, 1 / scale, ox / scale, oy / scale), null);
    }

    /** Separable Gaussian blur, edge-extended by 3 sigma so ConvolveOp's untouched border stays clean. */
    static BufferedImage gaussian(BufferedImage src, float sigma) {
        if (sigma < 0.3f) {
            return src;
        }
        int r = (int) Math.ceil(sigma * 3);
        int w = src.getWidth();
        int h = src.getHeight();
        BufferedImage padded = new BufferedImage(w + 2 * r, h + 2 * r, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = padded.createGraphics();
        g.drawImage(src, r, r, null);
        g.drawImage(src.getSubimage(0, 0, w, 1), r, 0, w, r, null);
        g.drawImage(src.getSubimage(0, h - 1, w, 1), r, h + r, w, r, null);
        g.drawImage(padded.getSubimage(r, 0, 1, h + 2 * r), 0, 0, r, h + 2 * r, null);
        g.drawImage(padded.getSubimage(w + r - 1, 0, 1, h + 2 * r), w + r, 0, r, h + 2 * r, null);
        g.dispose();
        float[] kernel = new float[2 * r + 1];
        float sum = 0;
        for (int i = -r; i <= r; i++) {
            kernel[i + r] = (float) Math.exp(-(i * i) / (2 * sigma * sigma));
            sum += kernel[i + r];
        }
        for (int i = 0; i < kernel.length; i++) {
            kernel[i] /= sum;
        }
        BufferedImage across = new ConvolveOp(new Kernel(kernel.length, 1, kernel), ConvolveOp.EDGE_NO_OP, null)
                .filter(padded, null);
        return new ConvolveOp(new Kernel(1, kernel.length, kernel), ConvolveOp.EDGE_NO_OP, null)
                .filter(across, null).getSubimage(r, r, w, h);
    }

    /**
     * The picture as seen through a capsule: bent along the rim, with an anti-aliased edge and
     * nothing outside it. (ox, oy) is where {@code src} starts, in device pixels of the capsule's
     * coordinates.
     *
     * @param strength 0..1, how much lensing; 0 is flat glass
     */
    static BufferedImage refract(BufferedImage src, double scale, RoundRectangle2D capsule, float strength,
                                 int ox, int oy) {
        int pw = src.getWidth();
        int ph = src.getHeight();
        int[] in = src.getRGB(0, 0, pw, ph, null, 0, pw);
        int[] out = new int[pw * ph];
        double radius = capsule.getHeight() / 2.0 * scale;
        double cy = (capsule.getY() + capsule.getHeight() / 2.0) * scale - oy;
        double x0 = (capsule.getX() + capsule.getHeight() / 2.0) * scale - ox;
        double x1 = Math.max(x0, (capsule.getMaxX() - capsule.getHeight() / 2.0) * scale - ox);
        double band = LENS_BAND * scale;
        double k = LENS_STRENGTH * scale * strength;
        int yMin = Math.max(0, (int) (cy - radius) - 1);
        int yMax = Math.min(ph, (int) Math.ceil(cy + radius) + 1);
        int xMin = Math.max(0, (int) (x0 - radius) - 1);
        int xMax = Math.min(pw, (int) Math.ceil(x1 + radius) + 1);
        for (int y = yMin; y < yMax; y++) {
            double py = y + 0.5;
            for (int x = xMin; x < xMax; x++) {
                double px = x + 0.5;
                double qx = Math.max(x0, Math.min(x1, px));
                double dx = px - qx;
                double dy = py - cy;
                double len = Math.sqrt(dx * dx + dy * dy);
                double d = len - radius;
                if (d > 0.5) {
                    continue;
                }
                double coverage = Math.min(1.0, 0.5 - d);
                double sx = px;
                double sy = py;
                if (-d < band && len > 1e-6) {
                    double t = 1.0 + d / band;
                    double offset = t * t * k;
                    sx += dx / len * offset;
                    sy += dy / len * offset;
                }
                int c = bilinear(in, pw, ph, sx - 0.5, sy - 0.5);
                int a = (int) Math.round(((c >>> 24) & 0xFF) * coverage);
                out[y * pw + x] = (a << 24) | (c & 0xFFFFFF);
            }
        }
        BufferedImage result = new BufferedImage(pw, ph, BufferedImage.TYPE_INT_ARGB);
        result.setRGB(0, 0, pw, ph, out, 0, pw);
        return result;
    }

    /**
     * A soft halo in the canvas colour behind the foreground glyphs. Only the alpha is blurred, and
     * only inside the bounding box of the glyphs (3 box blurs approximate the Gaussian).
     */
    static BufferedImage halo(BufferedImage foreground, float sigma) {
        int w = foreground.getWidth();
        int h = foreground.getHeight();
        BufferedImage out = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
        int[] px = foreground.getRGB(0, 0, w, h, null, 0, w);
        int minX = w;
        int minY = h;
        int maxX = -1;
        int maxY = -1;
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                if ((px[y * w + x] >>> 24) != 0) {
                    minX = Math.min(minX, x);
                    maxX = Math.max(maxX, x);
                    minY = Math.min(minY, y);
                    maxY = Math.max(maxY, y);
                }
            }
        }
        if (maxX < 0) {
            return out;
        }
        int pad = (int) Math.ceil(sigma * 3);
        int x0 = Math.max(0, minX - pad);
        int y0 = Math.max(0, minY - pad);
        int bw = Math.min(w, maxX + 1 + pad) - x0;
        int bh = Math.min(h, maxY + 1 + pad) - y0;
        int colour = Glass.HALO.getRGB() & 0xFFFFFF;
        float haloAlpha = Glass.HALO.getAlpha() / 255f;
        float[] a = new float[bw * bh];
        float[] tmp = new float[bw * bh];
        for (int y = 0; y < bh; y++) {
            for (int x = 0; x < bw; x++) {
                a[y * bw + x] = (px[(y + y0) * w + x + x0] >>> 24) * haloAlpha;
            }
        }

        // box sizes for a 3-pass Gaussian approximation
        double ideal = Math.sqrt(12.0 * sigma * sigma / 3 + 1);
        int lower = (int) Math.floor(ideal);
        if (lower % 2 == 0) {
            lower--;
        }
        lower = Math.max(1, lower);
        int upper = lower + 2;
        int m = (int) Math.round((12.0 * sigma * sigma - 3 * lower * lower - 12 * lower - 9) / (-4.0 * lower - 4));
        for (int pass = 0; pass < 3; pass++) {
            boxBlur(a, tmp, bw, bh, ((pass < m ? lower : upper) - 1) / 2);
        }

        int[] result = new int[bw * bh];
        for (int i = 0; i < result.length; i++) {
            int alpha = Math.min(255, Math.round(a[i] * 2));  // a blurred thin glyph is faint: boost
            result[i] = (alpha << 24) | colour;
        }
        out.setRGB(x0, y0, bw, bh, result, 0, bw);
        return out;
    }

    /** One horizontal + one vertical running-sum box blur of radius r (zero outside), in place via tmp. */
    private static void boxBlur(float[] a, float[] tmp, int w, int h, int r) {
        float inverse = 1f / (2 * r + 1);
        for (int y = 0; y < h; y++) {
            int row = y * w;
            float sum = 0;
            for (int i = 0; i <= Math.min(r, w - 1); i++) {
                sum += a[row + i];
            }
            for (int x = 0; x < w; x++) {
                tmp[row + x] = sum * inverse;
                if (x + r + 1 < w) {
                    sum += a[row + x + r + 1];
                }
                if (x - r >= 0) {
                    sum -= a[row + x - r];
                }
            }
        }
        for (int x = 0; x < w; x++) {
            float sum = 0;
            for (int i = 0; i <= Math.min(r, h - 1); i++) {
                sum += tmp[i * w + x];
            }
            for (int y = 0; y < h; y++) {
                a[y * w + x] = sum * inverse;
                if (y + r + 1 < h) {
                    sum += tmp[(y + r + 1) * w + x];
                }
                if (y - r >= 0) {
                    sum -= tmp[(y - r) * w + x];
                }
            }
        }
    }

    /** The colour between four pixels of a w x h ARGB array; (x, y) is clamped to the image. */
    static int bilinear(int[] px, int w, int h, double x, double y) {
        if (w < 2 || h < 2) {
            return px[0];
        }
        x = Math.max(0, Math.min(w - 1.001, x));
        y = Math.max(0, Math.min(h - 1.001, y));
        int ix = (int) x;
        int iy = (int) y;
        double fx = x - ix;
        double fy = y - iy;
        int c00 = px[iy * w + ix];
        int c10 = px[iy * w + ix + 1];
        int c01 = px[(iy + 1) * w + ix];
        int c11 = px[(iy + 1) * w + ix + 1];
        int result = 0;
        for (int shift = 0; shift <= 24; shift += 8) {
            double v = ((c00 >>> shift) & 0xFF) * (1 - fx) * (1 - fy) + ((c10 >>> shift) & 0xFF) * fx * (1 - fy)
                    + ((c01 >>> shift) & 0xFF) * (1 - fx) * fy + ((c11 >>> shift) & 0xFF) * fx * fy;
            result |= ((int) Math.round(v) & 0xFF) << shift;
        }
        return result;
    }
}
