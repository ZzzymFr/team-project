package data_access;

import java.awt.Graphics2D;
import java.awt.Image;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.util.Arrays;

/**
 * Turns a picture of a handwritten formula into the input the model was trained on, the same
 * way as {@code src/neutral network/preprocess.py} ({@code load_formula_image}) with
 * {@code IMAGE_INVERT = False} from {@code train.py}:
 * grey levels, light/dark kept as they are, cropped to the ink, scaled so the ink fits 128 px
 * high with an 8 px margin (at most 1024 px wide), centred on a canvas 128 px high whose width
 * is a multiple of 16 (at least 64), the background filled with the original background colour.
 *
 * <p>{@code vital_pixel.forward} only accepts {@code [B,3,128,W]} with W a multiple of 16, so an
 * image straight from the canvas must go through here first.
 */
final class FormulaImagePreprocessor {
    static final int HEIGHT = 128;
    static final int MARGIN = 8;
    static final int STRIDE = 16;
    static final int MAX_WIDTH = 1024;
    static final int MIN_WIDTH = 64;
    private static final int INK_THRESHOLD = 32;  // grey difference from the background that counts as ink

    private FormulaImagePreprocessor() {
    }

    /**
     * @return an RGB image {@value #HEIGHT} px high, width a multiple of {@value #STRIDE},
     *         grey (R = G = B) like the training images
     * @throws IllegalArgumentException if there is no ink in the image
     */
    static BufferedImage prepare(BufferedImage source) {
        int width = source.getWidth();
        int height = source.getHeight();
        int[] gray = toGray(source);

        // A light border means a light background (as on the canvas); the ink is then the dark part.
        boolean lightBackground = median(border(gray, width, height)) >= 128;
        int left = width;
        int top = height;
        int right = -1;
        int bottom = -1;
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                int value = gray[y * width + x];
                int foreground = lightBackground ? 255 - value : value;
                if (foreground > INK_THRESHOLD) {
                    left = Math.min(left, x);
                    top = Math.min(top, y);
                    right = Math.max(right, x);
                    bottom = Math.max(bottom, y);
                }
            }
        }
        if (right < 0) {
            throw new IllegalArgumentException("The image has no handwriting in it.");
        }
        int inkWidth = right - left + 1;
        int inkHeight = bottom - top + 1;
        BufferedImage ink = grayImage(gray, width, left, top, inkWidth, inkHeight);

        // Same scaling as training: very flat ink (a long line) is measured as 20% of its width high.
        double scale = Math.min((HEIGHT - 2.0 * MARGIN) / Math.max(inkHeight, 0.2 * inkWidth),
                (MAX_WIDTH - 2.0 * MARGIN) / inkWidth);
        int resizedWidth = Math.max(1, (int) Math.round(inkWidth * scale));
        int resizedHeight = Math.max(1, (int) Math.round(inkHeight * scale));
        BufferedImage resized = resize(ink, resizedWidth, resizedHeight);

        int canvasWidth = Math.max(MIN_WIDTH,
                (int) Math.ceil((resizedWidth + 2.0 * MARGIN) / STRIDE) * STRIDE);
        BufferedImage canvas = new BufferedImage(canvasWidth, HEIGHT, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = canvas.createGraphics();
        int background = lightBackground ? 255 : 0;  // not inverted, so the background keeps its colour
        g.setColor(new java.awt.Color(background, background, background));
        g.fillRect(0, 0, canvasWidth, HEIGHT);
        g.drawImage(resized, (canvasWidth - resizedWidth) / 2, (HEIGHT - resizedHeight) / 2, null);
        g.dispose();
        return canvas;
    }

    /** Grey levels 0..255, transparent parts on white, with PIL's "L" weights (ITU-R 601-2). */
    private static int[] toGray(BufferedImage source) {
        int width = source.getWidth();
        int height = source.getHeight();
        int[] gray = new int[width * height];
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                int argb = source.getRGB(x, y);
                double alpha = ((argb >>> 24) & 0xFF) / 255.0;
                double r = ((argb >> 16) & 0xFF) * alpha + 255 * (1 - alpha);
                double gr = ((argb >> 8) & 0xFF) * alpha + 255 * (1 - alpha);
                double b = (argb & 0xFF) * alpha + 255 * (1 - alpha);
                gray[y * width + x] = (int) Math.round((r * 299 + gr * 587 + b * 114) / 1000);
            }
        }
        return gray;
    }

    private static int[] border(int[] gray, int width, int height) {
        int[] values = new int[2 * width + 2 * height];
        int n = 0;
        for (int x = 0; x < width; x++) {
            values[n++] = gray[x];
            values[n++] = gray[(height - 1) * width + x];
        }
        for (int y = 0; y < height; y++) {
            values[n++] = gray[y * width];
            values[n++] = gray[y * width + width - 1];
        }
        return values;
    }

    private static double median(int[] values) {
        int[] sorted = values.clone();
        Arrays.sort(sorted);
        int middle = sorted.length / 2;
        return sorted.length % 2 == 1 ? sorted[middle] : (sorted[middle - 1] + sorted[middle]) / 2.0;
    }

    private static BufferedImage grayImage(int[] gray, int width, int left, int top, int w, int h) {
        BufferedImage image = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                int v = gray[(top + y) * width + left + x];
                image.setRGB(x, y, (v << 16) | (v << 8) | v);
            }
        }
        return image;
    }

    /** Smooth resizing: area averaging when shrinking (close to training's Lanczos), bicubic when enlarging. */
    private static BufferedImage resize(BufferedImage image, int width, int height) {
        BufferedImage out = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = out.createGraphics();
        if (width < image.getWidth() || height < image.getHeight()) {
            g.drawImage(image.getScaledInstance(width, height, Image.SCALE_AREA_AVERAGING), 0, 0, null);
        } else {
            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
            g.drawImage(image, 0, 0, width, height, null);
        }
        g.dispose();
        return out;
    }
}
