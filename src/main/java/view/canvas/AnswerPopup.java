package view.canvas;

import org.scilab.forge.jlatexmath.TeXConstants;
import org.scilab.forge.jlatexmath.TeXFormula;
import org.scilab.forge.jlatexmath.TeXIcon;
import view.board.BoardTheme;
import view.board.GlassBar;
import view.board.Icons;
import view.stack.Fonts;

import javax.swing.JComponent;
import javax.swing.JPanel;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.event.MouseAdapter;
import java.awt.geom.RoundRectangle2D;
import java.util.function.Consumer;
import java.util.function.LongSupplier;

// glass capsule (Calculate, maybe Graph) + white card with the recognised formula
public class AnswerPopup extends JPanel {
    private static final float TEX_SIZE = 22f;
    private static final int CARD_SHIFT = 24;   // card sits a bit right of the button
    private static final int GAP = 4;
    private static final int PAD_X = 14;
    private static final int PAD_Y = 10;
    private static final int ARC = 14;
    private static final int SHADOW = 6;
    private static final int MAX_TEXT = 320;
    private static final int EDGE = 10;        // stays this far inside the canvas
    private static final int POPUP_GAP = 8;    // between formula and popup
    private static final int POPUP_LEFT = 40;  // starts a bit left of the formula's right end
    private static final Color BORDER = new Color(203, 213, 225);
    private static final Color ERROR_TEXT = new Color(185, 28, 28);

    private final GlassBar calcBar;   // Calculate only
    private final GlassBar bothBar;   // Calculate + Graph
    private final GlassBar.Item calcOnly;
    private final GlassBar.Item calcItem;
    private final GlassBar.Item graphItem;
    private GlassBar bar;             // the one on show, null for a problem
    private final Card card = new Card();
    private Runnable onCalculate = () -> { };
    private Runnable onGraph = () -> { };
    private boolean graphable;
    private String latex;
    private String problem;
    private TeXIcon icon;

    public AnswerPopup(Consumer<Graphics2D> backdrop) {
        this(backdrop, () -> 0L);
    }

    // backdrop paints in canvas coords, version changes when it looks different
    public AnswerPopup(Consumer<Graphics2D> backdrop, LongSupplier version) {
        super(null);
        setOpaque(false);
        calcBar = newBar(backdrop, version);
        calcOnly = calcBar.action("Calculate", Icons::equalCircle, () -> onCalculate.run(), () -> true);
        calcBar.finish();
        bothBar = newBar(backdrop, version);
        calcItem = bothBar.action("Calculate", Icons::equalCircle, () -> onCalculate.run(), () -> true);
        graphItem = bothBar.action("Graph", Icons::scribble, () -> onGraph.run(), () -> graphable);
        bothBar.finish();
        add(calcBar);
        add(bothBar);
        add(card);
        calcBar.setVisible(false);
        bothBar.setVisible(false);
        setVisible(false);
    }

    private GlassBar newBar(Consumer<Graphics2D> backdrop, LongSupplier version) {
        GlassBar[] self = new GlassBar[1];
        self[0] = new GlassBar(BoardTheme.font(14f),
                () -> new Point(getX() + self[0].getX(), getY() + self[0].getY()), backdrop, version);
        return self[0];
    }

    public void showFormula(String latex, Runnable onCalculate) {
        show(latex, onCalculate, null, false);
    }

    // board popup: Graph next to Calculate, grey when the formula can't be plotted
    public void showFormula(String latex, Runnable onCalculate, Runnable onGraph, boolean graphable) {
        show(latex, onCalculate, onGraph, graphable);
    }

    private void show(String latex, Runnable onCalculate, Runnable onGraph, boolean graphable) {
        this.latex = latex;
        this.problem = null;
        this.onCalculate = onCalculate;
        this.onGraph = onGraph == null ? () -> { } : onGraph;
        this.graphable = graphable;
        icon = typeset(latex);
        bar = onGraph == null ? calcBar : bothBar;
        relayout();
    }

    public void showProblem(String reason) {
        this.latex = null;
        this.problem = reason;
        this.onCalculate = () -> { };
        this.onGraph = () -> { };
        icon = null;
        bar = null;
        relayout();
    }

    // above-right of the formula, below if no room, always inside the canvas
    Rectangle spotBeside(Rectangle formula, int areaWidth, int areaHeight) {
        Dimension d = getPreferredSize();
        int m = bar != null ? GlassBar.MARGIN : 0;  // clear shadow margin top-left
        int x = formula.x + formula.width - POPUP_LEFT - m;
        int y = formula.y - POPUP_GAP - d.height;
        if (y + m < EDGE) {
            y = formula.y + formula.height + POPUP_GAP - m;
        }
        x = Math.max(EDGE - m, Math.min(x, areaWidth - d.width - EDGE));
        y = Math.max(EDGE - m, Math.min(y, areaHeight - d.height - EDGE));
        return new Rectangle(x, y, d.width, d.height);
    }

