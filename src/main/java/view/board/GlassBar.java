package view.board;

import javax.swing.JComponent;
import javax.swing.SwingUtilities;
import javax.swing.Timer;
import java.awt.AlphaComposite;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Composite;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.GradientPaint;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.LinearGradientPaint;
import java.awt.Paint;
import java.awt.Point;
import java.awt.RadialGradientPaint;
import java.awt.RenderingHints;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.font.FontRenderContext;
import java.awt.geom.AffineTransform;
import java.awt.geom.Arc2D;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Line2D;
import java.awt.geom.Point2D;
import java.awt.geom.Rectangle2D;
import java.awt.geom.RoundRectangle2D;
import java.awt.image.BufferedImage;
import java.awt.image.ConvolveOp;
import java.awt.image.Kernel;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.LongSupplier;
import java.util.function.Supplier;

/**
 * Floating glass capsule from the glass demo: segments, actions, swatches, separators, plus a grip
 * to drag the whole bar and an icon-only compact mode.
 *
 * <p>The backdrop is painted in {@code reference}'s coordinates; {@code version} changes whenever
 * it looks different, so cached renders know when they are stale.
 */
public class GlassBar extends JComponent {
    public static final int SEG_H = 40;
    public static final int PAD = 6;
    public static final int MARGIN = 28;   // room for shadow and hover lens
    public static final int CAP_H = SEG_H + 2 * PAD;
    static final int ICON = 18;
    static final int ICON_GAP = 7;
    static final int ITEM_PAD_X = 16;
    static final int COMPACT_W = 48;
    static final int GRIP_W = 20;

    private static final float BLUR_SIGMA = 0.5f;
    private static final float LENS_BAND = 14f;
    private static final float LENS_STRENGTH = 10f;
    private static final float HOVER_MAG = 1.28f;
    private static final float HOVER_GROW_X = 16f;
    private static final float HOVER_GROW_Y = 8f;
    private static final float HOVER_STICK = 0.28f;
    private static final float HOVER_EDGE_BAND = 11f;
    private static final float HOVER_EDGE_K = 9f;
    private static final float HOVER_DISPERSION = 0.45f;
    private static final int FRAME_MS = 5;
    private static final float REF_MS = 16f;

    public enum Kind { SEGMENT, ACTION, SWATCH, SEPARATOR, GRIP }

    /** One thing in the bar. */
    public static final class Item {
        final Kind kind;
        final String label;
        final Icons.Glyph icon;
        final int group;
        final float base;   // natural width for fixed-size items
        float w;
        float x;
        Color color;
        boolean custom;
        BooleanSupplier onActivate = () -> true;  // false keeps the old selection
        BooleanSupplier enabled = () -> true;

        Item(Kind kind, String label, Icons.Glyph icon, int group, float w) {
            this.kind = kind;
            this.label = label;
            this.icon = icon;
            this.group = group;
            this.base = w;
            this.w = w;
        }

        float center() {
            return x + w / 2f;
        }

        public String getLabel() {
            return label;
        }

        public Color getColor() {
            return color;
        }

        public void setColor(Color color) {
            this.color = color;
        }

        public void setEnabled(BooleanSupplier enabled) {
            this.enabled = enabled;
        }

        public boolean isEnabled() {
            return enabled.getAsBoolean();
        }
    }

    /** Drag from the grip, screen coords. */
    public interface GripListener {
        void pressed(Point screen);

        void dragged(Point screen);

        void released(Point screen);
    }

    private static final class Platter {
        final Spring x = new Spring();
        final Spring w = new Spring();
    }

    private static final class Blurred {
        long key;
        BufferedImage img;
    }

    private final Supplier<Point> origin;
    private final Consumer<Graphics2D> backdrop;
    private final LongSupplier version;
    private final Font font;
    private final List<Item> items = new ArrayList<>();
    private final Map<Integer, Item> selected = new HashMap<>();
    private final Map<Integer, Platter> platters = new HashMap<>();
    private float capW;
    private final float capH = CAP_H;
    private boolean compact;
    private float squeeze = 1f;   // < 1 when even icons-only doesn't fit
    private boolean vertical;     // standing up on a side edge; drawn as a flipped horizontal bar
    private static final AffineTransform FLIP = new AffineTransform(0, 1, 1, 0, 0, 0);  // swaps x and y
    private GripListener gripListener;

    private Item hoverItem;
    private Item pressItem;
    private boolean dragSwitched;
    private float mouseX;
    private final Spring press = new Spring();
    private float pressTarget;
    private float glow;
    private final Point2D.Float glowAt = new Point2D.Float();
    private float appear = 1f;
    private float appearTarget = 1f;
    private final Spring lensX = new Spring();
    private final Spring lensW = new Spring();
    private float lensAlpha;

    private final Timer timer = new Timer(FRAME_MS, e -> step());
    private long lastNanos;
    private final Map<Double, BufferedImage> shadowCaches = new HashMap<>();
    private long prevKey;
    private long glassKey;
    private long sceneKey;
    private BufferedImage glassCache;
    private BufferedImage sceneCache;
    private final Map<Double, Blurred> blurCaches = new HashMap<>();

    public GlassBar(Font font, JComponent reference, Consumer<Graphics2D> backdrop, LongSupplier version) {
        this(font, (Supplier<Point>) null, backdrop, version, reference);
    }

    // origin = where this bar's top-left is in the backdrop's coords
    public GlassBar(Font font, Supplier<Point> origin, Consumer<Graphics2D> backdrop, LongSupplier version) {
        this(font, origin, backdrop, version, null);
    }

    private GlassBar(Font font, Supplier<Point> origin, Consumer<Graphics2D> backdrop, LongSupplier version,
                     JComponent reference) {
        this.font = font;
        this.origin = origin != null ? origin
                : () -> SwingUtilities.convertPoint(this, 0, 0, reference);
        this.backdrop = backdrop;
        this.version = version;
        setOpaque(false);
        setToolTipText("");
        MouseAdapter m = new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent e) {
                if (!SwingUtilities.isLeftMouseButton(e)) {
                    return;
                }
                pressItem = itemAt(mainOf(e));
                dragSwitched = false;
                glowAt.setLocation(mainOf(e), crossOf(e));
                glow = 1f;
                pressTarget = 1f;
                timer.start();
                if (pressItem != null && pressItem.kind == Kind.GRIP && gripListener != null) {
                    gripListener.pressed(e.getLocationOnScreen());
                }
            }

