package data_access;

import org.junit.jupiter.api.Test;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The model only accepts 128 px high images whose width is a multiple of 16. */
class FormulaImagePreprocessorTest {

    /** An image of the given size and background with one thick line drawn in the given colour. */
    private static BufferedImage drawing(int width, int height, Color background, Color ink,
                                         int x1, int y1, int x2, int y2) {
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = image.createGraphics();
        g.setColor(background);
        g.fillRect(0, 0, width, height);
        g.setColor(ink);
        g.setStroke(new BasicStroke(4f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        g.drawLine(x1, y1, x2, y2);
        g.dispose();
        return image;
    }

    private static int gray(BufferedImage image, int x, int y) {
        return image.getRGB(x, y) & 0xFF;
    }

    private static void assertModelShape(BufferedImage image) {
        assertEquals(128, image.getHeight(), "128 px high");
        assertEquals(0, image.getWidth() % 16, "width a multiple of 16");
        assertTrue(image.getWidth() >= 64 && image.getWidth() <= 1024 + 16, "width " + image.getWidth());
    }

    @Test
    void canvasImageOfAnySizeComesOutModelShaped() {
        int[][] sizes = {{74, 37}, {437, 337}, {212, 107}, {1600, 90}, {60, 600}};
        for (int[] size : sizes) {
            BufferedImage image = drawing(size[0], size[1], Color.WHITE, Color.BLACK,
                    18, 18, size[0] - 18, size[1] - 18);
            assertModelShape(FormulaImagePreprocessor.prepare(image));
        }
    }

    @Test
    void whiteBackgroundAndDarkInkAreKeptNotInverted() {
        BufferedImage out = FormulaImagePreprocessor.prepare(
                drawing(300, 120, Color.WHITE, Color.BLACK, 20, 60, 280, 60));
        assertEquals(255, gray(out, 0, 0), "background stays white");
        assertEquals(255, gray(out, out.getWidth() - 1, 127));
        int darkest = 255;
        for (int x = 0; x < out.getWidth(); x++) {
            darkest = Math.min(darkest, gray(out, x, 64));
        }
        assertTrue(darkest < 80, "the ink is still dark: " + darkest);
    }

    @Test
    void inkFitsInsideTheEightPixelMarginAndIsCentred() {
        BufferedImage out = FormulaImagePreprocessor.prepare(
                drawing(200, 400, Color.WHITE, Color.BLACK, 100, 20, 100, 380));  // tall, thin
        int top = 128;
        int bottom = -1;
        for (int y = 0; y < 128; y++) {
            for (int x = 0; x < out.getWidth(); x++) {
                if (gray(out, x, y) < 128) {
                    top = Math.min(top, y);
                    bottom = Math.max(bottom, y);
                }
            }
        }
        assertTrue(top >= 7 && bottom <= 120, "ink rows " + top + ".." + bottom);
        assertTrue(Math.abs((top + bottom) / 2.0 - 63.5) <= 2, "vertically centred");
        assertEquals(64, out.getWidth(), "narrow ink gets the minimum width");
    }

    @Test
    void veryWideFormulaIsShrunkToTheMaximumWidth() {
        BufferedImage out = FormulaImagePreprocessor.prepare(
                drawing(4000, 60, Color.WHITE, Color.BLACK, 10, 30, 3990, 30));
        assertModelShape(out);
        assertTrue(out.getWidth() <= 1024 + 16);
    }

    @Test
    void darkBackgroundKeepsItsColourToo() {
        BufferedImage out = FormulaImagePreprocessor.prepare(
                drawing(300, 120, Color.BLACK, Color.WHITE, 20, 60, 280, 60));
        assertEquals(0, gray(out, 0, 0));
    }

    @Test
    void blankImageIsRejectedWithAMessage() {
        BufferedImage blank = drawing(100, 50, Color.WHITE, Color.WHITE, 0, 0, 0, 0);
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
                () -> FormulaImagePreprocessor.prepare(blank));
        assertTrue(e.getMessage().contains("no handwriting"));
    }
}
