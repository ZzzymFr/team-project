package view.canvas;

import view.ribbon.Ribbon;

import javax.swing.JComponent;
import javax.swing.JFrame;
import javax.swing.JPanel;
import javax.swing.JRootPane;
import javax.swing.KeyStroke;
import javax.swing.SwingUtilities;
import java.awt.Component;
import java.awt.Frame;
import java.awt.Point;
import java.awt.Toolkit;
import java.awt.Window;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.Locale;

/**
 * The content of the window: the page filling it, with the zoom at the bottom right, a scroll bar
 * down the right edge, and optionally a Word-like ribbon across the top and a panel at the left
 * ({@link #setHeader}, {@link #setSidePanel}). The drawing tools go on the ribbon's Draw tab
 * ({@link #addDrawTab}); while it is chosen, their hint floats at the bottom. The page holds the
 * shared canvas and everything else that floats over it.
 */
public class CanvasShell extends JPanel {
    private static final int TITLE_STRIP_HEIGHT = 28;
    private static final boolean MAC = System.getProperty("os.name", "").toLowerCase(Locale.ROOT).startsWith("mac");

    private final SharedCanvas canvas;
    private final CanvasPage page;
    private static final int MAC_WINDOW_BUTTONS = 80;  // room for the traffic lights at the left of the ribbon
    private static final int HEADER_GAP = 10;
    private static final int SCROLL_GAP = 6;            // between the scroll bar and what is above and below it          // between the ribbon and what floats below it
    private static final int SIDE_PANEL_WIDTH = 300;
    private static final int SIDE_PANEL_INSET = 14;
    private static final int SIDE_PANEL_BOTTOM = 104;  // clear of the hint at the bottom

    private final DrawTab drawTab;
    private final ZoomControl zoom;
    private final PageScrollBar scroll;
    private final JComponent titleStrip = MAC ? new TitleStrip() : null;
    private Ribbon header;
    private JComponent sidePanel;

    public CanvasShell(SharedCanvas canvas, CanvasPage page) {
        super(null);
        this.canvas = canvas;
        this.page = page;
        drawTab = new DrawTab(canvas);
        zoom = new ZoomControl(canvas);
        scroll = new PageScrollBar(canvas);
        zoom.addTo(this);      // added first = drawn last = on top
        add(scroll.getBar());
        drawTab.addTo(this);
        if (titleStrip != null) {
            add(titleStrip);
        }
        add(page);  // under the glass
        canvas.attachTo(page);
    }

    /**
     * Makes this the content of the frame. On macOS the canvas also extends under a transparent
     * title bar, leaving only the traffic-light buttons; elsewhere the window keeps the system's
     * own title bar. Call before the frame is shown.
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
        zoom.layout(w, h);
        drawTab.layout(w, h, zoom.getDock().getBounds());
        int headerHeight = 0;
        if (header != null) {
            headerHeight = header.preferredHeight();
            header.setBounds(0, 0, w, headerHeight);
        }
        // the scroll bar runs down the right edge, from below the ribbon to above the zoom
        int scrollTop = headerHeight + SCROLL_GAP;
        int scrollBottom = zoom.getDock().getY() - SCROLL_GAP;
        scroll.getBar().setBounds(w - PageScrollBar.WIDTH - 2, scrollTop, PageScrollBar.WIDTH,
                Math.max(0, scrollBottom - scrollTop));
        if (sidePanel != null) {
            int top = headerHeight + HEADER_GAP;
            sidePanel.setBounds(SIDE_PANEL_INSET, top, SIDE_PANEL_WIDTH, Math.max(0, h - top - SIDE_PANEL_BOTTOM));
        }
        if (titleStrip != null) {
            titleStrip.setBounds(0, 0, w, TITLE_STRIP_HEIGHT);
        }
    }

    /**
     * Puts the ribbon across the top of the window, like Word's; the canvas carries on under it, so
     * the paper starts below it and the page's own controls and messages float just under it. On
     * macOS the ribbon's title row takes the place of the title bar.
     */
    public void setHeader(Ribbon ribbon) {
        if (header != null) {
            remove(header);
        }
        header = ribbon;
        add(ribbon, 0);
        if (titleStrip != null) {
            titleStrip.setVisible(false);
            ribbon.useAsTitleBar(MAC_WINDOW_BUTTONS);
            MouseAdapter drag = windowDragger();
            ribbon.getTitleRow().addMouseListener(drag);
            ribbon.getTitleRow().addMouseMotionListener(drag);
        }
        page.setFunctionTop(ribbon.preferredHeight() + HEADER_GAP);
        revalidate();
        repaint();
    }

    /**
     * Adds the Draw tab, with the drawing tools, to the ribbon. The tools work only while that tab
     * is chosen; on the other tabs the canvas is a plain pointer and leaves no ink.
     */
    public Ribbon.Tab addDrawTab(Ribbon ribbon) {
        return drawTab.addTo(ribbon);
    }

    /** Floats a panel at the left, below the top bar; show and hide it with setVisible. */
    public void setSidePanel(JComponent panel) {
        if (sidePanel != null) {
            remove(sidePanel);
        }
        sidePanel = panel;
        add(panel, 0);
        revalidate();
        repaint();
    }

    DrawTab getDrawTab() {
        return drawTab;
    }

    ZoomControl getZoomControl() {
        return zoom;
    }

    PageScrollBar getScrollBar() {
        return scroll;
    }

    /** macOS: drags the window by a component, and maximises or restores it on a double-click. */
    private static MouseAdapter windowDragger() {
        return new MouseAdapter() {
            private Window dragged;
            private Point dragFrom;
            private Point windowFrom;

            @Override
            public void mousePressed(MouseEvent e) {
                Window window = SwingUtilities.getWindowAncestor((Component) e.getSource());
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
    }

    /** macOS: the strip along the top that drags the window, since the title bar lets the canvas under it. */
    private static final class TitleStrip extends JComponent {
        TitleStrip() {
            MouseAdapter drag = windowDragger();
            addMouseListener(drag);
            addMouseMotionListener(drag);
        }
    }
}
