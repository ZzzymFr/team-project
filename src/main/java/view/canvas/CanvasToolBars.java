package view.canvas;

import view.DrawingCanvas;
import view.glass.Glass;
import view.glass.GlassBar;
import view.glass.GlassDock;
import view.glass.GlassIcons;
import view.glass.GlassLabel;
import view.stack.Fonts;

import javax.swing.JColorChooser;
import javax.swing.SwingUtilities;
import java.awt.Color;
import java.awt.Container;
import java.awt.Dimension;
import java.awt.Font;
import java.util.ArrayList;
import java.util.List;

/**
 * The tools at the bottom of the window: Pen / Eraser / Lasso, the ink colours, Undo, Clear and
 * Export PDF. While the eraser is on, a small bar with its modes floats above them; a hint above those
 * says what the tool does. The tools belong to the shared canvas, so every page shows the same ones.
 */
final class CanvasToolBars {
    private static final int TOOL_GROUP = 1;
    private static final int COLOR_GROUP = 2;
    private static final int ERASER_MODE_GROUP = 3;
    private static final int BOTTOM = 22;  // distance of the tools from the bottom of the window
    private static final int GAP = 6;      // between the floating rows

    private final SharedCanvas canvas;
    private final GlassBar tools;
    private final GlassBar eraserModes;
    private final GlassLabel hint = new GlassLabel("");
    private final GlassDock hintDock = new GlassDock();
    private final List<GlassBar.Item> toolItems = new ArrayList<>();
    private final List<GlassBar.Item> eraserModeItems = new ArrayList<>();
    private GlassBar.Item undoItem;
    private GlassBar.Item clearItem;
    private GlassBar.Item exportItem;

    CanvasToolBars(SharedCanvas canvas, Runnable exportPdf) {
        this.canvas = canvas;
        Font font = Fonts.ui(Font.PLAIN, 14f);
        tools = new GlassBar(canvas, font);
        eraserModes = new GlassBar(canvas, font);
        buildTools(exportPdf);
        buildEraserModes();
        hintDock.add(hint);
        hintDock.setBackdrop(canvas);
        hint.setText(hintFor(canvas.getTool(), canvas.getEraserMode()));
        hint.setForeground(Glass.TEXT_MUTED);

        canvas.addToolListener(tool -> {
            tools.select(toolItems.get(tool.ordinal()));
            eraserModes.setShown(tool == DrawingCanvas.Tool.ERASER);
            hint.setText(hintFor(tool, canvas.getEraserMode()));
        });
        canvas.addEraserModeListener(mode -> {
            eraserModes.select(eraserModeItems.get(mode.ordinal()));
            hint.setText(hintFor(canvas.getTool(), mode));
        });
    }

    private void buildTools(Runnable exportPdf) {
        toolItems.add(toolSegment("Pen", GlassIcons::pencil, DrawingCanvas.Tool.PEN));
        toolItems.add(toolSegment("Eraser", GlassIcons::eraser, DrawingCanvas.Tool.ERASER));
        toolItems.add(toolSegment("Lasso", GlassIcons::lasso, DrawingCanvas.Tool.LASSO));
        tools.separator();
        GlassBar.Item firstColor = null;
        for (Color color : Glass.inkColors()) {
            GlassBar.Item swatch = tools.swatch(color, COLOR_GROUP, () -> {
                useColor(color);
                return true;
            });
            firstColor = firstColor == null ? swatch : firstColor;
        }
        GlassBar.Item[] custom = new GlassBar.Item[1];
        custom[0] = tools.customSwatch(COLOR_GROUP, () -> {
            Color chosen = JColorChooser.showDialog(SwingUtilities.getWindowAncestor(tools), "Choose Color",
                    canvas.getInkColor());
            if (chosen == null) {
                return false;  // cancelled: keep the previous selection
            }
            custom[0].setColor(chosen);
            useColor(chosen);
            return true;
        });
        tools.separator();
        undoItem = tools.action("Undo", GlassIcons::undo, canvas::undo, canvas::canUndo);
        clearItem = tools.action("Clear", GlassIcons::trash, canvas::clear, () -> !canvas.isBlank());
        exportItem = tools.action("Export PDF", GlassIcons::document, exportPdf, () -> true);
        tools.finish();
        tools.select(toolItems.get(canvas.getTool().ordinal()));
        tools.select(firstColor);
        canvas.setInkColor(firstColor.getColor());
    }

    private GlassBar.Item toolSegment(String label, GlassIcons.Glyph icon, DrawingCanvas.Tool tool) {
        return tools.segment(label, icon, TOOL_GROUP, () -> {
            canvas.setTool(tool);
            return true;
        });
    }

    private void buildEraserModes() {
        for (DrawingCanvas.EraserMode mode : DrawingCanvas.EraserMode.values()) {
            String label = mode == DrawingCanvas.EraserMode.PRECISE ? "Precise" : "Stroke";
            eraserModeItems.add(eraserModes.segment(label, null, ERASER_MODE_GROUP, () -> {
                canvas.setEraserMode(mode);
                return true;
            }));
        }
        eraserModes.finish();
        eraserModes.select(eraserModeItems.get(canvas.getEraserMode().ordinal()));
        eraserModes.setShownNow(canvas.getTool() == DrawingCanvas.Tool.ERASER);
    }

    /** Picking a colour while holding the eraser or lasso goes back to the pen. */
    private void useColor(Color color) {
        canvas.setInkColor(color);
        canvas.setTool(DrawingCanvas.Tool.PEN);
    }

    private static String hintFor(DrawingCanvas.Tool tool, DrawingCanvas.EraserMode eraserMode) {
        switch (tool) {
            case PEN:
                return "Write, then pause to graph or calculate · scroll or right-drag to move · Ctrl+scroll to zoom";
            case ERASER:
                return eraserMode == DrawingCanvas.EraserMode.PRECISE
                        ? "Drag over the ink to erase just that part."
                        : "Touch a stroke to erase all of it.";
            default:
                return "Circle ink to graph or calculate it · drag to move · drag a corner to resize · Delete to remove";
        }
    }

    /** Adds the bars to the parent, on top of whatever it adds later. */
    void addTo(Container parent) {
        parent.add(tools);
        parent.add(eraserModes);
        parent.add(hintDock);
    }

    /** Stacks the rows up from the bottom of a window of this size. */
    void layout(int width, int height) {
        int toolsTop = height - BOTTOM - GlassBar.CAPSULE_HEIGHT;
        tools.placeCentered(width, toolsTop);
        int eraserTop = toolsTop - GAP - GlassBar.CAPSULE_HEIGHT;
        eraserModes.placeCentered(width, eraserTop);
        Dimension hintSize = hintDock.getPreferredSize();
        int above = eraserModes.isShown() ? eraserTop : toolsTop;
        hintDock.setBounds((width - hintSize.width) / 2, above - hintSize.height, hintSize.width, hintSize.height);
    }

    GlassBar getTools() {
        return tools;
    }

    GlassBar getEraserModes() {
        return eraserModes;
    }

    String currentHint() {
        return hint.getText();
    }

    GlassBar.Item toolItem(DrawingCanvas.Tool tool) {
        return toolItems.get(tool.ordinal());
    }

    GlassBar.Item undoItem() {
        return undoItem;
    }

    GlassBar.Item clearItem() {
        return clearItem;
    }

    GlassBar.Item exportItem() {
        return exportItem;
    }
}
