package interface_adapter.evaluate;

import interface_adapter.MathLabViewModel;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class EvaluatePresenterTest {
    private MathLabViewModel viewModel;
    private EvaluatePresenter presenter;

    @BeforeEach
    void setUp() {
        viewModel = new MathLabViewModel();
        presenter = new EvaluatePresenter(viewModel);
    }

    @Test
    void formatsNumbers() {
        assertEquals("0.3", EvaluatePresenter.format(0.1 + 0.2));
        assertEquals("2", EvaluatePresenter.format(2.0));
        assertEquals("-100", EvaluatePresenter.format(-100));
        assertEquals("0.3333333333", EvaluatePresenter.format(1.0 / 3));
        assertEquals("0", EvaluatePresenter.format(Math.sin(Math.PI)));
        assertEquals("undefined", EvaluatePresenter.format(1.0 / 0));
    }

    @Test
    void showsNumber() {
        presenter.prepareNumberView(2);
        assertEquals("= 2", viewModel.getState().getResultText());
        assertEquals("", viewModel.getState().getErrorText());
    }

    @Test
    void showsSolutions() {
        presenter.prepareSolutionsView("x", Arrays.asList(-2.0000000001, 1.9999999999));
        assertEquals("x = -2 or x = 2", viewModel.getState().getResultText());
    }

    @Test
    void showsAtMostTenSolutions() {
        List<Double> solutions = new ArrayList<>();
        for (int i = 0; i < 12; i++) {
            solutions.add((double) i);
        }
        presenter.prepareSolutionsView("x", solutions);
        assertEquals("x = 0 or x = 1 or x = 2 or x = 3 or x = 4 or x = 5 or x = 6 or x = 7 or x = 8 or x = 9"
                + " (and 2 more)", viewModel.getState().getResultText());
    }

    @Test
    void showsError() {
        presenter.prepareNumberView(2);
        presenter.prepareFailView("Oops");
        assertEquals("", viewModel.getState().getResultText());
        assertEquals("Oops", viewModel.getState().getErrorText());
    }
}
