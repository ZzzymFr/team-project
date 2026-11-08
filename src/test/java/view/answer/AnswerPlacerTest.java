package view.answer;

import entity.InkPoint;
import entity.Stroke;
import interface_adapter.AnswerKind;
import org.junit.jupiter.api.Test;

import java.awt.geom.Rectangle2D;
import java.util.List;
import java.util.function.Predicate;

import static org.junit.jupiter.api.Assertions.assertTrue;

/** Where an answer goes when its usual spot does not fit (runs off the paper). */
class AnswerPlacerTest {
    // "1=" 40 tall, x 100..170, y 100..140
    private static final List<Stroke> ONE_EQUALS = List.of(line(100, 100, 100, 140), line(130, 115, 170, 115),
            line(130, 125, 170, 125));
    private static final Predicate<Rectangle2D> NO_INK = box -> false;

    private static Stroke line(int x0, int y0, int x1, int y1) {
        Stroke s = new Stroke(0);
        s.addPoint(new InkPoint(x0, y0, 0));
        s.addPoint(new InkPoint(x1, y1, 1));
        return s;
    }

    private static Rectangle2D place(AnswerKind kind, Predicate<Rectangle2D> hits, Predicate<Rectangle2D> fits) {
        return AnswerPlacer.place(ONE_EQUALS, kind, "42", null, false, hits, fits).getBox();
    }

    private static boolean isRight(Rectangle2D box) {
        return box.getMinX() > 170;
    }

    private static boolean isBelow(Rectangle2D box) {
        return box.getMinY() > 140;
    }

    @Test
    void rightWhenItFits() {
        assertTrue(isRight(place(AnswerKind.AFTER_EQUALS, NO_INK, box -> true)));
    }

    @Test
    void belowWhenTheRightDoesNotFit() {
        Rectangle2D box = place(AnswerKind.AFTER_EQUALS, NO_INK, b -> !isRight(b));
        assertTrue(isBelow(box) && !isRight(box));
    }

    @Test
    void staysRightWhenBelowWouldHitOtherInk() {
        assertTrue(isRight(place(AnswerKind.AFTER_EQUALS, AnswerPlacerTest::isBelow, b -> !isRight(b))));
    }

    @Test
    void belowAnswersGoRightWhenBelowDoesNotFit() {
        assertTrue(isRight(place(AnswerKind.BELOW, NO_INK, b -> !isBelow(b))));
    }

    @Test
    void belowWhenNothingFitsSoTheCallerCanSlideItWithoutCoveringTheFormula() {
        assertTrue(isBelow(place(AnswerKind.AFTER_EQUALS, NO_INK, b -> false)));
        assertTrue(isBelow(place(AnswerKind.BELOW, NO_INK, b -> false)));
        assertTrue(isRight(place(AnswerKind.AFTER_EQUALS, AnswerPlacerTest::isBelow, b -> false)),
                "unless below is taken by other ink");
    }
}
