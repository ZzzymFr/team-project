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
                                     Predicate<Rectangle2D> hitsOtherInk) {
        return place(ink, kind, text, runs, error, hitsOtherInk, box -> true);
    }

    // same, but a spot that does not fit (e.g. runs off the paper) gives way to the other one
    // (right <-> below), as long as below stays clear of other ink
    public static CanvasAnswer place(List<Stroke> ink, AnswerKind kind, String text, List<TextRun> runs, boolean error,
                                     Predicate<Rectangle2D> hitsOtherInk, Predicate<Rectangle2D> fits) {
        double h = AnswerLayout.charHeight(ink);
        float size = AnswerFont.sizeFor(h);
        Rectangle2D text2d = runs == null ? AnswerFont.visualBounds(text, size) : AnswerFont.visualBounds(runs, size);
        double w = text2d.getWidth();
        double ht = text2d.getHeight();
        Point2D below = AnswerLayout.below(ink, h);
        Point2D right = AnswerLayout.rightOf(ink, h, ht);
        Rectangle2D belowBox = new Rectangle2D.Double(below.getX(), below.getY(), w, ht);
        Rectangle2D rightBox = new Rectangle2D.Double(right.getX(), right.getY(), w, ht);
        boolean belowClear = !hitsOtherInk.test(belowBox);
        Point2D at;
        if (kind.isBelow()) {
            if (belowClear && fits.test(belowBox)) {
                at = below;
            } else if (fits.test(rightBox)) {
                at = right;
            } else {
                at = belowClear ? below : right;
            }
        } else {
            // no room on the right: below, even if the caller must still slide it sideways to fit,
            // since a right-hand answer slid back would land on the formula
            at = fits.test(rightBox) || !belowClear ? right : below;
        }
        return runs == null ? new CanvasAnswer(text, error, at.getX(), at.getY(), w, ht, size)
                : new CanvasAnswer(runs, error, at.getX(), at.getY(), w, ht, size);
    }
}
