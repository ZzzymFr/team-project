package pdf_editor.domain;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class FormulaInputTest {
    @Test void latexInputIsNeverModified() {
        assertEquals("\\frac{1}{2}+\\sin x", FormulaInput.asLatex("\\frac{1}{2}+\\sin x", false));
    }
    @Test void normalKeyboardTrigIsRenderableLatex() {
        assertEquals("\\sin(x)+\\cos(x)+\\tan(x)", FormulaInput.asLatex("sin(x)+cos(x)+tan(x)", true));
    }
    @Test void inverseTrigAndCotCscSecConvertIndependently() {
        assertEquals("\\arcsin(x)+\\arccos(x)+\\arctan(x)+\\cot(x)+\\csc(x)+\\sec(x)",
                FormulaInput.asLatex("arcsin(x)+arccos(x)+arctan(x)+cot(x)+csc(x)+sec(x)", true));
    }
    @Test void plainPiAndUnicodePiAreSupported() {
        assertTrue(FormulaInput.asLatex("pi+π", true).contains("\\pi"));
    }
    @Test void longerIdentifiersAreNotPartiallyEscaped() {
        assertEquals("sinewave+cost+print", FormulaInput.asLatex("sinewave+cost+print", true));
    }
    @Test void existingLatexCommandsAreNotDoubleEscaped() {
        assertEquals("\\sin(x)+\\cot(x)", FormulaInput.asLatex("\\sin(x)+cot(x)", true));
    }
    @Test void nullInputRejected() {
        assertThrows(NullPointerException.class, () -> FormulaInput.asLatex(null, true));
    }
}
