package view.answer;

import entity.Stroke;
import org.scilab.forge.jlatexmath.TeXConstants;
import org.scilab.forge.jlatexmath.TeXFormula;

import java.awt.Rectangle;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

// where a formula's "=" is in the ink, hovering there brings the popup back
public final class EqualsSpot {
    private static final double FLAT = 0.35;      // a bar is at most this tall (x char height)
    private static final double MIN_BAR = 0.25;   // and at least this wide
    private static final double MIN_GAP = 0.08;   // bars' centers this far apart, up to
    private static final double MAX_GAP = 0.8;
    private static final double PAD = 0.3;
    private static final float TEX_SIZE = 20f;

    private EqualsSpot() {
    }

    // world box, null if no ink; latex null = not read yet
    public static Rectangle find(Collection<Stroke> strokes, double charHeight, String latex) {
        Rectangle all = AnswerLayout.bounds(strokes);
        if (all == null) {
            return null;
        }
        int at = latex == null ? -1 : lastEquals(latex);
        if (latex != null && at < 0) {
            return all;  // no "=" at all, whole formula
        }
        double guess = at < 0 ? Double.NaN : all.x + all.width * fractionAt(latex, at);
        Rectangle pair = closest(pairs(strokes, charHeight), guess);
        if (pair != null) {
            return padded(pair, charHeight);
        }
        if (!Double.isNaN(guess)) {
            int w = (int) Math.round(charHeight);
            return new Rectangle((int) Math.round(guess) - w / 2, all.y, w, all.height);
        }
        return all;
    }

    // is there an "=" yet: the read says so, or the ink if it wasn't read
    public static boolean written(Collection<Stroke> strokes, double charHeight, String latex) {
        return latex != null ? lastEquals(latex) >= 0 : !pairs(strokes, charHeight).isEmpty();
    }

    // last "=" outside any {...}, -1 if none
    static int lastEquals(String latex) {
        int depth = 0;
        int found = -1;
        for (int i = 0; i < latex.length(); i++) {
            char c = latex.charAt(i);
            boolean escaped = i > 0 && latex.charAt(i - 1) == '\\';
            if (c == '{' && !escaped) {
                depth++;
            } else if (c == '}' && !escaped) {
                depth--;
            } else if (c == '=' && depth == 0) {
                found = i;
            }
        }
        return found;
    }

    // how far along the typeset formula the "=" sits, 0..1; NaN if it won't typeset
    static double fractionAt(String latex, int at) {
        try {
            double before = width(latex.substring(0, at));
            double through = width(latex.substring(0, at + 1));
            double full = width(latex);
            if (full <= 0) {
                return Double.NaN;
            }
            return Math.max(0, Math.min(1, (before + through) / 2 / full));
        } catch (RuntimeException | Error bad) {
            return Double.NaN;
        }
    }

    private static double width(String latex) {
        if (latex.isBlank()) {
            return 0;
        }
        return new TeXFormula(latex).createTeXIcon(TeXConstants.STYLE_DISPLAY, TEX_SIZE).getIconWidth();
    }

    // two flat strokes stacked close, roughly same span = an "="
    static List<Rectangle> pairs(Collection<Stroke> strokes, double h) {
        List<Rectangle> bars = new ArrayList<>();
        for (Stroke s : strokes) {
            Rectangle b = AnswerLayout.bounds(List.of(s));
            if (b != null && b.height <= FLAT * h && b.width >= MIN_BAR * h && b.width >= 1.5 * b.height) {
                bars.add(b);
            }
        }
        List<Rectangle> found = new ArrayList<>();
        for (int i = 0; i < bars.size(); i++) {
            for (int j = i + 1; j < bars.size(); j++) {
                Rectangle a = bars.get(i);
                Rectangle b = bars.get(j);
                double overlap = Math.min(a.getMaxX(), b.getMaxX()) - Math.max(a.x, b.x);
                double dy = Math.abs(a.getCenterY() - b.getCenterY());
                double ratio = (double) Math.max(a.width, b.width) / Math.max(1, Math.min(a.width, b.width));
                if (overlap >= 0.5 * Math.min(a.width, b.width) && dy >= MIN_GAP * h && dy <= MAX_GAP * h
                        && ratio <= 3) {
                    found.add(a.union(b));
                }
            }
        }
        return found;
    }

    // nearest to the guess, rightmost if no guess
    private static Rectangle closest(List<Rectangle> pairs, double guess) {
        Rectangle best = null;
        for (Rectangle p : pairs) {
            if (best == null || (Double.isNaN(guess) ? p.getCenterX() > best.getCenterX()
                    : Math.abs(p.getCenterX() - guess) < Math.abs(best.getCenterX() - guess))) {
                best = p;
            }
        }
        return best;
    }

    // a bit bigger, at least a char tall so it's easy to hit
    private static Rectangle padded(Rectangle pair, double h) {
        int padX = (int) Math.round(PAD * h);
        int half = (int) Math.round(Math.max(pair.height / 2.0 + PAD * h, h / 2));
        int cy = (int) Math.round(pair.getCenterY());
        return new Rectangle(pair.x - padX, cy - half, pair.width + 2 * padX, 2 * half);
    }
}
