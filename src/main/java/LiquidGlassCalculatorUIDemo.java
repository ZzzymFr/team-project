import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.awt.font.FontRenderContext;
import java.awt.font.TextAttribute;
import java.awt.geom.*;
import java.awt.image.BufferedImage;
import java.awt.image.ConvolveOp;
import java.awt.image.Kernel;
import java.util.*;
import java.util.List;
import java.util.function.BooleanSupplier;

/**
 * Handwriting Calculator — UI demo in an approximation of Apple's Liquid Glass style.
 * Pure Swing, no third-party dependencies.
 *
 * Top bar:    Draw / Export PDF / Answer
 * Bottom bar: Pen / Eraser | colors | Undo / Clear   (only shown in Draw mode)
 * Shortcut:   Ctrl+Z (Cmd+Z on macOS) = Undo
 *
 * Effects follow the layers described in WWDC25 "Meet Liquid Glass": lensing, specular highlights,
 * shadow, touch illumination, rounded floating shapes, color only on primary elements, no glass-on-glass.
 * Hovering a button shows a magnifying glass "droplet" that sticks to the button.
 * Apple does not publish numeric values; every GLASS / LENS constant below is an estimate.
 *
 * Icons are hand-drawn with Java2D in a style similar to SF Symbols
 * (SF Symbols itself is licensed for Apple-platform apps only).
 *
 * Run: javac -encoding UTF-8 LiquidGlassCalculatorUIDemo.java && java LiquidGlassCalculatorUIDemo
 */
public class LiquidGlassCalculatorUIDemo {

    // ===== Theme: switch to Theme.DARK for dark mode =====
    static final Theme THEME = Theme.LIGHT;

    enum Theme {
        LIGHT(new Color(0xF2F2F7),            // canvas: secondarySystemBackground (light)
              new Color(0, 0, 0, 220),        // label
              new Color(0, 0, 0, 70),         // disabled label
              new Color(0x007AFF),            // selected label: systemBlue
              new Color(0, 0, 0, 18),         // selection platter (solid fill, not a second glass layer)
              new Color(255, 255, 255, 38),   // glass tint — low, so strokes show through
              28,                             // top gloss alpha
              230, 110,                       // specular rim: top-left / bottom-right alpha
              new Color(0, 0, 0, 24),         // outer hairline
              0.12f,                          // drop shadow strength
              new Color(242, 242, 247, 170),  // legibility halo behind labels/icons
              new Color[]{new Color(0x1C1C1E), new Color(255, 59, 48), new Color(0x007AFF),
                          new Color(52, 199, 89), new Color(255, 149, 0)}),
        DARK (new Color(0x1C1C1E),
              new Color(255, 255, 255, 235),
              new Color(255, 255, 255, 80),
              new Color(0x0A84FF),
              new Color(255, 255, 255, 30),
              new Color(44, 44, 48, 70),
              18,
              120, 55,
              new Color(0, 0, 0, 80),
              0.40f,
              new Color(28, 28, 30, 170),
              new Color[]{new Color(0xF2F2F7), new Color(255, 69, 58), new Color(0x0A84FF),
                          new Color(48, 209, 88), new Color(255, 159, 10)});

        final Color canvas, label, labelDisabled, labelSelected, platter, tint, edge, halo;
        final int gloss, rimTop, rimBottom;
        final float shadow;
        final Color[] swatches;   // [0] default ink, then systemRed / Blue / Green / Orange

        Theme(Color canvas, Color label, Color labelDisabled, Color labelSelected, Color platter,
              Color tint, int gloss, int rimTop, int rimBottom, Color edge, float shadow, Color halo,
              Color[] swatches) {
            this.canvas = canvas; this.label = label; this.labelDisabled = labelDisabled;
            this.labelSelected = labelSelected; this.platter = platter; this.tint = tint; this.gloss = gloss;
            this.rimTop = rimTop; this.rimBottom = rimBottom; this.edge = edge; this.shadow = shadow;
            this.halo = halo; this.swatches = swatches;
        }
    }

    // ===== Geometry (logical px) =====
    static final int SEG_H = 40, PAD = 6;   // button height inside the glass, glass padding
    static final int MARGIN = 28;           // room around the capsule for shadow and hover lens
    static final int TOP = 18, BOTTOM = 22; // distance of the bars from the window edges
    static final int ICON = 18, ICON_GAP = 7, ITEM_PAD_X = 16;

    // ===== Glass (estimates) =====
    static final float BLUR_SIGMA = 0.5f;   // barely blurred, so strokes stay readable through the glass
    static final float LENS_BAND = 14f;     // width of the refracting edge
    static final float LENS_STRENGTH = 10f; // max edge displacement

    // ===== Hover lens (estimates) =====
    static final float HOVER_MAG = 1.28f;     // magnification inside the droplet
    static final float HOVER_GROW_X = 16f;    // droplet is wider than the button...
    static final float HOVER_GROW_Y = 8f;     // ...and sticks out above/below the bar
    static final float HOVER_STICK = 0.28f;   // how much the droplet leans toward the cursor
    static final float HOVER_EDGE_BAND = 11f, HOVER_EDGE_K = 9f, HOVER_DISPERSION = 0.45f;

    // ===== Brush =====
    static final float PEN_WIDTH = 3f, ERASER_WIDTH = 26f;

    static final String FONT_FAMILY = pickFamily();

    static String pickFamily() {
        String[] candidates = {"SF Pro Text", "SF Pro", "Helvetica Neue", "Segoe UI", "Inter"};
        List<String> installed = List.of(GraphicsEnvironment.getLocalGraphicsEnvironment()
                .getAvailableFontFamilyNames());
        for (String c : candidates) if (installed.contains(c)) return c;
        return "Dialog";
    }

