package entity;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class EquationDisplayTest {
    @Test void trailingEqualsAndLeadingEqualsProduceOneSign() { assertEquals("1+1 = 2", EquationDisplay.equation("1+1=", "=2")); }
    @Test void noEqualsInEitherInputStillProducesOneSign() { assertEquals("1+1 = 2", EquationDisplay.equation("1+1", "2")); }
    @Test void repeatedEqualsAreNormalized() { assertEquals("1+1 = 2", EquationDisplay.equation("1+1==", "===2")); }
    @Test void whitespaceIsNormalized() { assertEquals("x = y", EquationDisplay.equation(" x = ", "  = y ")); }
    @Test void equationWithinFormulaIsPreserved() { assertEquals("x=1 = true", EquationDisplay.equation("x=1", "true")); }
    @Test void emptyResultPreservesOriginalEquation() { assertEquals("x=1", EquationDisplay.equation("x=1", "")); }
    @Test void nullInputsAreSafe() { assertEquals("", EquationDisplay.equation(null, null)); }
    @Test void resultOnlyIsDisplayedOnce() { assertEquals("= 2", EquationDisplay.equation("", "=2")); }
    @Test void latexEqualityCommandIsNotAccidentallyRemoved() { assertEquals("x\\\\leq y = true", EquationDisplay.equation("x\\\\leq y", "true")); }
    @Test void solvedEquationUsesImplicationRatherThanAnotherEquality() {
        assertEquals("3x=6 ⇒ x=3", EquationDisplay.equation("3x=6", "x=3"));
        assertEquals("3x=6 ⇒ x=2", EquationDisplay.equation("3x=6=", "=x=2"));
        assertEquals("\\frac{x}{2}=3 ⇒ x=6", EquationDisplay.equation("\\frac{x}{2}=3", "x=6"));
    }
    @Test void arithmeticStillUsesOneEqualitySign() {
        assertEquals("1+1 = 2", EquationDisplay.equation("1+1=", "=2"));
        assertEquals("x=1 = true", EquationDisplay.equation("x=1", "true"));
        assertEquals("y=x", EquationDisplay.equation("y=x", ""));
    }
    @Test void separatorAgreesWithTransformationPredicate() {
        assertTrue(EquationDisplay.isTransformation("3x=6", "x=2"));
        assertFalse(EquationDisplay.isTransformation("y=x", ""));
        assertFalse(EquationDisplay.isTransformation("1+1=", "2"));
        assertEquals(" ⇒ ", EquationDisplay.separator("3x=6", "x=2"));
        assertEquals("\\Rightarrow", EquationDisplay.latexSeparator("3x=6", "x=2"));
        assertEquals("=", EquationDisplay.latexSeparator("1+1", "2"));
    }
}
