package view.canvas;

import view.DrawingCanvas;
import view.glass.SlimScrollBarUI;

import javax.swing.JScrollBar;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;

/**
 * The scroll bar at the right edge of the window, like Word's: its thumb shows which part of the
 * paper is in view, and dragging it (or clicking above or below it) scrolls the paper up and down.
 * It follows the view however it moves, by the wheel, by dragging the canvas or by zooming.
 */
final class PageScrollBar {
    static final int WIDTH = 14;

    private final DrawingCanvas canvas;
    private final JScrollBar bar = new JScrollBar(JScrollBar.VERTICAL);
    private boolean following;  // setting the bar from the view, not the other way round

    PageScrollBar(SharedCanvas shared) {
        canvas = shared.getDrawingCanvas();
        bar.setUI(new SlimScrollBarUI());
        bar.setOpaque(false);
        bar.setFocusable(false);
        bar.setUnitIncrement(40);
        bar.addAdjustmentListener(e -> {
            if (!following) {
                canvas.setScrollY(e.getValue());
            }
        });
        canvas.addViewListener(this::follow);
        shared.addStrokeListener(stroke -> follow());  // new ink can add a sheet
        shared.addZoomListener(zoom -> follow());
        canvas.addComponentListener(new ComponentAdapter() {
            @Override
            public void componentResized(ComponentEvent e) {
                follow();
            }
        });
        follow();
    }

    /** Sets the thumb from the view; hidden when all the paper fits. */
    void follow() {
        int extent = Math.max(1, canvas.getScrollExtentY());
        int max = canvas.getScrollMaxY();
        following = true;
        try {
            bar.setValues(canvas.getScrollY(), extent, 0, max + extent);
            bar.setBlockIncrement(Math.max(40, extent - 40));
        } finally {
            following = false;
        }
        bar.setVisible(max > 0);
    }

    JScrollBar getBar() {
        return bar;
    }
}
