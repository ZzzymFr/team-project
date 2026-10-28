package entity;

import entity.node.Node;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ConditionSolverTest {
    private static final double DELTA = 1e-9;

    private Node parse(String text) {
        return new MathParser(MathLexer.tokenize(text), new HashMap<>()).parse();
    }

    private Context context() {
        return new Context(new HashMap<>());
    }

    @Test
    void solvesQuadraticInequality() {
        List<Interval> intervals = new ConditionSolver(parse("x^2\\leq4"), "x", context()).solve();
        assertEquals(1, intervals.size());
        Interval interval = intervals.get(0);
        assertEquals(-2, interval.getLow(), DELTA);
        assertEquals(2, interval.getHigh(), DELTA);
        assertTrue(interval.isLowIncluded());
        assertTrue(interval.isHighIncluded());
    }

    @Test
    void somewhereAndEverywhere() {
        ConditionSolver solver = new ConditionSolver(parse("x>3"), "x", context());
        assertTrue(solver.isTrueSomewhere());
        assertFalse(solver.isTrueEverywhere());
    }

    @Test
    void skipsRelationsWithForeignVariables() {
        // y not bound, only x>1 gives a root
        List<Double> roots = new ConditionSolver(parse("x>1\\land x<y"), "x", context()).boundaryRoots();
        assertEquals(1, roots.size());
        assertEquals(1, roots.get(0), DELTA);
    }

    @Test
    void solvesRelationsWithBoundVariables() {
        List<Double> roots = new ConditionSolver(parse("x>1\\land x<y"), "x",
                context().withVariable("y", 5)).boundaryRoots();
        assertEquals(2, roots.size());
        assertEquals(1, roots.get(0), DELTA);
        assertEquals(5, roots.get(1), DELTA);
    }

    @Test
    void usesRelationsWithBoundVariables() {
        List<Interval> intervals = new ConditionSolver(parse("x>1\\land x<y"), "x",
                context().withVariable("y", 5)).solve();
        assertEquals(1, intervals.size());
        Interval interval = intervals.get(0);
        assertEquals(1, interval.getLow(), DELTA);
        assertEquals(5, interval.getHigh(), DELTA);
        assertFalse(interval.isLowIncluded());
        assertFalse(interval.isHighIncluded());
    }
}
