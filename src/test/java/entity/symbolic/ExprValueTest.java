package entity.symbolic;

import entity.Context;
import entity.MathLexer;
import entity.MathParser;
import entity.node.Node;
import entity.value.NumberValue;
import org.junit.jupiter.api.Test;

import java.util.HashMap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ExprValueTest {
    private Node parse(String latex) {
        return new MathParser(MathLexer.tokenize(latex), new HashMap<>()).parse();
    }

    private Context at(double x) {
        return new Context(new HashMap<>()).withVariable("x", x).withVariable("y", 3);
    }

    // the written d/dx at x
    private double d(String body, double x) {
        return ExprValue.of(SymbolicDerivative.partial(parse(body), "x", 1, new HashMap<>()), at(x));
    }

    // Expr of the body vs the body node itself
    private void sameAsNode(String body, double x) {
        Expr e = SymbolicDerivative.partial(parse(body), "x", 0, new HashMap<>());
        double node = ((NumberValue) parse(body).evaluate(at(x))).getValue();
        assertEquals(node, ExprValue.of(e, at(x)), 1e-12, body);
    }

    @Test
    void valuesOfDerivatives() {
        assertEquals(6, d("x^2y", 1), 0);
        assertEquals(1.0 / 12, d("x^{1/3}", -8), 1e-15);
        assertEquals(1 / (4 * Math.log(2)), d("\\log_{2}x", 4), 1e-15);
        assertEquals(1 / (4 * Math.log(10)), d("\\log x", 4), 1e-15);
        assertEquals(Math.cos(2), d("\\sin x", 2), 0);
        assertEquals(1 / Math.cosh(0.5) / Math.cosh(0.5), d("\\tanh x", 0.5), 1e-15);
    }

    @Test
    void sameRulesAsTheNodes() {
        sameAsNode("x^{1/3}", -8);
        sameAsNode("(-8)^{1/3}", 1);
        sameAsNode("x^{2/3}", -27);
        sameAsNode("\\sqrt[3]{x}", -8);
        sameAsNode("x^{-1}", 0);
        sameAsNode("\\ln x", -1);
        sameAsNode("\\tan x", Math.PI / 2);
        sameAsNode("\\cot x", 0);
        sameAsNode("x^y", -2);
        sameAsNode("\\arccos x", 0.3);
    }

    @Test
    void zeroToANegativePowerIsUndefined() {
        assertTrue(Double.isNaN(d("\\sqrt{x}", 0)));
        assertTrue(Double.isNaN(d("x^{1/3}", 0)));
    }

    @Test
    void onlyKnownParts() {
        assertTrue(ExprValue.canEvaluate(Expr.fn("cosh", Expr.var("x"))));
        assertFalse(ExprValue.canEvaluate(Expr.fn("nope", Expr.var("x"))));
        assertFalse(ExprValue.canEvaluate(Expr.add(Expr.var("x"), Expr.dvar("y", "x"))));
    }
}
