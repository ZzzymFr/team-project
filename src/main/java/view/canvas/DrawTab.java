package view.canvas;

import view.DrawingCanvas;
import view.glass.Glass;
import view.glass.GlassBar;
import view.glass.GlassDock;
import view.glass.GlassIcons;
import view.glass.GlassLabel;
import view.ribbon.Ribbon;
import view.ribbon.RibbonButton;

import javax.swing.JColorChooser;
import javax.swing.SwingUtilities;
import java.awt.Color;
import java.awt.Container;
import java.awt.Dimension;
import java.awt.Rectangle;
import java.util.ArrayList;
import java.util.List;

/**
 * The Draw tab of the ribbon: Pen / Eraser / Lasso, the eraser's two modes, the ink colours, Undo
 * and Clear. The tools only work while this tab is chosen; on any other tab the canvas is a plain
 * pointer that leaves no ink. While drawing, a hint at the bottom of the window says what the tool
 * does. The tools belong to the shared canvas, so every page shows the same ones.
 */
final class DrawTab {
    private static final int TOOL_GROUP = 1;
    private static final int COLOR_GROUP = 2;
    private static final int ERASER_MODE_GROUP = 3;
    private static final int BOTTOM = 22;  // distance of the hint from the bottom of the window
    private static final int GAP = 12;     // kept between the hint and the zoom

    private final SharedCanvas canvas;
    private final GlassLabel hint = new GlassLabel("");
    private final GlassDock hintDock = new GlassDock();
    private final List<GlassBar.Item> toolItems = new ArrayList<>();
    private final List<GlassBar.Item> eraserModeItems = new ArrayList<>();
    private Ribbon.Tab tab;
    private GlassBar bar;
    private RibbonButton undoButton;
    private RibbonButton clearButton;

    DrawTab(SharedCanvas canvas) {
        this.canvas = canvas;
        hintDock.add(hint);
        hintDock.setBackdrop(canvas);
        hintDock.setVisible(false);
        hint.setText(hintFor(canvas.getTool(), canvas.getEraserMode()));
        hint.setForeground(Glass.TEXT_MUTED);
    }

    /** Adds the hint to the parent, on top of whatever it adds later. */
    void addTo(Container parent) {
        parent.add(hintDock);
    }

    /**
     * Adds the Draw tab to the ribbon. From then on the canvas draws only while the tab is chosen.
     * Can be called once.
     */
    Ribbon.Tab addTo(Ribbon ribbon) {
        if (tab != null) {
            throw new IllegalStateException("The Draw tab is already on a ribbon");
        }
        tab = ribbon.addTab("Draw", () -> true);
        bar = tab.getBar();
        buildTools();
        canvas.addToolListener(tool -> {
            bar.select(toolItems.get(tool.ordinal()));
            hint.setText(hintFor(tool, canvas.getEraserMode()));
            bar.repaint();
        });
        canvas.addEraserModeListener(mode -> {
            bar.select(eraserModeItems.get(mode.ordinal()));
            hint.setText(hintFor(canvas.getTool(), mode));
            bar.repaint();
        });
        ribbon.addTabListener(chosen -> drawingChanged());
        drawingChanged();
        return tab;
    }

