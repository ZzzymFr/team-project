package entity;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

// which x values to try
final class SearchPoints {
    private static final double[] SPECIAL_POINTS = {0, 0.5, -0.5, 1, -1};
    private static final double[] FAR_POINTS = {1e3, -1e3, 1e6, -1e6};
    private static final double OUTER_SCALE = 10;
    private static final double[] FAR_INTEGERS = {1e3, 1e6, 1e9, 1e12};

    private final Context context;
    private final double step;
    private final ConditionRoots roots;

    SearchPoints(Context context, double step, ConditionRoots roots) {
        this.context = context;
        this.step = step;
        this.roots = roots;
    }

    List<Double> integerPoints(boolean naturalOnly, int range) {
        Set<Double> points = new LinkedHashSet<>();
        for (int n = naturalOnly ? 0 : -range; n <= range; n++) {
            points.add((double) n);
        }
        for (double far : FAR_INTEGERS) {
            points.add(far);
            points.add(-far);
        }
        for (double value : context.getVariableValues()) {
            double[] near = {Math.floor(value) - 1, Math.floor(value), Math.ceil(value), Math.ceil(value) + 1,
                    -Math.floor(value), -Math.ceil(value)};
            for (double n : near) {
                points.add(n + 0.0);
            }
        }
        List<Double> integers = new ArrayList<>();
        for (double point : points) {
            if (Double.isFinite(point) && (!naturalOnly || point >= 0)) {
                integers.add(point);
            }
        }
        return integers;
    }

    List<Double> cheapPoints(boolean wide) {
        List<Double> points = new ArrayList<>();
        int count = (int) Math.round((EquationSolver.SEARCH_END - EquationSolver.SEARCH_START) / step) + 1;
        for (int i = 0; i < count; i++) {
            points.add(EquationSolver.SEARCH_START + i * step);
        }
        for (double point : SPECIAL_POINTS) {
            points.add(point);
        }
        if (wide) {
            for (double point : FAR_POINTS) {
                points.add(point);
            }
            for (double value : context.getVariableValues()) {
                points.add(value);
                points.add(value - 0.5);
                points.add(value + 0.5);
                points.add(value - 1);
                points.add(value + 1);
                points.add(-value);
            }
            // past every outer value so y > 2x works at x=1e6
            double beyond = 1;
            for (double value : context.getVariableValues()) {
                beyond += OUTER_SCALE * Math.abs(value);
            }
            if (!context.getVariableValues().isEmpty() && !Double.isInfinite(beyond)) {
                points.add(beyond);
                points.add(-beyond);
            }
        }
        return points;
    }

    private List<Double> rootPoints() {
        List<Double> points = new ArrayList<>();
        for (ConditionRoots.Root root : roots.all()) {
            points.add(root.value);
            points.add(root.value - SideScan.ROOT_OFFSET);
            points.add(root.value + SideScan.ROOT_OFFSET);
        }
        return points;
    }

    double[] sortedCandidates() {
        List<Double> points = cheapPoints(false);
        points.addAll(rootPoints());
        points.addAll(roots.rootMidpoints());
        double[] sorted = new double[points.size()];
        for (int i = 0; i < sorted.length; i++) {
            sorted[i] = points.get(i);
        }
        Arrays.sort(sorted);
        int size = 0;
        for (int i = 0; i < sorted.length; i++) {
            if (size == 0 || sorted[i] != sorted[size - 1]) {
                sorted[size] = sorted[i];
                size++;
            }
        }
        return Arrays.copyOf(sorted, size);
    }
}
