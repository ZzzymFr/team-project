package use_case.evaluate;

// float noise -> 0, real tiny values stay
final class NoiseFilter {
    // noise unknown: anything under this counts as 0
    private static final double UNKNOWN_NOISE_CUTOFF = 1e-12;

    private NoiseFilter() {
    }

    static boolean isTiny(double number) {
        return Math.abs(number) < UNKNOWN_NOISE_CUTOFF;
    }

    static double zeroIfTiny(double number) {
        return isTiny(number) ? 0 : number;
    }
}
