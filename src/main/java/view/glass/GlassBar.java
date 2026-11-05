package view.glass;

import view.glass.GlassIcons.Glyph;

import javax.swing.JComponent;
import java.awt.AlphaComposite;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Composite;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.GradientPaint;
import java.awt.Graphics;
import java.awt.Graphics2D;
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
import java.awt.image.DataBufferInt;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.BooleanSupplier;

/**
 * One floating glass capsule holding segments, actions, ink swatches and separators, in an
 * approximation of Apple's Liquid Glass: the backdrop is bent along the rim, the selection platter
 * moves on an under-damped spring, a press lights up the glass where it was touched, and hovering a
 * button shows a magnifying droplet that sticks to it. Dragging along a segmented group switches live.
 *
 * <p>Build it with {@link #segment}, {@link #action}, {@link #swatch} and {@link #separator}, then
 * call {@link #finish}. Only the capsule takes the mouse; the margin around it passes clicks through.
 */
public class GlassBar extends JComponent {
    private static final int SEGMENT_HEIGHT = 40;
    private static final int PAD = 6;              // glass padding around the buttons
    /** The height of the capsule itself. */
    public static final int CAPSULE_HEIGHT = SEGMENT_HEIGHT + 2 * PAD;
    /** Room around the capsule for its shadow and the hover droplet. */
    public static final int MARGIN = 28;
    private static final int ICON = 18;
    private static final int ICON_GAP = 7;
    private static final int ITEM_PAD_X = 16;
    private static final int MIN_ITEM_WIDTH = 84;
    private static final int SWATCH_WIDTH = 36;
    private static final int SEPARATOR_WIDTH = 13;

    // hover droplet (estimates; Apple publishes no numbers)
    private static final float HOVER_MAG = 1.28f;     // magnification inside the droplet
    private static final float HOVER_GROW_X = 16f;    // the droplet is wider than the button...
    private static final float HOVER_GROW_Y = 8f;     // ...and sticks out above and below the bar
    private static final float HOVER_STICK = 0.28f;   // how much the droplet leans toward the cursor
    private static final float HOVER_EDGE_BAND = 11f;
    private static final float HOVER_EDGE_K = 9f;
    private static final float HOVER_DISPERSION = 0.45f;

    private enum Kind { SEGMENT, ACTION, SWATCH, SEPARATOR }

    /** One thing in the bar. */
    public static final class Item {
        private final Kind kind;
        private final String label;
        private final Glyph icon;
        private final int group;
        private float width;
        private float x;
        private Color color;
        private boolean custom;
        private BooleanSupplier onActivate = () -> true;  // false keeps the current selection
        private BooleanSupplier enabled = () -> true;

        private Item(Kind kind, String label, Glyph icon, int group, float width) {
            this.kind = kind;
            this.label = label;
            this.icon = icon;
            this.group = group;
            this.width = width;
        }

        private float center() {
            return x + width / 2f;
        }

        /** The colour a swatch shows; for a custom swatch, the colour last chosen. */
        public void setColor(Color color) {
            this.color = color;
        }

        public Color getColor() {
            return color;
        }
    }

    /** Under-damped spring: overshoots a little, which reads as "liquid". */
    private static final class Spring {
        float x;
        float v;

        /** f = elapsed time in units of the 16 ms reference frame, so the feel is independent of frame rate. */
        boolean step(float target, float k, float damping, float f) {
            v = (v + (target - x) * k * f) * (float) Math.pow(damping, f);
            x += v * f;
            if (Math.abs(target - x) < 0.15f && Math.abs(v) < 0.15f) {
                x = target;
                v = 0;
                return true;
            }
            return false;
        }
    }

    private static final class Platter {
        final Spring x = new Spring();
        final Spring width = new Spring();
    }

    /** The blurred backdrop at one render scale: it depends only on the picture, not on the animation. */
    private static final class Blurred {
        long key;
        BufferedImage image;
    }

    private final GlassBackdrop backdrop;
    private final Font font;
    private final List<Item> items = new ArrayList<>();
    private final Map<Integer, Item> selected = new HashMap<>();
    private final Map<Integer, Platter> platters = new HashMap<>();
    private float capsuleWidth;

    private Item hoverItem;
    private Item pressItem;
    private boolean dragSwitched;   // a drag already switched the selection, so the release must not activate again
    private float mouseX;
    private final Spring press = new Spring();
    private float pressTarget;
    private float glow;
    private final Point2D.Float glowAt = new Point2D.Float();
    private float appear = 1f;
    private float appearTarget = 1f;
    private final Spring lensX = new Spring();
    private final Spring lensWidth = new Spring();
    private float lensAlpha;

