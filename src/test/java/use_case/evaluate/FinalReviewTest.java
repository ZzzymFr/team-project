package use_case.evaluate;

import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTimeout;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FinalReviewTest {
    private static final String UNCALCULABLE = "Uncalculable";

    // right answer or uncalc, never wrong
    private static void correctOrUncalculable(String correct, String input) {
        String answer = assertTimeout(Duration.ofSeconds(2), () -> Calc.once(input));
        assertTrue(answer.equals(correct) || answer.equals(UNCALCULABLE), input + " -> " + answer);
    }

    private static String quick(String input) {
        return assertTimeout(Duration.ofSeconds(2), () -> Calc.once(input));
    }

    @Test
    void uncalculablePointsAreNotFalse() {
        correctOrUncalculable("true", "\\forall a, \\sum_{n=1}^{\\infty}\\frac{1}{n\\ln(n+1)}>0");
        correctOrUncalculable("true", "\\forall a\\in\\{1\\}, \\lim_{x\\to\\infty}(\\ln x-10^{12})\\neq5");
        correctOrUncalculable("true for all x", "\\lim_{t\\to\\infty}(\\ln t-10^{12})+x>0");
        correctOrUncalculable("true for all x", "\\lim_{t\\to\\infty}(\\ln t-10^{12})+x\\neq0");
        assertEquals(UNCALCULABLE, quick("\\forall x, (x,1)+(1,2,3)\\neq(0,0)"));
        assertEquals(UNCALCULABLE, quick("\\exists x, (x,1)+(1,2,3)\\neq(0,0)"));
        assertEquals(UNCALCULABLE, quick("\\forall x, \\{x\\}+1\\neq2"));
        assertEquals(UNCALCULABLE, quick("(x,1)+(1,2,3)\\neq(0,0)"));
    }

    @Test
    void undefinedPointsStillFail() {
        assertEquals("false", quick("\\forall x\\in\\mathbb{R}, \\frac{1}{x}\\neq0"));
        assertEquals("x > 0", quick("\\frac{1}{x}>0"));
        assertEquals("x < 0 or x > 0", quick("\\frac{1}{x}\\neq0"));
        assertEquals("x ≥ 0", quick("\\sqrt{x}\\geq0"));
        assertEquals("n = 10", quick("\\sum_{i=1}^{n}i=55"));
    }
}
