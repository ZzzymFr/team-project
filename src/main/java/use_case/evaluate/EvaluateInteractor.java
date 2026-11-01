package use_case.evaluate;

import entity.Context;
import entity.MathLexer;
import entity.MathParser;
import entity.MathSyntaxException;
import entity.UncalculableException;
import entity.UserFunction;
import entity.node.Node;
import entity.node.RelationNode;
import entity.symbolic.Expr;
import entity.symbolic.ExprWriter;
import entity.symbolic.SymbolicDerivative;
import entity.symbolic.TextRun;
import entity.value.NumberSetValue;
import entity.value.NumberValue;
import entity.value.Value;
import entity.value.ValueVisitor;
import entity.value.Values;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

// picks what kind of input it is and hands it off
public class EvaluateInteractor implements EvaluateInputBoundary {
    private static final int MAX_PLUS_MINUS = 4;
    private static final int MAX_ANSWER_LENGTH = 60;
    private static final ValueVisitor<Value> ROUNDER = new ValueRounder();

    private final FunctionDataAccessInterface functionDataAccess;
    private final EvaluateOutputBoundary presenter;
    private final FunctionDefiner functionDefiner;

    public EvaluateInteractor(FunctionDataAccessInterface functionDataAccess,
                              EvaluateOutputBoundary presenter) {
        this.functionDataAccess = functionDataAccess;
        this.presenter = presenter;
        this.functionDefiner = new FunctionDefiner(functionDataAccess, presenter);
    }

    @Override
    public void execute(EvaluateInputData inputData) {
        String expression = inputData.getExpression();
        try {
            List<String> tokens = MathLexer.tokenize(expression);

            if (!tokens.isEmpty() && tokens.get(tokens.size() - 1).equals("=")) {
                tokens.remove(tokens.size() - 1);
            }
            if (tokens.isEmpty()) {
                presenter.prepareFailView("Please enter an expression.");
                return;
            }
            if (FunctionDefiner.isDefinition(tokens)) {
                functionDefiner.define(tokens, expression);
                return;
            }
            if (tokens.get(0).equals("=")) {
                presenter.prepareFailView("There is nothing on the left of \"=\".");
                return;
            }
            if (tokens.get(tokens.size() - 1).equals("=")) {
                presenter.prepareFailView("There is nothing on the right of \"=\".");
                return;
            }
            evaluate(tokens);
        } catch (MathSyntaxException e) {
            presenter.prepareFailView(e.getMessage());
        } catch (StackOverflowError e) {
            // nested too deep to even walk
            presenter.prepareFailView(new UncalculableException(UncalculableException.TOO_MUCH_WORK).getMessage());
        } catch (RuntimeException e) {
            presenter.prepareFailView("Uncalculable");
        }
    }

    private void evaluate(List<String> tokens) {
        Map<String, UserFunction> functions = functionDataAccess.getAllFunctions();
        MathParser parser = new MathParser(tokens, functions);
        Node node = parser.parse();
        int masks = signMaskCount(parser);
        Set<String> freeVariables = new LinkedHashSet<>();
        node.collectFreeVariables(freeVariables);
        List<String> unknowns = new ArrayList<>(freeVariables);

        if (unknowns.isEmpty()) {
            presenter.prepareValuesView(calculateAll(node, functions, masks));
        } else if (countEquals(node) > 1) {
            presenter.prepareFailView("Please use only one \"=\".");
        } else if (!node.isCondition() && SymbolicDerivative.containsDerivative(node)) {
            showDerivative(node, functions);
        } else if (!node.isCondition()) {
            String name = unknowns.get(0);
            presenter.prepareFailView(unknownVariableMessage(name));
        } else if (unknowns.size() > 1) {
            presenter.prepareFailView("This equation has more than one unknown (" + String.join(", ", unknowns)
                    + "). Only one unknown is supported.");
        } else {
            new ConditionAnswerer(presenter, node, unknowns.get(0), functions, masks).answer();
        }
    }

    // d/dx w/ no value for x: write the derivative out
    private void showDerivative(Node node, Map<String, UserFunction> functions) {
        Expr derivative = SymbolicDerivative.differentiate(node, functions);
        List<TextRun> runs = ExprWriter.runs(derivative);
        if (ExprWriter.plain(runs).length() > MAX_ANSWER_LENGTH) {
            throw new UncalculableException(UncalculableException.ANSWER_TOO_LONG);
        }
        presenter.prepareExpressionView(ExprWriter.latex(derivative), runs);
    }

    private Value calculate(Node node, Context context) {
        Value value = node.evaluate(context);
        if (value instanceof NumberValue && Double.isInfinite(((NumberValue) value).getValue())
                && !node.canBeInfinite()) {
            throw new UncalculableException(UncalculableException.TOO_BIG);
        }
        if (value instanceof NumberSetValue) {
            throw new UncalculableException(UncalculableException.SET_ALONE);
        }
        return value;
    }

    private int signMaskCount(MathParser parser) {
        if (parser.getPlusMinusCount() > MAX_PLUS_MINUS) {
            throw new UncalculableException(UncalculableException.MAX_PLUS_MINUS);
        }
        return 1 << parser.getPlusMinusCount();
    }

    // one value per \pm sign choice, dupes dropped
    private List<Value> calculateAll(Node node, Map<String, UserFunction> functions, int masks) {
        List<Value> values = new ArrayList<>();
        for (int mask = 0; mask < masks; mask++) {
            Value value = calculate(node, new Context(functions).withSigns(mask));
            if (node.isApproximate()) {
                value = value.accept(ROUNDER);
            }
            boolean seen = false;
            for (Value existing : values) {
                if (Values.areEqual(existing, value)) {
                    seen = true;
                }
            }
            if (!seen) {
                values.add(value);
            }
        }
        return values;
    }

    private String unknownVariableMessage(String name) {
        return "Unknown variable \"" + name + "\". To solve for it, write an equation such as " + name + "+1=3.";
    }

    private int countEquals(Node node) {
        if (!(node instanceof RelationNode)) {
            return 0;
        }
        return Collections.frequency(((RelationNode) node).getOperators(), "=");
    }
}
