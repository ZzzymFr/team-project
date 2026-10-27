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
        BufferedImage out = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = out.createGraphics();
        try {
            g.setColor(Color.WHITE); g.fillRect(0, 0, width, height);
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            if (material.isGraph()) {
                double scale = Math.min((double) width / material.graph.getWidth(), (double) height / material.graph.getHeight());
                int w = (int) (material.graph.getWidth() * scale);
                int h = (int) (material.graph.getHeight() * scale);
                g.drawImage(material.graph, (width - w) / 2, (height - h) / 2, w, h, null);
            } else {
                int half = material.result == null || material.result.isEmpty() ? height : height / 2;
                drawFormula(g, material.formula, width, half, 0);
                if (half != height) {
                    g.setColor(new Color(215, 215, 215)); g.drawLine(5, half, width - 5, half);
                    drawFormula(g, material.result, width, height - half, half);
                }
            }
        } finally { g.dispose(); }
        return out;
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
            g.setColor(Color.DARK_GRAY); g.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));
            String plain = text.replace('\n', ' ');
            int maxChars = Math.max(1, (width - 12) / 7);
            g.drawString(plain.substring(0, Math.min(plain.length(), maxChars)), 6, top + Math.min(height - 4, 18));
        }
    }
}
