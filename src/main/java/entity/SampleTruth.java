package entity;

import entity.node.BinaryNode;
import entity.node.Node;
import entity.node.UnaryNode;
import entity.value.BooleanValue;
import entity.value.Value;

import java.util.Set;

// truth of the condition at sample points, rounding runs filled in
final class SampleTruth {
    static final int TRUE = 1;
    static final int FALSE = 0;
    private static final int UNKNOWN = 2;

    private final Node condition;
    private final String variable;
    private final Context context;
    private final RoundingCheck rounding;
    private boolean lostInSample;

    SampleTruth(Node condition, String variable, Context context, RoundingCheck rounding) {
        this.condition = condition;
        this.variable = variable;
        this.context = context;
        this.rounding = rounding;
    }

    boolean[] truthAt(double[] points, Set<Double> genuineRoots) {
        Sample[] samples = new Sample[points.length];
        for (int i = 0; i < points.length; i++) {
            samples[i] = sampleAt(points[i], genuineRoots.contains(points[i]));
        }
        return withRoundingRunsFilled(samples);
    }

    boolean isTrueAt(double point) {
        context.spendStep();
        Value value;
        try {
            value = condition.evaluate(context.withVariable(variable, point));
        } catch (UndefinedException e) {
            return false;
        }
        if (!(value instanceof BooleanValue)) {
            throw new UndecidableException();
        }
        return ((BooleanValue) value).getValue();
    }

    // truth at sample + whether its just rounding
    private Sample sampleAt(double point, boolean atRoot) {
        context.setAbsorbed(false);
        context.setTolerated(false);
        context.spendStep();
        Context at = context.withVariable(variable, point);
        Value value;
        try {
            value = condition.evaluate(at);
        } catch (UndefinedException e) {
            return new Sample(false, FALSE, false);
        } catch (OverflowException e) {
            // overflow = lost value, rest decides
            lostInSample = true;
            int known = partTruth(condition, at, atRoot);
            return new Sample(false, known, true);
        }
        if (!(value instanceof BooleanValue)) {
            throw new UndecidableException();
        }
        boolean truth = ((BooleanValue) value).getValue();
        if (!context.isAbsorbed() && (atRoot || !context.isTolerated())) {
            return new Sample(truth, truth ? TRUE : FALSE, false);
        }
        lostInSample = false;
        int known = partTruth(condition, at, atRoot);
        return new Sample(truth, known, lostInSample);
    }

    private int partTruth(Node node, Context at, boolean atRoot) {
        if (node instanceof UnaryNode && ((UnaryNode) node).getOp() == UnaryNode.Op.NOT) {
            int inner = partTruth(((UnaryNode) node).getOperand(), at, atRoot);
            return inner == UNKNOWN ? UNKNOWN : TRUE - inner;
        }
        if (node instanceof BinaryNode && node.isCondition()) {
            BinaryNode binary = (BinaryNode) node;
            int p = partTruth(binary.getLeft(), at, atRoot);
            int q = partTruth(binary.getRight(), at, atRoot);
            switch (binary.getOp()) {
                case AND:
                    return p == FALSE || q == FALSE ? FALSE : (p == TRUE && q == TRUE ? TRUE : UNKNOWN);
                case OR:
                    return p == TRUE || q == TRUE ? TRUE : (p == FALSE && q == FALSE ? FALSE : UNKNOWN);
                case IMPLIES:
                    return p == FALSE || q == TRUE ? TRUE : (p == TRUE && q == FALSE ? FALSE : UNKNOWN);
                default:
                    if (p == UNKNOWN || q == UNKNOWN) {
                        return UNKNOWN;
                    }
                    return (binary.getOp() == BinaryNode.Op.IFF) == (p == q) ? TRUE : FALSE;
            }
        }
        context.setAbsorbed(false);
        context.setTolerated(false);
        try {
            Value value = node.evaluate(at);
            if (!(value instanceof BooleanValue)) {
                return UNKNOWN;
            }
            boolean tolerated = context.isTolerated();
            if (context.isAbsorbed() && rounding.isRoundingOnly(node, at)) {
                lostInSample = true;
                return UNKNOWN;
            }
            if (tolerated && !atRoot) {
                return UNKNOWN;
            }
            return ((BooleanValue) value).getValue() ? TRUE : FALSE;
        } catch (OverflowException e) {
            lostInSample = true;
            return UNKNOWN;
        } catch (BudgetExceededException | UndecidableException e) {
            throw e;
        } catch (UncalculableException e) {
            return UNKNOWN;
        }
    }

    // rounding runs take truth from neighbours
    private static boolean[] withRoundingRunsFilled(Sample[] samples) {
        boolean[] truth = new boolean[samples.length];
        int i = 0;
        while (i < samples.length) {
            if (samples[i].known != UNKNOWN) {
                truth[i] = samples[i].known == TRUE;
                i++;
                continue;
            }
            int start = i;
            boolean lost = false;
            while (i < samples.length && samples[i].known == UNKNOWN) {
                truth[i] = samples[i].truth;
                lost = lost || samples[i].lost;
                i++;
            }
            int before = start > 0 ? samples[start - 1].known : UNKNOWN;
            int after = i < samples.length ? samples[i].known : UNKNOWN;
            // only fill from a stretch at the end, not one point
            boolean atEnd = start == 0 || i == samples.length;
            if (lost && atEnd && !(isBacked(samples, start - 1, -1) && isBacked(samples, i, 1))) {
                throw new UncalculableException(UncalculableException.TOO_BIG);
            }
            if (before == UNKNOWN && after == UNKNOWN || before != UNKNOWN && after != UNKNOWN && before != after) {
                if (lost) {
                    throw new UncalculableException(UncalculableException.TOO_BIG);
                }
                continue;
            }
            boolean fill = (before == UNKNOWN ? after : before) == TRUE;
            for (int k = start; k < i; k++) {
                truth[k] = fill;
            }
        }
        return truth;
    }

    // true if past the search or next sample agrees
    private static boolean isBacked(Sample[] samples, int index, int direction) {
        if (index < 0 || index >= samples.length) {
            return true;
        }
        for (int further = index + direction; further >= 0 && further < samples.length; further += direction) {
            if (samples[further].known != UNKNOWN) {
                return samples[further].known == samples[index].known;
            }
        }
        return false;
    }

    private static final class Sample {
        private final boolean truth;
        private final int known;
        private final boolean lost;

        private Sample(boolean truth, int known, boolean lost) {
            this.truth = truth;
            this.known = known;
            this.lost = lost;
        }
    }
}
