package view.board;

import entity.EquationDisplay;
import interface_adapter.history.HistoryItem;
import view.answer.AnswerFont;

import java.awt.Graphics2D;
import java.awt.geom.Rectangle2D;

/**
 * Rendering policy for History entries. The implication is part of the very
 * same JLaTeXMath expression used by the PDF Library, not a hand-drawn glyph
 * or a font-dependent Unicode arrow. This keeps the two panels consistent.
 */
final class HistoryAnswerArtwork {
    private HistoryAnswerArtwork() { }

    static boolean implication(HistoryItem item) {
        return EquationDisplay.isTransformation(item.getLatex(), item.getAnswer());
    }

    /** Formula and (when applicable) the implication, typeset together. */
    static String formulaLatex(HistoryItem item) {
        String formula = EquationDisplay.formula(item.getLatex(), item.getAnswer());
        return implication(item) ? formula + EquationDisplay.latexSeparator(item.getLatex(), item.getAnswer()) : formula;
    }

    static String answerText(HistoryItem item) {
        String answer = EquationDisplay.answer(item.getAnswer());
        if (answer.isEmpty()) return "";
        return implication(item) ? answer : "= " + answer;
    }

    static double width(HistoryItem item, float fontSize) {
        String text = answerText(item);
        return text.isEmpty() ? 0 : AnswerFont.visualBounds(text, fontSize).getWidth();
    }

    static double height(HistoryItem item, float fontSize) {
        String text = answerText(item);
        return text.isEmpty() ? 0 : AnswerFont.visualBounds(text, fontSize).getHeight();
    }

    /** Draw only the answer: the Library-style LaTeX arrow belongs to the formula icon. */
    static void paint(Graphics2D g, HistoryItem item, float fontSize, double left, double centerY) {
        String text = answerText(item);
        if (text.isEmpty()) return;
        Rectangle2D bounds = AnswerFont.visualBounds(text, fontSize);
        AnswerFont.draw(g, text, fontSize, left, centerY - bounds.getHeight() / 2);
    }
}
