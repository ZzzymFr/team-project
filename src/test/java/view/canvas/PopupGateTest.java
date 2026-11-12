package view.canvas;

import entity.InkPoint;
import entity.Stroke;
import org.junit.jupiter.api.Test;
import view.answer.Formula;
import view.answer.FormulaBook;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

// popup only once there's an "=", lasso picks always
class PopupGateTest {
    private final PopupGate gate = new PopupGate();

    private static Stroke stroke(int... xy) {
        Stroke s = new Stroke();
        for (int i = 0; i < xy.length; i += 2) {
            s.addPoint(new InkPoint(xy[i], xy[i + 1], i));
        }
        return s;
    }

    private static Formula one() {
        return new FormulaBook().add(List.of(stroke(100, 100, 100, 150)), 1);
    }

    // "1 =" in ink
    private static Formula oneEquals() {
        return new FormulaBook().add(List.of(stroke(100, 100, 100, 150),
                stroke(115, 120, 145, 120), stroke(115, 130, 145, 130)), 1);
    }

    @Test
    void readWithEqualsPasses() {
        assertTrue(gate.allows(one(), "1+1="));
        assertTrue(gate.allows(one(), "y=3x"));
    }

    @Test
    void readWithoutEqualsWaits() {
        assertFalse(gate.allows(oneEquals(), "1+1"));
        assertFalse(gate.allows(one(), "\\sum_{i=1}^{3}i"), "= in a sub/superscript doesn't count");
    }

    @Test
    void unreadFallsBackToTheInk() {
        assertTrue(gate.allows(oneEquals(), null));
        assertFalse(gate.allows(one(), null));
    }

    @Test
    void pickedAlwaysPasses() {
        Formula f = one();
        gate.picked(f.getId());
        assertTrue(gate.allows(f, "1+1"));
        assertTrue(gate.allows(f, null));
    }

    @Test
    void goneFormulaNeverPasses() {
        assertFalse(gate.allows(null, "1+1="));
    }
}
