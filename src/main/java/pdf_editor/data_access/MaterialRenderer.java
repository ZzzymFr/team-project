package pdf_editor.data_access;

import pdf_editor.domain.PdfMaterial;
import entity.EquationDisplay;

import org.scilab.forge.jlatexmath.TeXConstants;
import org.scilab.forge.jlatexmath.TeXFormula;
import org.scilab.forge.jlatexmath.TeXIcon;

import javax.swing.JLabel;
import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.util.Collections;
import java.util.Map;
import java.util.WeakHashMap;

public final class MaterialRenderer {
    private MaterialRenderer() { }
    // Graph PNGs are immutable; cache decoding across repaints and PDF preview generation.
    private static final Map<PdfMaterial, BufferedImage> GRAPH_CACHE =
            Collections.synchronizedMap(new WeakHashMap<>());
    public static BufferedImage render(PdfMaterial material, int width, int height) {
        width = Math.max(8, width); height = Math.max(8, height);
        BufferedImage out = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = out.createGraphics();
        try {
            if (!material.isGraph()) { g.setColor(Color.WHITE); g.fillRect(0, 0, width, height); }
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
            g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
            if (material.isGraph()) {
                double scale = Math.min((double) width / material.imageWidth(), (double) height / material.imageHeight());
                int w = Math.max(1, (int) Math.round(material.imageWidth() * scale));
                int h = Math.max(1, (int) Math.round(material.imageHeight() * scale));
                g.drawImage(graphImage(material), (width - w) / 2, (height - h) / 2, w, h, null);
            } else {
                String formula = material.formula() == null ? "" : material.formula().trim();
                String result = material.result() == null ? "" : material.result().trim();
                if (result.isEmpty()) {
                    drawFormula(g, formula, width, height, 0);
                } else if (formula.isEmpty()) {
                    drawFormula(g, result, width, height, 0);
                } else {
                    // Preserve an already complete equation; use an implication for its solution.
                    formula = EquationDisplay.formula(formula, result);
                    result = EquationDisplay.answer(result);
                    String leftText = formula + EquationDisplay.latexSeparator(formula, result);
                    // Typeset at the actual raster resolution instead of painting a 20px
                    // icon and enlarging its already-rasterized pixels.
                    TeXIcon[] icons = fittedPair(leftText, result, width - 20, height - 12);
                    if (icons != null) {
                        int gap = Math.max(6, Math.min(24, width / 40));
                        int total = icons[0].getIconWidth() + icons[1].getIconWidth() + gap;
                        int startX = Math.max(5, (width - total) / 2);
                        paintIcon(g, icons[0], startX, (height - icons[0].getIconHeight()) / 2);
                        paintIcon(g, icons[1], startX + icons[0].getIconWidth() + gap,
                                (height - icons[1].getIconHeight()) / 2);
                    } else {
                        drawFormula(g, leftText + result, width, height, 0);
                    }
                }
            }
        } finally { g.dispose(); }
        return out;
    }
    private static BufferedImage graphImage(PdfMaterial material) {
        synchronized (GRAPH_CACHE) {
            return GRAPH_CACHE.computeIfAbsent(material, source -> {
                try {
                    BufferedImage image = javax.imageio.ImageIO.read(
                            new java.io.ByteArrayInputStream(source.imagePng()));
                    if (image == null) throw new java.io.IOException("Unsupported image");
                    return image;
                } catch (java.io.IOException ex) {
                    throw new IllegalStateException("Could not decode graph image", ex);
                }
            });
        }
    }
    private static TeXIcon latexIcon(String text, float size) {
        try { return new TeXFormula(text).createTeXIcon(TeXConstants.STYLE_DISPLAY, size); }
        catch (RuntimeException e) { return null; }
    }
    private static TeXIcon[] fittedPair(String left, String right, int width, int height) {
        TeXIcon a = latexIcon(left, 20f), b = latexIcon(right, 20f);
        if (a == null || b == null) return null;
        int gap = Math.max(6, Math.min(24, (width + 20) / 40));
        double fit = Math.min((width - gap) / (double) Math.max(1, a.getIconWidth() + b.getIconWidth()),
                height / (double) Math.max(1, Math.max(a.getIconHeight(), b.getIconHeight())));
        float size = (float) Math.max(2, Math.min(320, 20 * fit));
        return new TeXIcon[] {latexIcon(left, size), latexIcon(right, size)};
    }
    private static void paintIcon(Graphics2D g, TeXIcon icon, int x, int y) {
        icon.paintIcon(new JLabel(), g, x, y);
    }
    private static void drawFormula(Graphics2D g, String text, int width, int height, int top) {
        if (text == null || text.isEmpty()) return;
        try {
            TeXIcon icon = latexIcon(text, 20f);
            if (icon == null) throw new IllegalArgumentException("Invalid LaTeX");
            double fit = Math.min((width - 12.0) / Math.max(1, icon.getIconWidth()),
                    (height - 8.0) / Math.max(1, icon.getIconHeight()));
            icon = latexIcon(text, (float) Math.max(2, Math.min(320, 20 * fit)));
            if (icon != null) paintIcon(g, icon, Math.max(6, (width - icon.getIconWidth()) / 2),
                    top + Math.max(4, (height - icon.getIconHeight()) / 2));
        } catch (RuntimeException invalidLatex) {
            g.setColor(Color.DARK_GRAY);
            String plain = text.replace('\n', ' ');
            float size = Math.max(8f, Math.min(height * .48f, (width - 12f) / Math.max(1, plain.length()) * 1.7f));
            g.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 12).deriveFont(size));
            g.drawString(plain, 6, top + Math.min(height - 4, (int) (size + 4)));
        }
    }
}
