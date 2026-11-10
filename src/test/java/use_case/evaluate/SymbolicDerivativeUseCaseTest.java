package use_case.evaluate;

import data_access.InMemoryFunctionDataAccessObject;
import entity.Interval;
import interface_adapter.TextRun;
import entity.value.Value;
import interface_adapter.MathLabState;
import interface_adapter.MathLabViewModel;
import interface_adapter.evaluate.EvaluatePresenter;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SymbolicDerivativeUseCaseTest {
    private final MathLabViewModel viewModel = new MathLabViewModel();
    private final EvaluateInteractor interactor =
            new EvaluateInteractor(new InMemoryFunctionDataAccessObject(), new EvaluatePresenter(viewModel));

    private String show(String input) {
        interactor.execute(new EvaluateInputData(input));
        MathLabState state = viewModel.getState();
        return state.getErrorText().isEmpty() ? state.getResultText() : state.getErrorText();
    }

    @Test
    void derivativeWithNoValueIsWrittenOut() {
        assertEquals("= 6y", show("\\frac{d}{dy}(3y^{2})="));
        assertEquals("= 2x+1", show("\\frac{d}{dx}(x^2+x)"));
    }

    @Test
    void bracketAfterDdxIsTheBodyForNumbersToo() {
        assertEquals("= 5x", show("\\frac{d}{dx}(x^2)+3x"));
        assertEquals("x = 2", show("\\frac{d}{dx}(x^2)+3x=10"));
        assertEquals("x = 3.5", show("\\frac{d}{dx}(x^2+3x)=10"));
        show("g(x)=\\frac{d}{dx}(x^2)-3x");
        assertEquals("= -2", show("g(2)="));
    }

    @Test
    void tinyDecimalsDontTurnIntoZero() {
        assertEquals("= 0.004", show("\\frac{d}{dx}0.004x"));
        assertEquals("= 0.002x", show("\\frac{d}{dx}0.001x^2"));
        assertEquals("= 0.13x", show("\\frac{d}{dx}0.0625x^2"));
    }

    @Test
    void definedFunctionPrime() {
        show("f(y)=3y^{2}");
        assertEquals("= 6y", show("f'(y)="));
        assertEquals("= 12", show("f'(2)="));
    }

    @Test
    void numericUsesStayTheSame() {
        assertEquals("x = 3", show("\\frac{d}{dx}x^2=6"));
        assertTrue(show("x+1").startsWith("Unknown variable \"x\""));
        assertTrue(show("\\frac{d}{dx}(\\tan^{-1}x)").startsWith("Uncalculable — tan⁻¹ is ambiguous"));
    }

    @Test
    void runsComeWithTheAnswer() {
        show("\\frac{d}{dx}(x^3)");
        List<TextRun> runs = viewModel.getState().getAnswerRuns();
        assertNotNull(runs);
        assertEquals("= 3x2", TextRun.plain(runs));
        assertTrue(runs.get(runs.size() - 1).isRaised());
    }

    @Test
    void numericAfterSymbolicHasNoRuns() {
        show("\\frac{d}{dx}(x^3)");
        show("1+1");
        assertNull(viewModel.getState().getAnswerRuns());
        show("\\frac{d}{dx}(x^3)");
        show("\\frac{d}{dx}|x|");
        assertNull(viewModel.getState().getAnswerRuns());
    }

    @Test
    void newFunctionsAreWrittenOut() {
        assertEquals("= \\frac{1}{x^{2}+1}", show("\\frac{d}{dx}\\arctan x"));
        assertEquals("= \\frac{1}{\\sqrt{-x^{2}+1}}", show("\\frac{d}{dx}\\arcsin x"));
        assertEquals("= -\\frac{1}{\\sqrt{-x^{2}+1}}", show("\\frac{d}{dx}\\arccos x"));
        assertEquals("= \\cosh x", show("\\frac{d}{dx}\\sinh x"));
        assertEquals("= \\sinh x", show("\\frac{d}{dx}\\cosh x"));
        assertEquals("= \\frac{1}{(\\cosh x)^{2}}", show("\\frac{d}{dx}\\tanh x"));
        assertEquals(show("\\frac{d}{dx}e^{2x}"), show("\\frac{d}{dx}\\exp(2x)"));
        assertEquals("= -\\frac{1}{(\\sin x)^{2}}", show("\\frac{d}{dx}\\cot x"));
        assertEquals("= \\frac{\\sin x}{(\\cos x)^{2}}", show("\\frac{d}{dx}\\sec x"));
        assertEquals("= -\\frac{\\cos x}{(\\sin x)^{2}}", show("\\frac{d}{dx}\\csc x"));
        assertEquals("= 2x\\cosh(x^{2})", show("\\frac{d}{dx}\\sinh(x^2)"));
    }

    @Test
    void newFunctionsOfZeroFoldWhenExact() {
        assertEquals("= 1", show("\\frac{d}{dx}x\\cosh 0"));
        assertEquals("= 1", show("\\frac{d}{dx}x\\sec 0"));
        assertEquals("= 1", show("\\frac{d}{dx}x\\cosh(x-x)"));
        assertEquals("= 1", show("\\frac{d}{dx}x\\sec(x-x)"));
        assertEquals("= \\arccos 0", show("\\frac{d}{dx}x\\arccos(x-x)"));
    }

    @Test
    void oldFunctionsAreWrittenTheSame() {
        assertEquals("= 2x\\cos(x^{2})", show("\\frac{d}{dx}\\sin(x^2)"));
        assertEquals("= \\frac{1}{(\\cos x)^{2}}", show("\\frac{d}{dx}\\tan x"));
        assertEquals("= \\frac{1}{x\\ln 2}", show("\\frac{d}{dx}\\log_2 x"));
        assertEquals("= \\frac{1}{2\\sqrt{x}}", show("\\frac{d}{dx}\\sqrt{x}"));
    }

    @Test
    void failuresGiveAReason() {
        assertEquals("Uncalculable — Can't differentiate |x|", show("\\frac{d}{dx}|x|"));
        assertEquals("Uncalculable — Answer too long", show("\\frac{d}{dx}(\\frac{\\sin x}{\\ln x+x^x})"));
    }

    @Test
    void constantWithNoValueIsUndefinedNotWrittenOut() {
        assertEquals("Uncalculable — Has an undefined part", show("\\frac{d}{dx}x\\ln 0"));
        assertEquals("Uncalculable — Has an undefined part", show("\\frac{d}{dx}x\\cot 0"));
        assertEquals("Uncalculable — Has an undefined part", show("\\frac{d}{dx}x\\sqrt{-1}"));
        assertEquals("= \\sin 2", show("\\frac{d}{dx}x\\sin 2"));
        assertEquals("= \\ln 2", show("\\frac{d}{dx}x\\ln 2"));
    }
}
