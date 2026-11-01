package use_case.evaluate;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

// root list helpers shared by the solving steps
final class Roots {
    private static final double SAME_FLOOR = 1e-15;

    private Roots() {
    }

    // relative, so +-1e-7 roots stay apart
    static boolean isSame(double a, double b) {
        return a == b || Math.abs(a - b) <= 1e-6 * Math.max(Math.abs(a), Math.abs(b)) + SAME_FLOOR;
    }

    static boolean containsSame(List<Double> values, double value) {
        for (double other : values) {
            if (isSame(other, value)) {
                return true;
            }
        }
        return false;
    }

    // sorts in place, then drops near-dupes
    static List<Double> sortedDistinct(List<Double> all) {
        Collections.sort(all);
        List<Double> distinct = new ArrayList<>();
        for (double value : all) {
            if (distinct.isEmpty() || !isSame(value, distinct.get(distinct.size() - 1))) {
                distinct.add(value);
            }
        }
        return distinct;
    }
}
