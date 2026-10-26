package view.stack;

import javax.swing.JComponent;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;
import javax.swing.Timer;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;
import java.awt.Color;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.GradientPaint;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.geom.Rectangle2D;
import java.awt.geom.RoundRectangle2D;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.List;

/**
 * A reusable "stack of cards" container, used like JTabbedPane: addPage(name, color, panel).
 *
 * <p>How the animation works: every card has a depth (0 = front, 1 = one behind, ...).
 * Its position, size and shade depend only on that depth. Selecting a card animates all
 * depths from their old values to their new ones with a Swing Timer. Cards are drawn back
 * to front by their current depth; two cards only swap drawing order when their depths are
 * equal, i.e. when they occupy exactly the same rectangle, so the swap is invisible.
 *
 * <p>During the animation the real page panels are hidden and snapshots (BufferedImages) of
 * them are drawn instead, because Swing components cannot be scaled while they are live.
 * When the animation ends, the new front page's real panel is shown again.
 *
 * <p>The stack knows nothing about windows or about the pages it holds. Extra controls can be
 * attached to the front card's title strip (setHeaderAccessory) and bottom-right corner
 * (setCornerAccessory); listeners added with addChangeListener hear when the front page changes.
 */
public class StackedPages extends JPanel {
    private static final int PEEK = 40;         // how much each card behind sticks out at the top
    private static final int HEADER = 46;       // title strip at the top of every card
    private static final int BOTTOM = 18;       // space below the page panel inside a card
    private static final int RADIUS = 18;
    private static final double SHRINK = 0.04;  // each step back is 4% narrower
    private static final int DURATION_MS = 460;
    private static final Color CARD = Color.WHITE;
    private static final Color TEXT = new Color(15, 23, 42);
    private static final Color MUTED = new Color(71, 85, 105);
    private static final Color LINE = new Color(226, 232, 240);
    private static final Color OUTLINE = new Color(203, 213, 225);

    private final List<Page> pages = new ArrayList<>();
    private final List<ChangeListener> changeListeners = new ArrayList<>();
    private final Timer timer = new Timer(15, e -> tick());
    private long animationStart;
    private int front = 0;
    private int hover = -1;
    private int margin = 14;                    // transparent room around the stack for the shadow
    private boolean flush;                      // all cards full width with square corners
    private JComponent headerAccessory;
    private JComponent cornerAccessory;

    private static final class Page {
        final String name;
        final Color accent;
        final JComponent content;
        double depth;
        double startDepth;
        double targetDepth;
        BufferedImage snapshot;

        Page(String name, Color accent, JComponent content) {
            this.name = name;
            this.accent = accent;
            this.content = content;
        }
    }

    public StackedPages() {
        setLayout(null);  // we place the front page ourselves in doLayout()
        setBackground(new Color(226, 232, 240));
        setFont(Fonts.ui(Font.PLAIN, 13f));
        setPreferredSize(new Dimension(980, 660));
        MouseAdapter mouse = new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent e) {
                if (!SwingUtilities.isLeftMouseButton(e)) {
                    return;
                }
                int hit = cardAt(e.getPoint());
                if (hit >= 0 && hit != front) {
                    select(hit);
                }
            }

            @Override
            public void mouseMoved(MouseEvent e) {
                int hit = cardAt(e.getPoint());
                int newHover = hit >= 0 && hit != front ? hit : -1;
                setCursor(Cursor.getPredefinedCursor(newHover >= 0 ? Cursor.HAND_CURSOR : Cursor.DEFAULT_CURSOR));
                if (newHover != hover) {
                    hover = newHover;
                    repaint();
                }
            }

