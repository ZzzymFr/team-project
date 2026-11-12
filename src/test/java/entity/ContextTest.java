package entity;

import entity.node.Node;
import entity.node.VariableNode;
import entity.value.NumberValue;
import entity.value.Value;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ContextTest {
    private static final List<String> NONE = Collections.emptyList();
    private final Node node = new VariableNode("t");
    private final Node other = new VariableNode("t");

    @Test
    void closedNodeIsWorkedOutOncePerCalculation() {
        Context context = new Context(new HashMap<>());
        int[] runs = {0};
        Value first = context.once(node, NONE, () -> {
            runs[0]++;
            return new NumberValue(3);
        });
        Value again = context.withVariable("x", 5).once(node, NONE, () -> {
            runs[0]++;
            return new NumberValue(4);
        });
        assertSame(first, again);
        assertEquals(1, runs[0]);
        context.once(other, NONE, () -> {
            runs[0]++;
            return new NumberValue(4);
        });
        assertEquals(2, runs[0]);
        // a fresh calculation starts over
        new Context(new HashMap<>()).once(node, NONE, () -> {
            runs[0]++;
            return new NumberValue(4);
        });
        assertEquals(3, runs[0]);
    }

    @Test
    void openNodeIsWorkedOutOncePerValueOfItsVariables() {
        Context context = new Context(new HashMap<>());
        List<String> x = Collections.singletonList("x");
        int[] runs = {0};
        for (double value : new double[] {1, 2, 1, 2, 1}) {
            Context at = context.withVariable("x", value).withVariable("y", value * 10);
            Value result = at.once(node, x, () -> {
                runs[0]++;
                return new NumberValue(at.getVariable("x"));
            });
            assertEquals(value, ((NumberValue) result).getValue());
        }
        assertEquals(2, runs[0]);
        // x not bound -> just work it out
        context.once(node, x, () -> {
            runs[0]++;
            return new NumberValue(0);
        });
        assertEquals(3, runs[0]);
    }

    @Test
    void hitCostsTheSameStepsAsARerun() {
        Context context = new Context(new HashMap<>());
        int[] runs = {0};
        // each run spends 2/3 of the budget, so the second one has to run out like a rerun would
        Runnable twoThirds = () -> context.once(node, NONE, () -> {
            runs[0]++;
            for (int i = 0; i < Context.MAX_STEPS / 3 * 2; i++) {
                context.spendStep();
            }
            return new NumberValue(1);
        });
        twoThirds.run();
        assertThrows(BudgetExceededException.class, twoThirds::run);
        assertEquals(1, runs[0]);
    }

    @Test
    void plusMinusChoicesAreSeparate() {
        Context context = new Context(new HashMap<>());
        Value plus = context.once(node, NONE, () -> new NumberValue(1));
        Value minus = context.withSigns(1).once(node, NONE, () -> new NumberValue(-1));
        assertEquals(1, ((NumberValue) plus).getValue());
        assertEquals(-1, ((NumberValue) minus).getValue());
    }

    @Test
    void hitLeavesTheSameFlagsAsTheFirstRun() {
        Context context = new Context(new HashMap<>());
        context.once(node, NONE, () -> {
            context.markAbsorbed();
            context.markTolerated();
            context.noteApproximation(1e-6);
            return new NumberValue(1);
        });
        context.setAbsorbed(false);
        context.setTolerated(false);
        context.setApproximation(0);
        context.once(node, NONE, () -> new NumberValue(2));
        assertTrue(context.isAbsorbed());
        assertTrue(context.isTolerated());
        assertEquals(1e-6, context.getApproximation());
        // flags already set before stay set
        Context quiet = new Context(new HashMap<>());
        quiet.markAbsorbed();
        quiet.once(node, NONE, () -> new NumberValue(1));
        assertTrue(quiet.isAbsorbed());
        assertFalse(quiet.isTolerated());
    }

    @Test
    void failuresAreRememberedButRunningOutOfStepsIsNot() {
        Context context = new Context(new HashMap<>());
        int[] runs = {0};
        for (int i = 0; i < 3; i++) {
            assertThrows(UncalculableException.class, () -> context.once(node, NONE, () -> {
                runs[0]++;
                throw new UncalculableException(UncalculableReason.LIMIT);
            }));
        }
        assertEquals(1, runs[0]);
        for (int i = 0; i < 3; i++) {
            assertThrows(BudgetExceededException.class, () -> context.once(other, NONE, () -> {
                runs[0]++;
                throw new BudgetExceededException();
            }));
        }
        assertEquals(4, runs[0]);
    }
}
