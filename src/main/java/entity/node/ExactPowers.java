package entity.node;

import entity.symbolic.Rational;

// whole powers and roots of exact p/q, overflow throws ArithmeticException
final class ExactPowers {
    private ExactPowers() {
    }

    static Rational power(Rational base, long exponent) {
        if (exponent < 0) {
            return Rational.ONE.div(power(base, Math.negateExact(exponent)));
        }
        Rational result = Rational.ONE;
        Rational square = base;
        long left = exponent;
        while (left > 0) {
            if (left % 2 == 1) {
                result = result.mul(square);
            }
            left = left / 2;
            if (left > 0) {
                square = square.mul(square);
            }
        }
        return result;
    }

    // degree-th root of x >= 0, null if its irrational
    static Rational root(Rational x, long degree) {
        long num = wholeRoot(x.getNum(), degree);
        long den = wholeRoot(x.getDen(), degree);
        if (num < 0 || den < 0) {
            return null;
        }
        return Rational.of(num, den);
    }

    // -1 if n isnt a perfect power
    private static long wholeRoot(long n, long degree) {
        if (n < 2 || degree == 1) {
            return n;
        }
        if (degree >= Long.SIZE) {
            return -1;
        }
        long guess = Math.round(Math.pow(n, 1.0 / degree));
        for (long r = Math.max(1, guess - 1); r <= guess + 1; r++) {
            if (isPower(r, degree, n)) {
                return r;
            }
        }
        return -1;
    }

    private static boolean isPower(long r, long degree, long n) {
        long result = 1;
        for (long i = 0; i < degree; i++) {
            if (result > n / r) {
                return false;
            }
            result = result * r;
        }
        return result == n;
    }
}
