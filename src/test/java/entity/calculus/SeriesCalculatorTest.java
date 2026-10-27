package entity.calculus;

import entity.UncalculableException;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SeriesCalculatorTest {
    @Test
    void finiteSumsAndProducts() {
        assertEquals(325, SeriesCalculator.series(i -> i, 1, 25, false), 0);
        assertEquals(120, SeriesCalculator.series(i -> i, 1, 5, true), 0);
    }

    @Test
    void infiniteSeries() {
        assertEquals(Math.PI * Math.PI / 6,
                SeriesCalculator.series(n -> 1.0 / ((double) n * n), 1, Long.MAX_VALUE, false), 1e-5);
        assertEquals(2, SeriesCalculator.series(n -> Math.pow(0.5, n), 0, Long.MAX_VALUE, false), 1e-9);
        assertEquals(Double.POSITIVE_INFINITY, SeriesCalculator.series(n -> 1.0 / n, 1, Long.MAX_VALUE, false));
        assertTrue(Double.isNaN(SeriesCalculator.series(n -> n % 2 == 0 ? 1 : -1, 0, Long.MAX_VALUE, false)));
    }

    private static double infinite(java.util.function.LongToDoubleFunction term, long start, boolean product) {
        return SeriesCalculator.series(term, start, Long.MAX_VALUE, product);
    }

    @Test
    void finiteEdgeCases() {
        assertEquals(0, SeriesCalculator.series(i -> i, 5, 1, false), 0);
        assertEquals(1, SeriesCalculator.series(i -> i, 5, 1, true), 0);
        assertTrue(Double.isNaN(SeriesCalculator.series(n -> n == 0 ? Double.NaN : 1.0 / n, 0, 3, false)));
        assertEquals(500000500000.0, SeriesCalculator.series(i -> i, 1, SeriesCalculator.MAX_TERMS, false), 0);
        assertThrows(UncalculableException.class,
                () -> SeriesCalculator.series(i -> i, 1, SeriesCalculator.MAX_TERMS + 1, false));
        assertThrows(UncalculableException.class,
                () -> SeriesCalculator.series(i -> i, Long.MIN_VALUE, Long.MAX_VALUE - 1, false));
    }

    @Test
    void convergentSeriesAndProducts() {
        assertEquals(Math.PI * Math.PI / 6, infinite(n -> 1.0 / ((double) n * n), 1, false), 1e-9);
        assertEquals(Math.log(2), infinite(n -> (n % 2 == 1 ? 1.0 : -1.0) / n, 1, false), 1e-9);
        assertEquals(Math.E, infinite(n -> 1 / factorial(n), 0, false), 1e-12);
        assertEquals(Math.PI / 4, infinite(n -> (n % 2 == 0 ? 1.0 : -1.0) / (2 * n + 1), 0, false), 1e-9);
        assertEquals(2.612375348685488, infinite(n -> Math.pow(n, -1.5), 1, false), 1e-8);
        assertEquals(Math.exp(2), infinite(n -> Math.pow(2, n) / factorial(n), 0, false), 1e-9);
        assertEquals(Math.sinh(Math.PI) / Math.PI, infinite(n -> 1 + 1.0 / ((double) n * n), 1, true), 1e-9);
        assertEquals(0.5, infinite(n -> 1 - 1.0 / ((double) n * n), 2, true), 1e-9);
        assertEquals(0, infinite(n -> n, 0, true), 0);
    }

    @Test
    void divergentSeriesAndProducts() {
        assertEquals(Double.POSITIVE_INFINITY, infinite(n -> 1 / Math.sqrt(n), 1, false));
        assertEquals(Double.POSITIVE_INFINITY, infinite(n -> n, 1, false));
        assertEquals(Double.NEGATIVE_INFINITY, infinite(n -> -1.0 / n, 1, false));
        assertEquals(Double.POSITIVE_INFINITY, infinite(n -> Math.pow(2, n), 0, false));
        assertTrue(Double.isNaN(infinite(n -> n % 2 == 0 ? n : -n, 0, false)));
        assertTrue(Double.isNaN(infinite(Math::sin, 1, false)));
        assertTrue(Double.isNaN(infinite(n -> n == 0 ? Double.NaN : 1.0 / n, 0, false)));
        assertEquals(0, infinite(n -> 0.5, 1, true), 0);
        assertEquals(Double.POSITIVE_INFINITY, infinite(n -> 2, 1, true));
        assertTrue(Double.isNaN(infinite(n -> -1, 1, true)));
    }

    @Test
    void inconclusiveSeriesAreUncalculable() {
        assertThrows(UncalculableException.class, () -> infinite(n -> 1 / (n * Math.log(n)), 2, false));
        assertThrows(UncalculableException.class, () -> infinite(n -> 1.0 / ((double) n * n), 1_000_000_000, false));
        assertThrows(UncalculableException.class, () -> infinite(n -> Math.pow(-30, n) / factorial(n), 0, false));
        assertThrows(UncalculableException.class, () -> infinite(n -> Math.pow(100, n) / factorial(n), 0, false));
    }

    private static double factorial(long n) {
        double result = 1;
        for (long i = 2; i <= n; i++) {
            result = result * i;
        }
        return result;
    }
}
