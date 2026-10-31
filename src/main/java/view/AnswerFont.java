package view;

import entity.symbolic.TextRun;
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
import java.util.List;

// Kalam handwriting for answers on the canvas
public final class AnswerFont {
    private static final FontRenderContext FRC = new FontRenderContext(null, true, true);
    private static final float RAISED_SCALE = 0.6f;  // exponent size vs body
    private static final double RAISE = 0.45;        // exponent baseline lift, x the digit height
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

    // ink box of runs, raised ones small and lifted; origin at baseline start
    public static Rectangle2D visualBounds(List<TextRun> runs, float size) {
        Rectangle2D box = null;
        double pen = 0;
        for (TextRun run : runs) {
            if (run.getText().isEmpty()) {
                continue;
            }
            TextLayout layout = layout(run.getText(), sizeOf(run, size));
            Rectangle2D ink = layout.getBounds();
            ink.setRect(ink.getX() + pen, ink.getY() - lift(run, size), ink.getWidth(), ink.getHeight());
            if (ink.getWidth() > 0) {
                box = box == null ? ink : box.createUnion(ink);
            }
            pen += layout.getAdvance();
        }
        return box == null ? new Rectangle2D.Double() : box;
    }

    // same as draw(String) but w/ exponents raised
    public static void draw(Graphics2D g, List<TextRun> runs, float size, double left, double top) {
        Rectangle2D ink = visualBounds(runs, size);
        double pen = left - ink.getX();
        for (TextRun run : runs) {
            if (run.getText().isEmpty()) {
                continue;
            }
            TextLayout layout = layout(run.getText(), sizeOf(run, size));
            layout.draw(g, (float) pen, (float) (top - ink.getY() - lift(run, size)));
            pen += layout.getAdvance();
        }
    }

    private static float sizeOf(TextRun run, float size) {
        return run.isRaised() ? size * RAISED_SCALE : size;
    }

    private static double lift(TextRun run, float size) {
        return run.isRaised() ? digitHeight(size) * RAISE : 0;
    }

    private static synchronized double digitHeight(float size) {
        if (digitRatio == 0) {
            digitRatio = visualBounds("0", 100f).getHeight() / 100;
        }
        return digitRatio * size;
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