    // what you actually see (capsule + card), parent coords; the glass margin is see-through
    Rectangle visibleBounds() {
        Rectangle r = card.getBounds();
        if (bar != null) {
            r = r.union(new Rectangle(GlassBar.MARGIN, GlassBar.MARGIN, bar.capsuleWidth(), GlassBar.CAP_H));
        }
        r.translate(getX(), getY());
        return r;
    }

    String getLatex() {
        return latex;
    }

    String getProblem() {
        return problem;
    }

    boolean hasCalculate() {
        return bar != null;
    }

    boolean hasGraph() {
        return bar == bothBar;
    }

    boolean isGraphEnabled() {
        return bar == bothBar && graphItem.isEnabled();
    }

    boolean isCalculateEnabled() {
        return bar != null && (bar == bothBar ? calcItem : calcOnly).isEnabled();
    }

    void clickGraph() {
        if (bar == bothBar) {
            bothBar.activate(graphItem);
        }
    }

    boolean isTypeset() {
        return icon != null;
    }

    void clickCalculate() {
        if (bar != null) {
            bar.activate(bar == bothBar ? calcItem : calcOnly);
        }
    }

    private static TeXIcon typeset(String latex) {
        try {
            return new TeXFormula(latex).createTeXIcon(TeXConstants.STYLE_DISPLAY, TEX_SIZE);
        } catch (RuntimeException | Error bad) {  // bad latex -> plain text
            return null;
        }
    }

    // the bar has a clear margin for its shadow, the card lines up with the capsule
    private void relayout() {
        calcBar.setVisible(bar == calcBar);
        bothBar.setVisible(bar == bothBar);
        Dimension c = card.getPreferredSize();
        if (bar != null) {
            Dimension d = bar.getPreferredSize();
            bar.setBounds(0, 0, d.width, d.height);
            card.setBounds(GlassBar.MARGIN + CARD_SHIFT, GlassBar.MARGIN + GlassBar.CAP_H + GAP, c.width, c.height);
        } else {
            card.setBounds(0, 0, c.width, c.height);
        }
        setSize(getPreferredSize());
        revalidate();
        repaint();
    }

    @Override
    public Dimension getPreferredSize() {
        Dimension c = card.getPreferredSize();
        if (bar == null) {
            return c;
        }
        Dimension d = bar.getPreferredSize();
        return new Dimension(Math.max(d.width, GlassBar.MARGIN + CARD_SHIFT + c.width),
                GlassBar.MARGIN + GlassBar.CAP_H + GAP + c.height);
    }

    // white rounded card, eats clicks so they never hit the canvas
    private final class Card extends JComponent {
        Card() {
            addMouseListener(new MouseAdapter() { });
            addMouseMotionListener(new MouseAdapter() { });
        }

        private Font textFont() {
            return problem != null ? Fonts.ui(Font.PLAIN, 13f) : Fonts.ui(Font.PLAIN, 16f);
        }

        private String text() {
            return problem != null ? problem : latex == null ? "" : latex;
        }

        @Override
        public Dimension getPreferredSize() {
            int w;
            int h;
            if (icon != null) {
                w = icon.getIconWidth();
                h = icon.getIconHeight();
            } else {
                FontMetrics fm = getFontMetrics(textFont());
                w = Math.min(MAX_TEXT, fm.stringWidth(text()));
                h = fm.getHeight();
            }
            return new Dimension(w + 2 * PAD_X + SHADOW, h + 2 * PAD_Y + SHADOW);
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            int w = getWidth() - SHADOW;
            int h = getHeight() - SHADOW;
            for (int i = 1; i <= SHADOW; i++) {
                g2.setColor(new Color(15, 23, 42, 7));
                g2.fill(new RoundRectangle2D.Double(i * 0.4, i * 0.8, w + i * 0.2, h + i * 0.2, ARC + i, ARC + i));
            }
            RoundRectangle2D shape = new RoundRectangle2D.Double(0, 0, w - 1, h - 1, ARC, ARC);
            g2.setColor(Color.WHITE);
            g2.fill(shape);
            g2.setStroke(new BasicStroke(1f));
            g2.setColor(BORDER);
            g2.draw(shape);
            if (icon != null) {
                icon.setForeground(Color.BLACK);
                icon.paintIcon(this, g2, PAD_X, PAD_Y);
            } else {
                g2.setFont(textFont());
                g2.setColor(problem != null ? ERROR_TEXT : Color.DARK_GRAY);
                FontMetrics fm = g2.getFontMetrics();
                g2.clipRect(PAD_X, 0, w - 2 * PAD_X, h);
                g2.drawString(text(), PAD_X, PAD_Y + fm.getAscent());
            }
            g2.dispose();
        }
    }
}
