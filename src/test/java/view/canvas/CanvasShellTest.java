package view.canvas;

import org.junit.jupiter.api.Test;
import view.DrawingCanvas;

import javax.swing.SwingUtilities;
import java.awt.Color;
import java.awt.Cursor;
import java.awt.Graphics2D;
import java.awt.event.MouseEvent;
import java.awt.image.BufferedImage;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CanvasShellTest {
    private final DrawingCanvas drawing = new DrawingCanvas();
    private final SharedCanvas canvas = new SharedCanvas(drawing);
    private final CanvasPage page = new AnswerCanvasPage(canvas);
    private CanvasShell shell;

    private void build() {
        shell = new CanvasShell(canvas, page);
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

    // the shell on a ribbon with an Edit tab before the Draw tab, as in the app
    private view.ribbon.Ribbon buildWithRibbon() {
        build();
        view.ribbon.Ribbon ribbon = new view.ribbon.Ribbon("Math Lab");
        ribbon.addTab("Edit", () -> true);
        shell.addDrawTab(ribbon);
        shell.setHeader(ribbon);
        layout(shell);
        return ribbon;
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
        });
    }

    @Test
    void theRibbonSpansTheTopAndTheSidePanelSitsBelowIt() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            build();
            view.ribbon.Ribbon ribbon = new view.ribbon.Ribbon("Math Lab");
            ribbon.addTab("Edit", () -> true);
            javax.swing.JPanel side = new javax.swing.JPanel();
            shell.setHeader(ribbon);
            shell.setSidePanel(side);
            layout(shell);
            assertEquals(new java.awt.Rectangle(0, 0, shell.getWidth(), ribbon.preferredHeight()), ribbon.getBounds());
            assertEquals(shell.getSize(), page.getSize(), "the canvas carries on under the ribbon");
            assertTrue(side.getY() > ribbon.preferredHeight(), "below the ribbon");
            assertTrue(side.getX() < 40 && side.getWidth() > 0 && side.getHeight() > 0);
            assertEquals(0, shell.getComponentZOrder(side), "on top of everything");
            assertTrue(shell.getComponentZOrder(ribbon) < shell.getComponentZOrder(page), "over the canvas");
        });
    }

    @Test
    void theScrollBarRunsDownTheRightEdgeAndScrollsThePaper() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            build();
            drawing.setKeepPaperInView(true);
            javax.swing.JScrollBar bar = shell.getScrollBar().getBar();
            shell.getScrollBar().follow();
            layout(shell);
            assertTrue(bar.isVisible(), "a sheet is taller than the window");
            assertTrue(bar.getX() + bar.getWidth() >= shell.getWidth() - 4, "at the right edge");
            assertTrue(bar.getY() + bar.getHeight() <= shell.getZoomControl().getDock().getY(), "above the zoom");

            bar.setValue(250);                          // as dragging the thumb does
            assertEquals(250, drawing.getScrollY());

            drawing.panBy(0, -100);                     // the wheel or a drag on the canvas
            assertEquals(350, bar.getValue(), "the thumb follows the view");
            assertEquals(drawing.getScrollMaxY() + drawing.getScrollExtentY(), bar.getMaximum());
        });
    }

    @Test
    void theToolsAreOnTheDrawTabAndWorkOnlyWhileItIsChosen() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            view.ribbon.Ribbon ribbon = buildWithRibbon();
            DrawTab draw = shell.getDrawTab();
            assertEquals("Draw", draw.getTab().getName());
            assertFalse(draw.getTab().isSelected(), "Edit, the first tab, starts chosen");
            assertFalse(drawing.isDrawingEnabled());
            assertEquals(Cursor.DEFAULT_CURSOR, drawing.getCursor().getType(), "a plain pointer");
            assertFalse(draw.isHintShown());

            write(100, 100);
            assertTrue(canvas.isBlank(), "no ink while the Draw tab is not chosen");

            draw.getTab().click();
            assertTrue(drawing.isDrawingEnabled());
            assertEquals(Cursor.CROSSHAIR_CURSOR, drawing.getCursor().getType());
            assertTrue(draw.isHintShown());
            write(100, 100);
            assertFalse(canvas.isBlank());

            ribbon.getTabs().get(0).click();
            assertFalse(drawing.isDrawingEnabled());
            assertEquals(Cursor.DEFAULT_CURSOR, drawing.getCursor().getType());
        });
    }

    @Test
    void withDrawingOffTheEraserLeavesTheInkAlone() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            view.ribbon.Ribbon ribbon = buildWithRibbon();
            shell.getDrawTab().getTab().click();
            write(100, 100);
            canvas.setTool(DrawingCanvas.Tool.ERASER);
            ribbon.getTabs().get(0).click();
            write(100, 100);
            assertFalse(canvas.isBlank());
        });
    }

    @Test
    void theDrawTabFollowsTheCanvasTool() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            buildWithRibbon();
            DrawTab draw = shell.getDrawTab();
            view.glass.GlassBar bar = draw.getBar();
            assertTrue(bar.isSelected(draw.toolItem(DrawingCanvas.Tool.PEN)));

            canvas.setTool(DrawingCanvas.Tool.ERASER);
            assertTrue(bar.isSelected(draw.toolItem(DrawingCanvas.Tool.ERASER)));
            assertEquals("Drag over the ink to erase just that part.", draw.currentHint());

            canvas.setEraserMode(DrawingCanvas.EraserMode.STROKE);
            assertTrue(bar.isSelected(draw.eraserModeItem(DrawingCanvas.EraserMode.STROKE)));
            assertEquals("Touch a stroke to erase all of it.", draw.currentHint());

            canvas.setTool(DrawingCanvas.Tool.LASSO);
            bar.activate(draw.eraserModeItem(DrawingCanvas.EraserMode.PRECISE));
            assertEquals(DrawingCanvas.Tool.ERASER, canvas.getTool(), "an eraser mode takes up the eraser");
            assertEquals(DrawingCanvas.EraserMode.PRECISE, canvas.getEraserMode());
        });
    }

    @Test
    void undoAndClearAreEnabledOnlyWhenThereIsSomethingToDo() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            buildWithRibbon();
            DrawTab draw = shell.getDrawTab();
            draw.getTab().click();
            assertFalse(draw.undoButton().isEnabled());
            assertFalse(draw.clearButton().isEnabled());

            write(100, 100);
            assertTrue(draw.undoButton().isEnabled());
            assertTrue(draw.clearButton().isEnabled());

            draw.clearButton().doClick();
            assertTrue(canvas.isBlank());
            assertFalse(draw.clearButton().isEnabled());
            assertTrue(draw.undoButton().isEnabled(), "Clear can be undone");

            draw.undoButton().doClick();
            assertFalse(canvas.isBlank());
        });
    }

    @Test
    void theCanvasIsPlainPaperWithNoDots() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            build();
            BufferedImage image = new BufferedImage(980, 660, BufferedImage.TYPE_INT_ARGB);
            Graphics2D g = image.createGraphics();
            shell.paint(g);
            g.dispose();
            int paper = Color.WHITE.getRGB();
            int background = new Color(0xF2F2F7).getRGB();
            for (int y = 90; y < 500; y++) {  // between the floating controls
                for (int x = 2; x < 790; x++) {  // the A4 sheet, inside its edge
                    assertEquals(paper, image.getRGB(x, y), "at " + x + "," + y);
                }
                for (int x = 800; x < 980; x++) {  // beside it, past its shadow
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
            assertEquals(Color.WHITE.getRGB(), image.getRGB(5, 330), "the paper shows between the bars");
            assertEquals(new Color(0xF2F2F7).getRGB(), image.getRGB(900, 330), "and the desk beside it");
        });
    }
}
