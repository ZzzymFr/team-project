package interface_adapter.evaluate;

import entity.EquationSolver;
import interface_adapter.MathLabState;
import interface_adapter.MathLabViewModel;
import use_case.evaluate.EvaluateOutputBoundary;

import java.math.BigDecimal;
import java.math.MathContext;
import java.util.List;

public class EvaluatePresenter implements EvaluateOutputBoundary {
    private static final int MAX_SOLUTIONS_SHOWN = 10;

    private final MathLabViewModel viewModel;

    public EvaluatePresenter(MathLabViewModel viewModel) {
        this.viewModel = viewModel;
    }

    @Override
    public void prepareNumberView(double value) {
        showResult("= " + format(value));
    }

    @Override
    public void prepareCheckView(boolean isCorrect, double leftValue, double rightValue) {
        if (isCorrect) {
            showResult("Correct: both sides are " + format(leftValue));
        } else {
            showResult("Not equal: the left side is " + format(leftValue)
                    + ", the right side is " + format(rightValue));
        }
    }

    @Override
    public void prepareSolutionsView(String variable, List<Double> solutions) {
        if (solutions.isEmpty()) {
            showResult("No solution found for " + variable + " between "
                    + format(EquationSolver.SEARCH_START) + " and " + format(EquationSolver.SEARCH_END));
            return;
        }
        String text = "";
        for (int i = 0; i < solutions.size() && i < MAX_SOLUTIONS_SHOWN; i++) {
            if (i > 0) {
                text = text + " or ";
            }
            text = text + variable + " = " + format(solutions.get(i));
        }
        if (solutions.size() > MAX_SOLUTIONS_SHOWN) {
            text = text + " (and " + (solutions.size() - MAX_SOLUTIONS_SHOWN) + " more)";
        }
        showResult(text);
    }

    @Override
    public void prepareAlwaysTrueView(String variable) {
        showResult("This is true for every value of " + variable);
    }

    @Override
    public void prepareFunctionDefinedView(String definition) {
        showResult("Defined " + definition);
    }

    @Override
    public void prepareFailView(String errorMessage) {
        MathLabState state = viewModel.getState();
        state.setResultText("");
        state.setErrorText(errorMessage);
        viewModel.firePropertyChanged(MathLabViewModel.RESULT);
    }

    private void showResult(String text) {
        MathLabState state = viewModel.getState();
        state.setResultText(text);
        state.setErrorText("");
        viewModel.firePropertyChanged(MathLabViewModel.RESULT);
    }

    static String format(double value) {
        if (Double.isNaN(value) || Double.isInfinite(value)) {
            return "undefined";
        }
        if (Math.abs(value) < 1e-12) {
            return "0";
        }
        BigDecimal rounded = new BigDecimal(value).round(new MathContext(10));
        return rounded.stripTrailingZeros().toPlainString();
    }
}
