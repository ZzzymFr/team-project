package view.board;

import java.awt.Point;

// where the toolbar docks: the middle of one edge of the canvas area
public enum ToolbarSpot {
    BOTTOM, TOP, LEFT, RIGHT;

    // the top edge starts under the History / PDF capsules
    public static final int TOP_Y = BoardTheme.TOP + GlassBar.CAP_H + 12;

    // left / right stand the bar up
    public boolean isVertical() {
        return this == LEFT || this == RIGHT;
    }

    // room along the edge for the capsule's length
    public int room(int areaW, int areaH) {
        if (isVertical()) {
            return areaH - TOP_Y - BoardTheme.BOTTOM;
        }
        return areaW - 2 * BoardTheme.SIDE;
    }

    // capsule top-left; len = along the bar, thick = across it
    public Point capsuleAt(int areaW, int areaH, int len, int thick) {
        switch (this) {
            case TOP:
                return new Point((areaW - len) / 2, TOP_Y);
            case LEFT:
                return new Point(BoardTheme.SIDE, middle(areaH, len));
            case RIGHT:
                return new Point(areaW - BoardTheme.SIDE - thick, middle(areaH, len));
            default:
                return new Point((areaW - len) / 2, areaH - BoardTheme.BOTTOM - thick);
        }
    }

    // centred between the top capsules and the bottom
    private static int middle(int areaH, int len) {
        int top = TOP_Y;
        int bottom = areaH - BoardTheme.BOTTOM;
        return Math.max(top, top + (bottom - top - len) / 2);
    }

    // the edge closest to where the bar's centre is
    public static ToolbarSpot nearest(Point center, int areaW, int areaH) {
        double top = center.y - TOP_Y;
        double bottom = areaH - center.y;
        double left = center.x;
        double right = areaW - center.x;
        double min = Math.min(Math.min(top, bottom), Math.min(left, right));
        if (min == bottom) {
            return BOTTOM;
        }
        if (min == top) {
            return TOP;
        }
        return min == left ? LEFT : RIGHT;
    }
}