            @Override
            public void mouseReleased(MouseEvent e) {
                Item it = itemAt(mainOf(e));
                Item pressed = pressItem;
                pressItem = null;
                pressTarget = 0f;
                timer.start();
                if (pressed != null && pressed.kind == Kind.GRIP) {
                    if (gripListener != null) {
                        gripListener.released(e.getLocationOnScreen());
                    }
                    return;
                }
                if (!dragSwitched && it != null && it == pressed && contains(e.getPoint())) {
                    activate(it);
                }
                dragSwitched = false;
            }

            @Override
            public void mouseDragged(MouseEvent e) {
                if (pressItem != null && pressItem.kind == Kind.GRIP) {
                    if (gripListener != null) {
                        gripListener.dragged(e.getLocationOnScreen());
                    }
                    return;
                }
                glowAt.setLocation(mainOf(e), crossOf(e));
                mouseX = mainOf(e);
                // drag along a segmented control switches live
                if (pressItem != null && pressItem.kind == Kind.SEGMENT) {
                    Item target = segmentNear(pressItem.group, mainOf(e));
                    if (target != null) {
                        hoverItem = target;
                        if (selected.get(target.group) != target && target.enabled.getAsBoolean()) {
                            activate(target);
                            dragSwitched = true;
                        }
                    }
                }
                timer.start();
            }

            @Override
            public void mouseMoved(MouseEvent e) {
                mouseX = mainOf(e);
                hoverItem = itemAt(mainOf(e));
                boolean onGrip = hoverItem != null && hoverItem.kind == Kind.GRIP;
                setCursor(Cursor.getPredefinedCursor(onGrip ? Cursor.MOVE_CURSOR : Cursor.DEFAULT_CURSOR));
                timer.start();
            }

