package view.board.doc;

import view.board.Icons;
import javax.swing.ImageIcon;
import javax.swing.JComponent;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;

/** Crisp vector-based monochrome glyphs shared by document and preview headers. */
public final class DocumentGlyphs {
    private DocumentGlyphs() { }
    /** Painted in device resolution, so Retina/Windows HiDPI scaling stays crisp. */
    public static JComponent component(int size, Icons.Glyph glyph) {
        if (size < 12 || size > 128) throw new IllegalArgumentException("size must be 12..128");
        java.util.Objects.requireNonNull(glyph);
        JComponent component = new JComponent() {
            @Override protected void paintComponent(Graphics graphics) {
                Graphics2D g = (Graphics2D) graphics.create();
                try {
                    g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                    g.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);
                    g.setColor(new Color(38, 44, 54));
                    g.translate(getWidth() / 2.0, getHeight() / 2.0);
                    g.scale(size / 23.0, size / 23.0);
                    glyph.paint(g);
                } finally { g.dispose(); }
            }
        };
        component.setPreferredSize(new Dimension(size, size));
        component.setMinimumSize(new Dimension(size, size));
        return component;
    }
    public static ImageIcon icon(int size) { return new ImageIcon(image(size)); }
    public static BufferedImage image(int size) {
        if (size < 12 || size > 128) throw new IllegalArgumentException("size must be 12..128");
        BufferedImage canvas = new BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = canvas.createGraphics();
        try {
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g.setColor(new Color(38, 44, 54));
            g.translate(size / 2.0, size / 2.0);
            g.scale(size / 23.0, size / 23.0);
            Icons.document(g);
        } finally { g.dispose(); }
        return canvas;
    }
}
