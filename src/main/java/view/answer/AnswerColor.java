package view.answer;

import entity.Stroke;

import java.awt.Color;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Function;

// answer goes in the colour most of its formula was written in
public final class AnswerColor {
    private AnswerColor() {
    }

    // most ink (by points) wins, ties go to the first seen; fallback if no ink
    public static Color of(Collection<Stroke> formula, Function<Stroke, Color> colorOf, Color fallback) {
        Map<Color, Integer> amount = new LinkedHashMap<>();
        for (Stroke s : formula) {
            amount.merge(colorOf.apply(s), Math.max(1, s.getPoints().size()), Integer::sum);
        }
        Color best = fallback;
        int most = 0;
        for (Map.Entry<Color, Integer> e : amount.entrySet()) {
            if (e.getValue() > most) {
                best = e.getKey();
                most = e.getValue();
            }
        }
        return best;
    }
}
