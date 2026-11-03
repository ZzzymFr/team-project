package pdf_editor;

import org.scilab.forge.jlatexmath.TeXConstants;
import org.scilab.forge.jlatexmath.TeXFormula;
import org.scilab.forge.jlatexmath.TeXIcon;

import javax.swing.JLabel;
import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;

public final class MaterialRenderer {
    private MaterialRenderer() { }
    public static BufferedImage render(MathLibrary.Material material, int width, int height) {
        width = Math.max(8, width); height = Math.max(8, height);
        BufferedImage out = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = out.createGraphics();
        try {
            if (!material.isGraph()) { g.setColor(Color.WHITE); g.fillRect(0, 0, width, height); }
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            if (material.isGraph()) {
                double scale = Math.min((double) width / material.graph.getWidth(), (double) height / material.graph.getHeight());
                int w = (int) (material.graph.getWidth() * scale);
                int h = (int) (material.graph.getHeight() * scale);
                g.drawImage(material.graph, (width - w) / 2, (height - h) / 2, w, h, null);
            } else {
                String formula = material.formula == null ? "" : material.formula.trim();
                String result = material.result == null ? "" : material.result.trim();
                if (result.isEmpty()) {
                    drawFormula(g, formula, width, height, 0);
                } else if (formula.isEmpty()) {
                    drawFormula(g, result, width, height, 0);
                } else {
                    // One equals sign: formula = result, in a horizontal layout.
                    formula = formula.replaceFirst("\\s*=+\\s*$", "");
                    result = result.replaceFirst("^\\s*=+\\s*", "");
                    String leftText = formula + "=";
                    TeXIcon leftIcon = latexIcon(leftText);
                    TeXIcon rightIcon = latexIcon(result);
                    if (leftIcon != null && rightIcon != null) {
                        int gap = 12;
                        int total = leftIcon.getIconWidth() + rightIcon.getIconWidth();
                        int tallest = Math.max(leftIcon.getIconHeight(), rightIcon.getIconHeight());
                        double scale = Math.max(.1, Math.min(1.8,
                            Math.min((width - gap - 20.0) / Math.max(1, total),
                                     (height - 12.0) / Math.max(1, tallest))));
                        int scaledLeft = (int)Math.round(leftIcon.getIconWidth() * scale);
                        int scaledRight = (int)Math.round(rightIcon.getIconWidth() * scale);
                        int startX = Math.max(5, (width - scaledLeft - gap - scaledRight) / 2);
                        paintIcon(g, leftIcon, startX,
                            (int)((height - leftIcon.getIconHeight() * scale) / 2), scale);
                        paintIcon(g, rightIcon, startX + scaledLeft + gap,
                            (int)((height - rightIcon.getIconHeight() * scale) / 2), scale);
                    } else {
                        drawFormula(g, leftText + result, width, height, 0);
                    }
                }
            }
        } finally { g.dispose(); }
        return out;
    }
    private static TeXIcon latexIcon(String text) {
        try { return new TeXFormula(text).createTeXIcon(TeXConstants.STYLE_DISPLAY, 20); }
        catch (RuntimeException e) { return null; }
    }
    private static void paintIcon(Graphics2D g, TeXIcon icon, int x, int y, double scale) {
        Graphics2D copy = (Graphics2D) g.create();
        copy.translate(x, y); copy.scale(scale, scale);
        icon.paintIcon(new JLabel(), copy, 0, 0);
        copy.dispose();
    }
    private static void drawFormula(Graphics2D g, String text, int width, int height, int top) {
        if (text == null || text.isEmpty()) return;
        try {
            TeXFormula formula = new TeXFormula(text);
            TeXIcon icon = formula.createTeXIcon(TeXConstants.STYLE_DISPLAY, 20);
            double scale = Math.min((width - 12.0) / Math.max(1, icon.getIconWidth()),
                    (height - 8.0) / Math.max(1, icon.getIconHeight()));
            Graphics2D copy = (Graphics2D) g.create();
            copy.translate(6, top + 4);
            copy.scale(Math.max(.1, scale), Math.max(.1, scale));
            icon.paintIcon(new JLabel(), copy, 0, 0);
            copy.dispose();
        } catch (RuntimeException invalidLatex) {
            g.setColor(Color.DARK_GRAY);
            String plain = text.replace('\n', ' ');
            float size = Math.max(8f, Math.min(height * .48f, (width - 12f) / Math.max(1, plain.length()) * 1.7f));
            g.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 12).deriveFont(size));
            g.drawString(plain, 6, top + Math.min(height - 4, (int) (size + 4)));
        }
    }
}
