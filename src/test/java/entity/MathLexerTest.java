package entity;

import org.junit.jupiter.api.Test;

import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class MathLexerTest {
    @Test
    void splitsLatexFraction() {
        assertEquals(Arrays.asList("\\frac", "{", "1", "}", "{", "2", "}", "+", "3.5", "x"),
                MathLexer.tokenize("\\frac{1}{2}+3.5x"));
    }

    @Test
    void turnsEveryMultiplyAndDivideIntoOneSymbol() {
        assertEquals(Arrays.asList("4", "*", "3", "*", "2", "/", "6", "*", "1", "/", "2"),
                MathLexer.tokenize("4\\times3\\cdot2\\div6×1÷2"));
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
