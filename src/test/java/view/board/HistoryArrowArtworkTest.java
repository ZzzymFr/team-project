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

    @Test void equationSolutionUsesVectorImplicationButKeepsAnswerText() {
        HistoryItem solution = item("3x=6", "x=2");
        assertTrue(HistoryAnswerArtwork.implication(solution));
        assertEquals("x=2", HistoryAnswerArtwork.answerText(solution));
        assertTrue(HistoryAnswerArtwork.width(solution, 19f) >
                view.answer.AnswerFont.visualBounds("x=2", 19f).getWidth());
    }

    @Test void ordinaryCalculationStillUsesSingleEquals() {
        HistoryItem arithmetic = item("1+1=", "=2");
        assertFalse(HistoryAnswerArtwork.implication(arithmetic));
        assertEquals("= 2", HistoryAnswerArtwork.answerText(arithmetic));
        assertEquals("", HistoryAnswerArtwork.answerText(item("y=x", "")));
        assertEquals(0.0, HistoryAnswerArtwork.width(item("y=x", ""), 19f), 0.0001);
    }

    @Test void implicationIsActuallyPaintedWithoutAnyUnicodeFontGlyph() {
        BufferedImage image = new BufferedImage(160, 60, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = image.createGraphics();
        g.setColor(Color.BLACK);
        HistoryAnswerArtwork.paint(g, item("3x=6", "x=2"), 19f, 5, 30);
        g.dispose();
        int visible = 0;
        // Vector arrow shafts and head are fully inside x=5..28, separate from answer text.
        for (int x = 5; x < 28; x++) {
            for (int y = 20; y < 40; y++) {
                if (((image.getRGB(x, y) >>> 24) & 0xff) > 80) visible++;
            }
        }
        assertTrue(visible > 25, "Implication arrow should be rendered as crisp vector strokes");
    }

    @Test void historyPreviewImageDoesNotTruncateSolution() {
        BufferedImage image = HistoryPanel.picture(item("3x=6", "x=2"));
        assertTrue(image.getWidth() > 70);
        assertTrue(image.getHeight() > 0);
    }
}
