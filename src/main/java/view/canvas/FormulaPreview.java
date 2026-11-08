package view.canvas;

import org.scilab.forge.jlatexmath.TeXConstants;
import org.scilab.forge.jlatexmath.TeXFormula;
import org.scilab.forge.jlatexmath.TeXIcon;
import view.glass.Glass;
import view.stack.Fonts;

import javax.swing.JComponent;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.RoundRectangle2D;

/**
 * The circled formula on the Math tab, on a pane of glass as tall as the buttons beside it:
 * typeset when it reads as LaTeX, as text when it does not, or why it could not be read, in red.
 */
final class FormulaPreview extends JComponent {
    private static final float TEX_SIZE = 20f;
    private static final int WIDTH = 280;
    private static final int HEIGHT = 40 + 2 * Glass.SHADOW;  // as tall as the buttons and text boxes
    private static final int PAD = 14;
    private static final int PAD_Y = 7;
    private static final Color MUTED = new Color(99, 99, 102);
    private static final Color ERROR_TEXT = new Color(185, 28, 28);
    private static final String HINT = "Circle a formula with the lasso";

    private String latex;
    private String text = HINT;
    private boolean problem;
    private TeXIcon icon;

    void showReading() {
        set(null, "Reading the formula…", false);
    }

    void showFormula(String formula) {
        set(formula, formula, false);
    }

    void showProblem(String reason) {
        set(null, reason, true);
    }

    void clear() {
        set(null, HINT, false);
    }

    String getLatex() {
        return latex;
    }

    String getText() {
        return text;
    }

    boolean isProblem() {
        return problem;
    }

    boolean isTypeset() {
        return icon != null;
    }

    private void set(String formula, String shown, boolean isProblem) {
        latex = formula;
        text = shown;
        problem = isProblem;
        icon = formula == null ? null : typeset(formula);
        setToolTipText(shown);
        repaint();
    }

    private static TeXIcon typeset(String latex) {
        try {
            return new TeXFormula(latex).createTeXIcon(TeXConstants.STYLE_DISPLAY, TEX_SIZE);
        } catch (RuntimeException | Error bad) {  // bad latex -> plain text
            return null;
        }
    }

    @Override
    public Dimension getPreferredSize() {
        return new Dimension(WIDTH, HEIGHT);
    }

    @Override
    protected void paintComponent(Graphics g) {
        Graphics2D g2 = (Graphics2D) g.create();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        int s = Glass.SHADOW;
        RoundRectangle2D shape = Glass.rounded(s, s, getWidth() - 2 * s, getHeight() - 2 * s);
        Glass.paint(g2, shape, null, 0, 0, 0, 0);  // a pane of liquid glass, like the buttons
        g2.clip(shape);
        int w = getWidth() - 2 * PAD;
        int h = getHeight() - 2 * PAD_Y;
        if (icon != null) {
            // shrunk to fit, never enlarged
            double scale = Math.min(1, Math.min((double) w / icon.getIconWidth(), (double) h / icon.getIconHeight()));
            Graphics2D t = (Graphics2D) g2.create();
            t.translate(PAD, (getHeight() - icon.getIconHeight() * scale) / 2);
            t.scale(scale, scale);
            icon.setForeground(Color.BLACK);
            icon.paintIcon(this, t, 0, 0);
            t.dispose();
        } else {
            g2.setFont(Fonts.ui(Font.PLAIN, 14f));
            g2.setColor(problem ? ERROR_TEXT : latex == null ? MUTED : Color.DARK_GRAY);
            FontMetrics fm = g2.getFontMetrics();
            g2.drawString(text, PAD, (getHeight() - fm.getHeight()) / 2 + fm.getAscent());
        }
        g2.dispose();
    }
}
