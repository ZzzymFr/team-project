package view.stack;

import javax.swing.JFrame;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Frame;
import java.awt.GraphicsConfiguration;
import java.awt.GraphicsDevice;
import java.awt.Insets;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.Toolkit;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.WindowEvent;
import java.util.Locale;

/**
 * Replaces the system title bar: makes the window frameless (and transparent where the OS
 * allows it, so only the cards are visible; opaque on macOS, see {@link #useTranslucentWindow}),
 * puts minimize / maximize / close on the front
 * card, and adds dragging, double-click-to-maximize and a resize grip.
 */
public final class WindowChrome {
    private static final int MARGIN = 14;
    private static final Dimension MINIMUM_SIZE = new Dimension(640, 440);

    private final JFrame frame;
    private final StackedPages stack;
    private final WindowButton maximizeButton;
    private final ResizeGrip grip;
    private Rectangle normalBounds;
    private boolean maximized;
    private Point dragOffset;

    /** Call before the frame is shown (undecorated can only be set on a frame that is not displayable). */
    public static void install(JFrame frame, StackedPages stack) {
        new WindowChrome(frame, stack);
    }

    private WindowChrome(JFrame frame, StackedPages stack) {
        this.frame = frame;
        this.stack = stack;
        frame.setUndecorated(true);
        boolean translucent = useTranslucentWindow(System.getProperty("os.name", ""),
                frame.getGraphicsConfiguration().getDevice()
                        .isWindowTranslucencySupported(GraphicsDevice.WindowTranslucency.PERPIXEL_TRANSLUCENT));
        if (translucent) {
            frame.setBackground(new Color(0, 0, 0, 0));
            stack.setOpaque(false);
            stack.setMargin(MARGIN);
        } else {
            stack.setMargin(0);  // no transparency: let the front card reach the window edges
        }
        frame.setContentPane(stack);
        frame.setMinimumSize(MINIMUM_SIZE);

        JPanel controls = new JPanel(new FlowLayout(FlowLayout.RIGHT, 2, 0));
        controls.setOpaque(false);
        controls.add(new WindowButton(WindowButton.Kind.MINIMIZE, () -> frame.setState(Frame.ICONIFIED)));
        maximizeButton = new WindowButton(WindowButton.Kind.MAXIMIZE, this::toggleMaximize);
        controls.add(maximizeButton);
        controls.add(new WindowButton(WindowButton.Kind.CLOSE,
                () -> frame.dispatchEvent(new WindowEvent(frame, WindowEvent.WINDOW_CLOSING))));
        stack.setHeaderAccessory(controls);
        grip = new ResizeGrip(frame);
        stack.setCornerAccessory(grip);

        MouseAdapter drag = new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent e) {
                boolean onTitle = SwingUtilities.isLeftMouseButton(e) && stack.isInFrontHeader(e.getPoint());
                dragOffset = onTitle && !maximized ? e.getPoint() : null;
            }

            @Override
            public void mouseDragged(MouseEvent e) {
                if (dragOffset == null) {
                    return;
                }
                Point screen = e.getLocationOnScreen();
                frame.setLocation(screen.x - dragOffset.x, screen.y - dragOffset.y);
            }

            @Override
            public void mouseReleased(MouseEvent e) {
                dragOffset = null;
            }

            @Override
            public void mouseClicked(MouseEvent e) {
                if (e.getClickCount() == 2 && stack.isInFrontHeader(e.getPoint())) {
                    toggleMaximize();
                }
            }
        };
        stack.addMouseListener(drag);
        stack.addMouseMotionListener(drag);
    }

    /**
     * Whether to make the window see-through around the cards. Not on macOS: there Java's
     * per-pixel translucent windows are cleared to fully transparent on every repaint, so the
     * window flickers and briefly vanishes when switching pages (issue #17), and hovering a card
     * title flickers without end (each repaint makes the pixel under the mouse transparent, macOS
     * reports the mouse as gone, the hover is undone and repainted, and so on). macOS therefore
     * gets an ordinary opaque window.
     */
    static boolean useTranslucentWindow(String osName, boolean translucencySupported) {
        boolean mac = osName.toLowerCase(Locale.ROOT).startsWith("mac");
        return translucencySupported && !mac;
    }

    private void toggleMaximize() {
        if (maximized) {
            frame.setBounds(normalBounds);
        } else {
            normalBounds = frame.getBounds();
            frame.setBounds(usableScreenArea());  // stays clear of the taskbar
        }
        maximized = !maximized;
        maximizeButton.setKind(maximized ? WindowButton.Kind.RESTORE : WindowButton.Kind.MAXIMIZE);
        grip.setVisible(!maximized);
        stack.setFlush(maximized);  // same-width square cards: no gaps at the screen's top corners
        if (!stack.isOpaque()) {
            stack.setMargin(maximized ? 0 : MARGIN);
        }
        frame.validate();
    }

    private Rectangle usableScreenArea() {
        GraphicsConfiguration gc = frame.getGraphicsConfiguration();
        Rectangle screen = gc.getBounds();
        Insets taskbar = Toolkit.getDefaultToolkit().getScreenInsets(gc);
        return new Rectangle(screen.x + taskbar.left, screen.y + taskbar.top,
                screen.width - taskbar.left - taskbar.right, screen.height - taskbar.top - taskbar.bottom);
    }
}
