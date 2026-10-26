package entity.calculus;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
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
}
