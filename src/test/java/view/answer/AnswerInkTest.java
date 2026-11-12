package view.answer;

import interface_adapter.TextRun;
import org.junit.jupiter.api.Test;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.Shape;
import java.awt.geom.Rectangle2D;
import java.awt.image.BufferedImage;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AnswerInkTest {
    private static final double PEN = 4;

    @Test
    void smallAnswerLinesAreAsThickAsThePen() {
        assertEquals(PEN, lineWidth(AnswerInk.outline(answer("0", 20), PEN)), 1);
    }

    @Test
    void bigAnswerLinesAreAsThickAsThePen() {
        assertEquals(PEN, lineWidth(AnswerInk.outline(answer("0", 150), PEN)), 1);
    }

    @Test
    void minusSurvivesBigSizes() {
        Rectangle2D b = AnswerInk.outline(answer("-", 150), PEN).getBounds2D();
        assertTrue(b.getWidth() > 50, "w " + b.getWidth());
        assertTrue(b.getHeight() < b.getWidth() / 2, "flat " + b);
    }

    @Test
    void zeroIsAsTallAsAPenStrokeOfThatHeight() {
        // a 40 tall stroke shows 40 + pen
        Rectangle2D b = AnswerInk.bounds("0", null, AnswerInk.sizeFor(40), PEN);
        assertEquals(40 + PEN, b.getHeight(), 2);
    }

    @Test
    void boundsMatchTheInk() {
        CanvasAnswer a = answer("12", 40);
        Rectangle2D ink = AnswerInk.outline(a, PEN).getBounds2D();
        assertEquals(a.getX(), ink.getX(), 0.5);
        assertEquals(a.getY(), ink.getY(), 0.5);
        assertEquals(a.getWidth(), ink.getWidth(), 0.5);
        assertEquals(a.getHeight(), ink.getHeight(), 0.5);
    }

    @Test
    void runsKeepTheExponentRaised() {
        float size = AnswerInk.sizeFor(40);
        Rectangle2D flat = AnswerInk.bounds("x2", null, size, PEN);
        Rectangle2D up = AnswerInk.bounds("x2", List.of(new TextRun("x", false), new TextRun("2", true)), size, PEN);
        assertTrue(up.getHeight() != flat.getHeight());
    }

    @Test
    void lettersKalamLacksStillDraw() {
        assertTrue(AnswerInk.bounds("θ", null, AnswerInk.sizeFor(40), PEN).getWidth() > PEN);
    }

    @Test
    void eraserOnTheInkTouches() {
        CanvasAnswer a = answer("1     1", 40);
        double left = a.getX() + PEN;
        assertTrue(AnswerInk.touches(a, PEN, left, a.getY() - 10, left, a.getY() + a.getHeight() + 10, 3));
    }

    @Test
    void eraserInTheGapMisses() {
        CanvasAnswer a = answer("1     1", 40);
        double mid = a.getBox().getCenterX();
        assertFalse(AnswerInk.touches(a, PEN, mid, a.getY() - 10, mid, a.getY() + a.getHeight() + 10, 3));
    }

    @Test
    void eraserFarAwayMisses() {
        assertFalse(AnswerInk.touches(answer("12", 40), PEN, 0, 0, 10, 10, 3));
    }

    @Test
    void refitKeepsTheBoxOnTheInk() {
        CanvasAnswer a = AnswerInk.refit(answer("12", 40).scaled(0, 0, 3), PEN);
        Rectangle2D ink = AnswerInk.outline(a, PEN).getBounds2D();
        assertEquals(ink.getWidth(), a.getWidth(), 0.5);
        assertEquals(ink.getHeight(), a.getHeight(), 0.5);
    }

    @Test
    void colourIsKept() {
        CanvasAnswer a = answer("12", 40).withColor(Color.RED);
        assertEquals(Color.RED, a.translated(5, 5).getColor());
        assertEquals(Color.RED, a.scaled(0, 0, 2).getColor());
        assertEquals(Color.RED, a.sized(1, 1).getColor());
    }

    @Test
    void skeletonOfABarIsOnePixelThick() {
        int w = 30;
        int h = 11;
        boolean[] bar = new boolean[w * h];
        for (int y = 3; y <= 7; y++) {
            for (int x = 2; x < 28; x++) {
                bar[y * w + x] = true;
            }
        }
        boolean[] line = Skeleton.thin(bar, w, h);
        int middle = 0;
        for (int y = 0; y < h; y++) {
            middle += line[y * w + 15] ? 1 : 0;
        }
        assertEquals(1, middle);
    }

    private static CanvasAnswer answer(String text, double charHeight) {
        float size = AnswerInk.sizeFor(charHeight);
        Rectangle2D b = AnswerInk.bounds(text, null, size, PEN);
        return new CanvasAnswer(text, false, 200, 100, b.getWidth(), b.getHeight(), size);
    }

    // avg width of ink runs across the middle row, 8x supersampled
    private static double lineWidth(Shape ink) {
        int k = 8;
        Rectangle2D b = ink.getBounds2D();
        int w = (int) Math.ceil(b.getWidth() * k) + 2;
        BufferedImage row = new BufferedImage(w, 1, BufferedImage.TYPE_BYTE_GRAY);
        Graphics2D g = row.createGraphics();
        g.scale(k, k);
        g.translate(-b.getX(), -b.getCenterY() + 0.5 / k);
        g.setColor(Color.WHITE);
        g.fill(ink);
        g.dispose();
        int filled = 0;
        int runs = 0;
        boolean in = false;
        for (int x = 0; x < w; x++) {
            boolean on = row.getRaster().getSample(x, 0, 0) > 127;
            filled += on ? 1 : 0;
            runs += on && !in ? 1 : 0;
            in = on;
        }
        return (double) filled / runs / k;
    }
}
