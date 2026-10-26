package entity.calculus;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DerivativeCalculatorTest {
    @Test
    void derivativesOfPolynomials() {
        assertEquals(6, DerivativeCalculator.derivative(x -> x * x, 3, 1), 1e-6);
        assertEquals(12, DerivativeCalculator.derivative(x -> x * x * x, 2, 2), 1e-6);
        assertEquals(24, DerivativeCalculator.derivative(x -> x * x * x * x, 1, 4), 1e-3);
    }

    @Test
    void cornerHasNoDerivative() {
        assertTrue(Double.isNaN(DerivativeCalculator.derivative(Math::abs, 0, 1)));
    }

    @Test
    void smoothFunctions() {
        assertEquals(Math.cos(1), DerivativeCalculator.derivative(Math::sin, 1, 1), 1e-9);
        assertEquals(-Math.sin(1), DerivativeCalculator.derivative(Math::sin, 1, 2), 1e-8);
        assertEquals(20, DerivativeCalculator.derivative(Math::log, 0.05, 1), 1e-6);
        assertEquals(Math.exp(2), DerivativeCalculator.derivative(Math::exp, 2, 3), 1e-6);
        assertEquals(0, DerivativeCalculator.derivative(x -> x * x * x, 2, 4), 0);
        assertEquals(-400, DerivativeCalculator.derivative(x -> 1 / x, 0.05, 1), 1e-6);
        assertEquals(7.68e7, DerivativeCalculator.derivative(x -> 1 / x, 0.05, 4), 1);
    }

    @Test
    void roundsToSignificantDigits() {
        assertEquals(1.23456789, Rounding.toSignificant(1.234567891234, 9), 0);
        assertEquals(12, Rounding.toSignificant(12.0000000003, 9), 0);
        assertEquals(-0.000123456789, Rounding.toSignificant(-0.0001234567891, 9), 0);
        assertTrue(Double.isNaN(Rounding.toSignificant(Double.NaN, 9)));
        assertEquals(0, Rounding.toSignificant(0, 9), 0);
    }
}
