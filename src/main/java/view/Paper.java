package view;

import java.awt.Rectangle;

/**
 * The A4 sheets the canvas writes on, like the pages of a Word document: portrait, stacked top to
 * bottom with a gap between them, the first one's top-left corner at world (0, 0).
 *
 * <p>World units are screen pixels at 100%, so a sheet is A4 at 96 dpi. There is always one blank
 * sheet after the last one with ink on it, so there is room to keep writing.
 */
public final class Paper {
    /** A4 (210 mm) at 96 dpi. */
    public static final int PAGE_WIDTH = 794;
    /** A4 (297 mm) at 96 dpi. */
    public static final int PAGE_HEIGHT = 1123;
    /** Space between two sheets. */
    public static final int PAGE_GAP = 24;

    private static final int PITCH = PAGE_HEIGHT + PAGE_GAP;

    private Paper() {
    }

    /** Sheet {@code index} (0 = the first) in world coordinates. */
    public static Rectangle page(int index) {
        return new Rectangle(0, index * PITCH, PAGE_WIDTH, PAGE_HEIGHT);
    }

    /** How tall {@code pageCount} sheets are, stacked with their gaps. */
    public static int stackHeight(int pageCount) {
        return pageCount * PITCH - PAGE_GAP;
    }

    /** How many sheets there are: up to the last one the ink reaches, plus a blank one; 1 when there is no ink. */
    public static int pageCount(Rectangle ink) {
        if (ink == null) {
            return 1;
        }
        int last = (int) Math.floor((double) (ink.y + ink.height) / PITCH);
        return Math.max(0, last) + 2;
    }

    /** The sheet under a world point, among the first {@code pageCount}, or -1 off the paper (in a gap or beside it). */
    public static int pageAt(int x, int y, int pageCount) {
        if (y < 0) {
            return -1;
        }
        int index = y / PITCH;
        return index < pageCount && page(index).contains(x, y) ? index : -1;
    }

    /** The sheet a box belongs on: the one its centre is over (the one above, in a gap), among the first {@code pageCount}. */
    public static int pageFor(Rectangle box, int pageCount) {
        int index = (int) Math.floor((box.getCenterY() + PAGE_GAP / 2.0) / PITCH);
        return Math.max(0, Math.min(pageCount - 1, index));
    }

    /**
     * The move nearest to (dx, dy) that leaves {@code box} on one sheet: the one it lands over, so
     * it can be dragged from sheet to sheet but never off the paper or into a gap.
     */
    public static int[] keepOnPaper(Rectangle box, int dx, int dy, int pageCount) {
        Rectangle moved = new Rectangle(box.x + dx, box.y + dy, box.width, box.height);
        Rectangle sheet = page(pageFor(moved, pageCount));
        return new int[]{
            fit(moved.x, moved.width, sheet.x, sheet.width) - box.x,
            fit(moved.y, moved.height, sheet.y, sheet.height) - box.y,
        };
    }

    /**
     * The resize nearest to {@code factor} that keeps {@code box}, scaled around (ax, ay), on its
     * sheet. A box that is not on one sheet to begin with is left as asked.
     */
    public static double keepOnPaper(Rectangle box, double ax, double ay, double factor, int pageCount) {
        Rectangle sheet = page(pageFor(box, pageCount));
        double maxX = sheet.x + sheet.width - 1;
        double maxY = sheet.y + sheet.height - 1;
        if (!sheet.contains(box.x, box.y) || box.x + box.width > maxX || box.y + box.height > maxY) {
            return factor;
        }
        double[] range = {0, Double.POSITIVE_INFINITY};
        limit(range, box.x - ax, sheet.x - ax, maxX - ax);
        limit(range, box.x + box.width - ax, sheet.x - ax, maxX - ax);
        limit(range, box.y - ay, sheet.y - ay, maxY - ay);
        limit(range, box.y + box.height - ay, sheet.y - ay, maxY - ay);
        return Math.max(range[0], Math.min(range[1], factor));
    }

    // narrows [lo, hi] to the factors f with low <= d * f <= high
    private static void limit(double[] range, double d, double low, double high) {
        if (d > 0) {
            range[0] = Math.max(range[0], low / d);
            range[1] = Math.min(range[1], high / d);
        } else if (d < 0) {
            range[0] = Math.max(range[0], high / d);
            range[1] = Math.min(range[1], low / d);
        }
    }

    // where a span starting at start goes to lie in [from, from + length - 1]; its start if it is too long
    private static int fit(int start, int span, int from, int length) {
        int last = from + length - 1 - span;
        return Math.max(from, Math.min(last, start));
    }

    /** The nearest point to (x, y) on sheet {@code index}. */
    public static int[] clampTo(int index, int x, int y) {
        Rectangle p = page(index);
        return new int[]{
            Math.max(p.x, Math.min(p.x + p.width - 1, x)),
            Math.max(p.y, Math.min(p.y + p.height - 1, y)),
        };
    }
}
