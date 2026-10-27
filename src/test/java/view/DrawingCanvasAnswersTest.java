package view;

import entity.AnswerKind;
import entity.CanvasAnswer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.awt.event.MouseEvent;
import java.awt.geom.Rectangle2D;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DrawingCanvasAnswersTest {
    private final DrawingCanvas canvas = new DrawingCanvas();
    private final List<Long> changed = new ArrayList<>();

    @BeforeEach
    void setUp() {
        canvas.setSize(900, 500);
        canvas.addFormulaChangeListener(changed::add);
    }

    private void drag(int... xy) {
        send(MouseEvent.MOUSE_PRESSED, xy[0], xy[1]);
        for (int i = 2; i < xy.length; i += 2) {
            send(MouseEvent.MOUSE_DRAGGED, xy[i], xy[i + 1]);
        }
        send(MouseEvent.MOUSE_RELEASED, xy[xy.length - 2], xy[xy.length - 1]);
    }

    private void send(int id, int x, int y) {
        canvas.dispatchEvent(new MouseEvent(canvas, id, 0, 0, x, y, 1, false, MouseEvent.BUTTON1));
    }

    // "1=" 40 tall, x 100..170, y 100..140
    private void writeOneEquals() {
        drag(100, 100, 100, 140);
        drag(130, 115, 170, 115);
        drag(130, 125, 170, 125);
    }

    private FormulaCapture calculateOne() {
        List<FormulaCapture> captures = canvas.takeFormulasToCalculate();
        assertEquals(1, captures.size());
        return captures.get(0);
    }

    private CanvasAnswer answer(AnswerKind kind, String text) {
        FormulaCapture c = calculateOne();
        assertTrue(canvas.showAnswer(c.getId(), c.getRevision(), kind, text, false));
        assertEquals(1, canvas.getAnswers().size());
        return canvas.getAnswers().get(0);
    }

    @Test
    void calculatingBindsTheInk() {
        writeOneEquals();
        calculateOne();
        assertEquals(1, canvas.getFormulas().size());
        assertEquals(3, canvas.getFormulas().get(0).getStrokes().size());
    }

    @Test
    void emptyCanvasCannotBeCalculated() {
        IllegalStateException e = assertThrows(IllegalStateException.class, canvas::takeFormulasToCalculate);
        assertEquals("The canvas is empty.", e.getMessage());
    }

    @Test
    void answerIsNotInTheFormulaImage() {
        writeOneEquals();
        BufferedImage before = canvas.toFormulaImage();
        answer(AnswerKind.AFTER_EQUALS, "2");
        BufferedImage after = canvas.toFormulaImage();
        assertEquals(before.getWidth(), after.getWidth());
        assertEquals(before.getHeight(), after.getHeight());
        assertEquals(canvas.getFormulaBounds().getMaxX(), 170);
    }

    @Test
    void afterEqualsAnswerIsRightOfTheInk() {
        writeOneEquals();
        CanvasAnswer a = answer(AnswerKind.AFTER_EQUALS, "2");
        assertTrue(a.getX() > 170, "x " + a.getX());
        assertFalse(a.getBox().intersects(new Rectangle2D.Double(100, 100, 70, 40)));
        assertEquals(120, a.getBox().getCenterY(), 2);  // lined up with "="
        assertEquals(40, a.getHeight(), 3);  // as tall as the "1"
        assertFalse(a.isError());
    }

    @Test
    void equationAnswerIsBelow() {
        writeOneEquals();
        CanvasAnswer a = answer(AnswerKind.BELOW, "x = 2");
        assertTrue(a.getY() > 140, "y " + a.getY());
        assertEquals(100, a.getX(), 1);
    }

    @Test
    void belowAnswerMovesRightWhenInkIsUnderneath() {
        writeOneEquals();
        FormulaCapture c = calculateOne();
        drag(100, 152, 170, 152);  // a line just under it, not part of it
        assertTrue(changed.isEmpty());
        assertTrue(canvas.showAnswer(c.getId(), c.getRevision(), AnswerKind.BELOW, "x = 2", false));
        CanvasAnswer a = canvas.getAnswers().get(0);
        assertTrue(a.getX() > 170, "x " + a.getX());
    }

    @Test
    void writingOnTheFormulaRemovesTheAnswerAndNotifies() {
        writeOneEquals();
        answer(AnswerKind.AFTER_EQUALS, "2");
        long id = canvas.getFormulas().get(0).getId();
        drag(180, 100, 180, 140);
        assertEquals(List.of(id), changed);
        assertTrue(canvas.getAnswers().isEmpty());
    }

    @Test
    void writingFarAwayKeepsTheAnswer() {
        writeOneEquals();
        answer(AnswerKind.AFTER_EQUALS, "2");
        drag(100, 400, 140, 440);
        assertTrue(changed.isEmpty());
        assertEquals(1, canvas.getAnswers().size());
    }

    @Test
    void staleRevisionIsIgnored() {
        writeOneEquals();
        FormulaCapture c = calculateOne();
        drag(180, 100, 180, 140);
        assertFalse(canvas.showAnswer(c.getId(), c.getRevision(), AnswerKind.AFTER_EQUALS, "2", false));
        assertTrue(canvas.getAnswers().isEmpty());
    }

    @Test
    void undoBringsTheAnswerBack() {
        writeOneEquals();
        answer(AnswerKind.AFTER_EQUALS, "2");
        drag(180, 100, 180, 140);
        assertTrue(canvas.getAnswers().isEmpty());
        canvas.undo();
        assertEquals(1, canvas.getAnswers().size());
        assertEquals(1, changed.size(), "undo does not notify");
    }

    @Test
    void secondCalculateWithoutLassoTakesOnlyNewInk() {
        writeOneEquals();
        answer(AnswerKind.AFTER_EQUALS, "2");
        drag(100, 300, 140, 330);
        FormulaCapture second = calculateOne();
        assertEquals(30 + 1 + 2 * 18, second.getImage().getHeight());
        assertEquals(2, canvas.getFormulas().size());
        assertEquals(1, canvas.getAnswers().size());
    }

    @Test
    void calculateWithEverythingBoundRedoesAll() {
        writeOneEquals();
        answer(AnswerKind.AFTER_EQUALS, "2");
        FormulaCapture again = calculateOne();
        assertTrue(canvas.getAnswers().isEmpty());
        assertTrue(canvas.showAnswer(again.getId(), again.getRevision(), AnswerKind.AFTER_EQUALS, "2", false));
    }

    @Test
    void recalculationCaptureOnlyWhenTheAnswerIsGone() {
        writeOneEquals();
        answer(AnswerKind.AFTER_EQUALS, "2");
        long id = canvas.getFormulas().get(0).getId();
        assertEquals(null, canvas.captureForRecalculation(id));
        drag(180, 100, 180, 140);
        FormulaCapture c = canvas.captureForRecalculation(id);
        assertEquals(id, c.getId());
        assertEquals(canvas.getFormulas().get(0).getRevision(), c.getRevision());
    }

    @Test
    void clearRemovesAnswers() {
        writeOneEquals();
        answer(AnswerKind.AFTER_EQUALS, "2");
        canvas.clear();
        assertTrue(canvas.getAnswers().isEmpty());
        assertTrue(canvas.getFormulas().isEmpty());
        canvas.undo();
        assertEquals(1, canvas.getAnswers().size());
    }
}
