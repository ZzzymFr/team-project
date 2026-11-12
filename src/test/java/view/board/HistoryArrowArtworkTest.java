package view.board;

import history.domain.HistoryEntry;
import interface_adapter.history.HistoryItem;
import org.junit.jupiter.api.Test;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.time.Instant;

import static org.junit.jupiter.api.Assertions.*;

/** Regression: Kalam does not contain U+21D2; History must paint it as vector lines. */
class HistoryArrowArtworkTest {
    private static HistoryItem item(String formula, String result) {
        return new HistoryItem(new HistoryEntry("h1", HistoryEntry.Kind.CALCULATION,
                "Calculation", formula, result, null, 0, 0, Instant.EPOCH));
    }

    @Test void equationSolutionUsesSameLatexImplicationAsLibrary() {
        HistoryItem solution = item("3x=6", "x=2");
        assertTrue(HistoryAnswerArtwork.implication(solution));
        assertEquals("x=2", HistoryAnswerArtwork.answerText(solution));
        assertEquals("3x=6\\Rightarrow", HistoryAnswerArtwork.formulaLatex(solution));
        assertEquals(entity.EquationDisplay.formula("3x=6", "x=2")
                + entity.EquationDisplay.latexSeparator("3x=6", "x=2"),
                HistoryAnswerArtwork.formulaLatex(solution));
    }

    @Test void ordinaryCalculationStillUsesSingleEquals() {
        HistoryItem arithmetic = item("1+1=", "=2");
        assertFalse(HistoryAnswerArtwork.implication(arithmetic));
        assertEquals("= 2", HistoryAnswerArtwork.answerText(arithmetic));
        assertEquals("1+1", HistoryAnswerArtwork.formulaLatex(arithmetic));
        assertEquals("", HistoryAnswerArtwork.answerText(item("y=x", "")));
        assertEquals(0.0, HistoryAnswerArtwork.width(item("y=x", ""), 19f), 0.0001);
    }

    @Test void libraryStyleImplicationTypesetsAndHistoryProducesArtwork() {
        var solution = item("3x=6", "x=2");
        var tex = new org.scilab.forge.jlatexmath.TeXFormula(
                HistoryAnswerArtwork.formulaLatex(solution));
        var icon = tex.createTeXIcon(org.scilab.forge.jlatexmath.TeXConstants.STYLE_DISPLAY, 18f);
        assertTrue(icon.getIconWidth() > 20);
        BufferedImage image = HistoryPanel.picture(solution);
        assertTrue(image.getWidth() > icon.getIconWidth() * 2,
                "The History preview must accommodate the typeset formula, arrow and answer");
        int painted = 0;
        for (int y = 0; y < image.getHeight(); y++) {
            for (int x = 0; x < image.getWidth(); x++) {
                if ((image.getRGB(x, y) >>> 24) != 0) painted++;
            }
        }
        assertTrue(painted > 30);
    }

    @Test void historyPreviewImageDoesNotTruncateSolution() {
        BufferedImage image = HistoryPanel.picture(item("3x=6", "x=2"));
        assertTrue(image.getWidth() > 70);
        assertTrue(image.getHeight() > 0);
    }
}
