package view.stack;

import javax.swing.JComponent;
import java.awt.Color;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Point;
import java.awt.RenderingHints;
import java.awt.Window;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;

/** Drag this corner to resize a frameless window. */
public final class ResizeGrip extends JComponent {
    private Point pressScreen;
    private Dimension pressSize;

    public ResizeGrip(Window window) {
        setPreferredSize(new Dimension(18, 18));
        setCursor(Cursor.getPredefinedCursor(Cursor.SE_RESIZE_CURSOR));
        setToolTipText("Drag to resize");
        MouseAdapter resize = new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent e) {
                pressScreen = e.getLocationOnScreen();
                pressSize = window.getSize();
            }

            @Override
            public void mouseDragged(MouseEvent e) {
                if (pressScreen == null) {
                    return;
                }
                Point now = e.getLocationOnScreen();
                Dimension min = window.getMinimumSize();
                window.setSize(Math.max(min.width, pressSize.width + now.x - pressScreen.x),
                        Math.max(min.height, pressSize.height + now.y - pressScreen.y));
                window.validate();
            }

            @Override
            public void mouseReleased(MouseEvent e) {
                pressScreen = null;
            }
        };
        addMouseListener(resize);
        addMouseMotionListener(resize);
    }

    @Override
    protected void paintComponent(Graphics g) {
        Graphics2D g2 = (Graphics2D) g.create();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2.setColor(new Color(148, 163, 184));
        int w = getWidth();
        int h = getHeight();
        for (int i = 0; i < 3; i++) {          // three dots along the diagonal, like a grip
            for (int j = 0; j <= i; j++) {
                g2.fillOval(w - 5 - 4 * (i - j), h - 5 - 4 * j, 2, 2);
            }
        }
        g2.dispose();
    }
}