    private final GlassAnimator animator = new GlassAnimator(this::step);

    // Render caches: while nothing but the hover droplet moves, the glass and the magnified scene are reused.
    private final Map<Double, BufferedImage> shadowCaches = new HashMap<>();
    private final Map<Double, Blurred> blurCaches = new HashMap<>();
    private BufferedImage sharp;     // the backdrop under the whole bar at the device scale
    private long sharpVersion;
    private long sharpPlace;
    private int sharpHash;           // writing elsewhere on the canvas repaints it, but leaves these pixels alone
    private long previousKey;
    private long glassKey;
    private long sceneKey;
    private BufferedImage glassCache;
    private BufferedImage sceneCache;

    public GlassBar(GlassBackdrop backdrop, Font font) {
        this.backdrop = backdrop;
        this.font = font;
        setOpaque(false);
        MouseAdapter mouse = new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent e) {
                pressItem = itemAt(e.getX());
                dragSwitched = false;
                glowAt.setLocation(e.getX(), e.getY());
                glow = 1f;
                pressTarget = 1f;
                animator.start();
            }

            @Override
            public void mouseReleased(MouseEvent e) {
                Item released = itemAt(e.getX());
                Item pressed = pressItem;
                pressItem = null;
                pressTarget = 0f;
                animator.start();
                if (!dragSwitched && released != null && released == pressed && contains(e.getPoint())) {
                    activate(released);
                }
                dragSwitched = false;
            }

            @Override
            public void mouseDragged(MouseEvent e) {
                glowAt.setLocation(e.getX(), e.getY());
                mouseX = e.getX();
                // Dragging along a segmented control switches live (like iOS): the segment under the
                // pointer, or the nearest one when the pointer is past either end, becomes selected.
                if (pressItem != null && pressItem.kind == Kind.SEGMENT) {
                    Item target = segmentNear(pressItem.group, e.getX());
                    if (target != null) {
                        hoverItem = target;
                        if (selected.get(target.group) != target && target.enabled.getAsBoolean()) {
                            activate(target);
                            dragSwitched = true;
                        }
                    }
                }
                animator.start();
            }

            @Override
            public void mouseMoved(MouseEvent e) {
                mouseX = e.getX();
                hoverItem = itemAt(e.getX());
                animator.start();
            }

