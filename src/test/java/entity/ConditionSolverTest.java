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
    void findsBoundaryPastTheRange() {
        List<Interval> intervals = new ConditionSolver(parse("x^2-1000001x+1000000<0"), "x", context()).solve();
        assertEquals(1, intervals.size());
        assertEquals(1, intervals.get(0).getLow(), DELTA);
        assertEquals(1e6, intervals.get(0).getHigh(), 1e-6);
        assertFalse(intervals.get(0).isLowIncluded());
        assertFalse(intervals.get(0).isHighIncluded());
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