            @Override
            public void mouseExited(MouseEvent e) {
                hoverItem = null;
                timer.start();
            }
        };
        addMouseListener(m);
        addMouseMotionListener(m);
    }

    // ---- building

    public Item segment(String label, Icons.Glyph icon, int group, BooleanSupplier onActivate) {
        Item it = new Item(Kind.SEGMENT, label, icon, group, 0);
        it.onActivate = onActivate;
        items.add(it);
        return it;
    }

    public Item action(String label, Icons.Glyph icon, Runnable r, BooleanSupplier enabled) {
        Item it = new Item(Kind.ACTION, label, icon, -1, 0);
        it.onActivate = () -> {
            r.run();
            return true;
        };
        it.enabled = enabled;
        items.add(it);
        return it;
    }

    public Item swatch(Color c, int group, BooleanSupplier onActivate) {
        Item it = new Item(Kind.SWATCH, null, null, group, 36);
        it.color = c;
        it.onActivate = onActivate;
        items.add(it);
        return it;
    }

    public Item customSwatch(int group, BooleanSupplier onActivate) {
        Item it = swatch(null, group, onActivate);
        it.custom = true;
        return it;
    }

    public void separator() {
        items.add(new Item(Kind.SEPARATOR, null, null, -1, 13));
    }

    public void grip(GripListener listener) {
        gripListener = listener;
        items.add(new Item(Kind.GRIP, null, Icons::grip, -1, GRIP_W));
    }

    /** Measures labels and lays the items out left to right. */
    public void finish() {
        FontRenderContext frc = new FontRenderContext(null, true, true);
        float x = 0;
        for (Item it : items) {
            if (it.kind == Kind.SEGMENT || it.kind == Kind.ACTION) {
                if (compact || vertical || it.label == null) {
                    it.w = Math.max(minWidth(it), COMPACT_W * squeeze);
                } else {
                    float text = (float) font.getStringBounds(it.label, frc).getWidth();
                    it.w = Math.max(84, ITEM_PAD_X * 2 + ICON + ICON_GAP + text);
                }
            } else {
                it.w = Math.max(minWidth(it), it.base * squeeze);
            }
            it.x = x;
            x += it.w;
        }
        capW = x + 2 * PAD;
        for (Map.Entry<Integer, Platter> e : platters.entrySet()) {
            Item t = selected.get(e.getKey());
            e.getValue().x.x = t.x;
            e.getValue().w.x = t.w;
        }
        shadowCaches.clear();
        setSize(getPreferredSize());
        repaint();
    }

    // narrowest an item gets when squeezed
    private static float minWidth(Item it) {
        switch (it.kind) {
            case SEGMENT:
            case ACTION:
                return ICON + 10;
            case SWATCH:
                return 20;
            case SEPARATOR:
                return 7;
            default:
                return 14;
        }
    }

    /**
     * Fits the capsule into maxCapW: labels first, then icons only, then every item narrower
     * together (height and icon size stay).
     */
    public void fitTo(int maxCapW) {
        boolean c = vertical || capsuleWidth(false) > maxCapW;  // standing up is icons only
        float s = 1f;
        if (c) {
            float natural = capsuleWidth(true) - 2 * PAD;
            s = Math.max(0f, Math.min(1f, (maxCapW - 2 * PAD) / natural));
        }
        if (c != compact || Math.abs(s - squeeze) > 0.002f) {
            compact = c;
            squeeze = s;
            finish();
        }
    }

    public float getSqueeze() {
        return squeeze;
    }

    /** Icons only, labels go to the tooltip. */
    public void setCompact(boolean compact) {
        if (this.compact != compact) {
            this.compact = compact;
            finish();
        }
    }

    public boolean isCompact() {
        return compact;
    }

    /** Capsule width with or without labels, whatever the current mode. */
    public int capsuleWidth(boolean compactMode) {
        boolean was = compact;
        compact = compactMode;
        float x = 0;
        FontRenderContext frc = new FontRenderContext(null, true, true);
        for (Item it : items) {
            if (it.kind == Kind.SEGMENT || it.kind == Kind.ACTION) {
                x += compact || it.label == null ? COMPACT_W
                        : Math.max(84, ITEM_PAD_X * 2 + ICON + ICON_GAP
                        + (float) font.getStringBounds(it.label, frc).getWidth());
            } else {
                x += it.base;  // natural width, not squeezed
            }
        }
        compact = was;
        return (int) Math.ceil(x + 2 * PAD);
    }

    public int capsuleWidth() {
        return (int) Math.ceil(capW);
    }

    @Override
    public Dimension getPreferredSize() {
        int along = (int) Math.ceil(capW) + 2 * MARGIN;
        int across = (int) capH + 2 * MARGIN;
        return vertical ? new Dimension(across, along) : new Dimension(along, across);
    }

    /** Stands the bar up (icons only, still upright) or lays it back down. */
    public void setVertical(boolean vertical) {
        if (this.vertical != vertical) {
            this.vertical = vertical;
            hoverItem = null;
            finish();
        }
    }

    public boolean isVertical() {
        return vertical;
    }

    // along the bar / across it, in the flat layout's terms
    private int mainOf(MouseEvent e) {
        return vertical ? e.getY() : e.getX();
    }

    private int crossOf(MouseEvent e) {
        return vertical ? e.getX() : e.getY();
    }

    // size as if lying flat
    private int lw() {
        return vertical ? getHeight() : getWidth();
    }

    private int lh() {
        return vertical ? getWidth() : getHeight();
    }

    @Override
    public String getToolTipText(MouseEvent e) {
        Item it = itemAt(mainOf(e));
        if (it == null || it.label == null || !contains(e.getPoint())) {
            return null;
        }
        return compact || vertical ? it.label : null;
    }

    // ---- interaction

    /** Same as a click on the item. */
    public void activate(Item it) {
        if (!it.enabled.getAsBoolean()) {
            return;
        }
        if (it.kind == Kind.SEGMENT || it.kind == Kind.SWATCH) {
            if (it.onActivate.getAsBoolean()) {
                select(it);
            }
        } else if (it.kind == Kind.ACTION) {
            it.onActivate.getAsBoolean();
        }
        repaint();
    }

    public void select(Item it) {
        selected.put(it.group, it);
        if (it.kind == Kind.SEGMENT && !platters.containsKey(it.group)) {
            Platter p = new Platter();
            p.x.x = it.x;
            p.w.x = it.w;
            platters.put(it.group, p);
        }
        timer.start();
    }

    public void deselect(int group) {
        selected.remove(group);
        platters.remove(group);
        timer.start();
        repaint();
    }

    public boolean isSelected(Item it) {
        return selected.get(it.group) == it;
    }

    public void setShown(boolean shown) {
        appearTarget = shown ? 1f : 0f;
        if (!shown) {
            hoverItem = null;
        }
        timer.start();
    }

    private Item itemAt(int x) {
        float lx = x - MARGIN - PAD;
        for (Item it : items) {
            if (it.kind != Kind.SEPARATOR && lx >= it.x && lx < it.x + it.w) {
                return it;
            }
        }
        return null;
    }

    private Item segmentNear(int group, int x) {
        float lx = x - MARGIN - PAD;
        Item best = null;
        float bestDist = Float.MAX_VALUE;
        for (Item it : items) {
            if (it.kind != Kind.SEGMENT || it.group != group) {
                continue;
            }
            float d = lx < it.x ? it.x - lx : lx >= it.x + it.w ? lx - (it.x + it.w) : 0f;
            if (d < bestDist) {
                bestDist = d;
                best = it;
            }
        }
        return best;
    }

    // only the capsule takes clicks, the margin passes through
    @Override
    public boolean contains(int x, int y) {
        return appearTarget > 0f && (vertical ? capsule(0).contains(y, x) : capsule(0).contains(x, y));
    }

    private RoundRectangle2D.Float capsule(float grow) {
        float w = capW + 2 * grow;
        float h = capH + 2 * grow;
        return new RoundRectangle2D.Float(MARGIN - grow, MARGIN - grow, w, h, h, h);
    }

    private void step() {
        long now = System.nanoTime();
        float f = lastNanos == 0 ? FRAME_MS / REF_MS
                : Math.max(0.1f, Math.min(2f, (now - lastNanos) / (REF_MS * 1e6f)));
        lastNanos = now;
        boolean settled = true;
        for (Map.Entry<Integer, Platter> e : platters.entrySet()) {
            Item t = selected.get(e.getKey());
            settled &= e.getValue().x.step(t.x, 0.16f, 0.70f, f);
            settled &= e.getValue().w.step(t.w, 0.16f, 0.70f, f);
        }
        settled &= press.step(pressTarget, 0.30f, 0.62f, f);
        if (pressTarget == 0f) {
            glow *= (float) Math.pow(0.86, f);
            if (glow < 0.01f) {
                glow = 0;
            } else {
                settled = false;
            }
        }
        appear += (appearTarget - appear) * ease(0.22f, f);
        if (Math.abs(appearTarget - appear) < 0.01f) {
            appear = appearTarget;
        } else {
            settled = false;
        }

        Item h = hoverItem != null && hoverItem.enabled.getAsBoolean() && hoverItem.kind != Kind.GRIP
                && appearTarget > 0 ? hoverItem : null;
        float alphaTarget = h != null ? 1f : 0f;
        if (h != null) {
            float cx = h.center() + ((mouseX - MARGIN - PAD) - h.center()) * HOVER_STICK;
            float w = h.w + HOVER_GROW_X;
            if (lensAlpha < 0.05f) {
                lensX.x = cx;
                lensW.x = w;
                lensX.v = 0;
                lensW.v = 0;
            }
            settled &= lensX.step(cx, 0.20f, 0.68f, f);
            settled &= lensW.step(w, 0.20f, 0.68f, f);
        }
        lensAlpha += (alphaTarget - lensAlpha) * ease(0.25f, f);
        if (Math.abs(alphaTarget - lensAlpha) < 0.01f) {
            lensAlpha = alphaTarget;
        } else {
            settled = false;
        }

        if (settled) {
            timer.stop();
            lastNanos = 0;
        }
        repaint();
    }

    static float ease(float k, float f) {
        return 1f - (float) Math.pow(1f - k, f);
    }

    // ---- painting

    @Override
    protected void paintComponent(Graphics g) {
        if (appear <= 0f || capW <= 0) {
            return;
        }
        Graphics2D g2 = (Graphics2D) g.create();
        double s = g2.getTransform().getScaleX();
        if (vertical) {
            g2.transform(FLIP);  // everything below draws the flat bar
        }
        int bw = lw();
        int bh = lh();
        int pw = (int) Math.ceil(bw * s);
        int ph = (int) Math.ceil(bh * s);

        if (appear < 1f) {
            g2.setComposite(AlphaComposite.SrcOver.derive(appear));
            double k = 0.9 + 0.1 * appear;
            g2.translate(bw / 2.0, bh / 2.0);
            g2.scale(k, k);
            g2.translate(-bw / 2.0, -bh / 2.0);
        }

        long key = stateKey(s);
        if (glassCache == null || glassKey != key) {
            glassCache = renderGlass(s, pw, ph, backdropImage(s, pw, ph, 0, 0), 0, 0);
            glassKey = key;
        }
        g2.drawImage(glassCache, 0, 0, bw, bh, null);

        if (lensAlpha > 0.01f) {
            double ls = s * HOVER_MAG;
            RoundRectangle2D.Float lens = lensShape();
            if (sceneCache != null && sceneKey == key) {
                paintHoverLens(g2, s, ls, sceneCache, 0, 0, lens);
            } else if (key == prevKey) {
                sceneCache = renderScene(ls, (int) Math.ceil(bw * ls), (int) Math.ceil(bh * ls), 0, 0);
                sceneKey = key;
                paintHoverLens(g2, s, ls, sceneCache, 0, 0, lens);
            } else {
                int pad = (int) Math.ceil(HOVER_EDGE_K * (1 + HOVER_DISPERSION)) + 3;
                int ox = Math.max(0, (int) Math.floor((lens.x - pad) * ls));
                int oy = Math.max(0, (int) Math.floor((lens.y - pad) * ls));
                int cw = Math.min((int) Math.ceil(bw * ls), (int) Math.ceil((lens.x + lens.width + pad) * ls)) - ox;
                int ch = Math.min((int) Math.ceil(bh * ls), (int) Math.ceil((lens.y + lens.height + pad) * ls)) - oy;
                if (cw > 0 && ch > 0) {
                    paintHoverLens(g2, s, ls, renderScene(ls, cw, ch, ox, oy), ox, oy, lens);
                }
            }
        }
        prevKey = key;
        g2.dispose();
    }

    private BufferedImage renderScene(double ls, int cw, int ch, int ox, int oy) {
        BufferedImage raw = backdropImage(ls, cw, ch, ox, oy);
        BufferedImage glass = renderGlass(ls, cw, ch, raw, ox, oy);
        BufferedImage scene = new BufferedImage(cw, ch, BufferedImage.TYPE_INT_ARGB);
        Graphics2D sg = scene.createGraphics();
        sg.drawImage(raw, 0, 0, null);
        sg.drawImage(glass, 0, 0, null);
        sg.dispose();
        return scene;
    }

    // where this bar sits in the backdrop's coords
    private Point origin() {
        return origin.get();
    }

    private long stateKey(double s) {
        long h = Double.doubleToLongBits(s);
        h = h * 31 + Float.floatToIntBits(appear);
        h = h * 31 + Float.floatToIntBits(press.x);
        h = h * 31 + Float.floatToIntBits(glow);
        h = h * 31 + Float.floatToIntBits(glowAt.x);
        h = h * 31 + Float.floatToIntBits(glowAt.y);
        for (Map.Entry<Integer, Platter> e : platters.entrySet()) {
            h = h * 31 + e.getKey();
            h = h * 31 + Float.floatToIntBits(e.getValue().x.x);
            h = h * 31 + Float.floatToIntBits(e.getValue().w.x);
        }
        for (Map.Entry<Integer, Item> e : selected.entrySet()) {
            h = h * 31 + e.getKey() * 17 + System.identityHashCode(e.getValue());
        }
        for (Item it : items) {
            h = h * 31 + (it.enabled.getAsBoolean() ? 1 : 0);
            if (it.color != null) {
                h = h * 31 + it.color.getRGB();
            }
        }
        h = h * 31 + (compact ? 1 : 0) + (vertical ? 2 : 0);
        h = h * 31 + version.getAsLong();
        Point o = origin();
        h = h * 31 + o.x;
        h = h * 31 + o.y;
        return h * 31 + getWidth() * 7919L + getHeight();
    }

    private BufferedImage renderGlass(double s, int pw, int ph, BufferedImage raw, int ox, int oy) {
        BufferedImage glass = new BufferedImage(pw, ph, BufferedImage.TYPE_INT_ARGB);
        Graphics2D gg = glass.createGraphics();
        hints(gg);
        gg.translate(-ox, -oy);
        gg.scale(s, s);
        paintGlass(gg, s, raw, ox, oy);
        gg.dispose();
        return glass;
    }

    static void hints(Graphics2D g) {
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);
        g.setRenderingHint(RenderingHints.KEY_FRACTIONALMETRICS, RenderingHints.VALUE_FRACTIONALMETRICS_ON);
    }

    // backdrop in the flat layout's coords (flipped when standing up)
    private BufferedImage backdropImage(double s, int pw, int ph, int ox, int oy) {
        if (!vertical) {
            return backdropReal(s, pw, ph, ox, oy);
        }
        return flip(backdropReal(s, ph, pw, oy, ox));
    }

    private static BufferedImage flip(BufferedImage src) {
        int w = src.getWidth();
        int h = src.getHeight();
        int[] in = src.getRGB(0, 0, w, h, null, 0, w);
        int[] out = new int[w * h];
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                out[x * h + y] = in[y * w + x];
            }
        }
        BufferedImage res = new BufferedImage(h, w, BufferedImage.TYPE_INT_ARGB);
        res.setRGB(0, 0, h, w, out, 0, h);
        return res;
    }

    // what's under the bar, at scale s, window starting at device px (ox, oy)
    private BufferedImage backdropReal(double s, int pw, int ph, int ox, int oy) {
        BufferedImage src = new BufferedImage(Math.max(1, pw), Math.max(1, ph), BufferedImage.TYPE_INT_ARGB);
        Graphics2D bg = src.createGraphics();
        bg.translate(-ox, -oy);
        bg.scale(s, s);
        bg.setColor(BoardTheme.CANVAS);
        bg.fillRect(0, 0, getWidth(), getHeight());
        Point o = origin();
        bg.translate(-o.x, -o.y);
        bg.clipRect(o.x + (int) Math.floor(ox / s), o.y + (int) Math.floor(oy / s),
                (int) Math.ceil(pw / s) + 1, (int) Math.ceil(ph / s) + 1);
        backdrop.accept(bg);
        bg.dispose();
        return src;
    }

    private static void drawPx(Graphics2D g, BufferedImage img, double s, int ox, int oy) {
        g.drawImage(img, new AffineTransform(1 / s, 0, 0, 1 / s, ox / s, oy / s), null);
    }

    private void paintGlass(Graphics2D g2, double s, BufferedImage raw, int ox, int oy) {
        RoundRectangle2D.Float cap = capsule(press.x * 3f);
        int pw = raw.getWidth();
        int ph = raw.getHeight();

        BufferedImage shadow = shadowCaches.computeIfAbsent(s, this::buildShadow);
        int sw = Math.min(pw, shadow.getWidth() - ox);
        int sh = Math.min(ph, shadow.getHeight() - oy);
        if (sw > 0 && sh > 0) {
            drawPx(g2, shadow.getSubimage(ox, oy, sw, sh), s, ox, oy);
        }
        Point o = origin();
        long bk = Objects.hash(ox, oy, pw, ph, version.getAsLong(), o.x, o.y, getWidth());
        Blurred bl = blurCaches.computeIfAbsent(s, k -> new Blurred());
        if (bl.img == null || bl.key != bk) {
            bl.img = gaussian(raw, (float) (BLUR_SIGMA * s));
            bl.key = bk;
        }
        drawPx(g2, refract(bl.img, s, cap, appear, ox, oy), s, ox, oy);

        g2.setColor(BoardTheme.TINT);
        g2.fill(cap);
        g2.setPaint(new GradientPaint(0, cap.y, new Color(255, 255, 255, BoardTheme.GLOSS),
                0, cap.y + cap.height * 0.55f, new Color(255, 255, 255, 0)));
        g2.fill(cap);

        float scale = cap.width / capW;
        for (Platter p : platters.values()) {
            g2.setColor(BoardTheme.PLATTER);
            g2.fill(slot(p.x.x, p.w.x, cap, scale));
        }

        if (glow > 0.01f) {
            float r = 70f + 50f * (1f - glow);
            g2.setPaint(new RadialGradientPaint(glowAt, r, new float[]{0f, 1f},
                    new Color[]{new Color(1f, 1f, 1f, 0.40f * glow), new Color(1f, 1f, 1f, 0f)}));
            g2.fill(cap);
        }

        g2.setStroke(new BasicStroke(1f));
        g2.setColor(BoardTheme.EDGE);
        g2.draw(inset(cap, 0.5f));
        g2.setStroke(new BasicStroke(1.4f));
        g2.setPaint(rimPaint(cap, BoardTheme.RIM_TOP, BoardTheme.RIM_BOTTOM));
        g2.draw(inset(cap, 1.2f));

        BufferedImage fg = new BufferedImage(pw, ph, BufferedImage.TYPE_INT_ARGB);
        Graphics2D fgG = fg.createGraphics();
        hints(fgG);
        fgG.translate(-ox, -oy);
        fgG.scale(s, s);
        paintForeground(fgG, cap, scale);
        fgG.dispose();
        drawPx(g2, halo(fg, (float) (2.2 * s)), s, ox, oy);
        drawPx(g2, fg, s, ox, oy);
    }

    private static Paint rimPaint(RoundRectangle2D.Float r, int top, int bottom) {
        return new LinearGradientPaint(r.x, r.y, r.x + r.width * 0.35f, r.y + r.height * 2.2f,
                new float[]{0f, 0.35f, 0.7f, 1f},
                new Color[]{new Color(255, 255, 255, top), new Color(255, 255, 255, 0),
                    new Color(255, 255, 255, 0), new Color(255, 255, 255, bottom)});
    }

    private void paintForeground(Graphics2D g2, RoundRectangle2D.Float cap, float scale) {
        g2.setFont(font);
        FontMetrics fm = g2.getFontMetrics();
        for (Item it : items) {
            Rectangle2D b = slot(it.x, it.w, cap, scale).getBounds2D();
            if (it.kind == Kind.SEGMENT || it.kind == Kind.ACTION) {
                Color c = !it.enabled.getAsBoolean() ? BoardTheme.LABEL_DISABLED
                        : selected.get(it.group) == it ? BoardTheme.LABEL_SELECTED : BoardTheme.LABEL;
                g2.setColor(c);
                boolean iconOnly = compact || vertical || it.label == null;
                float textW = iconOnly ? 0 : fm.stringWidth(it.label);
                double total = iconOnly ? ICON * scale : (ICON + ICON_GAP) * scale + textW;
                double x0 = b.getCenterX() - total / 2;
                AffineTransform t = g2.getTransform();
                g2.translate(x0 + ICON * scale / 2, b.getCenterY());
                if (vertical) {
                    g2.transform(FLIP);  // flipped twice = upright
                }
                g2.scale(scale * ICON / 20.0, scale * ICON / 20.0);
                it.icon.paint(g2);
                g2.setTransform(t);
                if (!iconOnly) {
                    g2.drawString(it.label, (float) (x0 + (ICON + ICON_GAP) * scale),
                            (float) (b.getCenterY() - fm.getHeight() / 2.0 + fm.getAscent()));
                }
            } else if (it.kind == Kind.SWATCH) {
                paintSwatch(g2, it, b.getCenterX(), b.getCenterY(), scale);
            } else if (it.kind == Kind.SEPARATOR) {
                g2.setColor(BoardTheme.EDGE);
                g2.setStroke(new BasicStroke(1f));
                double h = 20 * scale;
                g2.draw(new Line2D.Double(b.getCenterX(), b.getCenterY() - h / 2,
                        b.getCenterX(), b.getCenterY() + h / 2));
            } else if (it.kind == Kind.GRIP) {
                g2.setColor(BoardTheme.LABEL_DISABLED);
                AffineTransform t = g2.getTransform();
                g2.translate(b.getCenterX() + 2 * scale, b.getCenterY());
                g2.scale(scale, scale);
                it.icon.paint(g2);
                g2.setTransform(t);
            }
        }
    }

    private void paintSwatch(Graphics2D g2, Item it, double cx, double cy, float scale) {
        double d = Math.min(22, it.w - 8) * scale;  // dot shrinks with a squeezed bar
        Ellipse2D dot = new Ellipse2D.Double(cx - d / 2, cy - d / 2, d, d);
        if (it.custom) {
            for (int i = 0; i < 24; i++) {
                g2.setColor(Color.getHSBColor(i / 24f, 0.75f, 0.95f));
                g2.fill(new Arc2D.Double(dot.getBounds2D(), 90 - i * 15, -15.5, Arc2D.PIE));
            }
            double di = d - 7 * scale;
            g2.setColor(it.color != null ? it.color : BoardTheme.CANVAS);
            g2.fill(new Ellipse2D.Double(cx - di / 2, cy - di / 2, di, di));
        } else {
            g2.setColor(it.color);
            g2.fill(dot);
        }
        g2.setColor(BoardTheme.EDGE);
        g2.setStroke(new BasicStroke(1f));
        g2.draw(dot);
        if (selected.get(it.group) == it) {
            double dr = d + 8 * scale;
            g2.setColor(BoardTheme.LABEL_SELECTED);
            g2.setStroke(new BasicStroke(2f));
            g2.draw(new Ellipse2D.Double(cx - dr / 2, cy - dr / 2, dr, dr));
        }
    }

    private RoundRectangle2D.Float lensShape() {
        float lh = capH + 2 * HOVER_GROW_Y + press.x * 4f;
        float lw = Math.max(lh, lensW.x + Math.min(Math.abs(lensX.v) * 1.2f, 28f) + press.x * 4f);
        float lcx = MARGIN + PAD + lensX.x;
        float lcy = MARGIN + capH / 2f;
        return new RoundRectangle2D.Float(lcx - lw / 2, lcy - lh / 2, lw, lh, lh, lh);
    }

    // magnifying droplet over the hovered button
    private void paintHoverLens(Graphics2D g2, double s, double ls, BufferedImage scene, int ox, int oy,
                                RoundRectangle2D.Float lens) {
        float lh = lens.height;
        float lw = lens.width;
        float lcx = lens.x + lw / 2;
        float lcy = lens.y + lh / 2;

        Composite base = g2.getComposite();
        float baseAlpha = base instanceof AlphaComposite ? ((AlphaComposite) base).getAlpha() : 1f;
        g2.setComposite(AlphaComposite.SrcOver.derive(baseAlpha * lensAlpha));
        hints(g2);

        for (int i = 4; i >= 1; i--) {
            g2.setColor(new Color(0f, 0f, 0f, BoardTheme.SHADOW * 0.16f));
            g2.fill(new RoundRectangle2D.Float(lens.x - i, lens.y + 2 - i + 3, lens.width + 2 * i,
                    lens.height + 2 * i, lens.height + 2 * i, lens.height + 2 * i));
        }

        int pw = (int) Math.ceil(lw() * s);
        int ph = (int) Math.ceil(lh() * s);
        int sw = scene.getWidth();
        int sh = scene.getHeight();
        double r = ls / s;
        int[] in = scene.getRGB(0, 0, sw, sh, null, 0, sw);
        int[] out = new int[pw * ph];
        double cx = lcx * s;
        double cy = lcy * s;
        double radius = lh / 2.0 * s;
        double x0 = cx - (lw / 2.0 - lh / 2.0) * s;
        double x1 = cx + (lw / 2.0 - lh / 2.0) * s;
        double band = HOVER_EDGE_BAND * s;
        double k = HOVER_EDGE_K * s;
        int yMin = Math.max(0, (int) (cy - radius) - 1);
        int yMax = Math.min(ph, (int) Math.ceil(cy + radius) + 1);
        int xMin = Math.max(0, (int) (x0 - radius) - 1);
        int xMax = Math.min(pw, (int) Math.ceil(x1 + radius) + 1);
        for (int y = yMin; y < yMax; y++) {
            double py = y + 0.5;
            for (int x = xMin; x < xMax; x++) {
                double px = x + 0.5;
                double qx = Math.max(x0, Math.min(x1, px));
                double dx = px - qx;
                double dy = py - cy;
                double len = Math.sqrt(dx * dx + dy * dy);
                double d = len - radius;
                if (d > 0.5) {
                    continue;
                }
                double coverage = Math.min(1.0, 0.5 - d);
                double bx = (cx + (px - cx) / HOVER_MAG) * r - ox;
                double by = (cy + (py - cy) / HOVER_MAG) * r - oy;
                double off = 0;
                double nx = 0;
                double ny = 0;
                if (-d < band && len > 1e-6) {
                    double t = 1.0 + d / band;
                    off = t * t * k;
                    nx = dx / len;
                    ny = dy / len;
                }
                double oR = off * (1 + HOVER_DISPERSION);
                double oB = off * (1 - HOVER_DISPERSION);
                int cr = bilinear(in, sw, sh, bx + nx * oR * r - 0.5, by + ny * oR * r - 0.5);
                int cg = bilinear(in, sw, sh, bx + nx * off * r - 0.5, by + ny * off * r - 0.5);
                int cb = bilinear(in, sw, sh, bx + nx * oB * r - 0.5, by + ny * oB * r - 0.5);
                int a = (int) Math.round(255 * coverage);
                out[y * pw + x] = (a << 24) | (cr & 0xFF0000) | (cg & 0xFF00) | (cb & 0xFF);
            }
        }
        BufferedImage img = new BufferedImage(pw, ph, BufferedImage.TYPE_INT_ARGB);
        img.setRGB(0, 0, pw, ph, out, 0, pw);
        g2.drawImage(img, 0, 0, lw(), lh(), null);

        g2.setPaint(new GradientPaint(0, lens.y, new Color(255, 255, 255, 40),
                0, lens.y + lens.height * 0.5f, new Color(255, 255, 255, 0)));
        g2.fill(lens);
        g2.setStroke(new BasicStroke(1f));
        g2.setColor(new Color(0, 0, 0, 30));
        g2.draw(inset(lens, 0.5f));
        g2.setStroke(new BasicStroke(1.6f));
        g2.setPaint(rimPaint(lens, 255, 170));
        g2.draw(inset(lens, 1.3f));
        g2.setComposite(base);
    }

    private RoundRectangle2D.Float slot(float x, float w, RoundRectangle2D.Float cap, float scale) {
        float sx = cap.x + (PAD + x) * scale;
        float sy = cap.y + PAD * scale;
        float sw = w * scale;
        float sh = SEG_H * scale;
        return new RoundRectangle2D.Float(sx, sy, sw, sh, sh, sh);
    }

    private static RoundRectangle2D.Float inset(RoundRectangle2D.Float r, float d) {
        return new RoundRectangle2D.Float(r.x + d, r.y + d, r.width - 2 * d, r.height - 2 * d,
                r.arcwidth - 2 * d, r.archeight - 2 * d);
    }

    // bends the backdrop at the rim, clips to the capsule
    private BufferedImage refract(BufferedImage src, double s, RoundRectangle2D.Float cap, float strength,
                                  int ox, int oy) {
        int pw = src.getWidth();
        int ph = src.getHeight();
        int[] in = src.getRGB(0, 0, pw, ph, null, 0, pw);
        int[] out = new int[pw * ph];
        double radius = cap.height / 2.0 * s;
        double cy = (cap.y + cap.height / 2.0) * s - oy;
        double x0 = (cap.x + cap.height / 2.0) * s - ox;
        double x1 = (cap.x + cap.width - cap.height / 2.0) * s - ox;
        double band = LENS_BAND * s;
        double k = LENS_STRENGTH * s * strength;
        int yMin = Math.max(0, (int) (cy - radius) - 1);
        int yMax = Math.min(ph, (int) Math.ceil(cy + radius) + 1);
        int xMin = Math.max(0, (int) (x0 - radius) - 1);
        int xMax = Math.min(pw, (int) Math.ceil(x1 + radius) + 1);
        for (int y = yMin; y < yMax; y++) {
            double py = y + 0.5;
            for (int x = xMin; x < xMax; x++) {
                double px = x + 0.5;
                double qx = Math.max(x0, Math.min(x1, px));
                double dx = px - qx;
                double dy = py - cy;
                double len = Math.sqrt(dx * dx + dy * dy);
                double d = len - radius;
                if (d > 0.5) {
                    continue;
                }
                double coverage = Math.min(1.0, 0.5 - d);
                double sx = px;
                double sy = py;
                if (-d < band && len > 1e-6) {
                    double t = 1.0 + d / band;
                    double off = t * t * k;
                    sx += dx / len * off;
                    sy += dy / len * off;
                }
                int c = bilinear(in, pw, ph, sx - 0.5, sy - 0.5);
                int a = (int) Math.round(((c >>> 24) & 0xFF) * coverage);
                out[y * pw + x] = (a << 24) | (c & 0xFFFFFF);
            }
        }
        BufferedImage res = new BufferedImage(pw, ph, BufferedImage.TYPE_INT_ARGB);
        res.setRGB(0, 0, pw, ph, out, 0, pw);
        return res;
    }

    // soft canvas-coloured halo behind the glyphs
    private static BufferedImage halo(BufferedImage fg, float sigma) {
        int w = fg.getWidth();
        int h = fg.getHeight();
        BufferedImage out = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
        int[] px = fg.getRGB(0, 0, w, h, null, 0, w);
        int minX = w;
        int minY = h;
        int maxX = -1;
        int maxY = -1;
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                if ((px[y * w + x] >>> 24) != 0) {
                    minX = Math.min(minX, x);
                    maxX = Math.max(maxX, x);
                    minY = Math.min(minY, y);
                    maxY = Math.max(maxY, y);
                }
            }
        }
        if (maxX < 0) {
            return out;
        }
        int pad = (int) Math.ceil(sigma * 3);
        int x0 = Math.max(0, minX - pad);
        int y0 = Math.max(0, minY - pad);
        int x1 = Math.min(w, maxX + 1 + pad);
        int y1 = Math.min(h, maxY + 1 + pad);
        int bw = x1 - x0;
        int bh = y1 - y0;
        int hc = BoardTheme.HALO.getRGB() & 0xFFFFFF;
        float ha = BoardTheme.HALO.getAlpha() / 255f;
        float[] a = new float[bw * bh];
        float[] tmp = new float[bw * bh];
        for (int y = 0; y < bh; y++) {
            for (int x = 0; x < bw; x++) {
                a[y * bw + x] = (px[(y + y0) * w + x + x0] >>> 24) * ha;
            }
        }
        double ideal = Math.sqrt(12.0 * sigma * sigma / 3 + 1);
        int wl = (int) Math.floor(ideal);
        if (wl % 2 == 0) {
            wl--;
        }
        if (wl < 1) {
            wl = 1;
        }
        int wu = wl + 2;
        int m = (int) Math.round((12.0 * sigma * sigma - 3 * wl * wl - 12 * wl - 9) / (-4.0 * wl - 4));
        for (int pass = 0; pass < 3; pass++) {
            boxBlur(a, tmp, bw, bh, ((pass < m ? wl : wu) - 1) / 2);
        }
        int[] o = new int[bw * bh];
        for (int i = 0; i < o.length; i++) {
            int al = Math.min(255, Math.round(a[i] * 2));
            o[i] = (al << 24) | hc;
        }
        out.setRGB(x0, y0, bw, bh, o, 0, bw);
        return out;
    }

    private static void boxBlur(float[] a, float[] tmp, int w, int h, int r) {
        float inv = 1f / (2 * r + 1);
        for (int y = 0; y < h; y++) {
            int b = y * w;
            float sum = 0;
            for (int i = 0; i <= Math.min(r, w - 1); i++) {
                sum += a[b + i];
            }
            for (int x = 0; x < w; x++) {
                tmp[b + x] = sum * inv;
                if (x + r + 1 < w) {
                    sum += a[b + x + r + 1];
                }
                if (x - r >= 0) {
                    sum -= a[b + x - r];
                }
            }
        }
        for (int x = 0; x < w; x++) {
            float sum = 0;
            for (int i = 0; i <= Math.min(r, h - 1); i++) {
                sum += tmp[i * w + x];
            }
            for (int y = 0; y < h; y++) {
                a[y * w + x] = sum * inv;
                if (y + r + 1 < h) {
                    sum += tmp[(y + r + 1) * w + x];
                }
                if (y - r >= 0) {
                    sum -= tmp[(y - r) * w + x];
                }
            }
        }
    }

    private static int bilinear(int[] px, int w, int h, double x, double y) {
        x = Math.max(0, Math.min(w - 1.001, x));
        y = Math.max(0, Math.min(h - 1.001, y));
        int ix = (int) x;
        int iy = (int) y;
        double fx = x - ix;
        double fy = y - iy;
        int c00 = px[iy * w + ix];
        int c10 = px[iy * w + Math.min(w - 1, ix + 1)];
        int c01 = px[Math.min(h - 1, iy + 1) * w + ix];
        int c11 = px[Math.min(h - 1, iy + 1) * w + Math.min(w - 1, ix + 1)];
        int r = 0;
        for (int sh = 0; sh <= 24; sh += 8) {
            double v = ((c00 >>> sh) & 0xFF) * (1 - fx) * (1 - fy) + ((c10 >>> sh) & 0xFF) * fx * (1 - fy)
                    + ((c01 >>> sh) & 0xFF) * (1 - fx) * fy + ((c11 >>> sh) & 0xFF) * fx * fy;
            r |= ((int) Math.round(v) & 0xFF) << sh;
        }
        return r;
    }

    private BufferedImage buildShadow(double s) {
        int pw = Math.max(1, (int) Math.ceil(lw() * s));
        int ph = Math.max(1, (int) Math.ceil(lh() * s));
        BufferedImage out = new BufferedImage(pw, ph, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = out.createGraphics();
        g.drawImage(shadowLayer(pw, ph, s, 0f, 1.5f, BoardTheme.SHADOW * 0.5f), 0, 0, null);
        g.drawImage(shadowLayer(pw, ph, s, 6f, 8f, BoardTheme.SHADOW), 0, 0, null);
        g.dispose();
        // cut the capsule out so the shadow doesn't darken what's under the glass
        Graphics2D c = out.createGraphics();
        c.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        c.scale(s, s);
        c.setComposite(AlphaComposite.Clear);
        c.fill(capsule(0));
        c.dispose();
        return out;
    }

    private BufferedImage shadowLayer(int pw, int ph, double s, float dy, float sigma, float alpha) {
        BufferedImage img = new BufferedImage(pw, ph, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = img.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.scale(s, s);
        g.setColor(new Color(0f, 0f, 0f, Math.min(1f, alpha)));
        RoundRectangle2D.Float c = capsule(0);
        c.y += dy;
        g.fill(c);
        g.dispose();
        return gaussian(img, (float) (sigma * s));
    }

    // separable gaussian, edges extended
    private static BufferedImage gaussian(BufferedImage src, float sigma) {
        if (sigma < 0.3f) {
            return src;
        }
        int r = (int) Math.ceil(sigma * 3);
        int w = src.getWidth();
        int h = src.getHeight();
        BufferedImage padded = new BufferedImage(w + 2 * r, h + 2 * r, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = padded.createGraphics();
        g.drawImage(src, r, r, null);
        g.drawImage(src.getSubimage(0, 0, w, 1), r, 0, w, r, null);
        g.drawImage(src.getSubimage(0, h - 1, w, 1), r, h + r, w, r, null);
        g.drawImage(padded.getSubimage(r, 0, 1, h + 2 * r), 0, 0, r, h + 2 * r, null);
        g.drawImage(padded.getSubimage(w + r - 1, 0, 1, h + 2 * r), w + r, 0, r, h + 2 * r, null);
        g.dispose();
        float[] k = new float[2 * r + 1];
        float sum = 0;
        for (int i = -r; i <= r; i++) {
            k[i + r] = (float) Math.exp(-(i * i) / (2 * sigma * sigma));
            sum += k[i + r];
        }
        for (int i = 0; i < k.length; i++) {
            k[i] /= sum;
        }
        BufferedImage a = new ConvolveOp(new Kernel(k.length, 1, k), ConvolveOp.EDGE_NO_OP, null)
                .filter(padded, null);
        BufferedImage b = new ConvolveOp(new Kernel(1, k.length, k), ConvolveOp.EDGE_NO_OP, null)
                .filter(a, null);
        return b.getSubimage(r, r, w, h);
    }
}
