package view.answer;

import entity.InkPoint;
import entity.Stroke;
import org.junit.jupiter.api.Test;

import java.awt.Color;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class AnswerColorTest {

    private static Stroke stroke(int points) {
        Stroke s = new Stroke();
        for (int i = 0; i < points; i++) {
            s.addPoint(new InkPoint(i, 0, i));
        }
        return s;
    }

    @Test
    void oneColourFormula() {
        Stroke a = stroke(5);
        Stroke b = stroke(5);
        assertEquals(Color.RED, AnswerColor.of(List.of(a, b), s -> Color.RED, Color.BLACK));
    }

    @Test
    void mostInkWins() {
        Stroke longBlue = stroke(40);
        Stroke red1 = stroke(5);
        Stroke red2 = stroke(5);
        Map<Stroke, Color> colors = Map.of(longBlue, Color.BLUE, red1, Color.RED, red2, Color.RED);
        assertEquals(Color.BLUE, AnswerColor.of(List.of(red1, longBlue, red2), colors::get, Color.BLACK));
    }

    @Test
    void noInkFallsBack() {
        assertEquals(Color.BLACK, AnswerColor.of(List.of(), s -> Color.RED, Color.BLACK));
    }
}
