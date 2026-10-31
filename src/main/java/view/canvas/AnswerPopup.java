package view.canvas;

import org.scilab.forge.jlatexmath.TeXConstants;
import org.scilab.forge.jlatexmath.TeXFormula;
import org.scilab.forge.jlatexmath.TeXIcon;
import view.glass.GlassButton;
import view.glass.GlassDock;
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
import java.awt.RenderingHints;
import java.awt.event.MouseAdapter;
import java.awt.geom.RoundRectangle2D;
import java.util.function.Consumer;

// floating "Calculate" capsule + white card with the recognised formula
public class AnswerPopup extends JPanel {
    private static final float TEX_SIZE = 22f;
    private static final int CARD_SHIFT = 24;   // card sits a bit right of the button
    private static final int GAP = 4;
    private static final int PAD_X = 14;
    private static final int PAD_Y = 10;
    private static final int ARC = 14;
    private static final int SHADOW = 6;
    private static final int MAX_TEXT = 320;
    private static final Color BORDER = new Color(203, 213, 225);
    private static final Color ERROR_TEXT = new Color(185, 28, 28);

    private final GlassDock dock = new GlassDock();
    private final GlassButton calculate = new GlassButton("Calculate", 34);
    private final Card card = new Card();
    private Runnable onCalculate = () -> { };
    private String latex;
    private String problem;
    private TeXIcon icon;

    public AnswerPopup(Consumer<Graphics2D> backdrop) {
        super(null);
        setOpaque(false);
        calculate.setFont(Fonts.ui(Font.PLAIN, 14f));
        calculate.addActionListener(e -> onCalculate.run());
        dock.add(calculate);
        // dock paints in our coords, backdrop wants canvas coords
        dock.setBackdrop(g -> {
            Graphics2D c = (Graphics2D) g.create();
            c.translate(-getX(), -getY());
            backdrop.accept(c);
            c.dispose();
        });
        add(dock);
        add(card);
        setVisible(false);
    }

    public void showFormula(String latex, Runnable onCalculate) {
        this.latex = latex;
        this.problem = null;
        this.onCalculate = onCalculate;
        icon = typeset(latex);
        dock.setVisible(true);
        relayout();
    }

    public void showProblem(String reason) {
        this.latex = null;
        this.problem = reason;
        this.onCalculate = () -> { };
        icon = null;
        dock.setVisible(false);
        relayout();
    }

    String getLatex() {
        return latex;
    }

    String getProblem() {
        return problem;
    }

    boolean hasCalculate() {
        return dock.isVisible();
    }

    boolean isTypeset() {
        return icon != null;
    }

    void clickCalculate() {
        calculate.doClick(0);
    }

    private static TeXIcon typeset(String latex) {
        try {
            return new TeXFormula(latex).createTeXIcon(TeXConstants.STYLE_DISPLAY, TEX_SIZE);
        } catch (RuntimeException | Error bad) {  // bad latex -> plain text
            return null;
        }
    }

    private void relayout() {
        Dimension d = dock.getPreferredSize();
        Dimension c = card.getPreferredSize();
        if (dock.isVisible()) {
            dock.setBounds(0, 0, d.width, d.height);
            card.setBounds(CARD_SHIFT, d.height + GAP, c.width, c.height);
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
        if (!dock.isVisible()) {
            return c;
        }
        Dimension d = dock.getPreferredSize();
        return new Dimension(Math.max(d.width, CARD_SHIFT + c.width), d.height + GAP + c.height);
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
