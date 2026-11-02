package entity;

import entity.node.BinaryNode;
import entity.node.Node;
import entity.node.RelationNode;
import entity.node.UnaryNode;
import entity.value.FiniteSetValue;
import entity.value.NumberValue;
import entity.value.Value;
import entity.value.VectorValue;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

// which sides (or parts of sides) the condition solver should compare
final class ComparisonFinder {
    private static final int NUMBER_SHAPE = 0;
    private static final int VECTOR_SHAPE = 1;
    private static final int SET_SHAPE = 2;
    private static final int OTHER_SHAPE = 3;
    private static final int MAX_ELEMENT_PAIRS = 16;
    private static final double[] SHAPE_POINTS = {0.5, -7.3, 2.9, 13.7};

    private final Node condition;
    private final String variable;
    private final Context context;
    private List<RelationNode> relations;

    ComparisonFinder(Node condition, String variable, Context context) {
        this.condition = condition;
        this.variable = variable;
        this.context = context;
    }

    List<SidePair> numericPairs() {
        List<SidePair> pairs = new ArrayList<>();
        for (RelationNode relation : relations()) {
            List<Node> operands = relation.getOperands();
            for (int i = 0; i + 1 < operands.size(); i++) {
                if (!isSetOperator(relation.getOperators().get(i))) {
                    Node left = operands.get(i);
                    Node right = operands.get(i + 1);
                    pairs.addAll(pairsOf(left, right));
                    if (isZero(right)) {
                        addFactorPairs(pairs, left, right);
                    } else if (isZero(left)) {
                        addFactorPairs(pairs, right, left);
                    }
                }
            }
        }
        return pairs;
    }

    // check each factor of a product separately
    private void addFactorPairs(List<SidePair> pairs, Node product, Node zero) {
        List<Node> factors = new ArrayList<>();
        collectFactors(product, factors);
        if (factors.size() > 1) {
            SidePair whole = new SidePair(product, zero, EquationSolver.WHOLE, EquationSolver.WHOLE);
            for (Node factor : factors) {
                pairs.add(new SidePair(factor, zero, EquationSolver.WHOLE, EquationSolver.WHOLE, whole));
            }
        }
    }

    private static void collectFactors(Node node, List<Node> factors) {
        if (node instanceof BinaryNode && ((BinaryNode) node).getOp() == BinaryNode.Op.MULTIPLY) {
            collectFactors(((BinaryNode) node).getLeft(), factors);
            collectFactors(((BinaryNode) node).getRight(), factors);
        } else if (node instanceof BinaryNode && ((BinaryNode) node).getOp() == BinaryNode.Op.DIVIDE) {
            collectFactors(((BinaryNode) node).getLeft(), factors);
        } else if (node instanceof UnaryNode && ((UnaryNode) node).getOp() == UnaryNode.Op.NEGATE) {
            collectFactors(((UnaryNode) node).getOperand(), factors);
        } else {
            factors.add(node);
        }
    }

    private boolean isZero(Node node) {
        Set<String> names = new HashSet<>();
        node.collectFreeVariables(names);
        if (!names.isEmpty()) {
            return false;
        }
        try {
            Value value = node.evaluate(context);
            return value instanceof NumberValue && ((NumberValue) value).getValue() == 0;
        } catch (BudgetExceededException | UndecidableException e) {
            throw e;
        } catch (UncalculableException e) {
            return false;
        }
    }

    // compare nums whole, vectors/sets part by part
    List<SidePair> pairsOf(Node left, Node right) {
        int[] leftShape = shapeOf(left);
        int[] rightShape = shapeOf(right);
        List<SidePair> pairs = new ArrayList<>();
        if (leftShape[0] == NUMBER_SHAPE && rightShape[0] == NUMBER_SHAPE) {
            pairs.add(new SidePair(left, right, EquationSolver.WHOLE, EquationSolver.WHOLE));
        } else if (leftShape[0] == VECTOR_SHAPE && rightShape[0] == VECTOR_SHAPE) {
            for (int i = 0; i < Math.min(leftShape[1], rightShape[1]); i++) {
                pairs.add(new SidePair(left, right, i, i));
            }
        } else if (leftShape[0] == SET_SHAPE && rightShape[0] == SET_SHAPE) {
            if (leftShape[1] * rightShape[1] > MAX_ELEMENT_PAIRS) {
                throw new UncalculableException(UncalculableReason.SETS_TOO_LARGE);
            }
            for (int j = 0; j < leftShape[1]; j++) {
                for (int k = 0; k < rightShape[1]; k++) {
                    pairs.add(new SidePair(left, right, j, k));
                }
            }
        }
        return pairs;
    }

    // {kind, max size} from a few points
    private int[] shapeOf(Node side) {
        int kind = NUMBER_SHAPE;
        int size = 0;
        for (double point : SHAPE_POINTS) {
            Value value;
            try {
                value = side.evaluate(context.withVariable(variable, point));
            } catch (BudgetExceededException | UndecidableException e) {
                throw e;
            } catch (UncalculableException e) {
                continue;
            }
            if (value instanceof VectorValue) {
                kind = VECTOR_SHAPE;
                size = Math.max(size, ((VectorValue) value).getComponents().size());
            } else if (value instanceof FiniteSetValue) {
                kind = SET_SHAPE;
                size = Math.max(size, ((FiniteSetValue) value).getElements().size());
            } else if (!(value instanceof NumberValue)) {
                kind = OTHER_SHAPE;
            }
        }
        return new int[] {kind, size};
    }

    // rels w/ all vars known after solving (R1)
    List<RelationNode> relations() {
        if (relations == null) {
            relations = new ArrayList<>();
            collectRelations(condition);
        }
        return relations;
    }

    private void collectRelations(Node node) {
        if (node instanceof RelationNode && isSolvable(node)) {
            relations.add((RelationNode) node);
        }
        for (Node child : node.children()) {
            collectRelations(child);
        }
    }

    static boolean isSetOperator(String operator) {
        return operator.equals("\\in") || operator.equals("\\notin") || operator.equals("\\subset");
    }

    private boolean isSolvable(Node relation) {
        Set<String> names = new HashSet<>();
        relation.collectFreeVariables(names);
        for (String name : names) {
            if (!name.equals(variable) && !context.hasVariable(name)) {
                return false;
            }
        }
        return true;
    }
}
