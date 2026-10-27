package entity;

import java.util.function.DoubleUnaryOperator;

/**
 * A real-valued function of one variable x, together with the LaTeX it came from.
 */
public class MathFunction {
    private final String latex;
    private final DoubleUnaryOperator rule;

    public MathFunction(String latex, DoubleUnaryOperator rule) {
        this.latex = latex;
        this.rule = rule;
    }

    public String getLatex() {
        return latex;
    }

    /**
     * Evaluates the function at x. Returns NaN (or an infinity) where the function is undefined.
     */
    public double evaluate(double x) {
        return rule.applyAsDouble(x);
    }
}
