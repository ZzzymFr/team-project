package entity.symbolic;

import entity.Context;
import entity.UserFunction;
import entity.node.DerivativeNode;
import entity.node.Node;
import entity.node.PointDerivative;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.WeakHashMap;

// d/dx from the written-out formula, worked out once per body then just evaluated
public final class ExactDerivative implements PointDerivative {
    // past this many parts ridders is cheaper
    private static final int MAX_SIZE = 3000;
    // how close the body has to be defined on both sides
    private static final double NEAR = 1e-9;
    // same cut as DerivativeCalculator
    private static final double MAX_NOISE = 1e-6;

    private final Map<Node, Map<String, Formula>> formulas = Collections.synchronizedMap(new WeakHashMap<>());

    @Override
    public double[] at(Node body, String variable, int order, Context context) {
        double x = context.getVariable(variable);
        Expr formula = formula(body, variable, order, context);
        if (formula == null || !Double.isFinite(x)) {
            return null;
        }
        double step = NEAR * (x == 0 ? 1 : Math.abs(x));
        double here = DerivativeNode.numberAt(body, context);
        double below = DerivativeNode.numberAt(body, context.withVariable(variable, x - step));
        double above = DerivativeNode.numberAt(body, context.withVariable(variable, x + step));
        if (Double.isNaN(here) || Double.isNaN(below) || Double.isNaN(above)) {
            return new double[] {Double.NaN, 0};
        }
        if (Double.isInfinite(here) || Double.isInfinite(below) || Double.isInfinite(above)) {
            return null;
        }
        double[] value = ExprValue.withNoise(formula, context);
        // too far out, x rounding eats the digits, ridders says so
        if (Double.isInfinite(value[0]) || value[1] > MAX_NOISE * Math.max(1, Math.abs(value[0]))) {
            return null;
        }
        return new double[] {Math.abs(value[0]) <= value[1] ? 0 : value[0], 0};
    }

    @Override
    public boolean isExact(Node body, String variable, int order, Context context) {
        return formula(body, variable, order, context) != null;
    }

    // null = cant write it out, numbers have to do it
    private Expr formula(Node body, String variable, int order, Context context) {
        String key = order + variable;
        Map<String, Formula> known = formulas.computeIfAbsent(body, k -> new HashMap<>());
        synchronized (known) {
            Formula formula = known.get(key);
            if (formula != null && formula.isCurrent(context)) {
                return formula.expr;
            }
            // f(x)=..f'(x).. comes back here while its being worked out
            known.put(key, new Formula(null, Collections.emptyMap()));
        }
        Formula fresh = derive(body, variable, order, context);
        synchronized (known) {
            known.put(key, fresh);
        }
        return fresh.expr;
    }

    private static Formula derive(Node body, String variable, int order, Context context) {
        SeenFunctions functions = new SeenFunctions(context);
        Expr expr;
        try {
            expr = SymbolicDerivative.partial(body, variable, order, functions);
        } catch (RuntimeException e) {
            expr = null;  // |x|, sums w/ x, n! etc
        }
        if (expr != null && (!ExprValue.canEvaluate(expr) || size(expr, MAX_SIZE) > MAX_SIZE)) {
            expr = null;
        }
        return new Formula(expr, functions.snapshot());
    }

    // stops counting once its past the limit
    private static int size(Expr e, int limit) {
        int total = 1;
        if (e.terms() != null) {
            for (Expr t : e.terms()) {
                total += size(t, limit - total);
                if (total > limit) {
                    return total;
                }
            }
        }
        if (e.base() != null) {
            total += size(e.base(), limit - total);
        }
        if (e.exponent() != null && total <= limit) {
            total += size(e.exponent(), limit - total);
        }
        return total;
    }

    private static final class Formula {
        private final Expr expr;
        private final Map<String, UserFunction> used;

        private Formula(Expr expr, Map<String, UserFunction> used) {
            this.expr = expr;
            this.used = used;
        }

        // same definitions as when it was worked out
        private boolean isCurrent(Context context) {
            for (Map.Entry<String, UserFunction> entry : used.entrySet()) {
                if (context.getFunction(entry.getKey()) != entry.getValue()) {
                    return false;
                }
            }
            return true;
        }
    }
}
