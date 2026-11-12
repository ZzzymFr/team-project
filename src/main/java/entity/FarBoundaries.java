package entity;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

// roots past [-100,100] as boundaries, checked against the far scan
final class FarBoundaries {
    private static final double SNAP = 1e-8;
    private static final double[] SIDES = {-1, 1};

    private final SideScan scan;
    private final ComparisonFinder comparisons;
    private final SampleTruth sampleTruth;

    FarBoundaries(SideScan scan, ComparisonFinder comparisons, SampleTruth sampleTruth) {
        this.scan = scan;
        this.comparisons = comparisons;
        this.sampleTruth = sampleTruth;
    }

    // scan dips can reach back into range, those roots are the grids job
    static List<Double> pastRange(List<Double> roots) {
        List<Double> far = new ArrayList<>();
        for (double root : roots) {
            if (Math.abs(root) > EquationSolver.SEARCH_END) {
                far.add(root);
            }
        }
        return far;
    }

    // roots where some pair really meets, probe sized to the root
    Set<Double> genuine(List<Double> sortedRoots) {
        Set<Double> genuine = new HashSet<>();
        for (double root : sortedRoots) {
            double offset = ConditionRoots.probeOffset(root, sortedRoots);
            for (SidePair pair : comparisons.numericPairs()) {
                if (scan.isGenuineRoot(pair, root, offset)) {
                    genuine.add(root);
                    break;
                }
            }
        }
        return genuine;
    }

    // far root a bisected boundary belongs to, else NaN
    static double snap(double point, List<Double> roots) {
        for (double root : roots) {
            if (Math.abs(root - point) < SNAP * Math.abs(root)) {
                return root;
            }
        }
        return Double.NaN;
    }

    // every far scan point w/ a known truth falls in or out like it should
    boolean agrees(List<Interval> intervals) {
        for (int k = 1; k <= SideScan.SCAN_POINTS; k++) {
            for (double sign : SIDES) {
                double point = sign * EquationSolver.SEARCH_END
                        * Math.pow(10, k / (double) SideScan.POINTS_PER_DECADE);
                int known = knownAt(point);
                if (known != SampleTruth.UNKNOWN && (known == SampleTruth.TRUE) != contains(intervals, point)) {
                    return false;
                }
            }
        }
        return true;
    }

    private int knownAt(double point) {
        try {
            return sampleTruth.knownAt(point);
        } catch (BudgetExceededException | UndecidableException e) {
            throw e;
        } catch (UncalculableException e) {
            return SampleTruth.UNKNOWN;
        }
    }

    private static boolean contains(List<Interval> intervals, double point) {
        for (Interval interval : intervals) {
            boolean fromLow = point > interval.getLow() || point == interval.getLow() && interval.isLowIncluded();
            boolean toHigh = point < interval.getHigh() || point == interval.getHigh() && interval.isHighIncluded();
            if (fromLow && toHigh) {
                return true;
            }
        }
        return false;
    }
}