            @Override
            public void mouseExited(MouseEvent e) {
                hoverItem = null;
                animator.start();
            }
        };
        addMouseListener(mouse);
        addMouseMotionListener(mouse);
    }

    // ---------- building ----------

    /** A button of a group where one is selected. {@code onActivate} returns false to keep the selection. */
    public Item segment(String label, Glyph icon, int group, BooleanSupplier onActivate) {
        Item item = new Item(Kind.SEGMENT, label, icon, group, 0);
        item.onActivate = onActivate;
        items.add(item);
        return item;
    }

    /** A plain button, greyed out while {@code enabled} says no. */
    public Item action(String label, Glyph icon, Runnable run, BooleanSupplier enabled) {
        Item item = new Item(Kind.ACTION, label, icon, -1, 0);
        item.onActivate = () -> {
            run.run();
            return true;
        };
        item.enabled = enabled;
        items.add(item);
        return item;
    }

    /** A colour dot of a group where one is selected. */
    public Item swatch(Color color, int group, BooleanSupplier onActivate) {
        Item item = new Item(Kind.SWATCH, null, null, group, SWATCH_WIDTH);
        item.color = color;
        item.onActivate = onActivate;
        items.add(item);
        return item;
    }

    /** A swatch with a rainbow ring, for choosing any colour; its colour is null until one is set. */
    public Item customSwatch(int group, BooleanSupplier onActivate) {
        Item item = swatch(null, group, onActivate);
        item.custom = true;
        return item;
    }

    public void separator() {
        items.add(new Item(Kind.SEPARATOR, null, null, -1, SEPARATOR_WIDTH));
    }

    /** Measures the labels and lays the items out left to right. */
    public void finish() {
        FontRenderContext frc = new FontRenderContext(null, true, true);
        float x = 0;
        for (Item item : items) {
            if (item.width <= 0) {
                float text = (float) font.getStringBounds(item.label, frc).getWidth();
                float icon = item.icon == null ? 0 : ICON + ICON_GAP;
                item.width = Math.max(MIN_ITEM_WIDTH, ITEM_PAD_X * 2 + icon + text);
            }
            item.x = x;
            x += item.width;
        }
        capsuleWidth = x + 2 * PAD;
        shadowCaches.clear();
        setSize(getPreferredSize());
    }

    @Override
    public Dimension getPreferredSize() {
        return new Dimension((int) Math.ceil(capsuleWidth) + 2 * MARGIN, CAPSULE_HEIGHT + 2 * MARGIN);
    }

    /** Puts the bar so its capsule is centred across the given width with its top at capsuleTop. */
    public void placeCentered(int width, int capsuleTop) {
        Dimension size = getPreferredSize();
        setBounds((width - size.width) / 2, capsuleTop - MARGIN, size.width, size.height);
    }

    // ---------- interaction ----------

    /** Does what a click on the item does, if it is enabled. */
    public void activate(Item item) {
        if (!item.enabled.getAsBoolean()) {
            return;
        }
        if (item.kind == Kind.ACTION) {
            item.onActivate.getAsBoolean();
        } else if (item.kind != Kind.SEPARATOR && item.onActivate.getAsBoolean()) {
            select(item);
        }
        repaint();
    }

    /** Shows the item as the selected one of its group, without running its action. */
    public void select(Item item) {
        selected.put(item.group, item);
        if (item.kind == Kind.SEGMENT && !platters.containsKey(item.group)) {
            Platter platter = new Platter();
            platter.x.x = item.x;
            platter.width.x = item.width;
            platters.put(item.group, platter);
        }
        animator.start();
    }

    public boolean isSelected(Item item) {
        return selected.get(item.group) == item;
    }

    public boolean isEnabled(Item item) {
        return item.enabled.getAsBoolean();
    }

    /** Forgets the mouse: for a bar that was hidden under it, which never hears that it left. */
    public void clearHover() {
        hoverItem = null;
        lensAlpha = 0;
    }

    /** Fades and scales the bar in or out; while hidden it takes no mouse events. */
    public void setShown(boolean shown) {
        appearTarget = shown ? 1f : 0f;
        if (!shown) {
            hoverItem = null;
        }
        animator.start();
    }

    /** Whether the bar is showing, or on its way to showing. */
    public boolean isShown() {
        return appearTarget > 0f;
    }

    /** Sets a bar that is not on screen yet straight to shown or hidden, with no animation. */
    public void setShownNow(boolean shown) {
        appearTarget = shown ? 1f : 0f;
        appear = appearTarget;
    }

    private Item itemAt(int x) {
        float lx = x - MARGIN - PAD;
        for (Item item : items) {
            if (item.kind != Kind.SEPARATOR && lx >= item.x && lx < item.x + item.width) {
                return item;
            }
        }
        return null;
    }

    /** The segment of a group under x, or the closest one if x is left or right of all of them. */
    private Item segmentNear(int group, int x) {
        float lx = x - MARGIN - PAD;
        Item best = null;
        float bestDistance = Float.MAX_VALUE;
        for (Item item : items) {
            if (item.kind != Kind.SEGMENT || item.group != group) {
                continue;
            }
            float distance = lx < item.x ? item.x - lx : lx >= item.x + item.width ? lx - (item.x + item.width) : 0f;
            if (distance < bestDistance) {
                bestDistance = distance;
                best = item;
            }
        }
        return best;
    }

    /** Only the capsule itself is hit-testable (nothing while hidden); the margin passes through. */
    @Override
    public boolean contains(int x, int y) {
        return appearTarget > 0f && capsule(0).contains(x, y);
    }

    private RoundRectangle2D.Float capsule(float grow) {
        float w = capsuleWidth + 2 * grow;
        float h = CAPSULE_HEIGHT + 2 * grow;
        return new RoundRectangle2D.Float(MARGIN - grow, MARGIN - grow, w, h, h, h);
    }

    private void step() {
        float f = animator.elapsedFrames();
        boolean settled = true;
        for (Map.Entry<Integer, Platter> e : platters.entrySet()) {
            Item target = selected.get(e.getKey());
            settled &= e.getValue().x.step(target.x, 0.16f, 0.70f, f);
            settled &= e.getValue().width.step(target.width, 0.16f, 0.70f, f);
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
        appear += (appearTarget - appear) * GlassAnimator.ease(0.22f, f);
        if (Math.abs(appearTarget - appear) < 0.01f) {
            appear = appearTarget;
        } else {
            settled = false;
        }

        // hover droplet: sticks to the hovered button, leaning slightly toward the cursor
        Item hovered = hoverItem != null && hoverItem.enabled.getAsBoolean() && appearTarget > 0 ? hoverItem : null;
        float alphaTarget = hovered != null ? 1f : 0f;
        if (hovered != null) {
            float cx = hovered.center() + ((mouseX - MARGIN - PAD) - hovered.center()) * HOVER_STICK;
            float w = hovered.width + HOVER_GROW_X;
            if (lensAlpha < 0.05f) {
                lensX.x = cx;
                lensWidth.x = w;
                lensX.v = 0;
                lensWidth.v = 0;
            }
            settled &= lensX.step(cx, 0.20f, 0.68f, f);
            settled &= lensWidth.step(w, 0.20f, 0.68f, f);
        }
        lensAlpha += (alphaTarget - lensAlpha) * GlassAnimator.ease(0.25f, f);
        if (Math.abs(alphaTarget - lensAlpha) < 0.01f) {
            lensAlpha = alphaTarget;
        } else {
            settled = false;
        }

        if (settled) {
            animator.stop();
        }
        repaint();
    }

    // ---------- painting ----------

    @Override
    protected void paintComponent(Graphics g) {
        int w = getWidth();
        int h = getHeight();
        if (appear <= 0f || w <= 0 || h <= 0) {
            return;
        }
        Graphics2D g2 = (Graphics2D) g.create();
        double scale = g2.getTransform().getScaleX();
        int pw = (int) Math.ceil(w * scale);
        int ph = (int) Math.ceil(h * scale);

        // Appear / disappear: scale + opacity; the lensing ramps with it
        if (appear < 1f) {
            g2.setComposite(AlphaComposite.SrcOver.derive(appear));
            double k = 0.9 + 0.1 * appear;
            g2.translate(w / 2.0, h / 2.0);
            g2.scale(k, k);
            g2.translate(-w / 2.0, -h / 2.0);
        }

        refreshSharp(scale, pw, ph);
        long key = stateKey(scale);
        if (glassCache == null || glassKey != key) {
            glassCache = renderGlass(scale, pw, ph, sharp, 0, 0);
            glassKey = key;
        }
        GlassOptics.drawPixels(g2, glassCache, scale, 0, 0);

        if (lensAlpha > 0.01f) {
            // The droplet magnifies everything under it: canvas strokes + the bar itself. That scene is
            // re-rendered at HOVER_MAG x the device resolution (strokes replayed as vectors, text
            // re-rasterized) so the magnified pixels are sampled ~1:1 instead of upscaled: sharp text.
            double lensScale = scale * HOVER_MAG;
            RoundRectangle2D.Float lens = lensShape();
            if (sceneCache != null && sceneKey == key) {
                paintHoverLens(g2, scale, lensScale, sceneCache, 0, 0, lens);  // static bar: only the lens moves
            } else if (key == previousKey) {
                // the state has settled: render the whole scene once and keep it
                sceneCache = renderScene(lensScale, (int) Math.ceil(w * lensScale), (int) Math.ceil(h * lensScale), 0, 0);
                sceneKey = key;
                paintHoverLens(g2, scale, lensScale, sceneCache, 0, 0, lens);
            } else {
                // the bar is still animating: render just the part under the droplet
                int pad = (int) Math.ceil(HOVER_EDGE_K * (1 + HOVER_DISPERSION)) + 3;
                int ox = Math.max(0, (int) Math.floor((lens.x - pad) * lensScale));
                int oy = Math.max(0, (int) Math.floor((lens.y - pad) * lensScale));
                int cw = Math.min((int) Math.ceil(w * lensScale), (int) Math.ceil((lens.x + lens.width + pad) * lensScale)) - ox;
                int ch = Math.min((int) Math.ceil(h * lensScale), (int) Math.ceil((lens.y + lens.height + pad) * lensScale)) - oy;
                if (cw > 0 && ch > 0) {
                    paintHoverLens(g2, scale, lensScale, renderScene(lensScale, cw, ch, ox, oy), ox, oy, lens);
                }
            }
        }
        previousKey = key;
        g2.dispose();
    }

    /** Re-reads the backdrop under the bar if it may have changed, and notes whether its pixels really did. */
    private void refreshSharp(double scale, int pw, int ph) {
        long version = backdrop.backdropVersion();
        long place = Objects.hash(getX(), getY(), pw, ph, scale);
        if (sharp == null || sharpVersion != version || sharpPlace != place) {
            sharp = renderBackdrop(scale, pw, ph, 0, 0);
            sharpHash = Arrays.hashCode(((DataBufferInt) sharp.getRaster().getDataBuffer()).getData());
            sharpVersion = version;
            sharpPlace = place;
        }
    }

    /** Backdrop + glass of the bar at a scale, cropped to a window starting at device pixel (ox, oy). */
    private BufferedImage renderScene(double scale, int cw, int ch, int ox, int oy) {
        BufferedImage raw = renderBackdrop(scale, cw, ch, ox, oy);
        BufferedImage glass = renderGlass(scale, cw, ch, raw, ox, oy);
        BufferedImage scene = new BufferedImage(cw, ch, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = scene.createGraphics();
        g.drawImage(raw, 0, 0, null);
        g.drawImage(glass, 0, 0, null);
        g.dispose();
        return scene;
    }

    /** Fingerprint of everything that changes how the glass bar looks, except the hover droplet. */
    private long stateKey(double scale) {
        long h = Double.doubleToLongBits(scale);
        h = h * 31 + Float.floatToIntBits(appear);
        h = h * 31 + Float.floatToIntBits(press.x);
        h = h * 31 + Float.floatToIntBits(glow);
        h = h * 31 + Float.floatToIntBits(glowAt.x);
        h = h * 31 + Float.floatToIntBits(glowAt.y);
        for (Map.Entry<Integer, Platter> e : platters.entrySet()) {
            h = h * 31 + e.getKey();
            h = h * 31 + Float.floatToIntBits(e.getValue().x.x);
            h = h * 31 + Float.floatToIntBits(e.getValue().width.x);
        }
        for (Map.Entry<Integer, Item> e : selected.entrySet()) {
            h = h * 31 + e.getKey() * 17 + System.identityHashCode(e.getValue());
        }
        for (Item item : items) {
            h = h * 31 + (item.enabled.getAsBoolean() ? 1 : 0);
            if (item.color != null) {
                h = h * 31 + item.color.getRGB();
            }
        }
        h = h * 31 + sharpHash;
        h = h * 31 + getX();
        h = h * 31 + getY();
        return h * 31 + getWidth() * 7919L + getHeight();
    }

    /** The backdrop at a scale, as a window onto the bar starting at device pixel (ox, oy). */
    private BufferedImage renderBackdrop(double scale, int pw, int ph, int ox, int oy) {
        BufferedImage image = new BufferedImage(pw, ph, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = image.createGraphics();
        g.translate(-ox, -oy);
        g.scale(scale, scale);
        g.translate(-getX(), -getY());
        backdrop.paintBackdrop(g);
        g.dispose();
        return image;
    }

    /**
     * Renders the glass bar (shadow, refraction, foreground) at a scale into a pw x ph image whose
     * top-left sits at device pixel (ox, oy) of the full-size bar (0, 0 = the whole bar).
     */
    private BufferedImage renderGlass(double scale, int pw, int ph, BufferedImage raw, int ox, int oy) {
        BufferedImage glass = new BufferedImage(pw, ph, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = glass.createGraphics();
        Glass.setUp(g);
        g.translate(-ox, -oy);
        g.scale(scale, scale);
        paintGlass(g, scale, raw, ox, oy);
        g.dispose();
        return glass;
    }

    /** g2 is scaled by scale and shifted by (-ox, -oy) device pixels; raw covers that window of the bar. */
    private void paintGlass(Graphics2D g2, double scale, BufferedImage raw, int ox, int oy) {
        RoundRectangle2D.Float capsule = capsule(press.x * 3f);
        int pw = raw.getWidth();
        int ph = raw.getHeight();

        BufferedImage shadow = shadowCaches.computeIfAbsent(scale, this::buildShadow);
        int sw = Math.min(pw, shadow.getWidth() - ox);
        int sh = Math.min(ph, shadow.getHeight() - oy);
        if (sw > 0 && sh > 0) {
            GlassOptics.drawPixels(g2, shadow.getSubimage(ox, oy, sw, sh), scale, ox, oy);
        }
        long blurKey = Objects.hash(ox, oy, pw, ph, sharpHash, getX(), getY());
        Blurred blurred = blurCaches.computeIfAbsent(scale, k -> new Blurred());
        if (blurred.image == null || blurred.key != blurKey) {
            blurred.image = GlassOptics.gaussian(raw, (float) (GlassOptics.BLUR_SIGMA * scale));
            blurred.key = blurKey;
        }
        GlassOptics.drawPixels(g2, GlassOptics.refract(blurred.image, scale, capsule, appear, ox, oy), scale, ox, oy);

        Glass.fillBody(g2, capsule);

        float stretch = capsule.width / capsuleWidth;
        for (Platter platter : platters.values()) {
            g2.setColor(Glass.PLATTER);
            g2.fill(slot(platter.x.x, platter.width.x, capsule, stretch));
        }

        if (glow > 0.01f) {
            float radius = 70f + 50f * (1f - glow);
            g2.setPaint(new RadialGradientPaint(glowAt, radius, new float[]{0f, 1f},
                    new Color[]{new Color(1f, 1f, 1f, 0.40f * glow), new Color(1f, 1f, 1f, 0f)}));
            g2.fill(capsule);
        }

        Glass.drawRim(g2, capsule);

        // Foreground (icons, labels, swatches) with a soft halo so it stays legible over strokes
        BufferedImage foreground = new BufferedImage(pw, ph, BufferedImage.TYPE_INT_ARGB);
        Graphics2D fg = foreground.createGraphics();
        Glass.setUp(fg);
        fg.translate(-ox, -oy);
        fg.scale(scale, scale);
        paintForeground(fg, capsule, stretch);
        fg.dispose();
        GlassOptics.drawPixels(g2, GlassOptics.halo(foreground, (float) (2.2 * scale)), scale, ox, oy);
        GlassOptics.drawPixels(g2, foreground, scale, ox, oy);
    }

    private void paintForeground(Graphics2D g2, RoundRectangle2D.Float capsule, float stretch) {
        g2.setFont(font);
        FontMetrics fm = g2.getFontMetrics();
        for (Item item : items) {
            Rectangle2D b = slot(item.x, item.width, capsule, stretch).getBounds2D();
            switch (item.kind) {
                case SEGMENT:
                case ACTION:
                    paintLabelled(g2, item, b, fm, stretch);
                    break;
                case SWATCH:
                    paintSwatch(g2, item, b.getCenterX(), b.getCenterY(), stretch);
                    break;
                default:
                    g2.setColor(Glass.EDGE);
                    g2.setStroke(new BasicStroke(1f));
                    double h = 20 * stretch;
                    g2.draw(new Line2D.Double(b.getCenterX(), b.getCenterY() - h / 2, b.getCenterX(), b.getCenterY() + h / 2));
                    break;
            }
        }
    }

    private void paintLabelled(Graphics2D g2, Item item, Rectangle2D b, FontMetrics fm, float stretch) {
        g2.setColor(!item.enabled.getAsBoolean() ? Glass.TEXT_DISABLED
                : selected.get(item.group) == item ? Glass.ACCENT : Glass.TEXT);
        double iconWidth = item.icon == null ? 0 : (ICON + ICON_GAP) * stretch;
        double left = b.getCenterX() - (iconWidth + fm.stringWidth(item.label)) / 2;
        if (item.icon != null) {
            AffineTransform t = g2.getTransform();
            g2.translate(left + ICON * stretch / 2, b.getCenterY());
            g2.scale(stretch * ICON / 20.0, stretch * ICON / 20.0);
            item.icon.paint(g2);
            g2.setTransform(t);
        }
        g2.drawString(item.label, (float) (left + iconWidth),
                (float) (b.getCenterY() - fm.getHeight() / 2.0 + fm.getAscent()));
    }

    private void paintSwatch(Graphics2D g2, Item item, double cx, double cy, float stretch) {
        double d = 22 * stretch;
        Ellipse2D dot = new Ellipse2D.Double(cx - d / 2, cy - d / 2, d, d);
        if (item.custom) {
            for (int i = 0; i < 24; i++) {
                g2.setColor(Color.getHSBColor(i / 24f, 0.75f, 0.95f));
                g2.fill(new Arc2D.Double(dot.getBounds2D(), 90 - i * 15, -15.5, Arc2D.PIE));
            }
            double inner = d - 7 * stretch;
            g2.setColor(item.color != null ? item.color : Glass.CANVAS);
            g2.fill(new Ellipse2D.Double(cx - inner / 2, cy - inner / 2, inner, inner));
        } else {
            g2.setColor(item.color);
            g2.fill(dot);
        }
        g2.setColor(Glass.EDGE);
        g2.setStroke(new BasicStroke(1f));
        g2.draw(dot);
        if (selected.get(item.group) == item) {
            double ring = 30 * stretch;
            g2.setColor(Glass.ACCENT);
            g2.setStroke(new BasicStroke(2f));
            g2.draw(new Ellipse2D.Double(cx - ring / 2, cy - ring / 2, ring, ring));
        }
    }

    /** The droplet's current outline in bar coordinates. */
    private RoundRectangle2D.Float lensShape() {
        float lh = CAPSULE_HEIGHT + 2 * HOVER_GROW_Y + press.x * 4f;
        float lw = Math.max(lh, lensWidth.x + Math.min(Math.abs(lensX.v) * 1.2f, 28f) + press.x * 4f);
        float lcx = MARGIN + PAD + lensX.x;
        float lcy = MARGIN + CAPSULE_HEIGHT / 2f;
        return new RoundRectangle2D.Float(lcx - lw / 2, lcy - lh / 2, lw, lh, lh, lh);
    }

    /**
     * Hover droplet: magnifies the scene and bends it at the rim with slight color dispersion. The
     * scene is the bar rendered at lensScale = scale * HOVER_MAG, cropped to start at (ox, oy).
     */
    private void paintHoverLens(Graphics2D g2, double scale, double lensScale, BufferedImage scene, int ox, int oy,
                                RoundRectangle2D.Float lens) {
        float lh = lens.height;
        float lw = lens.width;
        float lcx = lens.x + lw / 2;
        float lcy = lens.y + lh / 2;

        Composite base = g2.getComposite();
        float baseAlpha = base instanceof AlphaComposite ? ((AlphaComposite) base).getAlpha() : 1f;
        g2.setComposite(AlphaComposite.SrcOver.derive(baseAlpha * lensAlpha));
        Glass.setUp(g2);

        // soft contact shadow (a few stacked translucent capsules: cheap enough per frame)
        for (int i = 4; i >= 1; i--) {
            g2.setColor(new Color(0f, 0f, 0f, Glass.DROP_SHADOW * 0.16f));
            g2.fill(new RoundRectangle2D.Float(lens.x - i, lens.y + 2 - i + 3, lens.width + 2 * i,
                    lens.height + 2 * i, lens.height + 2 * i, lens.height + 2 * i));
        }

        // The output is device-resolution; the scene was rendered at lensScale, so r = HOVER_MAG and
        // the magnified sample lands on ~1 source pixel per output pixel.
        int pw = (int) Math.ceil(getWidth() * scale);
        int ph = (int) Math.ceil(getHeight() * scale);
        int sw = scene.getWidth();
        int sh = scene.getHeight();
        double r = lensScale / scale;
        int[] in = scene.getRGB(0, 0, sw, sh, null, 0, sw);
        int[] out = new int[pw * ph];
        double cx = lcx * scale;
        double cy = lcy * scale;
        double radius = lh / 2.0 * scale;
        double x0 = cx - (lw / 2.0 - lh / 2.0) * scale;
        double x1 = cx + (lw / 2.0 - lh / 2.0) * scale;
        double band = HOVER_EDGE_BAND * scale;
        double k = HOVER_EDGE_K * scale;
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
                double offset = 0;
                double nx = 0;
                double ny = 0;
                if (-d < band && len > 1e-6) {
                    double t = 1.0 + d / band;
                    offset = t * t * k;
                    nx = dx / len;
                    ny = dy / len;
                }
                double offsetRed = offset * (1 + HOVER_DISPERSION);
                double offsetBlue = offset * (1 - HOVER_DISPERSION);
                int red = GlassOptics.bilinear(in, sw, sh, bx + nx * offsetRed * r - 0.5, by + ny * offsetRed * r - 0.5);
                int green = GlassOptics.bilinear(in, sw, sh, bx + nx * offset * r - 0.5, by + ny * offset * r - 0.5);
                int blue = GlassOptics.bilinear(in, sw, sh, bx + nx * offsetBlue * r - 0.5, by + ny * offsetBlue * r - 0.5);
                int alpha = (int) Math.round(255 * coverage);
                out[y * pw + x] = (alpha << 24) | (red & 0xFF0000) | (green & 0xFF00) | (blue & 0xFF);
            }
        }
        BufferedImage image = new BufferedImage(pw, ph, BufferedImage.TYPE_INT_ARGB);
        image.setRGB(0, 0, pw, ph, out, 0, pw);
        GlassOptics.drawPixels(g2, image, scale, 0, 0);

        // gloss + specular rim
        g2.setPaint(new GradientPaint(0, lens.y, new Color(255, 255, 255, 40),
                0, lens.y + lens.height * 0.5f, new Color(255, 255, 255, 0)));
        g2.fill(lens);
        g2.setStroke(new BasicStroke(1f));
        g2.setColor(new Color(0, 0, 0, 30));
        g2.draw(Glass.inset(lens, 0.5));
        g2.setStroke(new BasicStroke(1.6f));
        g2.setPaint(Glass.rimPaint(lens, 255, 170));
        g2.draw(Glass.inset(lens, 1.3));
        g2.setComposite(base);
    }

    private RoundRectangle2D.Float slot(float x, float width, RoundRectangle2D.Float capsule, float stretch) {
        float sx = capsule.x + (PAD + x) * stretch;
        float sy = capsule.y + PAD * stretch;
        float sw = width * stretch;
        float sh = SEGMENT_HEIGHT * stretch;
        return new RoundRectangle2D.Float(sx, sy, sw, sh, sh, sh);
    }

    private BufferedImage buildShadow(double scale) {
        int pw = (int) Math.ceil(getWidth() * scale);
        int ph = (int) Math.ceil(getHeight() * scale);
        BufferedImage out = new BufferedImage(pw, ph, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = out.createGraphics();
        g.drawImage(shadowLayer(pw, ph, scale, 0f, 1.5f, Glass.DROP_SHADOW * 0.5f), 0, 0, null);
        g.drawImage(shadowLayer(pw, ph, scale, 6f, 8f, Glass.DROP_SHADOW), 0, 0, null);
        g.dispose();
        // cut the capsule out: with a transparent glass the shadow must not darken what's under it
        Graphics2D cut = out.createGraphics();
        cut.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        cut.scale(scale, scale);
        cut.setComposite(AlphaComposite.Clear);
        cut.fill(capsule(0));
        cut.dispose();
        return out;
    }

    private BufferedImage shadowLayer(int pw, int ph, double scale, float dy, float sigma, float alpha) {
        BufferedImage image = new BufferedImage(pw, ph, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = image.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.scale(scale, scale);
        g.setColor(new Color(0f, 0f, 0f, Math.min(1f, alpha)));
        RoundRectangle2D.Float shape = capsule(0);
        shape.y += dy;
        g.fill(shape);
        g.dispose();
        return GlassOptics.gaussian(image, (float) (sigma * scale));
    }

    /**
     * The first hover or click otherwise pays for JIT-compiling the glass pipeline (hundreds of ms of
     * stutter). Exercises it once on a throw-away bar in a background thread; no real UI state is touched.
     */
    public static void warmUp(Font font) {
        Thread thread = new Thread(() -> {
            try {
                GlassBar bar = new GlassBar(g -> {
                    g.setColor(Glass.CANVAS);
                    g.fillRect(0, 0, 900, 640);
                }, font);
                Item first = bar.segment("Answer", GlassIcons::equalCircle, 0, () -> true);
                Item second = bar.segment("Graph", GlassIcons::scribble, 0, () -> true);
                bar.finish();
                bar.selected.put(first.group, first);
                bar.hoverItem = second;
                bar.mouseX = bar.getWidth() / 2f;
                for (double scale : new double[]{2.0, 1.0}) {
                    BufferedImage image = new BufferedImage((int) Math.ceil(bar.getWidth() * scale),
                            (int) Math.ceil(bar.getHeight() * scale), BufferedImage.TYPE_INT_ARGB);
                    for (int i = 0; i < 24; i++) {
                        bar.press.x = i % 6 < 3 ? 0f : 0.6f;  // the cached path and the animating path
                        bar.glow = i % 6 < 3 ? 0f : 0.5f;
                        bar.mouseX = 40 + i * 9;
                        bar.step();
                        Graphics2D g = image.createGraphics();
                        g.scale(scale, scale);
                        bar.paintComponent(g);
                        g.dispose();
                    }
                }
            } catch (RuntimeException ignored) {
                // only a warm-up: the real bars work without it
            }
        }, "glass-warmup");
        thread.setDaemon(true);
        thread.setPriority(Thread.MIN_PRIORITY);
        thread.start();
    }
}
