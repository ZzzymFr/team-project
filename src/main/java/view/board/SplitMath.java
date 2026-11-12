package view.board;

// panel vs canvas widths for the side panels
public final class SplitMath {
    public static final double DEFAULT_PANEL = 0.4;
    public static final int MIN_CANVAS = 320;
    public static final int MIN_PANEL = 280;

    private SplitMath() {
    }

    // panel width for a ratio, kept inside the minimums when the window allows it
    public static int panelWidth(double ratio, int total) {
        int w = (int) Math.round(ratio * total);
        int max = total - MIN_CANVAS;
        if (max < MIN_PANEL) {
            return Math.max(0, Math.min(total, (int) Math.round(DEFAULT_PANEL * total)));  // tiny window
        }
        return Math.max(MIN_PANEL, Math.min(max, w));
    }

    // ratio after dragging the panel to this width (stops at the minimums)
    public static double ratioFor(int panelWidth, int total) {
        if (total <= 0) {
            return DEFAULT_PANEL;
        }
        return panelWidth(panelWidth / (double) total, total) / (double) total;
    }
}