            @Override
            public void mouseExited(MouseEvent e) {
                if (hover != -1) {
                    hover = -1;
                    repaint();
                }
            }
        };
        addMouseListener(mouse);
        addMouseMotionListener(mouse);
    }

    public void addPage(String name, Color accent, JComponent content) {
        Page page = new Page(name, accent, content);
        page.depth = pages.size();
        page.startDepth = page.depth;
        page.targetDepth = page.depth;
        pages.add(page);
        add(content);
        content.setVisible(pages.size() == 1);
        revalidate();
        repaint();
    }

    public int getPageCount() {
        return pages.size();
    }

    /** Index of the front page, in the order the pages were added; -1 if there are none. */
    public int getSelectedIndex() {
        return pages.isEmpty() ? -1 : front;
    }

    /** The front page's panel, or null if there are no pages. */
    public JComponent getSelectedComponent() {
        return pages.isEmpty() ? null : pages.get(front).content;
    }

    /** Notified when a different page is brought to the front (as the animation starts). */
    public void addChangeListener(ChangeListener listener) {
        changeListeners.add(listener);
    }

    public void removeChangeListener(ChangeListener listener) {
        changeListeners.remove(listener);
    }

    /** A component shown at the right end of the front card's title strip. */
    public void setHeaderAccessory(JComponent accessory) {
        if (headerAccessory != null) {
            remove(headerAccessory);
        }
        headerAccessory = accessory;
        add(accessory, 0);  // index 0 = painted last = on top
        revalidate();
    }

    /** A component shown in the bottom-right corner of the front card. */
    public void setCornerAccessory(JComponent accessory) {
        if (cornerAccessory != null) {
            remove(cornerAccessory);
        }
        cornerAccessory = accessory;
        add(accessory, 0);
        revalidate();
    }

    /** Space around the stack (for the shadow); 0 makes the front card fill the component. */
    public void setMargin(int margin) {
        this.margin = Math.max(0, margin);
        revalidate();
        repaint();
    }

    /**
     * Flush cards all have the full width and square corners, so the stack is a solid rectangle
     * with nothing showing past the cards (e.g. in a maximized window). Otherwise each card
     * further back is narrower and the corners are rounded.
     */
    public void setFlush(boolean flush) {
        this.flush = flush;
        revalidate();
        repaint();
    }

    /** True for the front card's title strip, e.g. to let the user drag the window there. */
    public boolean isInFrontHeader(Point p) {
        RoundRectangle2D card = cardShape(0);
        return card.contains(p) && p.y < card.getY() + HEADER;
    }

    /** Bring page i to the front; the others keep their order behind it. */
    public void select(int index) {
        if (index < 0 || index >= pages.size() || index == front) {
            return;
        }
        takeSnapshots();
        for (Page p : pages) {
            p.startDepth = p.depth;
            p.content.setVisible(false);
        }
        // New order: the chosen page, then the others in their current front-to-back order.
        List<Page> order = new ArrayList<>(pages);
        order.sort((a, b) -> Double.compare(a.targetDepth, b.targetDepth));
        Page chosen = pages.get(index);
        order.remove(chosen);
        order.add(0, chosen);
        for (int d = 0; d < order.size(); d++) {
            order.get(d).targetDepth = d;
        }
        front = index;
        hover = -1;
        if (headerAccessory != null) {
            headerAccessory.setVisible(false);  // back once the new card has landed
        }
        setCursor(Cursor.getDefaultCursor());
        fireStateChanged();
        animationStart = System.nanoTime();
        timer.start();
    }

    private void fireStateChanged() {
        ChangeEvent event = new ChangeEvent(this);
        for (ChangeListener listener : new ArrayList<>(changeListeners)) {
            listener.stateChanged(event);
        }
    }

    private void tick() {
        double t = Math.min(1, (System.nanoTime() - animationStart) / (DURATION_MS * 1_000_000.0));
        double eased = 1 - Math.pow(1 - t, 3);  // ease-out cubic: fast start, gentle stop
        for (Page p : pages) {
            p.depth = p.startDepth + (p.targetDepth - p.startDepth) * eased;
        }
        if (t >= 1) {
            timer.stop();
            for (Page p : pages) {
                p.depth = p.targetDepth;
                p.snapshot = null;
            }
            pages.get(front).content.setVisible(true);
            if (headerAccessory != null) {
                headerAccessory.setVisible(true);
            }
            revalidate();
        }
        repaint();
    }

    private boolean animating() {
        return timer.isRunning();
    }

    // ---------- geometry: everything follows from a card's depth ----------

    private RoundRectangle2D cardShape(double depth) {
        double frontTop = margin + Math.max(0, pages.size() - 1) * PEEK;
        double baseWidth = getWidth() - 2.0 * margin;
        double height = getHeight() - frontTop - margin;
        double width = flush ? baseWidth : baseWidth * (1 - SHRINK * depth);
        double x = margin + (baseWidth - width) / 2;
        double y = frontTop - depth * PEEK;
        int radius = flush ? 0 : RADIUS;
        return new RoundRectangle2D.Double(x, y, width, height, radius, radius);
    }

    private Rectangle contentBounds(RoundRectangle2D card) {
        return new Rectangle((int) card.getX() + 1, (int) card.getY() + HEADER,
                (int) card.getWidth() - 2, (int) card.getHeight() - HEADER - BOTTOM);
    }

    @Override
    public void doLayout() {
        if (pages.isEmpty()) {
            return;
        }
        RoundRectangle2D card = cardShape(0);
        Rectangle area = contentBounds(card);
        for (Page p : pages) {
            p.content.setBounds(area);  // all pages share the front-card size
        }
        if (headerAccessory != null) {
            Dimension size = headerAccessory.getPreferredSize();
            int x = (int) (card.getX() + card.getWidth()) - size.width - 8;
            int y = (int) card.getY() + 4 + (HEADER - 4 - size.height) / 2;
            headerAccessory.setBounds(x, y, size.width, size.height);
        }
        if (cornerAccessory != null) {
            Dimension size = cornerAccessory.getPreferredSize();
            int x = (int) (card.getX() + card.getWidth()) - size.width - 3;
            int y = (int) (card.getY() + card.getHeight()) - size.height - 3;
            cornerAccessory.setBounds(x, y, size.width, size.height);
        }
    }

    /** The visible card under a point: check from the front card backwards. */
    private int cardAt(Point point) {
        if (animating()) {
            return -1;
        }
        List<Page> frontToBack = new ArrayList<>(pages);
        frontToBack.sort((a, b) -> Double.compare(a.depth, b.depth));
        for (Page p : frontToBack) {
            if (cardShape(p.depth).contains(point)) {
                return pages.indexOf(p);
            }
        }
        return -1;
    }

    private void takeSnapshots() {
        doLayout();
        for (Page p : pages) {
            JComponent c = p.content;
            c.validate();
            int w = Math.max(1, c.getWidth());
            int h = Math.max(1, c.getHeight());
            BufferedImage image = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
            Graphics2D g = image.createGraphics();
            c.printAll(g);
            g.dispose();
            p.snapshot = image;
        }
    }

    // ---------- painting ----------

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);  // fills the background only when opaque (no transparent window)
        Graphics2D g2 = (Graphics2D) g.create();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g2.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        List<Page> backToFront = new ArrayList<>(pages);
        backToFront.sort((a, b) -> Double.compare(b.depth, a.depth));
        for (Page p : backToFront) {
            paintCard(g2, p);
        }
        g2.dispose();
    }

    private void paintCard(Graphics2D g2, Page p) {
        RoundRectangle2D card = cardShape(p.depth);
        double x = card.getX();
        double y = card.getY();
        double w = card.getWidth();

        // soft shadow: translucent copies, slightly larger and lower
        for (int i = 1; i <= 6; i++) {
            g2.setColor(new Color(15, 23, 42, 9));
            g2.fill(new RoundRectangle2D.Double(x - i * 0.8, y + i * 0.9, w + i * 1.6,
                    card.getHeight() + i * 0.6, RADIUS + i * 2, RADIUS + i * 2));
        }
        g2.setColor(CARD);
        g2.fill(card);

        // live content is drawn by Swing itself; while animating we draw the snapshot instead
        if (p.snapshot != null) {
            Graphics2D clip = (Graphics2D) g2.create();
            clip.clip(card);
            double scale = w / cardShape(0).getWidth();
            clip.drawImage(p.snapshot, (int) x + 1, (int) y + HEADER,
                    (int) (p.snapshot.getWidth() * scale), (int) (p.snapshot.getHeight() * scale), null);
            clip.dispose();
        }

        // accent strip along the top edge, with a faint tint of the accent below it
        Graphics2D top = (Graphics2D) g2.create();
        top.clip(card);
        top.setPaint(new GradientPaint(0, (float) y, withAlpha(p.accent, 34),
                0, (float) (y + HEADER), withAlpha(p.accent, 0)));
        top.fill(new Rectangle2D.Double(x, y, w, HEADER));
        top.setColor(p.accent);
        top.fill(new Rectangle2D.Double(x, y, w, 4));
        top.dispose();

        // title: a small rounded badge in the accent colour, then the name
        boolean isFront = p.depth < 0.5;
        boolean isHover = pages.indexOf(p) == hover;
        g2.setFont(Fonts.ui(Font.BOLD, 15f));
        FontMetrics fm = g2.getFontMetrics();
        int textY = (int) (y + 4 + (PEEK - 4 + fm.getAscent() - fm.getDescent()) / 2.0);
        int badge = 12;
        g2.setColor(p.accent);
        g2.fill(new RoundRectangle2D.Double(x + 22, textY - fm.getAscent() / 2.0 - badge / 2.0 + 2,
                badge, badge, 5, 5));
        g2.setColor(isHover ? p.accent : isFront ? TEXT : MUTED);
        g2.drawString(p.name, (int) x + 44, textY);
        if (isHover) {
            g2.setColor(withAlpha(p.accent, 160));
            g2.fillRect((int) x + 44, textY + 4, fm.stringWidth(p.name), 2);
        }
        if (isFront) {
            g2.setColor(LINE);
            g2.drawLine((int) x + 1, (int) y + HEADER - 1, (int) (x + w) - 2, (int) y + HEADER - 1);
        }

        // cards further back are slightly darker, and every card has a thin outline
        g2.setColor(new Color(15, 23, 42, (int) Math.min(48, 16 * p.depth)));
        g2.fill(card);
        g2.setColor(OUTLINE);
        g2.draw(card);
    }

    private static Color withAlpha(Color c, int alpha) {
        return new Color(c.getRed(), c.getGreen(), c.getBlue(), alpha);
    }
}