    static Font font(float size) {
        return new Font(Map.of(TextAttribute.FAMILY, FONT_FAMILY, TextAttribute.SIZE, size,
                               TextAttribute.WEIGHT, TextAttribute.WEIGHT_MEDIUM));
    }

    final JFrame frame = new JFrame("Handwriting Calculator");
    final DrawingCanvas canvas = new DrawingCanvas();
    final GlassBar topBar = new GlassBar(canvas, font(15f));
    final GlassBar toolBar = new GlassBar(canvas, font(14f));

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> new LiquidGlassCalculatorUIDemo().show());
    }

    LiquidGlassCalculatorUIDemo() {
        // ---- Top: modes ----
        GlassBar.Item draw = topBar.segment("Draw", Icons::scribble, 0, () -> { setDrawMode(true); return true; });
        topBar.segment("Export PDF", Icons::document, 0, () -> { setDrawMode(false); return true; }); // TODO: PDF export
        topBar.segment("Answer", Icons::equalCircle, 0, () -> { setDrawMode(false); return true; });  // TODO: recognition
        topBar.finish();
        topBar.select(draw);

        // ---- Bottom: drawing tools ----
        GlassBar.Item pen = toolBar.segment("Pen", Icons::pencil, 1,
                () -> { canvas.tool = Tool.PEN; canvas.updateCursor(); return true; });
        toolBar.segment("Eraser", Icons::eraser, 1,
                () -> { canvas.tool = Tool.ERASER; canvas.updateCursor(); return true; });
        toolBar.separator();
        GlassBar.Item firstSwatch = null;
        for (Color c : THEME.swatches) {
            GlassBar.Item[] self = new GlassBar.Item[1];
            self[0] = toolBar.swatch(c, 2, () -> { useColor(self[0].color, pen); return true; });
            if (firstSwatch == null) firstSwatch = self[0];
        }
        GlassBar.Item[] custom = new GlassBar.Item[1];
        custom[0] = toolBar.swatch(null, 2, () -> {
            Color chosen = JColorChooser.showDialog(frame, "Choose Color", canvas.color);
            if (chosen == null) return false;          // cancelled: keep previous selection
            custom[0].color = chosen;
            useColor(chosen, pen);
            return true;
        });
        custom[0].custom = true;
        toolBar.separator();
        toolBar.action("Undo", Icons::undo, canvas::undo, canvas::canUndo);
        toolBar.action("Clear", Icons::trash, canvas::clear, canvas::canClear);
        toolBar.finish();
        toolBar.select(pen);
        toolBar.select(firstSwatch);
        canvas.color = THEME.swatches[0];

        canvas.onChange = toolBar::repaint;   // Undo / Clear enabled state follows the strokes
        canvas.setBars(topBar, toolBar);

        int shortcut = Toolkit.getDefaultToolkit().getMenuShortcutKeyMaskEx();
        frame.getRootPane().registerKeyboardAction(e -> canvas.undo(),
                KeyStroke.getKeyStroke(KeyEvent.VK_Z, shortcut), JComponent.WHEN_IN_FOCUSED_WINDOW);

        frame.setContentPane(canvas);
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.setSize(900, 640);
        frame.setLocationRelativeTo(null);
    }

    void show() { frame.setVisible(true); }

    void setDrawMode(boolean on) {
        canvas.drawingEnabled = on;
        canvas.updateCursor();
        toolBar.setShown(on);   // tool bar only appears in Draw mode
    }

    /** Picking a color while holding the eraser switches back to the pen. */
    void useColor(Color c, GlassBar.Item pen) {
        canvas.color = c;
        if (canvas.tool != Tool.PEN) {
            canvas.tool = Tool.PEN;
            toolBar.select(pen);
            canvas.updateCursor();
        }
    }

    enum Tool { PEN, ERASER }

    // =====================================================================
    /** Line icons on a 20×20 grid, centered at (0,0). Caller sets color; stroke is set here. */
    interface Glyph { void paint(Graphics2D g); }

    static final class Icons {
        static void prep(Graphics2D g) {
            g.setStroke(new BasicStroke(1.7f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        }

        static void scribble(Graphics2D g) {
            prep(g);
            Path2D p = new Path2D.Double();
            p.moveTo(-8, 5);
            p.curveTo(-6, -7, -3, -7, -1, 0);
            p.curveTo(1, 7, 5, 7, 8, -5);
            g.draw(p);
        }

        static void document(Graphics2D g) {
            prep(g);
            Path2D p = new Path2D.Double();
            p.moveTo(-6, -8.5); p.lineTo(2, -8.5); p.lineTo(6.5, -4); p.lineTo(6.5, 8.5);
            p.lineTo(-6, 8.5); p.closePath();
            g.draw(p);
            Path2D fold = new Path2D.Double();
            fold.moveTo(2, -8.5); fold.lineTo(2, -4); fold.lineTo(6.5, -4);
            g.draw(fold);
            g.draw(new Line2D.Double(-3, 0, 3.5, 0));
            g.draw(new Line2D.Double(-3, 4, 3.5, 4));
        }

        static void equalCircle(Graphics2D g) {
            prep(g);
            g.draw(new Ellipse2D.Double(-8.5, -8.5, 17, 17));
            g.draw(new Line2D.Double(-4, -2.2, 4, -2.2));
            g.draw(new Line2D.Double(-4, 2.2, 4, 2.2));
        }

        static void pencil(Graphics2D g) {
            prep(g);
            AffineTransform t = g.getTransform();
            g.rotate(-Math.PI / 4);
            Path2D p = new Path2D.Double();
            p.moveTo(-6, -2.8); p.lineTo(8, -2.8); p.lineTo(8, 2.8); p.lineTo(-6, 2.8);
            p.lineTo(-10.5, 0); p.closePath();
            g.draw(p);
            g.draw(new Line2D.Double(-6, -2.8, -6, 2.8));
            g.draw(new Line2D.Double(5, -2.8, 5, 2.8));
            g.setTransform(t);
        }

        static void eraser(Graphics2D g) {
            prep(g);
            AffineTransform t = g.getTransform();
            g.rotate(-Math.PI / 4);
            RoundRectangle2D body = new RoundRectangle2D.Double(-8.5, -3.8, 17, 7.6, 3, 3);
            Shape clip = g.getClip();
            g.clip(new Rectangle2D.Double(-9, -5, 7.5, 10));
            g.fill(body);
            g.setClip(clip);
            g.draw(body);
            g.setTransform(t);
            g.draw(new Line2D.Double(-2, 8.5, 8, 8.5));
        }

        static void undo(Graphics2D g) {
            prep(g);
            Path2D p = new Path2D.Double();
            p.moveTo(-7, -3);
            p.lineTo(2, -3);
            p.append(new Arc2D.Double(-2.5, -3, 9, 9, 90, -180, Arc2D.OPEN), true);
            p.lineTo(-3, 6);
            g.draw(p);
            Path2D head = new Path2D.Double();
            head.moveTo(-3.5, -6.5); head.lineTo(-7, -3); head.lineTo(-3.5, 0.5);
            g.draw(head);
        }

        static void trash(Graphics2D g) {
            prep(g);
            g.draw(new Line2D.Double(-7.5, -5.5, 7.5, -5.5));
            Path2D lid = new Path2D.Double();
            lid.moveTo(-2.5, -5.5); lid.lineTo(-2.5, -8); lid.lineTo(2.5, -8); lid.lineTo(2.5, -5.5);
            g.draw(lid);
            Path2D can = new Path2D.Double();
            can.moveTo(-5.5, -5.5); can.lineTo(-4.5, 8.5); can.lineTo(4.5, 8.5); can.lineTo(5.5, -5.5);
            g.draw(can);
            g.draw(new Line2D.Double(-1.8, -2, -1.5, 5.5));
            g.draw(new Line2D.Double(1.8, -2, 1.5, 5.5));
        }
    }

    // =====================================================================
    /** One floating glass capsule containing segments, actions, swatches and separators. */
    static class GlassBar extends JComponent {
        enum Kind { SEGMENT, ACTION, SWATCH, SEPARATOR }

        static class Item {
            final Kind kind; final String label; final Glyph icon; final int group;
            float w, x;
            Color color; boolean custom;
            BooleanSupplier onActivate = () -> true;   // return false to keep the current selection
            BooleanSupplier enabled = () -> true;
            Item(Kind kind, String label, Glyph icon, int group, float w) {
                this.kind = kind; this.label = label; this.icon = icon; this.group = group; this.w = w;
            }
            float center() { return x + w / 2f; }
        }

        /** Under-damped spring: overshoots a little, which reads as "liquid". */
        static class Spring {
            float x, v;
            boolean step(float target, float k, float damping) {
                v = (v + (target - x) * k) * damping; x += v;
                if (Math.abs(target - x) < 0.15f && Math.abs(v) < 0.15f) { x = target; v = 0; return true; }
                return false;
            }
        }

        static class Platter { final Spring x = new Spring(), w = new Spring(); }

        final DrawingCanvas backdrop;
        final Font font;
        final List<Item> items = new ArrayList<>();
        final Map<Integer, Item> selected = new HashMap<>();
        final Map<Integer, Platter> platters = new HashMap<>();
        float capW, capH = SEG_H + 2 * PAD;

        Item hoverItem, pressItem;
        float mouseX;
        final Spring press = new Spring();  float pressTarget;
        float glow;
        final Point2D.Float glowAt = new Point2D.Float();
        float appear = 1f, appearTarget = 1f;
        // hover lens state
        final Spring lensX = new Spring(), lensW = new Spring();
        float lensAlpha;

        final javax.swing.Timer timer = new javax.swing.Timer(16, e -> step());
        private BufferedImage shadowCache;
        private double shadowScale;

        GlassBar(DrawingCanvas backdrop, Font font) {
            this.backdrop = backdrop; this.font = font;
            setOpaque(false);
            MouseAdapter m = new MouseAdapter() {
                @Override public void mousePressed(MouseEvent e) {
                    pressItem = itemAt(e.getX());
                    glowAt.setLocation(e.getX(), e.getY());
                    glow = 1f; pressTarget = 1f; timer.start();
                }
                @Override public void mouseReleased(MouseEvent e) {
                    Item it = itemAt(e.getX());
                    Item pressed = pressItem;
                    pressItem = null; pressTarget = 0f; timer.start();
                    if (it != null && it == pressed && contains(e.getPoint())) activate(it);
                }
                @Override public void mouseDragged(MouseEvent e) {
                    glowAt.setLocation(e.getX(), e.getY()); mouseX = e.getX(); timer.start();
                }
                @Override public void mouseMoved(MouseEvent e) {
                    mouseX = e.getX();
                    hoverItem = itemAt(e.getX());
                    timer.start();
                }
                @Override public void mouseExited(MouseEvent e) { hoverItem = null; timer.start(); }
            };
            addMouseListener(m);
            addMouseMotionListener(m);
        }

        // ---- building ----
        Item segment(String label, Glyph icon, int group, BooleanSupplier onActivate) {
            Item it = new Item(Kind.SEGMENT, label, icon, group, 0); it.onActivate = onActivate;
            items.add(it); return it;
        }
        Item action(String label, Glyph icon, Runnable r, BooleanSupplier enabled) {
            Item it = new Item(Kind.ACTION, label, icon, -1, 0);
            it.onActivate = () -> { r.run(); return true; }; it.enabled = enabled; items.add(it); return it;
        }
        Item swatch(Color c, int group, BooleanSupplier onActivate) {
            Item it = new Item(Kind.SWATCH, null, null, group, 36); it.color = c; it.onActivate = onActivate;
            items.add(it); return it;
        }
        void separator() { items.add(new Item(Kind.SEPARATOR, null, null, -1, 13)); }

        /** Measures labels, lays items out left to right. */
        void finish() {
            FontRenderContext frc = new FontRenderContext(null, true, true);
            float x = 0;
            for (Item it : items) {
                if (it.w <= 0) {
                    float text = (float) font.getStringBounds(it.label, frc).getWidth();
                    it.w = Math.max(84, ITEM_PAD_X * 2 + ICON + ICON_GAP + text);
                }
                it.x = x; x += it.w;
            }
            capW = x + 2 * PAD;
            setSize(getPreferredSize());
        }

        @Override public Dimension getPreferredSize() {
            return new Dimension((int) Math.ceil(capW) + 2 * MARGIN, (int) capH + 2 * MARGIN);
        }

        // ---- interaction ----
        void activate(Item it) {
            if (!it.enabled.getAsBoolean()) return;
            switch (it.kind) {
                case SEGMENT, SWATCH -> { if (it.onActivate.getAsBoolean()) select(it); }
                case ACTION -> it.onActivate.getAsBoolean();
                default -> { }
            }
            repaint();
        }

        void select(Item it) {
            selected.put(it.group, it);
            if (it.kind == Kind.SEGMENT && !platters.containsKey(it.group)) {
                Platter p = new Platter(); p.x.x = it.x; p.w.x = it.w; platters.put(it.group, p);
            }
            timer.start();
        }

        void setShown(boolean shown) {
            appearTarget = shown ? 1f : 0f;
            if (!shown) hoverItem = null;
            timer.start();
        }

        Item itemAt(int x) {
            float lx = x - MARGIN - PAD;
            for (Item it : items) if (it.kind != Kind.SEPARATOR && lx >= it.x && lx < it.x + it.w) return it;
            return null;
        }

        /** Only the capsule itself is hit-testable (nothing while hidden); the margin passes through. */
        @Override public boolean contains(int x, int y) {
            return appearTarget > 0f && capsule(0).contains(x, y);
        }

        RoundRectangle2D.Float capsule(float grow) {
            float w = capW + 2 * grow, h = capH + 2 * grow;
            return new RoundRectangle2D.Float(MARGIN - grow, MARGIN - grow, w, h, h, h);
        }

        void step() {
            boolean settled = true;
            for (Map.Entry<Integer, Platter> e : platters.entrySet()) {
                Item t = selected.get(e.getKey());
                settled &= e.getValue().x.step(t.x, 0.16f, 0.70f);
                settled &= e.getValue().w.step(t.w, 0.16f, 0.70f);
            }
            settled &= press.step(pressTarget, 0.30f, 0.62f);
            if (pressTarget == 0f) { glow *= 0.86f; if (glow < 0.01f) glow = 0; else settled = false; }
            appear += (appearTarget - appear) * 0.22f;
            if (Math.abs(appearTarget - appear) < 0.01f) appear = appearTarget; else settled = false;

            // hover lens: sticks to the hovered button, leaning slightly toward the cursor
            Item h = (hoverItem != null && hoverItem.enabled.getAsBoolean() && appearTarget > 0) ? hoverItem : null;
            float alphaTarget = h != null ? 1f : 0f;
            if (h != null) {
                float cx = h.center() + ((mouseX - MARGIN - PAD) - h.center()) * HOVER_STICK;
                float w = h.w + HOVER_GROW_X;
                if (lensAlpha < 0.05f) { lensX.x = cx; lensW.x = w; lensX.v = lensW.v = 0; }
                settled &= lensX.step(cx, 0.20f, 0.68f);
                settled &= lensW.step(w, 0.20f, 0.68f);
            }
            lensAlpha += (alphaTarget - lensAlpha) * 0.25f;
            if (Math.abs(alphaTarget - lensAlpha) < 0.01f) lensAlpha = alphaTarget; else settled = false;

            if (settled) timer.stop();
            repaint();
        }

        // ---- painting ----
        @Override
        protected void paintComponent(Graphics g) {
            if (appear <= 0f) return;
            Graphics2D g2 = (Graphics2D) g.create();
            double s = g2.getTransform().getScaleX();
            int W = getWidth(), H = getHeight();
            int pw = (int) Math.ceil(W * s), ph = (int) Math.ceil(H * s);

            // Appear / disappear: scale + opacity; lensing strength ramps with it
            if (appear < 1f) {
                g2.setComposite(AlphaComposite.SrcOver.derive(appear));
                double k = 0.9 + 0.1 * appear;
                g2.translate(W / 2.0, H / 2.0); g2.scale(k, k); g2.translate(-W / 2.0, -H / 2.0);
            }

            BufferedImage raw = backdropImage(s, pw, ph);
            BufferedImage glass = new BufferedImage(pw, ph, BufferedImage.TYPE_INT_ARGB);
            Graphics2D gg = glass.createGraphics();
            hints(gg);
            gg.scale(s, s);
            paintGlass(gg, s, raw);
            gg.dispose();
            g2.drawImage(glass, 0, 0, W, H, null);

            if (lensAlpha > 0.01f) {
                // The droplet magnifies everything under it: canvas strokes + the bar itself
                BufferedImage scene = new BufferedImage(pw, ph, BufferedImage.TYPE_INT_ARGB);
                Graphics2D sg = scene.createGraphics();
                sg.drawImage(raw, 0, 0, null);
                sg.drawImage(glass, 0, 0, null);
                sg.dispose();
                paintHoverLens(g2, s, scene);
            }
            g2.dispose();
        }

        static void hints(Graphics2D g) {
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            g.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);
            g.setRenderingHint(RenderingHints.KEY_FRACTIONALMETRICS, RenderingHints.VALUE_FRACTIONALMETRICS_ON);
        }

        BufferedImage backdropImage(double s, int pw, int ph) {
            BufferedImage src = new BufferedImage(pw, ph, BufferedImage.TYPE_INT_ARGB);
            Graphics2D bg = src.createGraphics();
            bg.scale(s, s);
            bg.translate(-getX(), -getY());
            backdrop.paintBackdrop(bg, s);
            bg.dispose();
            return src;
        }

        void paintGlass(Graphics2D g2, double s, BufferedImage raw) {
            int W = getWidth(), H = getHeight();
            RoundRectangle2D.Float cap = capsule(press.x * 3f);

            if (shadowCache == null || shadowScale != s) { shadowCache = buildShadow(s); shadowScale = s; }
            g2.drawImage(shadowCache, 0, 0, W, H, null);
            g2.drawImage(refract(gaussian(raw, (float) (BLUR_SIGMA * s)), s, cap, appear), 0, 0, W, H, null);

            g2.setColor(THEME.tint);
            g2.fill(cap);
            g2.setPaint(new GradientPaint(0, cap.y, new Color(255, 255, 255, THEME.gloss),
                                          0, cap.y + cap.height * 0.55f, new Color(255, 255, 255, 0)));
            g2.fill(cap);

            float scale = cap.width / capW;
            for (Platter p : platters.values()) {
                g2.setColor(THEME.platter);
                g2.fill(slot(p.x.x, p.w.x, cap, scale));
            }

            if (glow > 0.01f) {
                float r = 70f + 50f * (1f - glow);
                g2.setPaint(new RadialGradientPaint(glowAt, r, new float[]{0f, 1f},
                        new Color[]{new Color(1f, 1f, 1f, 0.40f * glow), new Color(1f, 1f, 1f, 0f)}));
                g2.fill(cap);
            }

            g2.setStroke(new BasicStroke(1f));
            g2.setColor(THEME.edge);
            g2.draw(inset(cap, 0.5f));
            g2.setStroke(new BasicStroke(1.4f));
            g2.setPaint(rimPaint(cap, THEME.rimTop, THEME.rimBottom));
            g2.draw(inset(cap, 1.2f));

            // Foreground (icons, labels, swatches) with a soft halo so it stays legible over strokes
            int pw = (int) Math.ceil(W * s), ph = (int) Math.ceil(H * s);
            BufferedImage fg = new BufferedImage(pw, ph, BufferedImage.TYPE_INT_ARGB);
            Graphics2D fgG = fg.createGraphics();
            hints(fgG);
            fgG.scale(s, s);
            paintForeground(fgG, cap, scale);
            fgG.dispose();
            g2.drawImage(halo(fg, (float) (2.2 * s)), 0, 0, W, H, null);
            g2.drawImage(fg, 0, 0, W, H, null);
        }

        static Paint rimPaint(RoundRectangle2D.Float r, int top, int bottom) {
            return new LinearGradientPaint(r.x, r.y, r.x + r.width * 0.35f, r.y + r.height * 2.2f,
                    new float[]{0f, 0.35f, 0.7f, 1f},
                    new Color[]{new Color(255, 255, 255, top), new Color(255, 255, 255, 0),
                                new Color(255, 255, 255, 0), new Color(255, 255, 255, bottom)});
        }

        void paintForeground(Graphics2D g2, RoundRectangle2D.Float cap, float scale) {
            g2.setFont(font);
            FontMetrics fm = g2.getFontMetrics();
            for (Item it : items) {
                Rectangle2D b = slot(it.x, it.w, cap, scale).getBounds2D();
                switch (it.kind) {
                    case SEGMENT, ACTION -> {
                        Color c = !it.enabled.getAsBoolean() ? THEME.labelDisabled
                                : selected.get(it.group) == it ? THEME.labelSelected : THEME.label;
                        float textW = fm.stringWidth(it.label);
                        double total = (ICON + ICON_GAP) * scale + textW;
                        double x0 = b.getCenterX() - total / 2;
                        g2.setColor(c);
                        AffineTransform t = g2.getTransform();
                        g2.translate(x0 + ICON * scale / 2, b.getCenterY());
                        g2.scale(scale * ICON / 20.0, scale * ICON / 20.0);
                        it.icon.paint(g2);
                        g2.setTransform(t);
                        g2.drawString(it.label, (float) (x0 + (ICON + ICON_GAP) * scale),
                                (float) (b.getCenterY() - fm.getHeight() / 2.0 + fm.getAscent()));
                    }
                    case SWATCH -> paintSwatch(g2, it, b.getCenterX(), b.getCenterY(), scale);
                    case SEPARATOR -> {
                        g2.setColor(THEME.edge);
                        g2.setStroke(new BasicStroke(1f));
                        double h = 20 * scale;
                        g2.draw(new Line2D.Double(b.getCenterX(), b.getCenterY() - h / 2, b.getCenterX(), b.getCenterY() + h / 2));
                    }
                }
            }
        }

        void paintSwatch(Graphics2D g2, Item it, double cx, double cy, float scale) {
            double d = 22 * scale;
            Ellipse2D dot = new Ellipse2D.Double(cx - d / 2, cy - d / 2, d, d);
            if (it.custom) {
                for (int i = 0; i < 24; i++) {
                    g2.setColor(Color.getHSBColor(i / 24f, 0.75f, 0.95f));
                    g2.fill(new Arc2D.Double(dot.getBounds2D(), 90 - i * 15, -15.5, Arc2D.PIE));
                }
                double di = d - 7 * scale;
                g2.setColor(it.color != null ? it.color : THEME.canvas);
                g2.fill(new Ellipse2D.Double(cx - di / 2, cy - di / 2, di, di));
            } else {
                g2.setColor(it.color);
                g2.fill(dot);
            }
            g2.setColor(THEME.edge);
            g2.setStroke(new BasicStroke(1f));
            g2.draw(dot);
            if (selected.get(it.group) == it) {
                double dr = 30 * scale;
                g2.setColor(THEME.labelSelected);
                g2.setStroke(new BasicStroke(2f));
                g2.draw(new Ellipse2D.Double(cx - dr / 2, cy - dr / 2, dr, dr));
            }
        }

        /** Hover droplet: magnifies the scene, bends it at the rim with slight color dispersion. */
        void paintHoverLens(Graphics2D g2, double s, BufferedImage scene) {
            float lh = capH + 2 * HOVER_GROW_Y + press.x * 4f;
            float lw = Math.max(lh, lensW.x + Math.min(Math.abs(lensX.v) * 1.2f, 28f) + press.x * 4f);
            float lcx = MARGIN + PAD + lensX.x, lcy = MARGIN + capH / 2f;
            RoundRectangle2D.Float lens = new RoundRectangle2D.Float(lcx - lw / 2, lcy - lh / 2, lw, lh, lh, lh);

            Composite base = g2.getComposite();
            float baseAlpha = base instanceof AlphaComposite ac ? ac.getAlpha() : 1f;
            g2.setComposite(AlphaComposite.SrcOver.derive(baseAlpha * lensAlpha));
            hints(g2);

            // soft contact shadow (a few stacked translucent capsules — cheap enough per frame)
            for (int i = 4; i >= 1; i--) {
                g2.setColor(new Color(0f, 0f, 0f, THEME.shadow * 0.16f));
                g2.fill(new RoundRectangle2D.Float(lens.x - i, lens.y + 2 - i + 3, lens.width + 2 * i,
                        lens.height + 2 * i, lens.height + 2 * i, lens.height + 2 * i));
            }

            int pw = scene.getWidth(), ph = scene.getHeight();
            int[] in = scene.getRGB(0, 0, pw, ph, null, 0, pw);
            int[] out = new int[pw * ph];
            double cx = lcx * s, cy = lcy * s, R = lh / 2.0 * s;
            double x0 = cx - (lw / 2.0 - lh / 2.0) * s, x1 = cx + (lw / 2.0 - lh / 2.0) * s;
            double band = HOVER_EDGE_BAND * s, k = HOVER_EDGE_K * s;
            int yMin = Math.max(0, (int) (cy - R) - 1), yMax = Math.min(ph, (int) Math.ceil(cy + R) + 1);
            int xMin = Math.max(0, (int) (x0 - R) - 1), xMax = Math.min(pw, (int) Math.ceil(x1 + R) + 1);
            for (int y = yMin; y < yMax; y++) {
                double py = y + 0.5;
                for (int x = xMin; x < xMax; x++) {
                    double px = x + 0.5;
                    double qx = Math.max(x0, Math.min(x1, px));
                    double dx = px - qx, dy = py - cy;
                    double len = Math.sqrt(dx * dx + dy * dy);
                    double d = len - R;
                    if (d > 0.5) continue;
                    double coverage = Math.min(1.0, 0.5 - d);
                    double bx = cx + (px - cx) / HOVER_MAG, by = cy + (py - cy) / HOVER_MAG;
                    double off = 0, nx = 0, ny = 0;
                    if (-d < band && len > 1e-6) {
                        double t = 1.0 + d / band;
                        off = t * t * k; nx = dx / len; ny = dy / len;
                    }
                    double oR = off * (1 + HOVER_DISPERSION), oB = off * (1 - HOVER_DISPERSION);
                    int cr = bilinear(in, pw, ph, bx + nx * oR - 0.5, by + ny * oR - 0.5);
                    int cg = bilinear(in, pw, ph, bx + nx * off - 0.5, by + ny * off - 0.5);
                    int cb = bilinear(in, pw, ph, bx + nx * oB - 0.5, by + ny * oB - 0.5);
                    int a = (int) Math.round(255 * coverage);
                    out[y * pw + x] = (a << 24) | (cr & 0xFF0000) | (cg & 0xFF00) | (cb & 0xFF);
                }
            }
            BufferedImage img = new BufferedImage(pw, ph, BufferedImage.TYPE_INT_ARGB);
            img.setRGB(0, 0, pw, ph, out, 0, pw);
            g2.drawImage(img, 0, 0, getWidth(), getHeight(), null);

            // gloss + specular rim
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

        RoundRectangle2D.Float slot(float x, float w, RoundRectangle2D.Float cap, float scale) {
            float sx = cap.x + (PAD + x) * scale, sy = cap.y + PAD * scale;
            float sw = w * scale, sh = SEG_H * scale;
            return new RoundRectangle2D.Float(sx, sy, sw, sh, sh, sh);
        }

        static RoundRectangle2D.Float inset(RoundRectangle2D.Float r, float d) {
            return new RoundRectangle2D.Float(r.x + d, r.y + d, r.width - 2 * d, r.height - 2 * d,
                    r.arcwidth - 2 * d, r.archeight - 2 * d);
        }

        /** Edge lensing of the backdrop + anti-aliased capsule clip. */
        BufferedImage refract(BufferedImage src, double s, RoundRectangle2D.Float cap, float strength) {
            int pw = src.getWidth(), ph = src.getHeight();
            int[] in = src.getRGB(0, 0, pw, ph, null, 0, pw);
            int[] out = new int[pw * ph];
            double R = cap.height / 2.0 * s, cy = (cap.y + cap.height / 2.0) * s;
            double x0 = (cap.x + cap.height / 2.0) * s, x1 = (cap.x + cap.width - cap.height / 2.0) * s;
            double band = LENS_BAND * s, k = LENS_STRENGTH * s * strength;
            int yMin = Math.max(0, (int) (cy - R) - 1), yMax = Math.min(ph, (int) Math.ceil(cy + R) + 1);
            int xMin = Math.max(0, (int) (x0 - R) - 1), xMax = Math.min(pw, (int) Math.ceil(x1 + R) + 1);
            for (int y = yMin; y < yMax; y++) {
                double py = y + 0.5;
                for (int x = xMin; x < xMax; x++) {
                    double px = x + 0.5;
                    double qx = Math.max(x0, Math.min(x1, px));
                    double dx = px - qx, dy = py - cy;
                    double len = Math.sqrt(dx * dx + dy * dy);
                    double d = len - R;
                    if (d > 0.5) continue;
                    double coverage = Math.min(1.0, 0.5 - d);
                    double sx = px, sy = py;
                    if (-d < band && len > 1e-6) {
                        double t = 1.0 + d / band, off = t * t * k;
                        sx += dx / len * off; sy += dy / len * off;
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

        /** Soft halo in the canvas color behind the foreground glyphs. */
        static BufferedImage halo(BufferedImage fg, float sigma) {
            int w = fg.getWidth(), h = fg.getHeight();
            int[] px = fg.getRGB(0, 0, w, h, null, 0, w);
            int hc = THEME.halo.getRGB() & 0xFFFFFF, ha = THEME.halo.getAlpha();
            for (int i = 0; i < px.length; i++) {
                int a = (px[i] >>> 24) * ha / 255;
                px[i] = (a << 24) | hc;
            }
            BufferedImage mask = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
            mask.setRGB(0, 0, w, h, px, 0, w);
            // Blur only the alpha; transparent pixels lose their RGB when copied, so recolor afterwards.
            int[] b = gaussian(mask, sigma).getRGB(0, 0, w, h, null, 0, w);
            for (int i = 0; i < b.length; i++) {
                int a = Math.min(255, (b[i] >>> 24) * 2);   // a blurred thin glyph is faint: boost
                b[i] = (a << 24) | hc;
            }
            BufferedImage out = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
            out.setRGB(0, 0, w, h, b, 0, w);
            return out;
        }

        static int bilinear(int[] px, int w, int h, double x, double y) {
            x = Math.max(0, Math.min(w - 1.001, x));
            y = Math.max(0, Math.min(h - 1.001, y));
            int ix = (int) x, iy = (int) y;
            double fx = x - ix, fy = y - iy;
            int c00 = px[iy * w + ix], c10 = px[iy * w + ix + 1];
            int c01 = px[(iy + 1) * w + ix], c11 = px[(iy + 1) * w + ix + 1];
            int r = 0;
            for (int sh = 0; sh <= 24; sh += 8) {
                double v = ((c00 >>> sh) & 0xFF) * (1 - fx) * (1 - fy) + ((c10 >>> sh) & 0xFF) * fx * (1 - fy)
                         + ((c01 >>> sh) & 0xFF) * (1 - fx) * fy + ((c11 >>> sh) & 0xFF) * fx * fy;
                r |= ((int) Math.round(v) & 0xFF) << sh;
            }
            return r;
        }

        BufferedImage buildShadow(double s) {
            int pw = (int) Math.ceil(getWidth() * s), ph = (int) Math.ceil(getHeight() * s);
            BufferedImage out = new BufferedImage(pw, ph, BufferedImage.TYPE_INT_ARGB);
            Graphics2D g = out.createGraphics();
            g.drawImage(shadowLayer(pw, ph, s, 0f, 1.5f, THEME.shadow * 0.5f), 0, 0, null);
            g.drawImage(shadowLayer(pw, ph, s, 6f, 8f, THEME.shadow), 0, 0, null);
            g.dispose();
            // cut the capsule out: with a transparent glass the shadow must not darken what's under it
            Graphics2D c = out.createGraphics();
            c.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            c.scale(s, s);
            c.setComposite(AlphaComposite.Clear);
            c.fill(capsule(0));
            c.dispose();
            return out;
        }

        BufferedImage shadowLayer(int pw, int ph, double s, float dy, float sigma, float alpha) {
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

        /** Separable Gaussian blur, edge-extended by 3σ so ConvolveOp's untouched border stays clean. */
        static BufferedImage gaussian(BufferedImage src, float sigma) {
            if (sigma < 0.3f) return src;
            int r = (int) Math.ceil(sigma * 3);
            int w = src.getWidth(), h = src.getHeight();
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
            for (int i = -r; i <= r; i++) { k[i + r] = (float) Math.exp(-(i * i) / (2 * sigma * sigma)); sum += k[i + r]; }
            for (int i = 0; i < k.length; i++) k[i] /= sum;
            BufferedImage a = new ConvolveOp(new Kernel(k.length, 1, k), ConvolveOp.EDGE_NO_OP, null).filter(padded, null);
            BufferedImage b = new ConvolveOp(new Kernel(1, k.length, k), ConvolveOp.EDGE_NO_OP, null).filter(a, null);
            return b.getSubimage(r, r, w, h);
        }
    }

    // =====================================================================
    /**
     * Full-window canvas, no border. Strokes are kept as vectors (ready for a recognizer);
     * an eraser pass is just another stroke, so Undo treats pen and eraser the same way.
     */
    static class DrawingCanvas extends JPanel {
        static class Stroke {
            final Path2D path = new Path2D.Double();
            final Color color; final float width; final boolean erase;
            Stroke(Color color, float width, boolean erase) { this.color = color; this.width = width; this.erase = erase; }
        }

        final List<Stroke> strokes = new ArrayList<>();
        /** History: a Stroke = one added stroke; a List = snapshot taken before Clear (so Clear is undoable). */
        private final Deque<Object> history = new ArrayDeque<>();

        boolean drawingEnabled = true;
        Tool tool = Tool.PEN;
        Color color = Color.BLACK;
        Runnable onChange = () -> { };

        private Stroke current;
        private Point2D.Double last;
        private Point mouse;
        private GlassBar[] bars = {};

        // cached ink layer; the eraser clears pixels with AlphaComposite.Clear
        private BufferedImage layer;
        private double layerScale;
        private boolean layerDirty = true;

        DrawingCanvas() {
            super(null);
            setBackground(THEME.canvas);
            MouseAdapter m = new MouseAdapter() {
                @Override public void mousePressed(MouseEvent e) {
                    if (!drawingEnabled || !SwingUtilities.isLeftMouseButton(e)) return;
                    boolean erase = tool == Tool.ERASER;
                    current = new Stroke(color, erase ? ERASER_WIDTH : PEN_WIDTH, erase);
                    current.path.moveTo(e.getX(), e.getY());
                    current.path.lineTo(e.getX(), e.getY());   // a click leaves a dot
                    last = new Point2D.Double(e.getX(), e.getY());
                    strokes.add(current);
                    history.push(current);
                    paintSegment(current, last, last);
                    repaint();
                    onChange.run();
                }
                @Override public void mouseDragged(MouseEvent e) {
                    mouse = e.getPoint();
                    if (current == null) { repaint(); return; }
                    Point2D.Double p = new Point2D.Double(e.getX(), e.getY());
                    current.path.lineTo(p.x, p.y);
                    paintSegment(current, last, p);
                    last = p;
                    repaint();
                }
                @Override public void mouseReleased(MouseEvent e) { current = null; }
                @Override public void mouseMoved(MouseEvent e) {
                    mouse = e.getPoint();
                    if (tool == Tool.ERASER) repaint();
                }
                @Override public void mouseExited(MouseEvent e) { mouse = null; repaint(); }
            };
            addMouseListener(m);
            addMouseMotionListener(m);
            updateCursor();
        }

        void setBars(GlassBar... b) { bars = b; for (GlassBar bar : b) add(bar); }

        @Override public void doLayout() {
            if (bars.length < 2) return;
            Dimension t = bars[0].getPreferredSize(), b = bars[1].getPreferredSize();
            bars[0].setBounds((getWidth() - t.width) / 2, TOP - MARGIN, t.width, t.height);
            bars[1].setBounds((getWidth() - b.width) / 2, getHeight() - BOTTOM - (int) bars[1].capH - MARGIN,
                              b.width, b.height);
        }

        void updateCursor() {
            if (drawingEnabled && tool == Tool.ERASER) {
                BufferedImage blank = new BufferedImage(16, 16, BufferedImage.TYPE_INT_ARGB);
                setCursor(Toolkit.getDefaultToolkit().createCustomCursor(blank, new Point(0, 0), "eraser"));
            } else if (drawingEnabled) {
                setCursor(Cursor.getPredefinedCursor(Cursor.CROSSHAIR_CURSOR));
            } else {
                setCursor(Cursor.getDefaultCursor());
            }
            repaint();
        }

        boolean canUndo() { return !history.isEmpty(); }
        boolean canClear() { return !strokes.isEmpty(); }

        void undo() {
            if (history.isEmpty() || current != null) return;
            Object h = history.pop();
            if (h instanceof Stroke) strokes.remove(strokes.size() - 1);
            else { strokes.clear(); strokes.addAll(castList(h)); }
            layerDirty = true;
            repaint();
            onChange.run();
        }

        void clear() {
            if (strokes.isEmpty()) return;
            history.push(new ArrayList<>(strokes));
            strokes.clear();
            layerDirty = true;
            repaint();
            onChange.run();
        }

        @SuppressWarnings("unchecked")
        private static List<Stroke> castList(Object o) { return (List<Stroke>) o; }

        private BufferedImage inkLayer(double s) {
            int pw = (int) Math.ceil(getWidth() * s), ph = (int) Math.ceil(getHeight() * s);
            if (layer == null || layer.getWidth() != Math.max(1, pw) || layer.getHeight() != Math.max(1, ph)
                    || layerScale != s) {
                layer = new BufferedImage(Math.max(1, pw), Math.max(1, ph), BufferedImage.TYPE_INT_ARGB);
                layerScale = s;
                layerDirty = true;
            }
            if (layerDirty) {
                Graphics2D g = layerGraphics();
                g.setComposite(AlphaComposite.Clear);
                g.fillRect(0, 0, getWidth(), getHeight());
                for (Stroke st : strokes) applyStyle(g, st).draw(st.path);
                g.dispose();
                layerDirty = false;
            }
            return layer;
        }

        private Graphics2D layerGraphics() {
            Graphics2D g = layer.createGraphics();
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);
            g.scale(layerScale, layerScale);
            return g;
        }

        private static Graphics2D applyStyle(Graphics2D g, Stroke st) {
            g.setComposite(st.erase ? AlphaComposite.Clear : AlphaComposite.SrcOver);
            g.setColor(st.color);
            g.setStroke(new BasicStroke(st.width, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            return g;
        }

        /** While writing, only the newest segment is drawn into the cached layer. */
        private void paintSegment(Stroke st, Point2D a, Point2D b) {
            if (layer == null || layerDirty) return;
            Graphics2D g = layerGraphics();
            applyStyle(g, st).draw(new Line2D.Double(a, b));
            g.dispose();
        }

        /** Background + ink (no bars) — the glass bars sample this. */
        void paintBackdrop(Graphics2D g, double s) {
            g.setColor(getBackground());
            g.fillRect(0, 0, getWidth(), getHeight());
            g.drawImage(inkLayer(s), 0, 0, getWidth(), getHeight(), null);
        }

        @Override protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g;
            paintBackdrop(g2, g2.getTransform().getScaleX());
            if (drawingEnabled && tool == Tool.ERASER && mouse != null) {
                Graphics2D c = (Graphics2D) g2.create();
                c.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                double r = ERASER_WIDTH / 2.0;
                Ellipse2D circle = new Ellipse2D.Double(mouse.x - r, mouse.y - r, 2 * r, 2 * r);
                c.setColor(new Color(255, 255, 255, 140));
                c.setStroke(new BasicStroke(3f));
                c.draw(circle);
                c.setColor(THEME.label);
                c.setStroke(new BasicStroke(1f));
                c.draw(circle);
                c.dispose();
            }
        }
    }
}
