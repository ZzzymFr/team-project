package entity.calculus;

import entity.UncalculableException;
import org.junit.jupiter.api.Test;

import java.util.function.DoubleUnaryOperator;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LimitCalculatorTest {
    @Test
    void finiteLimits() {
        assertEquals(1, LimitCalculator.limit(x -> Math.sin(x) / x, 0, 0), 1e-6);
        assertEquals(Math.E, LimitCalculator.limit(n -> Math.pow(1 + 1 / n, n), Double.POSITIVE_INFINITY, 0), 1e-6);
        assertEquals(0, LimitCalculator.limit(x -> 1 / (8 * x + 3), Double.POSITIVE_INFINITY, 0), 1e-9);
        assertEquals(2, LimitCalculator.limit(x -> (x * x - 1) / (x - 1), 1, 0), 1e-6);
    }

    @Test
    void infiniteLimits() {
        assertEquals(Double.POSITIVE_INFINITY, LimitCalculator.limit(x -> 8 * x + 3, Double.POSITIVE_INFINITY, 0));
        assertEquals(Double.POSITIVE_INFINITY, LimitCalculator.limit(Math::log, Double.POSITIVE_INFINITY, 0));
        assertEquals(Double.POSITIVE_INFINITY, LimitCalculator.limit(x -> 1 / (x * x), 0, 0));
    }

    @Test
    void undefinedAndOneSidedLimits() {
        assertTrue(Double.isNaN(LimitCalculator.limit(x -> 1 / x, 0, 0)));
        assertTrue(Double.isNaN(LimitCalculator.limit(x -> Math.abs(x) / x, 0, 0)));
        assertEquals(1, LimitCalculator.limit(x -> Math.abs(x) / x, 0, 1), 1e-9);
        assertTrue(Double.isNaN(LimitCalculator.limit(Math::sin, Double.POSITIVE_INFINITY, 0)));
    }

    @Test
    void polesAndGapsNearTheTarget() {
        assertEquals(-2, LimitCalculator.limit(x -> 1 / (x - 10.5), 10, 0), 1e-9);
        assertEquals(-2, LimitCalculator.limit(x -> 1 / (x - 10.5), 10, 1), 1e-9);
        assertEquals(-10, LimitCalculator.limit(x -> 1 / (x - 2.1), 2, 0), 1e-9);
        assertEquals(-0.2, LimitCalculator.limit(x -> 1 / (x - 105), 100, 0), 1e-9);
        assertEquals(0, LimitCalculator.limit(x -> (x * x - 9) / (x - 3.15), 3, 0), 1e-9);
        assertEquals(-20, LimitCalculator.limit(x -> 1 / (x - 0.05), 0, 0), 1e-9);
        // factor 2 band always has a sample
        DoubleUnaryOperator gap = x -> Math.abs(x - 2) > 0.003 && Math.abs(x - 2) < 0.006 ? Double.NaN : x + 1;
        DoubleUnaryOperator pole = x -> Math.abs(x - 2) > 0.003 && Math.abs(x - 2) < 0.006
                ? Double.POSITIVE_INFINITY : x + 1;
        assertEquals(3, LimitCalculator.limit(gap, 2, 0), 1e-9);
        assertEquals(3, LimitCalculator.limit(pole, 2, 0), 1e-9);
    }

    @Test
    void slowFunctionsAreRightOrUncalculable() {
        assertEquals(0, LimitCalculator.limit(x -> Math.pow(x, 0.01), 0, 1), 0);
        assertEquals(0, LimitCalculator.limit(x -> Math.log(x) / Math.pow(x, 0.1), Double.POSITIVE_INFINITY, 0), 0);
        assertEquals(Double.POSITIVE_INFINITY, LimitCalculator.limit(x -> Math.pow(x, -0.01), 0, 1));
        assertThrows(UncalculableException.class,
                () -> LimitCalculator.limit(x -> 1 / Math.log(x), Double.POSITIVE_INFINITY, 0));
        assertThrows(UncalculableException.class,
                () -> LimitCalculator.limit(x -> Math.log(Math.log(x)), Double.POSITIVE_INFINITY, 0));
    }

    @Test
    void overflowAndDomain() {
        assertEquals(Double.POSITIVE_INFINITY, LimitCalculator.limit(Math::exp, Double.POSITIVE_INFINITY, 0));
        assertEquals(Double.POSITIVE_INFINITY, LimitCalculator.limit(x -> Math.exp(1 / x), 0, 1));
        assertEquals(1, LimitCalculator.limit(x -> Math.exp(x) / Math.exp(x), Double.POSITIVE_INFINITY, 0), 1e-9);
        assertEquals(0, LimitCalculator.limit(Math::sqrt, 0, 1), 1e-9);
        assertTrue(Double.isNaN(LimitCalculator.limit(Math::sqrt, 0, 0)));
    }

    @Test
    void allInfiniteSamplesOscillationAndOffsets() {
        assertEquals(Double.POSITIVE_INFINITY,
                LimitCalculator.limit(x -> Math.exp(1000 * x), Double.POSITIVE_INFINITY, 0));
        assertEquals(Double.POSITIVE_INFINITY, LimitCalculator.limit(x -> Math.exp(100 / (x * x)), 0, 0));
        assertTrue(Double.isNaN(LimitCalculator.limit(x -> Math.sin(Math.log(x)), Double.POSITIVE_INFINITY, 0)));
        assertEquals(Double.POSITIVE_INFINITY, LimitCalculator.limit(x -> x - 1e12, Double.POSITIVE_INFINITY, 0));
        assertEquals(1e12, LimitCalculator.limit(x -> 1e12 + 1 / x, Double.POSITIVE_INFINITY, 0), 1e-3);
    }

    @Test
    void oscillations() {
        double inf = Double.POSITIVE_INFINITY;
        assertEquals(0, LimitCalculator.limit(x -> Math.sqrt(x) * Math.cos(Math.log(x)), 0, 1), 0);
        assertEquals(1, LimitCalculator.limit(x -> 1 + Math.sqrt(x) * Math.cos(Math.log(x)), 0, 1), 1e-9);
        assertEquals(0, LimitCalculator.limit(x -> Math.sin(Math.log(x)) / x, inf, 0), 0);
        assertThrows(UncalculableException.class, () -> LimitCalculator.limit(x -> Math.sin(x) / Math.sqrt(x), inf, 0));
        assertTrue(Double.isNaN(LimitCalculator.limit(Math::cos, inf, 0)));
        assertTrue(Double.isNaN(LimitCalculator.limit(x -> Math.sin(1 / x), 0, 0)));
    }

    @Test
    void earlyEstimateDroppedOnceSamplesWalkAway() {
        double inf = Double.POSITIVE_INFINITY;
        assertEquals(1, LimitCalculator.limit(x -> x * x * x / (x * x * x + 1e18), inf, 0), 1e-9);
        // samples at 0.98 and climbing, 0 is not an answer
        assertEquals(1, LimitCalculator.limit(x -> x / (x + 1e8), inf, 0), 1e-9);
    }

    @Test
    void bigConstantsNeedSamplesFurtherOut() {
        double inf = Double.POSITIVE_INFINITY;
        assertEquals(1, LimitCalculator.limit(x -> x / (x + 1e10), inf, 0), 1e-9);
        assertEquals(1, LimitCalculator.limit(x -> x / (x + 1e100), inf, 0), 1e-9);
        assertEquals(0, LimitCalculator.limit(x -> 1e12 / (x + 1e12), inf, 0), 0);
        assertEquals(0, LimitCalculator.limit(x -> Math.exp(-x / 1e12), inf, 0), 0);
        assertEquals(0, LimitCalculator.limit(x -> 1 / (x - 1e12), inf, 0), 0);
        assertEquals(0, LimitCalculator.limit(x -> x / (x + 1e-8), 0, 0), 0);
        assertEquals(0, LimitCalculator.limit(x -> x / (x + 1e-12), 0, 0), 0);
    }

    @Test
    void absorbedSamplesDontSettle() {
        double inf = Double.POSITIVE_INFINITY;
        DoubleUnaryOperator noNoise = x -> Double.NaN;
        // x lost in x-1e20 until x is huge
        assertEquals(inf, LimitCalculator.limit(x -> x - 1e20, noNoise, x -> x - 1e20 == -1e20, inf, 0));
        assertEquals(-inf, LimitCalculator.limit(x -> 1e20 - x, noNoise, x -> 1e20 - x == 1e20, inf, 0));
        assertEquals(1e12, LimitCalculator.limit(x -> 1e12 + 1 / x, noNoise, x -> 1e12 + 1 / x == 1e12, inf, 0), 1e-3);
        // near a nonzero point x really doesnt matter
        assertEquals(-1e20, LimitCalculator.limit(x -> x - 1e20, noNoise, x -> true, 3, 0), 0);
    }

    @Test
    void growthOnlyCountsIfItKeepsGrowing() {
        double inf = Double.POSITIVE_INFINITY;
        assertEquals(inf, LimitCalculator.limit(x -> Math.pow(x, 0.001), inf, 0));
        assertEquals(inf, LimitCalculator.limit(x -> x / 1e100, inf, 0));
        assertEquals(inf, LimitCalculator.limit(x -> x * x, inf, 0));
        assertEquals(inf, LimitCalculator.limit(x -> Math.sqrt(x) - 1e8, inf, 0));
        assertEquals(-inf, LimitCalculator.limit(Math::log, 0, 1));
        assertEquals(inf, LimitCalculator.limit(x -> 1 / x, 0, 1));
        assertThrows(UncalculableException.class, () -> LimitCalculator.limit(x -> Math.pow(x, -0.001), inf, 0));
    }

    @Test
    void exponentialBeatsBigPowerAtInfinity() {
        double inf = Double.POSITIVE_INFINITY;
        assertEquals(inf, LimitCalculator.limit(x -> Math.exp(x) / Math.pow(x, 30), inf, 0));
        assertEquals(inf, LimitCalculator.limit(x -> Math.pow(2, x) / Math.pow(x, 20), inf, 0));
        assertEquals(0, LimitCalculator.limit(x -> Math.pow(x, 50) / Math.exp(x), inf, 0), 1e-9);
    }
}
