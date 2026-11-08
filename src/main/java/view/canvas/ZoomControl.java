package view.canvas;

import view.DrawingCanvas;
import view.glass.GlassDock;
import view.glass.GlassSlider;
import view.glass.GlassTextField;

import javax.swing.AbstractAction;
import javax.swing.JComponent;
import javax.swing.KeyStroke;
import javax.swing.SwingConstants;
import java.awt.Container;
import java.awt.Dimension;
import java.awt.event.ActionEvent;
import java.awt.event.FocusAdapter;
import java.awt.event.FocusEvent;

/**
 * The zoom at the bottom right of the window: a slider from {@value DrawingCanvas#MIN_ZOOM}x to
 * {@value DrawingCanvas#MAX_ZOOM}x, with 100% in the middle (the scale is logarithmic, so zooming
 * in and out feel the same), and beside it the zoom in percent, which can be typed over. Both follow
 * Ctrl+scroll. Like the tools, it belongs to the shared canvas, so every page shows the same one.
 */
final class ZoomControl {
    private static final int SLIDER_WIDTH = 168;
    private static final int HEIGHT = 40;
    private static final int RIGHT = 22;   // distance from the right edge of the window
    private static final int BOTTOM = 26;  // distance from the bottom of the window

    private static final double LOG_MIN = Math.log(DrawingCanvas.MIN_ZOOM);
    private static final double LOG_MAX = Math.log(DrawingCanvas.MAX_ZOOM);

    private final SharedCanvas canvas;
    private final GlassSlider slider = new GlassSlider(SLIDER_WIDTH, HEIGHT, toFraction(1));
    private final GlassTextField field = new GlassTextField(4, null);
    private final GlassDock dock = new GlassDock();

    ZoomControl(SharedCanvas canvas) {
        this.canvas = canvas;
        slider.setToolTipText("Drag to zoom · double-click for 100% · Ctrl+scroll on the paper also zooms");
        slider.addValueListener(fraction -> canvas.setZoom(toZoom(fraction)));
        field.setHorizontalAlignment(SwingConstants.CENTER);
        field.setToolTipText("Type a zoom in percent (25 to 400) and press Enter");
        field.addActionListener(e -> {
            commit();
            canvas.getDrawingCanvas().requestFocusInWindow();  // back to writing
        });
        field.addFocusListener(new FocusAdapter() {
            @Override
            public void focusLost(FocusEvent e) {
                commit();
            }
        });
        field.getInputMap(JComponent.WHEN_FOCUSED).put(KeyStroke.getKeyStroke("ESCAPE"), "revertZoom");
        field.getActionMap().put("revertZoom", new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) {
                show(canvas.getZoom());
                canvas.getDrawingCanvas().requestFocusInWindow();
            }
        });
        canvas.addZoomListener(this::show);
        show(canvas.getZoom());

        dock.add(slider);
        dock.add(field);
        dock.setBackdrop(canvas);
    }

    /** The slider position (0..1) for a zoom. */
    static double toFraction(double zoom) {
        return (Math.log(zoom) - LOG_MIN) / (LOG_MAX - LOG_MIN);
    }

    /** The zoom for a slider position (0..1). */
    static double toZoom(double fraction) {
        return Math.exp(LOG_MIN + fraction * (LOG_MAX - LOG_MIN));
    }

    /** The zoom typed in the field ("150", "150%", " 75 % "), or null if it is not a positive number. */
    static Double parsePercent(String text) {
        String t = text.trim();
        if (t.endsWith("%")) {
            t = t.substring(0, t.length() - 1).trim();
        }
        try {
            double percent = Double.parseDouble(t);
            return Double.isFinite(percent) && percent > 0 ? percent / 100 : null;
        } catch (NumberFormatException e) {
            return null;
        }
    }

    static String percent(double zoom) {
        return Math.round(zoom * 100) + "%";
    }

    /** Zooms to what was typed (kept within the limits); anything else just shows the zoom again. */
    private void commit() {
        Double zoom = parsePercent(field.getText());
        if (zoom != null) {
            canvas.setZoom(zoom);
        }
        show(canvas.getZoom());
    }

    private void show(double zoom) {
        slider.setValue(toFraction(zoom));
        String text = percent(zoom);
        if (!text.equals(field.getText())) {
            field.setText(text);
        }
    }

    /** Adds the control to the parent, on top of whatever it adds later. */
    void addTo(Container parent) {
        parent.add(dock);
    }

    /**
     * Puts the control at the bottom right, level with the tools; if the window is too narrow for
     * both side by side, it goes just above the tools' right end instead.
     *
     * @param tools where the tools' capsule is, in the parent's coordinates
     */
    /** Puts the zoom at the bottom right of a window of this size. */
    void layout(int width, int height) {
        Dimension size = dock.getPreferredSize();
        dock.setBounds(width - RIGHT - size.width, height - BOTTOM - size.height, size.width, size.height);
    }

    GlassSlider getSlider() {
        return slider;
    }

    GlassTextField getField() {
        return field;
    }

    JComponent getDock() {
        return dock;
    }
}
