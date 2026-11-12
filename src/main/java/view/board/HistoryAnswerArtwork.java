package view.board;

import entity.EquationDisplay;
import interface_adapter.history.HistoryItem;
import view.answer.AnswerFont;

import java.awt.BasicStroke;
import java.awt.Graphics2D;
import java.awt.Stroke;
import java.awt.geom.Line2D;
import java.awt.geom.Rectangle2D;

/**
 * Paints History results without relying on a font containing U+21D2.
 * The handwritten answer font is used for numbers, while equation implication
 * is a small Java2D vector, so Windows font fallback cannot show a tofu box.
 */
final class HistoryAnswerArtwork {
    private static final int ARROW_ADVANCE = 28;
    private static final double ARROW_HEIGHT = 16;

    private HistoryAnswerArtwork() { }

    static boolean implication(HistoryItem item) {
        return EquationDisplay.isTransformation(item.getLatex(), item.getAnswer());
    }

    static String answerText(HistoryItem item) {
        String answer = EquationDisplay.answer(item.getAnswer());
        if (answer.isEmpty()) return "";
        return implication(item) ? answer : "= " + answer;
    }

    static double width(HistoryItem item, float fontSize) {
        String text = answerText(item);
        if (text.isEmpty()) return 0;
        return AnswerFont.visualBounds(text, fontSize).getWidth()
                + (implication(item) ? ARROW_ADVANCE : 0);
    }

    static double height(HistoryItem item, float fontSize) {
        String text = answerText(item);
        if (text.isEmpty()) return 0;
        Rectangle2D box = AnswerFont.visualBounds(text, fontSize);
        return Math.max(box.getHeight(), implication(item) ? ARROW_HEIGHT : 0);
    }

    /** Draw centered on centerY, starting at the supplied left edge. */
    static void paint(Graphics2D g, HistoryItem item, float fontSize, double left, double centerY) {
        String text = answerText(item);
        if (text.isEmpty()) return;
        if (implication(item)) {
            paintImplication(g, left, centerY);
            left += ARROW_ADVANCE;
        }
        double h = AnswerFont.visualBounds(text, fontSize).getHeight();
        AnswerFont.draw(g, text, fontSize, left, centerY - h / 2);
    }

    private static void paintImplication(Graphics2D g, double x, double centerY) {
        Stroke oldStroke = g.getStroke();
        g.setStroke(new BasicStroke(1.65f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        // Two parallel shafts and an arrowhead: a font-independent double arrow.
        g.draw(new Line2D.Double(x + 2, centerY - 2.2, x + 21, centerY - 2.2));
        g.draw(new Line2D.Double(x + 2, centerY + 2.2, x + 21, centerY + 2.2));
        g.draw(new Line2D.Double(x + 14, centerY - 7.5, x + 22, centerY));
        g.draw(new Line2D.Double(x + 22, centerY, x + 14, centerY + 7.5));
        g.setStroke(oldStroke);
    }
}
