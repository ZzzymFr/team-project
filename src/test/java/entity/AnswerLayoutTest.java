package entity;

import org.junit.jupiter.api.Test;

import java.awt.Rectangle;
import java.awt.geom.Point2D;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AnswerLayoutTest {

    private static Stroke stroke(int... xy) {
        Stroke s = new Stroke();
        for (int i = 0; i < xy.length; i += 2) {
            s.addPoint(new InkPoint(xy[i], xy[i + 1], i));
        }
        return s;
    }

    // "1+2=" written 40 tall, "=" at 240..270
    private static List<Stroke> onePlusTwoEquals() {
        return List.of(
                stroke(100, 100, 100, 140),               // 1
                stroke(120, 120, 160, 120),               // + bar
                stroke(140, 100, 140, 140),               // + stem
                stroke(180, 100, 220, 100, 180, 140, 220, 140),  // 2
                stroke(240, 115, 270, 115),               // = top
                stroke(240, 125, 270, 125));              // = bottom
    }

    @Test
    void glyphsGroupOverlappingStrokes() {
        List<Rectangle> glyphs = AnswerLayout.glyphs(onePlusTwoEquals());
        assertEquals(4, glyphs.size());
        assertEquals(new Rectangle(120, 100, 40, 40), glyphs.get(1));
        assertEquals(new Rectangle(240, 115, 30, 10), glyphs.get(3));
    }

    @Test
    void charHeightIgnoresEqualsAndMinus() {
        assertEquals(40, AnswerLayout.charHeight(onePlusTwoEquals()), 0.01);
    }

    @Test
    void charHeightIgnoresATallFraction() {
        List<Stroke> ink = List.of(
                stroke(100, 100, 100, 140), stroke(120, 100, 120, 140),
                stroke(140, 100, 140, 140), stroke(160, 100, 160, 140),
                stroke(200, 40, 200, 160));  // 120 tall
        assertEquals(40, AnswerLayout.charHeight(ink), 0.01);
    }

    @Test
    void charHeightIsClamped() {
        assertEquals(AnswerLayout.MIN_CHAR_HEIGHT, AnswerLayout.charHeight(List.of(stroke(0, 0, 100, 0))), 0.01);
    }

    @Test
    void rightOfStartsPastTheEqualsSign() {
        List<Stroke> ink = List.of(stroke(100, 100, 100, 140), stroke(260, 115, 300, 115), stroke(260, 125, 300, 125));
        Point2D.Double at = AnswerLayout.rightOf(ink, 40, 30);
        assertEquals(300 + 0.4 * 40, at.x, 0.01);
        assertEquals(120, at.y + 15, 0.01);  // answer middle == "=" middle
    }

    @Test
    void belowStartsUnderTheLeftEdge() {
        List<Stroke> ink = List.of(stroke(100, 110, 200, 150));
        Point2D.Double at = AnswerLayout.below(ink, 40);
        assertEquals(100, at.x, 0.01);
        assertEquals(162, at.y, 0.01);
    }

    @Test
    void strokeOnTheFormulaJoins() {
        assertTrue(AnswerLayout.joins(new Rectangle(100, 100, 200, 40), 40, new Rectangle(310, 105, 20, 30)));
    }

    @Test
    void verticalLineOnTheFormulaJoins() {
        assertTrue(AnswerLayout.joins(new Rectangle(100, 100, 200, 40), 40, new Rectangle(150, 90, 0, 60)));
    }

    @Test
    void nextLineBelowIsNotJoined() {
        assertFalse(AnswerLayout.joins(new Rectangle(100, 100, 200, 40), 40, new Rectangle(100, 152, 200, 40)));
    }
}