    private void buildTools() {
        toolItems.add(toolSegment("Pen", GlassIcons::pencil, DrawingCanvas.Tool.PEN));
        toolItems.add(toolSegment("Eraser", GlassIcons::eraser, DrawingCanvas.Tool.ERASER));
        toolItems.add(toolSegment("Lasso", GlassIcons::lasso, DrawingCanvas.Tool.LASSO));
        bar.separator();
        for (DrawingCanvas.EraserMode mode : DrawingCanvas.EraserMode.values()) {
            String label = mode == DrawingCanvas.EraserMode.PRECISE ? "Precise" : "Stroke";
            // picking a mode takes up the eraser too
            eraserModeItems.add(bar.segment(label, null, ERASER_MODE_GROUP, () -> {
                canvas.setEraserMode(mode);
                canvas.setTool(DrawingCanvas.Tool.ERASER);
                return true;
            }));
        }
        bar.separator();
        GlassBar.Item firstColor = null;
        for (Color color : Glass.inkColors()) {
            GlassBar.Item swatch = bar.swatch(color, COLOR_GROUP, () -> {
                useColor(color);
                return true;
            });
            firstColor = firstColor == null ? swatch : firstColor;
        }
        GlassBar.Item[] custom = new GlassBar.Item[1];
        custom[0] = bar.customSwatch(COLOR_GROUP, () -> {
            Color chosen = JColorChooser.showDialog(SwingUtilities.getWindowAncestor(bar), "Choose Color",
                    canvas.getInkColor());
            if (chosen == null) {
                return false;  // cancelled: keep the previous selection
            }
            custom[0].setColor(chosen);
            useColor(chosen);
            return true;
        });
        bar.finish();
        tab.addSeparator();
        undoButton = tab.addButton("Undo", GlassIcons::undo, canvas::undo, canvas::canUndo);
        clearButton = tab.addButton("Clear", GlassIcons::trash, canvas::clear, () -> !canvas.isBlank());
        bar.select(toolItems.get(canvas.getTool().ordinal()));
        bar.select(eraserModeItems.get(canvas.getEraserMode().ordinal()));
        bar.select(firstColor);
        canvas.setInkColor(firstColor.getColor());
    }

    private GlassBar.Item toolSegment(String label, GlassIcons.Glyph icon, DrawingCanvas.Tool tool) {
        return bar.segment(label, icon, TOOL_GROUP, () -> {
            canvas.setTool(tool);
            return true;
        });
    }

    /** Picking a colour while holding the eraser or lasso goes back to the pen. */
    private void useColor(Color color) {
        canvas.setInkColor(color);
        canvas.setTool(DrawingCanvas.Tool.PEN);
    }

    /** The tools work only while the tab is chosen; the hint shows only then. */
    private void drawingChanged() {
        boolean drawing = tab.isSelected();
        canvas.setDrawingEnabled(drawing);
        hintDock.setVisible(drawing);
    }

    private static String hintFor(DrawingCanvas.Tool tool, DrawingCanvas.EraserMode eraserMode) {
        switch (tool) {
            case PEN:
                return "Write on the paper · circle a formula with the Lasso, then use the Math tab · scroll or right-drag to move";
            case ERASER:
                return eraserMode == DrawingCanvas.EraserMode.PRECISE
                        ? "Drag over the ink to erase just that part."
                        : "Touch a stroke to erase all of it.";
            default:
                return "Circle a formula, then open the Math tab · click an item on the paper to move it"
                        + " · drag a corner to resize · Delete to remove";
        }
    }

    /**
     * Puts the hint at the bottom middle of a window of this size, or above the zoom when the two
     * would overlap.
     */
    void layout(int width, int height, Rectangle zoom) {
        Dimension size = hintDock.getPreferredSize();
        int x = (width - size.width) / 2;
        int y = height - BOTTOM - size.height;
        if (zoom != null && x + size.width + GAP > zoom.x) {
            y = Math.min(y, zoom.y - GAP - size.height);
        }
        hintDock.setBounds(x, y, size.width, size.height);
    }

    Ribbon.Tab getTab() {
        return tab;
    }

    GlassBar getBar() {
        return bar;
    }

    boolean isHintShown() {
        return hintDock.isVisible();
    }

    String currentHint() {
        return hint.getText();
    }

    GlassBar.Item toolItem(DrawingCanvas.Tool tool) {
        return toolItems.get(tool.ordinal());
    }

    GlassBar.Item eraserModeItem(DrawingCanvas.EraserMode mode) {
        return eraserModeItems.get(mode.ordinal());
    }

    RibbonButton undoButton() {
        return undoButton;
    }

    RibbonButton clearButton() {
        return clearButton;
    }
}
