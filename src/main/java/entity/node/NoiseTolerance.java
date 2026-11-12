package entity.node;

import entity.value.Values;

// the tol = uses on plain formulas, NaN = usual 1e-9 band
final class NoiseTolerance {
    private static final double USUAL_TOLERANCE = 1e-9;

    private NoiseTolerance() {
    }

    static double of(double scale, double a, double b) {
        if (Double.isNaN(scale) || Double.isInfinite(scale)) {
            return Double.NaN;
        }
        double size = Math.max(1, Math.max(Math.abs(a), Math.abs(b)));
        // never looser than the usual 1e-9, noisy spots keep the old band
        return Math.min(NoiseScale.TOLERANCE * scale, USUAL_TOLERANCE * size);
    }

    static boolean within(double a, double b, double tolerance) {
        if (Double.isNaN(tolerance)) {
            return Values.areAlmostEqual(a, b);
        }
        return a == b || Math.abs(a - b) <= tolerance;
    }
}
