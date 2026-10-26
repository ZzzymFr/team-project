package interface_adapter.evaluate;

import entity.Interval;
import entity.value.BooleanValue;
import entity.value.FiniteSetValue;
import entity.value.NumberValue;
import entity.value.Value;
import entity.value.VectorValue;
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
    public void prepareValuesView(List<Value> values) {
        String text = "";
        boolean onlyBooleans = true;
        for (int i = 0; i < values.size(); i++) {
            if (i > 0) {
                text = text + " or ";
            }
            text = text + format(values.get(i));
            if (!(values.get(i) instanceof BooleanValue)) {
                onlyBooleans = false;
            }
        }
        if (onlyBooleans) {
            showResult(text);
        } else {
            showResult("= " + text);
        }
    }

    @Override
    public void prepareSolutionsView(String variable, List<Double> solutions) {
        if (solutions.isEmpty()) {
            showResult("no solution");
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
    public void prepareIntervalsView(String variable, List<Interval> intervals) {
        if (intervals.isEmpty()) {
            showResult("no solution");
            return;
        }
        String text = "";
        for (int i = 0; i < intervals.size(); i++) {
            if (i > 0) {
                text = text + " or ";
            }
            text = text + formatInterval(variable, intervals.get(i));
        }
        showResult(text);
    }

    @Override
    public void prepareExcludedView(String variable, List<Double> excluded) {
        if (excluded.isEmpty()) {
            prepareAlwaysTrueView(variable);
            return;
        }
        String text = "";
        for (int i = 0; i < excluded.size(); i++) {
            if (i > 0) {
                text = text + " and ";
            }
            text = text + variable + " ≠ " + format(excluded.get(i));
        }
        showResult(text);
    }

    @Override
    public void prepareAlwaysTrueView(String variable) {
        showResult("true for all " + variable);
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

    public static String format(double value) {
        if (Double.isNaN(value)) {
            return "undefined";
        }
        if (value == Double.POSITIVE_INFINITY) {
            return "∞";
        }
        if (value == Double.NEGATIVE_INFINITY) {
            return "-∞";
        }
        if (Math.abs(value) < 1e-12) {
            return "0";
        }
        BigDecimal rounded = new BigDecimal(value).round(new MathContext(10));
        return rounded.stripTrailingZeros().toPlainString();
    }

    public static String format(Value value) {
        if (value instanceof NumberValue) {
            return format(((NumberValue) value).getValue());
        }
        if (value instanceof BooleanValue) {
            return ((BooleanValue) value).getValue() ? "true" : "false";
        }
        if (value instanceof FiniteSetValue) {
            return "{" + formatList(((FiniteSetValue) value).getElements()) + "}";
        }
        if (value instanceof VectorValue) {
            return "(" + formatList(((VectorValue) value).getComponents()) + ")";
        }
        return "Uncalculable";
    }

    private static String formatList(List<Double> numbers) {
        String text = "";
        for (int i = 0; i < numbers.size(); i++) {
            if (i > 0) {
                text = text + ", ";
            }
            text = text + format(numbers.get(i));
        }
        return text;
    }

    private static String formatInterval(String variable, Interval interval) {
        double low = interval.getLow();
        double high = interval.getHigh();
        String lowSign = interval.isLowIncluded() ? " ≤ " : " < ";
        String highSign = interval.isHighIncluded() ? " ≤ " : " < ";
        boolean lowBounded = low != Double.NEGATIVE_INFINITY;
        boolean highBounded = high != Double.POSITIVE_INFINITY;
        if (!lowBounded && !highBounded) {
            return "true for all " + variable;
        }
        if (!lowBounded) {
            return variable + highSign + format(high);
        }
        if (!highBounded) {
            String sign = interval.isLowIncluded() ? " ≥ " : " > ";
            return variable + sign + format(low);
        }
        if (interval.isPoint()) {
            return variable + " = " + format((low + high) / 2);
        }
        return format(low) + lowSign + variable + highSign + format(high);
    }
}
