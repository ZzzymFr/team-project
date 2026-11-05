package view.canvas;

import org.scilab.forge.jlatexmath.TeXConstants;
import org.scilab.forge.jlatexmath.TeXFormula;
import org.scilab.forge.jlatexmath.TeXIcon;
import view.glass.GlassBackdrop;
import view.glass.GlassBar;
import view.glass.GlassIcons;
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
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.event.MouseAdapter;
import java.awt.geom.RoundRectangle2D;

// floating Graph | Calculate capsule + white card with the recognised formula
public class AnswerPopup extends JPanel {
    private static final float TEX_SIZE = 22f;
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

    private final GlassBar buttons;
    private final GlassBar.Item graph;
    private final GlassBar.Item calculate;
    private final Card card = new Card();
    private Runnable onGraph;  // null = no Graph to press
    private Runnable onCalculate = () -> { };
    private String latex;
    private String problem;
    private TeXIcon icon;

    public AnswerPopup(GlassBackdrop backdrop) {
        super(null);
        setOpaque(false);
        // buttons paint in our coords, backdrop wants canvas coords
        buttons = new GlassBar(new GlassBackdrop() {
            @Override
            public void paintBackdrop(Graphics2D g) {
                Graphics2D c = (Graphics2D) g.create();
                c.translate(-getX(), -getY());
                backdrop.paintBackdrop(c);
                c.dispose();
            }

            @Override
            public long backdropVersion() {
                return backdrop.backdropVersion() * 31 + getLocation().hashCode();
            }
        }, Fonts.ui(Font.PLAIN, 14f));
        graph = buttons.action("Graph", GlassIcons::scribble, () -> onGraph.run(), () -> onGraph != null);
        calculate = buttons.action("Calculate", GlassIcons::equalCircle, () -> onCalculate.run(), () -> true);
        buttons.finish();
        add(buttons);  // added first = drawn last: the hover droplet goes over the card
        add(card);
        setVisible(false);
    }

    // onGraph null = Graph stays greyed out
    public void showFormula(String latex, Runnable onGraph, Runnable onCalculate) {
        this.latex = latex;
        this.problem = null;
        this.onGraph = onGraph;
        this.onCalculate = onCalculate;
        icon = typeset(latex);
        buttons.setVisible(true);
        relayout();
    }

    public void showProblem(String reason) {
        this.latex = null;
        this.problem = reason;
        this.onGraph = null;
        this.onCalculate = () -> { };
        icon = null;
        buttons.setVisible(false);
        relayout();
    }

    // above-right of the formula, below if no room, always inside the canvas
    Rectangle spotBeside(Rectangle formula, int areaWidth, int areaHeight) {
        Dimension d = getPreferredSize();
        int margin = buttonMargin();  // clear room for the droplet: only the capsule has to stay inside
        int x = formula.x + formula.width - POPUP_LEFT - margin;
        int y = formula.y - POPUP_GAP - d.height;
        if (y + margin < EDGE) {
            y = formula.y + formula.height + POPUP_GAP;
        }
        x = Math.max(EDGE - margin, Math.min(x, areaWidth - d.width - EDGE + margin));
        y = Math.max(EDGE - margin, Math.min(y, areaHeight - d.height - EDGE));
        return new Rectangle(x, y, d.width, d.height);
    }

    String getLatex() {
        return latex;
    }

    String getProblem() {
        return problem;
    }

    boolean hasCalculate() {
        return buttons.isVisible();
    }

    boolean canGraph() {
        return buttons.isVisible() && buttons.isEnabled(graph);
    }

    boolean isTypeset() {
        return icon != null;
    }

    void clickCalculate() {
        buttons.activate(calculate);
    }

    void clickGraph() {
        buttons.activate(graph);
    }

    private static TeXIcon typeset(String latex) {
        try {
            return new TeXFormula(latex).createTeXIcon(TeXConstants.STYLE_DISPLAY, TEX_SIZE);
        } catch (RuntimeException | Error bad) {  // bad latex -> plain text
            return null;
        }
    }

    private int buttonMargin() {
        return buttons.isVisible() ? GlassBar.MARGIN : 0;
    }

    private void relayout() {
        buttons.clearHover();
        Dimension d = buttons.getPreferredSize();
        Dimension c = card.getPreferredSize();
        if (buttons.isVisible()) {
            buttons.setBounds(0, 0, d.width, d.height);
            card.setBounds(GlassBar.MARGIN, GlassBar.MARGIN + GlassBar.CAPSULE_HEIGHT + GAP, c.width, c.height);
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
        if (!buttons.isVisible()) {
            return c;
        }
        return new Dimension(Math.max(buttons.getPreferredSize().width, GlassBar.MARGIN + c.width),
                GlassBar.MARGIN + GlassBar.CAPSULE_HEIGHT + GAP + c.height);
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
