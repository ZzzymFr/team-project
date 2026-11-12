package view.board;

import javax.swing.JComponent;
import javax.swing.SwingUtilities;
import java.awt.Color;
import java.awt.Cursor;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.function.IntConsumer;

// draggable line between the canvas and a side panel
public class SplitDivider extends JComponent {
    public static final int HIT = 8;
    private static final Color LINE = new Color(0, 0, 0, 23);
    private static final Color ACTIVE = new Color(0x007AFF);

    private boolean hover;
    private boolean dragging;

    // dragged gets the line's x in the parent's coords
    public SplitDivider(IntConsumer dragged, Runnable reset) {
        setOpaque(false);
        setCursor(Cursor.getPredefinedCursor(Cursor.E_RESIZE_CURSOR));
        MouseAdapter m = new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent e) {
                if (SwingUtilities.isLeftMouseButton(e)) {
                    dragging = true;
                    repaint();
                }
            }

            @Override
            public void mouseDragged(MouseEvent e) {
                if (dragging) {
                    dragged.accept(getX() + e.getX());
                }
            }

            @Override
            public void mouseReleased(MouseEvent e) {
                dragging = false;
                repaint();
            }

            @Override
            public void mouseClicked(MouseEvent e) {
                if (e.getClickCount() == 2) {
                    reset.run();
                }
            }

            @Override
            public void mouseEntered(MouseEvent e) {
                hover = true;
                repaint();
            }

            @Override
            public void mouseExited(MouseEvent e) {
                hover = false;
                repaint();
            }
        };
        addMouseListener(m);
        addMouseMotionListener(m);
    }

    @Override
    protected void paintComponent(Graphics g) {
        Graphics2D g2 = (Graphics2D) g.create();
        boolean active = hover || dragging;
        int w = active ? 2 : 1;
        g2.setColor(active ? ACTIVE : LINE);
        g2.fillRect((getWidth() - w) / 2, 0, w, getHeight());
        g2.dispose();
    }
}
