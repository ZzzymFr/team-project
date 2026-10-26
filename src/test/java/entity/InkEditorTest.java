package entity;

import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class InkEditorTest {

    private static Stroke stroke(int... xy) {
        Stroke s = new Stroke();
        for (int i = 0; i < xy.length; i += 2) {
            s.addPoint(new InkPoint(xy[i], xy[i + 1], i));
        }
        return s;
    }

    private static List<InkPoint> square(int left, int top, int right, int bottom) {
        return Arrays.asList(new InkPoint(left, top, 0), new InkPoint(right, top, 0),
                new InkPoint(right, bottom, 0), new InkPoint(left, bottom, 0));
    }

    @Test
    void erasingTheMiddleOfALineSplitsItInTwo() {
        // A long segment with no points near the eraser must still be cut.
        List<Stroke> after = InkEditor.erase(List.of(stroke(0, 0, 100, 0)), 50, 0, 10);

        assertEquals(2, after.size());
        Stroke left = after.get(0);
        Stroke right = after.get(1);
        assertEquals(0, left.getPoints().get(0).getX());
        assertTrue(left.getPoints().get(left.getPoints().size() - 1).getX() < 40);
        assertTrue(right.getPoints().get(0).getX() > 60);
        assertEquals(100, right.getPoints().get(right.getPoints().size() - 1).getX());
    }

    @Test
    void erasingAwayFromTheInkChangesNothing() {
        List<Stroke> strokes = List.of(stroke(0, 0, 100, 0));
        assertSame(strokes, InkEditor.erase(strokes, 50, 40, 10));
    }

    @Test
    void erasingTheEndOfALineShortensIt() {
        List<Stroke> after = InkEditor.erase(List.of(stroke(0, 0, 100, 0)), 100, 0, 10);
        assertEquals(1, after.size());
        List<InkPoint> points = after.get(0).getPoints();
        assertTrue(points.get(points.size() - 1).getX() <= 90, "nothing is left inside the eraser");
    }

    @Test
    void erasingADotRemovesIt() {
        assertTrue(InkEditor.erase(List.of(stroke(5, 5)), 6, 6, 4).isEmpty());
    }

    @Test
    void eraseAlongLeavesNoGapsOnAFastDrag() {
        // Three vertical lines; one quick horizontal sweep across all of them.
        List<Stroke> strokes = List.of(stroke(10, 0, 10, 100), stroke(50, 0, 50, 100), stroke(90, 0, 90, 100));
        List<Stroke> after = InkEditor.eraseAlong(strokes, 0, 50, 100, 50, 6);
        assertEquals(6, after.size(), "each line is cut into a top and a bottom piece");
    }

    @Test
    void wholeStrokeEraserRemovesEveryTouchedStrokeEntirely() {
        Stroke touched = stroke(0, 0, 100, 0);
        Stroke crossed = stroke(50, -40, 50, 40);
        Stroke away = stroke(0, 200, 100, 200);

        List<Stroke> after = InkEditor.removeTouchedAlong(List.of(touched, crossed, away), 45, 0, 55, 0, 4);

        assertEquals(List.of(away), after);
    }

    @Test
    void wholeStrokeEraserMissingEverythingChangesNothing() {
        List<Stroke> strokes = List.of(stroke(0, 0, 100, 0));
        assertSame(strokes, InkEditor.removeTouchedAlong(strokes, 0, 50, 100, 50, 4));
    }

    @Test
    void lassoSelectsStrokesMostlyInside() {
        Stroke inside = stroke(20, 20, 30, 30);
        Stroke outside = stroke(200, 200, 210, 210);
        Stroke mostlyOutside = stroke(40, 40, 120, 120, 130, 130);

        List<Stroke> selected = InkEditor.selectInside(List.of(inside, outside, mostlyOutside), square(0, 0, 50, 50));

        assertEquals(List.of(inside), selected);
    }

    @Test
    void lassoNeedsAtLeastThreePoints() {
        List<InkPoint> line = Arrays.asList(new InkPoint(0, 0, 0), new InkPoint(50, 50, 0));
        assertTrue(InkEditor.selectInside(List.of(stroke(10, 10, 20, 20)), line).isEmpty());
    }

    @Test
    void translateMovesEveryPointAndKeepsTheOriginal() {
        Stroke original = stroke(1, 2, 3, 4);
        Stroke moved = InkEditor.translate(original, 10, -2);
        assertEquals(11, moved.getPoints().get(0).getX());
        assertEquals(0, moved.getPoints().get(0).getY());
        assertEquals(13, moved.getPoints().get(1).getX());
        assertEquals(1, original.getPoints().get(0).getX());
    }
}
