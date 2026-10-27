package view;

import view.stack.Fonts;

import java.awt.Font;
import java.awt.FontFormatException;
import java.awt.Graphics2D;
import java.awt.font.FontRenderContext;
import java.awt.font.TextAttribute;
import java.awt.font.TextLayout;
import java.awt.geom.Rectangle2D;
import java.io.IOException;
import java.io.InputStream;
import java.text.AttributedString;

// Kalam handwriting for answers on the canvas
public final class AnswerFont {
    private static final FontRenderContext FRC = new FontRenderContext(null, true, true);
    private static Font base;
    private static double digitRatio;  // ink height of "0" per point of font size

    private AnswerFont() {
    }

    // Kalam at size 1, or ui font if it won't load
    public static synchronized Font base() {
        if (base == null) {
            try (InputStream in = AnswerFont.class.getResourceAsStream("/fonts/Kalam-Regular.ttf")) {
                base = in == null ? Fonts.ui(Font.PLAIN, 1f) : Font.createFont(Font.TRUETYPE_FONT, in).deriveFont(1f);
            } catch (IOException | FontFormatException e) {
                base = Fonts.ui(Font.PLAIN, 1f);
            }
        }
        return base;
    }

    // font size whose "0" is charHeight tall
    public static synchronized float sizeFor(double charHeight) {
        if (digitRatio == 0) {
            digitRatio = visualBounds("0", 100f).getHeight() / 100;
        }
        return (float) (charHeight / digitRatio);
    }

    // ink box, origin at baseline start
    public static Rectangle2D visualBounds(String text, float size) {
        if (text.isEmpty()) {
            return new Rectangle2D.Double();
        }
        return layout(text, size).getBounds();
    }

    // ink top-left lands on (left, top); caller sets color
    public static void draw(Graphics2D g, String text, float size, double left, double top) {
        if (text.isEmpty()) {
            return;
        }
        TextLayout layout = layout(text, size);
        Rectangle2D ink = layout.getBounds();
        layout.draw(g, (float) (left - ink.getX()), (float) (top - ink.getY()));
    }

    private static TextLayout layout(String text, float size) {
        Font kalam = base().deriveFont(size);
        Font fallback = Fonts.ui(Font.PLAIN, size);
        AttributedString styled = new AttributedString(text);
        styled.addAttribute(TextAttribute.FONT, kalam);
        for (int i = 0; i < text.length(); i++) {
            if (!kalam.canDisplay(text.charAt(i))) {
                styled.addAttribute(TextAttribute.FONT, fallback, i, i + 1);  // e.g. theta
            }
        }
        return new TextLayout(styled.getIterator(), FRC);
    }
}
