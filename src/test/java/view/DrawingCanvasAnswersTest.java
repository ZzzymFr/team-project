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

    // ---- lasso + eraser on answers

    private void lasso(int left, int top, int right, int bottom) {
        canvas.setTool(DrawingCanvas.Tool.LASSO);
        drag(left, top, right, top, right, bottom, left, bottom);
    }

    // lasso just the answer of "1=" -> "2"
    private CanvasAnswer selectAnswer() {
        writeOneEquals();
        CanvasAnswer a = answer(AnswerKind.AFTER_EQUALS, "2");
        Rectangle2D box = a.getBox();
        lasso(176, (int) box.getMinY() - 10, (int) box.getMaxX() + 10, (int) box.getMaxY() + 10);
        assertTrue(canvas.hasSelection());
        return a;
    }

    @Test
    void lassoSelectsAnAnswerAndDeleteRemovesIt() {
        selectAnswer();
        canvas.deleteSelection();
        assertTrue(canvas.getAnswers().isEmpty());
        assertTrue(canvas.getFormulas().isEmpty());
        assertEquals(3, canvas.getStrokes().size());
    }

    @Test
    void draggingAnAnswerMovesOnlyIt() {
        CanvasAnswer a = selectAnswer();
        int cx = (int) a.getBox().getCenterX();
        int cy = (int) a.getBox().getCenterY();
        drag(cx, cy, cx + 100, cy + 50);
        CanvasAnswer moved = canvas.getAnswers().get(0);
        assertEquals(a.getX() + 100, moved.getX(), 0.01);
        assertEquals(a.getY() + 50, moved.getY(), 0.01);
        assertEquals(100, canvas.getStrokes().get(0).getPoints().get(0).getX());
        assertTrue(changed.isEmpty());
    }

    @Test
    void draggingTheWholeFormulaTakesTheAnswerAlong() {
        writeOneEquals();
        CanvasAnswer a = answer(AnswerKind.AFTER_EQUALS, "2");
        lasso(90, 90, 175, 150);
        drag(135, 120, 135, 220);
        assertTrue(changed.isEmpty());
        assertEquals(1, canvas.getAnswers().size());
        assertEquals(a.getY() + 100, canvas.getAnswers().get(0).getY(), 0.01);
        assertEquals(3, canvas.getFormulas().get(0).getStrokes().size());
        assertEquals(200, canvas.getStrokes().get(0).getPoints().get(0).getY());
    }

    @Test
    void draggingPartOfTheFormulaAwayChangesIt() {
        writeOneEquals();
        answer(AnswerKind.AFTER_EQUALS, "2");
        long id = canvas.getFormulas().get(0).getId();
        lasso(90, 90, 110, 150);
        drag(100, 120, 100, 420);
        assertEquals(List.of(id), changed);
        assertTrue(canvas.getAnswers().isEmpty());
    }

    @Test
    void resizingAnAnswerScalesItsFont() {
        CanvasAnswer a = selectAnswer();
        // bottom-right handle sits 8px outside the box
        double ax = a.getBox().getMinX() - 8;
        double ay = a.getBox().getMinY() - 8;
        double bx = a.getBox().getMaxX() + 8;
        double by = a.getBox().getMaxY() + 8;
        Rectangle2D sel = new Rectangle2D.Double(ax, ay, bx - ax, by - ay).getBounds2D();
        int px = (int) Math.ceil(sel.getMaxX());
        int py = (int) Math.ceil(sel.getMaxY());
        drag(px, py, (int) Math.round(ax + 2 * (px - ax)), (int) Math.round(ay + 2 * (py - ay)));
        float ratio = canvas.getAnswers().get(0).getFontSize() / a.getFontSize();
        assertTrue(ratio > 1.8 && ratio < 2.2, "ratio " + ratio);
        assertTrue(changed.isEmpty());
    }

    @Test
    void eraserRemovesAWholeAnswer() {
        writeOneEquals();
        CanvasAnswer a = answer(AnswerKind.AFTER_EQUALS, "2");
        canvas.setTool(DrawingCanvas.Tool.ERASER);
        int cx = (int) a.getBox().getCenterX();
        drag(cx + 4, 110, cx + 4, 130);
        assertTrue(canvas.getAnswers().isEmpty());
        assertTrue(canvas.getFormulas().isEmpty());
        assertEquals(3, canvas.getStrokes().size());
        canvas.undo();
        assertEquals(1, canvas.getAnswers().size());
    }

    @Test
    void selectionOfOnlyAnswersFallsBackToUnboundInk() {
        selectAnswer();
        canvas.setTool(DrawingCanvas.Tool.PEN);
        drag(100, 300, 140, 330);
        lasso(176, 90, 260, 150);
        assertTrue(canvas.hasSelection());
        FormulaCapture c = calculateOne();
        assertEquals(30 + 1 + 2 * 18, c.getImage().getHeight());
        canvas.getFormulas().forEach(f -> assertFalse(f.getStrokes().isEmpty()));
        assertEquals(2, canvas.getFormulas().size());
    }

    @Test
    void undoRestoresADeletedAnswer() {
        selectAnswer();
        canvas.deleteSelection();
        canvas.undo();
        assertEquals(1, canvas.getAnswers().size());
        assertEquals(1, canvas.getFormulas().size());
    }

    // ---- review fixes

    @Test
    void undoToAStateWithoutTheAnswerAsksForARecalc() {
        writeOneEquals();
        FormulaCapture c = calculateOne();
        drag(100, 400, 140, 440);  // far away, while the calc runs
        assertTrue(canvas.showAnswer(c.getId(), c.getRevision(), AnswerKind.AFTER_EQUALS, "2", false));
        canvas.undo();
        assertEquals(List.of(c.getId()), changed, "unanswered formula is sent for recalc");
        assertTrue(canvas.captureForRecalculation(c.getId()) != null);
    }

    @Test
    void answerArrivingMidStrokeDoesNotPanUnderThePen() {
        writeOneEquals();
        FormulaCapture c = calculateOne();
        canvas.panBy(-2000, 0);  // formula far off screen
        send(MouseEvent.MOUSE_PRESSED, 300, 300);
        send(MouseEvent.MOUSE_DRAGGED, 310, 300);
        assertTrue(canvas.showAnswer(c.getId(), c.getRevision(), AnswerKind.AFTER_EQUALS, "2", false));
        send(MouseEvent.MOUSE_DRAGGED, 320, 300);
        send(MouseEvent.MOUSE_RELEASED, 320, 300);
        List<entity.InkPoint> pts = canvas.getStrokes().get(canvas.getStrokes().size() - 1).getPoints();
        for (int i = 1; i < pts.size(); i++) {
            assertTrue(Math.abs(pts.get(i).getX() - pts.get(i - 1).getX()) <= 10, "jump at point " + i);
        }
    }
}
