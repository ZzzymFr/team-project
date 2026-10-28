package interface_adapter.evaluate;

import entity.Interval;
import entity.value.BooleanValue;
import entity.value.FiniteSetValue;
import entity.value.NumberValue;
import entity.value.VectorValue;
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
        assertEquals("1/3", EvaluatePresenter.format(1.0 / 3));
        assertEquals("0", EvaluatePresenter.format(Math.sin(Math.PI)));
        assertEquals("∞", EvaluatePresenter.format(1.0 / 0));
        assertEquals("undefined", EvaluatePresenter.format(Double.NaN));
        assertEquals("-∞", EvaluatePresenter.format(-1.0 / 0));
    }

    @Test
    void roundsToTwoDecimalsOrFractions() {
        assertEquals("3.14", EvaluatePresenter.format(Math.PI));
        assertEquals("1.41", EvaluatePresenter.format(Math.sqrt(2)));
        assertEquals("-9.5", EvaluatePresenter.format(-9.5));
        assertEquals("0.25", EvaluatePresenter.format(0.25));
        assertEquals("-5/3", EvaluatePresenter.format(-5.0 / 3));
        assertEquals("22/7", EvaluatePresenter.format(22.0 / 7));
        assertEquals("5/3", EvaluatePresenter.format(1.66666667));
        // ends as a decimal -> just round
        assertEquals("0.13", EvaluatePresenter.format(0.125));
        assertEquals("2.68", EvaluatePresenter.format(2.675));
        assertEquals("1.64", EvaluatePresenter.format(Math.PI * Math.PI / 6));
        // rounding to a plain-looking number keeps digits
        assertEquals("99.999", EvaluatePresenter.format(99.999));
        assertEquals("1.001", EvaluatePresenter.format(1.001));
        assertEquals("2.0031", EvaluatePresenter.format(2.0031415));
        assertEquals("3.01", EvaluatePresenter.format(3.0123));
        // tiny keeps 2 sig figs, not 0
        assertEquals("0.00000001", EvaluatePresenter.format(1e-8));
        assertEquals("-0.0046", EvaluatePresenter.format(-0.004567));
        assertEquals("3333333.33", EvaluatePresenter.format(1e7 / 3));
    }

    @Test
    void neighboursThatRoundTheSameKeepDigits() {
        presenter.prepareSolutionsView("x", Arrays.asList(1.0, 1.001, 3.0));
        assertEquals("x = 1 or x = 1.001 or x = 3", viewModel.getState().getResultText());
        presenter.prepareIntervalsView("x", List.of(new Interval(1, false, 1.0001, false)));
        assertEquals("1 < x < 1.0001", viewModel.getState().getResultText());
        presenter.prepareIntervalsView("x", List.of(new Interval(Double.NEGATIVE_INFINITY, false, 2, false),
                new Interval(2, false, Double.POSITIVE_INFINITY, false)));
        assertEquals("x < 2 or x > 2", viewModel.getState().getResultText());
    }

    @Test
    void showsNumber() {
        presenter.prepareValuesView(List.of(new NumberValue(2)));
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
        presenter.prepareValuesView(List.of(new NumberValue(2)));
        presenter.prepareFailView("Oops");
        assertEquals("", viewModel.getState().getResultText());
        assertEquals("Oops", viewModel.getState().getErrorText());
    }

    @Test
    void formatsValues() {
        assertEquals("true", EvaluatePresenter.format(BooleanValue.of(true)));
        assertEquals("{1, 2, 3}", EvaluatePresenter.format(new FiniteSetValue(Arrays.asList(3.0, 1.0, 2.0, 1.0))));
        assertEquals("{}", EvaluatePresenter.format(new FiniteSetValue(new ArrayList<>())));
        assertEquals("(4, 6)", EvaluatePresenter.format(new VectorValue(Arrays.asList(4.0, 6.0))));
        presenter.prepareValuesView(Arrays.asList(new NumberValue(3), new NumberValue(-1)));
        assertEquals("= 3 or -1", viewModel.getState().getResultText());
        presenter.prepareSolutionsView("x", new ArrayList<>());
        assertEquals("no solution", viewModel.getState().getResultText());
    }

    @Test
    void formatsIntervalsAndExclusions() {
        double inf = Double.POSITIVE_INFINITY;
        presenter.prepareIntervalsView("x", Arrays.asList(new Interval(-inf, false, -2, false), new Interval(2, true, inf, false)));
        assertEquals("x < -2 or x ≥ 2", viewModel.getState().getResultText());
        presenter.prepareIntervalsView("x", Arrays.asList(new Interval(1, false, 3, true), new Interval(5, true, 5, true)));
        assertEquals("1 < x ≤ 3 or x = 5", viewModel.getState().getResultText());
        presenter.prepareIntervalsView("x", Arrays.asList(new Interval(-inf, false, 0, true)));
        assertEquals("x ≤ 0", viewModel.getState().getResultText());
        presenter.prepareIntervalsView("x", new ArrayList<>());
        assertEquals("no solution", viewModel.getState().getResultText());
        presenter.prepareExcludedView("x", Arrays.asList(-2.0, 2.0));
        assertEquals("x ≠ -2 and x ≠ 2", viewModel.getState().getResultText());
    }

    @Test
    void showsBooleansWithoutEqualsSign() {
        presenter.prepareValuesView(List.of(BooleanValue.of(false)));
        assertEquals("false", viewModel.getState().getResultText());
        presenter.prepareAlwaysTrueView("x");
        assertEquals("true for all x", viewModel.getState().getResultText());
        presenter.prepareFunctionDefinedView("f(x) = x^2+1");
        assertEquals("Defined f(x) = x^2+1", viewModel.getState().getResultText());
    }
}
