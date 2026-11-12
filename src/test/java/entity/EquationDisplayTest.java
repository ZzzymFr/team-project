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
}
