package view.canvas;

import org.junit.jupiter.api.Test;
import view.DrawingCanvas;

import javax.swing.SwingUtilities;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.event.MouseEvent;
import java.awt.image.BufferedImage;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CanvasShellTest {
    private final DrawingCanvas drawing = new DrawingCanvas();
    private final SharedCanvas canvas = new SharedCanvas(drawing);
    private final CanvasPage page = new AnswerCanvasPage(canvas);
    private final AtomicInteger exports = new AtomicInteger();
    private CanvasShell shell;

    private void build() {
        shell = new CanvasShell(canvas, page, exports::incrementAndGet);
        shell.setSize(980, 660);
        layout(shell);
    }

    // validate() is a no-op without a window, lay out by hand
    private static void layout(java.awt.Container c) {
        c.doLayout();
        for (java.awt.Component child : c.getComponents()) {
            if (child instanceof java.awt.Container) {
                layout((java.awt.Container) child);
            }
        }
    }

    private void write(int x, int y) {
        drawing.dispatchEvent(new MouseEvent(drawing, MouseEvent.MOUSE_PRESSED, 1L, MouseEvent.BUTTON1_DOWN_MASK,
                x, y, 1, false, MouseEvent.BUTTON1));
        drawing.dispatchEvent(new MouseEvent(drawing, MouseEvent.MOUSE_RELEASED, 2L, 0,
                x, y, 1, false, MouseEvent.BUTTON1));
    }

    @Test
    void thePageHoldsTheCanvasAndFillsTheWindow() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            build();
            assertTrue(canvas.isAttachedTo(page));
            assertEquals(shell.getSize(), page.getSize());
            assertEquals(shell.getSize(), drawing.getSize());
            assertEquals(shell.getWidth() / 2, shell.getToolBars().getTools().getBounds().getCenterX(), 1);
        });
    }

    @Test
    void exportPdfOpensTheEditor() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            build();
            CanvasToolBars tools = shell.getToolBars();
            tools.getTools().activate(tools.exportItem());
            assertEquals(1, exports.get());
        });
    }

    @Test
    void onlyTheCapsuleTakesTheMouse() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            build();
            view.glass.GlassBar bar = shell.getToolBars().getTools();
            assertFalse(bar.contains(2, 2), "the margin passes clicks through to the canvas");
            assertTrue(bar.contains(bar.getWidth() / 2, bar.getHeight() / 2));
        });
    }

    @Test
    void theToolBarFollowsTheCanvasToolAndShowsEraserModesOnlyWithTheEraser() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            build();
            CanvasToolBars tools = shell.getToolBars();
            assertTrue(tools.getTools().isSelected(tools.toolItem(DrawingCanvas.Tool.PEN)));
            assertFalse(tools.getEraserModes().isShown());

            canvas.setTool(DrawingCanvas.Tool.ERASER);
            assertTrue(tools.getTools().isSelected(tools.toolItem(DrawingCanvas.Tool.ERASER)));
            assertTrue(tools.getEraserModes().isShown());
            assertEquals("Drag over the ink to erase just that part.", tools.currentHint());

            canvas.setEraserMode(DrawingCanvas.EraserMode.STROKE);
            assertEquals("Touch a stroke to erase all of it.", tools.currentHint());

            canvas.setTool(DrawingCanvas.Tool.LASSO);
            assertFalse(tools.getEraserModes().isShown());
        });
    }

    @Test
    void undoAndClearAreEnabledOnlyWhenThereIsSomethingToDo() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            build();
            CanvasToolBars tools = shell.getToolBars();
            assertFalse(tools.getTools().isEnabled(tools.undoItem()));
            assertFalse(tools.getTools().isEnabled(tools.clearItem()));

            write(100, 100);
            assertTrue(tools.getTools().isEnabled(tools.undoItem()));
            assertTrue(tools.getTools().isEnabled(tools.clearItem()));

            tools.getTools().activate(tools.clearItem());
            assertTrue(canvas.isBlank());
            assertFalse(tools.getTools().isEnabled(tools.clearItem()));
            assertTrue(tools.getTools().isEnabled(tools.undoItem()), "Clear can be undone");

            tools.getTools().activate(tools.undoItem());
            assertFalse(canvas.isBlank());
        });
    }

    @Test
    void theCanvasIsPlainWithNoDots() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            build();
            BufferedImage image = new BufferedImage(980, 660, BufferedImage.TYPE_INT_ARGB);
            Graphics2D g = image.createGraphics();
            shell.paint(g);
            g.dispose();
            int background = new Color(0xF2F2F7).getRGB();
            for (int x = 0; x < 980; x++) {
                for (int y = 90; y < 500; y++) {  // between the floating controls
                    assertEquals(background, image.getRGB(x, y), "at " + x + "," + y);
                }
            }
        });
    }

    @Test
    void theWholeWindowPaintsWithoutAScreen() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            build();
            write(490, 620);  // under the tools, so the glass has ink to bend
            BufferedImage image = new BufferedImage(980, 660, BufferedImage.TYPE_INT_ARGB);
            Graphics2D g = image.createGraphics();
            shell.paint(g);
            g.dispose();
            assertEquals(new Color(0xF2F2F7).getRGB(), image.getRGB(5, 330), "the canvas shows between the bars");
        });
    }
}
