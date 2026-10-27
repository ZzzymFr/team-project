package use_case.evaluate;

import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTimeout;
import static org.junit.jupiter.api.Assertions.assertTrue;

class QuantifierTest {
    @Test
    void quantifiers() {
        assertEquals("true", Calc.once("\\forall x\\in\\mathbb{R}, x^2\\geq0"));
        assertEquals("false", Calc.once("\\forall x\\in\\mathbb{R}, x^2>0"));
        assertEquals("true", Calc.once("\\exists x\\in\\mathbb{R}, x^2=4"));
        assertEquals("true", Calc.once("\\exists x\\in\\mathbb{R}, x^2=2"));
        assertEquals("false", Calc.once("\\exists x\\in\\mathbb{R}, x^2=-1"));
        assertEquals("false", Calc.once("\\forall x\\in\\mathbb{R}, x^2\\neq2"));
        assertEquals("true", Calc.once("\\forall x\\in\\{1,2,3\\}, x>0"));
        assertEquals("true", Calc.once("\\exists x\\in\\{1,2,3\\}, x>2"));
        assertEquals("true", Calc.once("\\forall n\\in\\mathbb{N}, n\\geq0"));
        assertEquals("false", Calc.once("\\forall n\\in\\mathbb{Z}, n\\geq0"));
        assertEquals("true", Calc.once("\\exists n\\in\\mathbb{N}, n^2=49"));
        assertEquals("true", Calc.once("\\forall x, x+0=x"));
        assertEquals("true", Calc.once("\\forall x\\in\\mathbb{R}: x^2\\geq0"));
        assertEquals("true", Calc.once("\\forall x\\in\\mathbb{R}(x^2\\geq0)"));
        assertEquals("true", Calc.once("\\forall x\\in\\mathbb{R} x^2\\geq0"));
        assertEquals("true", Calc.once("\\neg\\forall x\\in\\mathbb{R}, x^2>0"));
        assertEquals("false", Calc.once("\\forall x\\in\\mathbb{R}, \\frac{1}{x}\\neq0"));
        assertEquals("Uncalculable", Calc.once("\\forall x\\in\\mathbb{R}^{n}"));
        assertEquals("Uncalculable", Calc.once("\\forall v\\in\\mathbb{R}^2, v=v"));
        assertTimeout(Duration.ofSeconds(2), () -> {
            assertEquals("true", Calc.once("\\forall x\\in\\mathbb{R}, \\exists y\\in\\mathbb{R}, y>x"));
            assertEquals("false", Calc.once("\\exists y\\in\\mathbb{R}, \\forall x\\in\\mathbb{R}, y>x"));
        });
    }

    @Test
    void freeVariableOutsideQuantifierIsSolvedFor() {
        String answer = Calc.once("\\forall x\\in\\mathbb{R}, x^2\\geq a");
        assertTrue(answer.equals("a ≤ 0") || answer.equals("Uncalculable"), answer);
    }

    @Test
    void neverClaimsWhatSamplingCannotShow() {
        assertEquals("Uncalculable", Calc.once("\\exists x\\in\\mathbb{Q}, x^2=2"));
        assertEquals("Uncalculable", Calc.once("\\forall x\\in\\mathbb{Q}, x^2\\neq2"));
        assertEquals("true", Calc.once("\\exists x\\in\\mathbb{Q}, x^2=4"));
        assertEquals("Uncalculable", Calc.once("\\exists z\\in\\mathbb{C}, z^2=-1"));
        assertEquals("true", Calc.once("\\exists z\\in\\mathbb{C}, z^2=4"));
        assertEquals("false", Calc.once("\\forall z\\in\\mathbb{C}, z^2>0"));
        assertEquals("Uncalculable", Calc.once("\\frac{1}{0}=1"));
        assertEquals("Uncalculable", Calc.once("\\forall x\\in5, x>0"));
    }

    @Test
    void innerSamplesReachPastOuterValues() {
        assertEquals("true", Calc.once("\\forall x\\in\\mathbb{Z}, \\exists y\\in\\mathbb{Z}, y>x"));
        assertEquals("true", Calc.once("\\forall n\\in\\mathbb{N}, \\exists m\\in\\mathbb{N}, m>n"));
        assertEquals("true", Calc.once("\\forall x\\in\\mathbb{R}, \\exists y\\in\\mathbb{R}, y>2x"));
        assertEquals("true", Calc.once("\\forall x, \\forall y, (x+y)-y=x"));
    }

    @Test
    void plusMinusInQuantifierIsUncalculable() {
        assertEquals("Uncalculable", Calc.once("\\forall x\\in\\mathbb{R}, x^2\\pm1\\geq0"));
        assertEquals("Uncalculable", Calc.once("\\exists x\\in\\{1\\pm1\\}, x=0"));
    }
}
