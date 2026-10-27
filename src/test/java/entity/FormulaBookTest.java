package entity;

import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.LongSupplier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FormulaBookTest {
    private final FormulaBook book = new FormulaBook();
    private final AtomicLong rev = new AtomicLong(100);
    private final LongSupplier revisions = rev::incrementAndGet;

    private static Stroke stroke(int... xy) {
        Stroke s = new Stroke();
        for (int i = 0; i < xy.length; i += 2) {
            s.addPoint(new InkPoint(xy[i], xy[i + 1], i));
        }
        return s;
    }

    private static final CanvasAnswer ANSWER = new CanvasAnswer("40", false, 320, 100, 30, 40, 50f);

    // "1+" 40 tall at 100..160, with an answer
    private Formula answered(Stroke... strokes) {
        Formula f = book.add(List.of(strokes), 1);
        book.put(f.withAnswer(ANSWER));
        return book.get(f.getId());
    }

    @Test
    void addReplacesAnOverlappingFormula() {
        Stroke a = stroke(100, 100, 100, 140);
        Stroke b = stroke(120, 100, 120, 140);
        Stroke c = stroke(140, 100, 140, 140);
        book.add(List.of(a, b), 1);
        Formula second = book.add(List.of(b, c), 2);
        assertEquals(1, book.all().size());
        assertEquals(second.getId(), book.all().get(0).getId());
        assertEquals(40, second.getCharHeight(), 0.01);
    }

    @Test
    void writingOnAFormulaChangesIt() {
        Stroke a = stroke(100, 100, 100, 140);
        Formula f = answered(a, stroke(120, 100, 160, 140));
        Stroke near = stroke(175, 100, 175, 140);

        Set<Long> changed = book.strokesReplaced(List.of(), List.of(near), revisions);

        assertEquals(Set.of(f.getId()), changed);
        Formula now = book.get(f.getId());
        assertNull(now.getAnswer());
        assertNotEquals(f.getRevision(), now.getRevision());
        assertTrue(now.getStrokes().contains(near));
        assertTrue(book.isBound(near));
    }

    @Test
    void writingFarAwayChangesNothing() {
        Formula f = answered(stroke(100, 100, 100, 140));
        Stroke far = stroke(100, 300, 140, 340);

        assertTrue(book.strokesReplaced(List.of(), List.of(far), revisions).isEmpty());
        assertNotNull(book.get(f.getId()).getAnswer());
        assertFalse(book.isBound(far));
    }

    @Test
    void erasingSplitsPiecesStayInTheFormula() {
        Stroke s = stroke(100, 100, 200, 100);
        Formula f = answered(s, stroke(100, 90, 100, 130));
        Stroke left = stroke(100, 100, 140, 100);
        Stroke right = stroke(160, 100, 200, 100);

        Set<Long> changed = book.strokesReplaced(List.of(s), List.of(left, right), revisions);

        assertEquals(Set.of(f.getId()), changed);
        assertEquals(3, book.get(f.getId()).getStrokes().size());
        assertFalse(book.get(f.getId()).getStrokes().contains(s));
    }

    @Test
    void movingPartOfAFormulaAwayChangesIt() {
        Stroke s = stroke(100, 100, 100, 140);
        Formula f = answered(s, stroke(120, 100, 120, 140));
        Stroke moved = InkEditor.translate(s, 500, 0);

        Set<Long> changed = book.strokesReplaced(List.of(s), List.of(moved), revisions);

        assertEquals(Set.of(f.getId()), changed);
        assertFalse(book.get(f.getId()).getStrokes().contains(s));
        assertFalse(book.isBound(moved));
        assertNull(book.get(f.getId()).getAnswer());
    }

    @Test
    void formulaWithNoStrokesLeftIsRemoved() {
        Stroke a = stroke(100, 100, 100, 140);
        Stroke b = stroke(120, 100, 120, 140);
        Formula f = answered(a, b);

        Set<Long> changed = book.strokesReplaced(List.of(a, b), List.of(), revisions);

        assertTrue(changed.contains(f.getId()));
        assertNull(book.get(f.getId()));
    }

    @Test
    void moveWholeKeepsTheAnswerAndRevision() {
        Stroke a = stroke(100, 100, 100, 140);
        Formula f = answered(a);
        Stroke copy = InkEditor.translate(a, 10, 0);
        Map<Stroke, Stroke> copies = new HashMap<>();
        copies.put(a, copy);

        book.moveWhole(f.getId(), copies, x -> x.translated(10, 0));

        Formula now = book.get(f.getId());
        assertEquals(330, now.getAnswer().getX(), 0.01);
        assertEquals(f.getRevision(), now.getRevision());
        assertEquals(Set.of(copy), now.getStrokes());
    }

    @Test
    void copyIsIndependent() {
        Formula f = book.add(List.of(stroke(100, 100, 100, 140)), 1);
        FormulaBook copy = book.copy();
        book.remove(f.getId());
        assertNotNull(copy.get(f.getId()));
        assertNull(book.get(f.getId()));
    }

    @Test
    void idsAreNotReusedAfterRestoringACopy() {
        Formula f = book.add(List.of(stroke(100, 100, 100, 140)), 1);
        FormulaBook old = book.copy();
        Formula g = book.add(List.of(stroke(300, 100, 300, 140)), 2);
        Formula h = old.add(List.of(stroke(500, 100, 500, 140)), 3);
        assertNotEquals(g.getId(), h.getId());
        assertNotEquals(f.getId(), h.getId());
    }

    @Test
    void answerScalesAroundAnAnchor() {
        CanvasAnswer a = new CanvasAnswer("1", false, 100, 100, 20, 40, 50f).scaled(0, 0, 2);
        assertEquals(200, a.getX(), 0.01);
        assertEquals(80, a.getHeight(), 0.01);
        assertEquals(100f, a.getFontSize(), 0.01);
        assertEquals(40, a.getBox().getWidth(), 0.01);
    }
}
