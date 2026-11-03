package entity;

import entity.node.LiteralNode;
import entity.node.Node;
import entity.node.RelationNode;
import entity.value.FiniteSetValue;
import entity.value.NumberValue;
import entity.value.Value;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

// roots of every comparison in the condition, found once
final class ConditionRoots {
    static final double PROBE = 1e-4;
    static final double MIN_PROBE = 1e-12;
    static final double NEIGHBOUR_SHARE = 4;

    private final String variable;
    private final Context context;
    private final double step;
    private final ComparisonFinder comparisons;
    private List<Root> roots;
    private boolean undersampled;
    private boolean nearMiss;

    ConditionRoots(String variable, Context context, double step, ComparisonFinder comparisons) {
        this.variable = variable;
        this.context = context;
        this.step = step;
        this.comparisons = comparisons;
    }

    boolean isUndersampled() {
        return undersampled;
    }

    boolean isNearMiss() {
        return nearMiss;
    }

    List<Root> all() {
        if (roots == null) {
            roots = new ArrayList<>();
            for (RelationNode relation : comparisons.relations()) {
                addRoots(relation);
            }
        }
        return roots;
    }

    private void addRoots(RelationNode relation) {
        List<Node> operands = relation.getOperands();
        List<String> operators = relation.getOperators();
        for (int i = 0; i + 1 < operands.size(); i++) {
            Node left = operands.get(i);
            Node right = operands.get(i + 1);
            if (ComparisonFinder.isSetOperator(operators.get(i))) {
                addMembershipRoots(left, right);
                continue;
            }
            for (SidePair pair : comparisons.pairsOf(left, right)) {
                EquationSolver solver = new EquationSolver(left, right, variable, context, step,
                        pair.leftPart, pair.rightPart);
                for (double value : solver.solve()) {
                    roots.add(new Root(value, pair, solver.isStretchEnd(value)));
                }
                undersampled = undersampled || solver.isUndersampled();
                nearMiss = nearMiss || solver.isNearMiss();
            }
        }
    }

    private void addMembershipRoots(Node left, Node right) {
        Set<String> names = new HashSet<>();
        right.collectFreeVariables(names);
        if (names.contains(variable)) {
            return;
        }
        try {
            Value set = right.evaluate(context);
            if (set instanceof FiniteSetValue) {
                for (double element : ((FiniteSetValue) set).getElements()) {
                    Node target = new LiteralNode(new NumberValue(element), false);
                    for (double value : new EquationSolver(left, target, variable, context, step).solve()) {
                        roots.add(new Root(value,
                                new SidePair(left, target, EquationSolver.WHOLE, EquationSolver.WHOLE), false));
                    }
                }
            }
        } catch (BudgetExceededException | UndecidableException e) {
            throw e;
        } catch (UncalculableException e) {
            // no roots here
        }
    }

    List<Double> values() {
        List<Double> values = new ArrayList<>();
        for (Root root : all()) {
            values.add(root.value);
        }
        return values;
    }

    // close roots, might miss it on the grid
    List<Double> rootMidpoints() {
        List<Double> rootValues = sortedRootValues();
        List<Double> midpoints = new ArrayList<>();
        for (int i = 0; i + 1 < rootValues.size(); i++) {
            midpoints.add((rootValues.get(i) + rootValues.get(i + 1)) / 2);
        }
        return midpoints;
    }

    // distinct roots sorted
    List<Double> sortedRootValues() {
        Set<Double> distinct = new HashSet<>(values());
        List<Double> values = new ArrayList<>(distinct);
        Collections.sort(values);
        return values;
    }

    // probe 1e-4 of root size, max 1/4 to next root
    static double probeOffset(double root, List<Double> rootValues) {
        double offset = PROBE * Math.max(1, Math.abs(root));
        int index = Collections.binarySearch(rootValues, root);
        if (index < 0) {
            return offset;
        }
        if (index > 0) {
            offset = Math.min(offset, (root - rootValues.get(index - 1)) / NEIGHBOUR_SHARE);
        }
        if (index + 1 < rootValues.size()) {
            offset = Math.min(offset, (rootValues.get(index + 1) - root) / NEIGHBOUR_SHARE);
        }
        return Math.max(offset, MIN_PROBE * Math.max(1, Math.abs(root)));
    }

    static final class Root {
        final double value;
        final SidePair pair;
        // just the end of an equal stretch
        final boolean stretchEnd;

        private Root(double value, SidePair pair, boolean stretchEnd) {
            this.value = value;
            this.pair = pair;
            this.stretchEnd = stretchEnd;
        }
    }
}
