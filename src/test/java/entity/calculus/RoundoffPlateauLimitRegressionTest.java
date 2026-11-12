package entity.calculus;

import entity.Context;
import entity.MathLexer;
import entity.MathParser;
import entity.node.DerivativeNode;
import entity.node.NoiseScale;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** Regression: a numerically flat asymptotic ratio must not be mistaken for an unknown limit. */
class RoundoffPlateauLimitRegressionTest {
    @Test
    void hyperbolicExponentialRatioApproachesHalfEvenWithRoundoffNoise() {
        // Exercise the exact AST and estimated-noise path used by the calculator,
        // not a hand-simplified Java expression where the issue did not occur.
        var ratio = new MathParser(MathLexer.tokenize("\\frac{\\cosh x}{e^x}"), Map.of()).parse();
        var context = new Context(Map.of());
        double result = LimitCalculator.limit(
                x -> DerivativeNode.numberAt(ratio, context.withVariable("x", x)),
                x -> NoiseScale.noiseOf(ratio, context.withVariable("x", x)),
                Double.POSITIVE_INFINITY, 0);
        assertEquals(0.5, result, 1e-8);
    }
}
