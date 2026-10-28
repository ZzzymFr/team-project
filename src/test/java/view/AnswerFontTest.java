package view;

import org.junit.jupiter.api.Test;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AnswerFontTest {

    @Test
    void loadsKalam() {
        assertEquals("Kalam", AnswerFont.base().getFamily());
    }

    @Test
    void hasTheMathSigns() {
        for (char c : "0123456789=−≠≤≥∞√π±?".toCharArray()) {
            assertTrue(AnswerFont.base().canDisplay(c), "" + c);
        }
    }

    @Test
    void digitHeightMatchesCharHeight() {
        float size = AnswerFont.sizeFor(40);
        assertEquals(40, AnswerFont.visualBounds("0", size).getHeight(), 2);
    }

    @Test
    void drawPutsTheInkTopLeftWhereAsked() {
        BufferedImage image = draw("40", 20, 30);
        int[] box = darkBox(image);
        assertEquals(20, box[0], 2);
        assertEquals(30, box[1], 2);
    }

    @Test
    void drawsLettersKalamLacks() {
        BufferedImage image = draw("θ = 1", 20, 30);
        assertTrue(darkBox(image)[0] >= 0, "something was drawn");
    }

    private static BufferedImage draw(String text, double left, double top) {
        BufferedImage image = new BufferedImage(300, 150, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = image.createGraphics();
        g.setColor(Color.WHITE);
        g.fillRect(0, 0, 300, 150);
        g.setColor(Color.BLACK);
        AnswerFont.draw(g, text, AnswerFont.sizeFor(40), left, top);
        g.dispose();
        return image;
    }

    // minX, minY of dark pixels, or -1s
    private static int[] darkBox(BufferedImage image) {
        int minX = -1;
        int minY = -1;
        for (int y = 0; y < image.getHeight(); y++) {
            for (int x = 0; x < image.getWidth(); x++) {
                if ((image.getRGB(x, y) & 0xff) < 128) {
                    minX = minX < 0 ? x : Math.min(minX, x);
                    minY = minY < 0 ? y : minY;
                }
            }
        }
        return new int[]{minX, minY};
    }
}
