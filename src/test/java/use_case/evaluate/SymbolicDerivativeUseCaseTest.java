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
    void failuresGiveAReason() {
        assertEquals("Uncalculable — Can't differentiate |x|", show("\\frac{d}{dx}|x|"));
        assertEquals("Uncalculable — Answer too long", show("\\frac{d}{dx}(\\frac{\\sin x}{\\ln x+x^x})"));
    }
}
