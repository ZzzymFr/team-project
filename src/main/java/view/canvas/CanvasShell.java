package view.canvas;

import javax.swing.JComponent;
import javax.swing.JFrame;
import javax.swing.JPanel;
import javax.swing.JRootPane;
import javax.swing.KeyStroke;
import javax.swing.SwingUtilities;
import java.awt.Frame;
import java.awt.Point;
import java.awt.Toolkit;
import java.awt.Window;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.Locale;

/**
 * The content of the window: the page filling it, with the drawing tools floating on top at the
 * bottom. The page holds the shared canvas and everything else that floats over it.
 */
public class CanvasShell extends JPanel {
    private static final int TITLE_STRIP_HEIGHT = 28;
    private static final boolean MAC = System.getProperty("os.name", "").toLowerCase(Locale.ROOT).startsWith("mac");

    private final SharedCanvas canvas;
    private final CanvasPage page;
    private final CanvasToolBars toolBars;
    private final JComponent titleStrip = MAC ? new TitleStrip() : null;

    public CanvasShell(SharedCanvas canvas, CanvasPage page, Runnable exportPdf) {
        super(null);
        this.canvas = canvas;
        this.page = page;
        toolBars = new CanvasToolBars(canvas, exportPdf);
        toolBars.addTo(this);  // added first = drawn last = on top
        if (titleStrip != null) {
            add(titleStrip);
        }
        add(page);  // under the glass
        canvas.attachTo(page);
    }

    /**
     * Makes this the content of the frame. On macOS the canvas also extends under a transparent
     * title bar, leaving only the traffic-light buttons. Call before the frame is shown.
     */
    public void install(JFrame frame) {
        if (MAC) {
            JRootPane root = frame.getRootPane();
            root.putClientProperty("apple.awt.fullWindowContent", true);
            root.putClientProperty("apple.awt.transparentTitleBar", true);
            root.putClientProperty("apple.awt.windowTitleVisible", false);
        }
        frame.setContentPane(this);
        int shortcut = Toolkit.getDefaultToolkit().getMenuShortcutKeyMaskEx();
        registerKeyboardAction(e -> canvas.undo(), KeyStroke.getKeyStroke(KeyEvent.VK_Z, shortcut),
                JComponent.WHEN_IN_FOCUSED_WINDOW);
    }

    /** The glass overlaps the canvas, so Swing must repaint it whenever the canvas repaints. */
    @Override
    public boolean isOptimizedDrawingEnabled() {
        return false;
    }

    @Override
    public void doLayout() {
        int w = getWidth();
        int h = getHeight();
        page.setBounds(0, 0, w, h);
        toolBars.layout(w, h);
        if (titleStrip != null) {
            titleStrip.setBounds(0, 0, w, TITLE_STRIP_HEIGHT);
        }
    }

    CanvasToolBars getToolBars() {
        return toolBars;
    }

    /** macOS: the strip along the top that drags the window, since the title bar lets the canvas under it. */
    private static final class TitleStrip extends JComponent {
        private Window dragged;
        private Point dragFrom;
        private Point windowFrom;

        TitleStrip() {
            MouseAdapter mouse = new MouseAdapter() {
                @Override
                public void mousePressed(MouseEvent e) {
                    Window window = SwingUtilities.getWindowAncestor(TitleStrip.this);
                    if (!SwingUtilities.isLeftMouseButton(e) || window == null) {
                        return;
                    }
                    if (e.getClickCount() == 2 && window instanceof Frame) {
                        Frame frame = (Frame) window;
                        frame.setExtendedState(frame.getExtendedState() ^ Frame.MAXIMIZED_BOTH);
                    } else {
                        dragged = window;
                        dragFrom = e.getLocationOnScreen();
                        windowFrom = window.getLocation();
                    }
                }

                @Override
                public void mouseDragged(MouseEvent e) {
                    if (dragged != null) {
                        Point now = e.getLocationOnScreen();
                        dragged.setLocation(windowFrom.x + now.x - dragFrom.x, windowFrom.y + now.y - dragFrom.y);
                    }
                }

                @Override
                public void mouseReleased(MouseEvent e) {
                    dragged = null;
                }
            };
            addMouseListener(mouse);
            addMouseMotionListener(mouse);
        }
    }
}
