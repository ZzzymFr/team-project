package view.answer;

import entity.Stroke;
import interface_adapter.AnswerKind;
import interface_adapter.TextRun;

import java.awt.geom.Point2D;
import java.awt.geom.Rectangle2D;
import java.util.List;
import java.util.function.Predicate;

// where an answer lands + how big, from the formula's ink
public final class AnswerPlacer {
    private AnswerPlacer() {
    }

    // runs null = plain text; below falls back to the right if it would hit other ink
    public static CanvasAnswer place(List<Stroke> ink, AnswerKind kind, String text, List<TextRun> runs, boolean error,
                                     double penWidth, Predicate<Rectangle2D> hitsOtherInk) {
        double h = AnswerLayout.charHeight(ink);
        float size = AnswerInk.sizeFor(h);
        Rectangle2D text2d = AnswerInk.bounds(text, runs, size, penWidth);
        double w = text2d.getWidth();
        double ht = text2d.getHeight();
        Point2D at = kind.isBelow() ? AnswerLayout.below(ink, h) : AnswerLayout.rightOf(ink, h, ht);
        if (kind.isBelow() && hitsOtherInk.test(new Rectangle2D.Double(at.getX(), at.getY(), w, ht))) {
            at = AnswerLayout.rightOf(ink, h, ht);
        }
        return runs == null ? new CanvasAnswer(text, error, at.getX(), at.getY(), w, ht, size)
                : new CanvasAnswer(runs, error, at.getX(), at.getY(), w, ht, size);
    }
}
