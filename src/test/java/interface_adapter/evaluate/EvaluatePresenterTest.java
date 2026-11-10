package interface_adapter.evaluate;

import entity.Interval;
import entity.value.BooleanValue;
import entity.value.FiniteSetValue;
import entity.value.NumberValue;
import entity.value.VectorValue;
import interface_adapter.MathLabViewModel;
import interface_adapter.TextRun;
import use_case.evaluate.FormulaShape;
import use_case.evaluate.OutputDataMapper;
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
        assertEquals("0.3", AnswerFormatter.format(0.1 + 0.2));
        assertEquals("2", AnswerFormatter.format(2.0));
        assertEquals("-100", AnswerFormatter.format(-100));
        assertEquals("1/3", AnswerFormatter.format(1.0 / 3));
        // sin(pi) noise is made 0 in the use case now
        assertEquals("0", AnswerFormatter.format(0.0));
        assertEquals("∞", AnswerFormatter.format(1.0 / 0));
        assertEquals("undefined", AnswerFormatter.format(Double.NaN));
        assertEquals("-∞", AnswerFormatter.format(-1.0 / 0));
    }

    @Test
    void roundsToTwoDecimalsOrFractions() {
        assertEquals("3.14", AnswerFormatter.format(Math.PI));
        assertEquals("1.41", AnswerFormatter.format(Math.sqrt(2)));
        assertEquals("-9.5", AnswerFormatter.format(-9.5));
        assertEquals("0.25", AnswerFormatter.format(0.25));
        assertEquals("-5/3", AnswerFormatter.format(-5.0 / 3));
        assertEquals("22/7", AnswerFormatter.format(22.0 / 7));
        assertEquals("5/3", AnswerFormatter.format(1.66666667));
        // ends as a decimal -> just round
        assertEquals("0.13", AnswerFormatter.format(0.125));
        assertEquals("2.68", AnswerFormatter.format(2.675));
        assertEquals("1.64", AnswerFormatter.format(Math.PI * Math.PI / 6));
        // rounding to a plain-looking number keeps digits
        assertEquals("99.999", AnswerFormatter.format(99.999));
        assertEquals("1.001", AnswerFormatter.format(1.001));
        assertEquals("2.0031", AnswerFormatter.format(2.0031415));
        assertEquals("3.01", AnswerFormatter.format(3.0123));
        // tiny keeps 2 sig figs, not 0
        assertEquals("0.00000001", AnswerFormatter.format(1e-8));
        assertEquals("-0.0046", AnswerFormatter.format(-0.004567));
        assertEquals("3333333.33", AnswerFormatter.format(1e7 / 3));
    }

    @Test
    void tinyOnesDropTrailingZero() {
        assertEquals("0.00000006", AnswerFormatter.format(5.99999e-8));
        assertEquals("0.001", AnswerFormatter.format(0.000999501));
        assertEquals("-0.000001", AnswerFormatter.format(-0.0000009999));
    }

    @Test
    void bigNumbersKeepTheirDigits() {
        assertEquals("123456123456", AnswerFormatter.format(123456.0 * 1000001));
        assertEquals("1099511627776", AnswerFormatter.format(Math.pow(2, 40)));
        assertEquals("12345678901.5", AnswerFormatter.format(12345678901.5));
        assertEquals("30000000000", AnswerFormatter.format((0.1 + 0.2) * 1e11));
        assertEquals("-999999999999999", AnswerFormatter.format(-999999999999999.0));
    }

    @Test
    void hugeNumbersGoScientific() {
        assertEquals("2.432902008×10^18", AnswerFormatter.format(2432902008176640000.0));
        assertEquals("1×10^300", AnswerFormatter.format(1e300));
        assertEquals("-5×10^20", AnswerFormatter.format(-5e20));
    }

    @Test
    void powerOfTenIsRaisedOnTheCanvas() {
        presenter.prepareFormulaShape(FormulaShape.ENDS_WITH_EQUALS);
        presenter.prepareValuesView(List.of(OutputDataMapper.of(new NumberValue(-5e20))));
        assertEquals("= -5×10^20", viewModel.getState().getResultText());
        assertEquals(List.of(new TextRun("−5×10", false), new TextRun("20", true)),
                viewModel.getState().getAnswerRuns());
        presenter.prepareValuesView(List.of(OutputDataMapper.of(new NumberValue(7))));
        assertEquals(null, viewModel.getState().getAnswerRuns());
    }

    @Test
    void reallyTinyGoesScientific() {
        assertEquals("1×10^-13", AnswerFormatter.format(1e-13));
        assertEquals("-9.4×10^-14", AnswerFormatter.format(-Math.exp(-30)));
        assertEquals("0.000000000001", AnswerFormatter.format(1e-12));
        presenter.prepareFormulaShape(FormulaShape.ENDS_WITH_EQUALS);
        presenter.prepareValuesView(List.of(OutputDataMapper.of(new NumberValue(-1e-13))));
        assertEquals("= -1×10^-13", viewModel.getState().getResultText());
        assertEquals(List.of(new TextRun("−1×10", false), new TextRun("−13", true)),
                viewModel.getState().getAnswerRuns());
        presenter.prepareFormulaShape(FormulaShape.PLAIN);
        presenter.prepareValuesView(List.of(OutputDataMapper.of(new NumberValue(3e-14))));
        assertEquals(List.of(new TextRun("= 3×10", false), new TextRun("−14", true)),
                viewModel.getState().getAnswerRuns());
    }

    @Test
    void neighboursThatRoundTheSameKeepDigits() {
        presenter.prepareSolutionsView("x", Arrays.asList(1.0, 1.001, 3.0));
        assertEquals("x = 1 or x = 1.001 or x = 3", viewModel.getState().getResultText());
        presenter.prepareIntervalsView("x", List.of(OutputDataMapper.of(new Interval(1, false, 1.0001, false))));
        assertEquals("1 < x < 1.0001", viewModel.getState().getResultText());
        presenter.prepareIntervalsView("x", List.of(OutputDataMapper.of(new Interval(Double.NEGATIVE_INFINITY, false, 2, false)),
                OutputDataMapper.of(new Interval(2, false, Double.POSITIVE_INFINITY, false))));
        assertEquals("x < 2 or x > 2", viewModel.getState().getResultText());
    }

    @Test
    void showsNumber() {
        presenter.prepareValuesView(List.of(OutputDataMapper.of(new NumberValue(2))));
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
        presenter.prepareValuesView(List.of(OutputDataMapper.of(new NumberValue(2))));
        presenter.prepareFailView("Oops");
        assertEquals("", viewModel.getState().getResultText());
        assertEquals("Oops", viewModel.getState().getErrorText());
    }

    @Test
    void formatsValues() {
        assertEquals("true", AnswerFormatter.format(OutputDataMapper.of(BooleanValue.of(true))));
        assertEquals("{1, 2, 3}", AnswerFormatter.format(OutputDataMapper.of(new FiniteSetValue(Arrays.asList(3.0, 1.0, 2.0, 1.0)))));
        assertEquals("{}", AnswerFormatter.format(OutputDataMapper.of(new FiniteSetValue(new ArrayList<>()))));
        assertEquals("(4, 6)", AnswerFormatter.format(OutputDataMapper.of(new VectorValue(Arrays.asList(4.0, 6.0)))));
        presenter.prepareValuesView(Arrays.asList(OutputDataMapper.of(new NumberValue(3)), OutputDataMapper.of(new NumberValue(-1))));
        assertEquals("= 3 or -1", viewModel.getState().getResultText());
        presenter.prepareSolutionsView("x", new ArrayList<>());
        assertEquals("no solution", viewModel.getState().getResultText());
    }

    @Test
    void formatsIntervalsAndExclusions() {
        double inf = Double.POSITIVE_INFINITY;
        presenter.prepareIntervalsView("x", Arrays.asList(OutputDataMapper.of(new Interval(-inf, false, -2, false)), OutputDataMapper.of(new Interval(2, true, inf, false))));
        assertEquals("x < -2 or x ≥ 2", viewModel.getState().getResultText());
        presenter.prepareIntervalsView("x", Arrays.asList(OutputDataMapper.of(new Interval(1, false, 3, true)), OutputDataMapper.of(new Interval(5, true, 5, true))));
        assertEquals("1 < x ≤ 3 or x = 5", viewModel.getState().getResultText());
        presenter.prepareIntervalsView("x", Arrays.asList(OutputDataMapper.of(new Interval(-inf, false, 0, true))));
        assertEquals("x ≤ 0", viewModel.getState().getResultText());
        presenter.prepareIntervalsView("x", new ArrayList<>());
        assertEquals("no solution", viewModel.getState().getResultText());
        presenter.prepareExcludedView("x", Arrays.asList(-2.0, 2.0));
        assertEquals("x ≠ -2 and x ≠ 2", viewModel.getState().getResultText());
    }

    @Test
    void showsBooleansWithoutEqualsSign() {
        presenter.prepareValuesView(List.of(OutputDataMapper.of(BooleanValue.of(false))));
        assertEquals("false", viewModel.getState().getResultText());
        presenter.prepareAlwaysTrueView("x");
        assertEquals("true for all x", viewModel.getState().getResultText());
        presenter.prepareFunctionDefinedView("f(x) = x^2+1");
        assertEquals("Defined f(x) = x^2+1", viewModel.getState().getResultText());
    }
}
