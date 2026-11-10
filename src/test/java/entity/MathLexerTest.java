package entity;

import org.junit.jupiter.api.Test;

import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MathLexerTest {
    @Test
    void splitsLatexFraction() {
        assertEquals(Arrays.asList("\\frac", "{", "1", "}", "{", "2", "}", "+", "3.5", "x"),
                MathLexer.tokenize("\\frac{1}{2}+3.5x"));
    }

    @Test
    void keepsDotAndCrossDistinct() {
        assertEquals(Arrays.asList("4", "\\times", "3", "\\cdot", "2", "/", "6", "\\times", "1", "/", "2", "*", "5"),
                MathLexer.tokenize("4\\times3\\cdot2\\div6×1÷2*5"));
    }

    @Test
    void normalisesAliasesAndUnicode() {
        assertEquals(Arrays.asList("\\leq", "\\geq", "\\neq", "\\approx", "\\pm", "\\infty", "\\rightarrow", "\\Rightarrow"),
                MathLexer.tokenize("\\le ≥ \\ne ≈ ± ∞ \\to \\implies"));
        assertEquals(Arrays.asList("50", "%"), MathLexer.tokenize("50\\%"));
        assertEquals(Arrays.asList("\\int", "\\int", "\\int", "\\ldots", "\\ldots"),
                MathLexer.tokenize("\\iint\\iiint\\oint\\vdots\\ddots"));
        assertEquals(Arrays.asList("\\{", "\\}", "\\cup", "\\{", "\\}"), MathLexer.tokenize("\\emptyset\\cup\\varnothing"));
    }

    @Test
    void greekLettersBecomeLetters() {
        assertEquals(Arrays.asList("α", "+", "Δ", "+", "\\pi"), MathLexer.tokenize("\\alpha+\\Delta+\\pi"));
        assertEquals(Arrays.asList("ϖ", "ε", "ϵ"), MathLexer.tokenize("\\varpi\\varepsilon\\epsilon"));
        assertTrue(MathLexer.isLetter("α"));
        assertTrue(MathLexer.isLetter("?"));
    }

    @Test
    void readsNewSymbols() {
        assertEquals(Arrays.asList("\\{", "1", ",", "2", "\\}", "<", "x", "'", "%", ":", ";", "?"),
                MathLexer.tokenize("\\{1,2\\}<x'%:;?"));
        assertEquals(Arrays.asList("x", "_", "1", ",", "\\ldots", "x"), MathLexer.tokenize("x_1,...x"));
    }

    @Test
    void textLikeCommandsAreUncalculable() {
        for (String s : new String[] {"\\text{Let }A", "\\begin{pmatrix}1\\end{pmatrix}", "\\square"}) {
            UncalculableException e = assertThrows(UncalculableException.class, () -> MathLexer.tokenize(s));
            assertEquals(UncalculableReason.TEXT, e.getReason());
        }
    }

    @Test
    void lineBreakIsNotACalculation() {
        for (String s : new String[] {"\\\\", "1\\\\2", "\\\\{1}", "x+1=3\\\\x=2"}) {
            UncalculableException e = assertThrows(UncalculableException.class, () -> MathLexer.tokenize(s), s);
            assertEquals(UncalculableReason.NOT_CALCULATION, e.getReason());
        }
    }

    @Test
    void spacesBetweenTokensDoNotMatter() {
        assertEquals(MathLexer.tokenize("\\sin(x)+x^{2}+\\frac{1}{2}"),
                MathLexer.tokenize("\\sin ( x ) + x ^ { 2 } + \\frac { 1 } { 2 }"));
    }

    @Test
    void readsPlainFunctionNames() {
        assertEquals(Arrays.asList("\\sin", "(", "x", ")", "+", "\\pi"), MathLexer.tokenize("sin(x) + pi"));
    }

    @Test
    void skipsLeftAndRight() {
        assertEquals(Arrays.asList("(", "1", ")"), MathLexer.tokenize("\\left( 1 \\right)"));
    }

    @Test
    void lettersAreSeparateTokens() {
        assertEquals(Arrays.asList("x", "y"), MathLexer.tokenize("xy"));
    }

    @Test
    void rejectsUnknownCharacters() {
        assertThrows(MathSyntaxException.class, () -> MathLexer.tokenize("1 # 2"));
    }
}
