package interface_adapter.evaluate;

import entity.Interval;
import entity.symbolic.TextRun;
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
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;

public class EvaluatePresenter implements EvaluateOutputBoundary {
    private static final int MAX_SOLUTIONS_SHOWN = 10;
    // answers: 2 dp, or a fraction if its p/q w/ q <= 100
    private static final int SHOWN_DECIMALS = 2;
    private static final long MAX_DENOMINATOR = 100;
    private static final double FRACTION_TOLERANCE = 1e-8;
    private static final double FRACTION_LIMIT = 1e6;
    private static final double TINY = 0.005;

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
        List<String> shown = formatApart(solutions);
        StringBuilder text = new StringBuilder();
        for (int i = 0; i < solutions.size() && i < MAX_SOLUTIONS_SHOWN; i++) {
            if (i > 0) {
                text.append(" or ");
            }
            text.append(variable).append(" = ").append(shown.get(i));
        }
        showResult(text + more(solutions.size()));
    }

    @Override
    public void prepareIntervalsView(String variable, List<Interval> intervals) {
        if (intervals.isEmpty()) {
            showResult("no solution");
            return;
        }
        // all ends together so x < 1 or x > 1.0001 stays apart
        List<Double> ends = new ArrayList<>();
        for (Interval interval : intervals) {
            ends.add(interval.getLow());
            ends.add(interval.getHigh());
        }
        List<String> shown = formatApart(ends);
        StringBuilder text = new StringBuilder();
        for (int i = 0; i < intervals.size() && i < MAX_SOLUTIONS_SHOWN; i++) {
            if (i > 0) {
                text.append(" or ");
            }
            text.append(formatInterval(variable, intervals.get(i), shown.get(2 * i), shown.get(2 * i + 1)));
        }
        showResult(text + more(intervals.size()));
    }

    @Override
    public void prepareExcludedView(String variable, List<Double> excluded) {
        if (excluded.isEmpty()) {
            prepareAlwaysTrueView(variable);
            return;
        }
        List<String> shown = formatApart(excluded);
        StringBuilder text = new StringBuilder();
        for (int i = 0; i < excluded.size() && i < MAX_SOLUTIONS_SHOWN; i++) {
            if (i > 0) {
                text.append(" and ");
            }
            text.append(variable).append(" ≠ ").append(shown.get(i));
        }
        showResult(text + more(excluded.size()));
    }

    // neighbours that round the same get full digits (no "x = 1 or x = 1")
    private static List<String> formatApart(List<Double> numbers) {
        List<String> shown = new ArrayList<>();
        for (double number : numbers) {
            shown.add(format(number));
        }
        for (int i = 1; i < numbers.size(); i++) {
            if (shown.get(i).equals(shown.get(i - 1)) && !numbers.get(i).equals(numbers.get(i - 1))) {
                shown.set(i - 1, precise(numbers.get(i - 1)));
                shown.set(i, precise(numbers.get(i)));
            }
        }
        return shown;
    }

    private static String precise(double value) {
        // tiny ones only need to differ from 0, 2 sig is enough
        int digits = Math.abs(value) < TINY ? SHOWN_DECIMALS : 10;
        return new BigDecimal(value).round(new MathContext(digits)).stripTrailingZeros().toPlainString();
    }

    // "n more" note for long lists
    private static String more(int count) {
        if (count <= MAX_SOLUTIONS_SHOWN) {
            return "";
        }
        return " (and " + (count - MAX_SOLUTIONS_SHOWN) + " more)";
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
    public void prepareExpressionView(String latex, List<TextRun> runs) {
        MathLabState state = viewModel.getState();
        state.setResultText("= " + latex);
        state.setResultRuns(runs);
        state.setErrorText("");
        viewModel.firePropertyChanged(MathLabViewModel.RESULT);
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
        // 10 sig first so 0.1+0.2 noise is gone
        BigDecimal clean = new BigDecimal(value).round(new MathContext(10)).stripTrailingZeros();
        if (clean.scale() <= SHOWN_DECIMALS) {
            return clean.toPlainString();
        }
        if (Math.abs(value) < TINY) {
            // keep tiny answers from showing as 0
            return clean.round(new MathContext(SHOWN_DECIMALS)).toPlainString();
        }
        String fraction = fraction(value);
        if (fraction != null) {
            return fraction;
        }
        BigDecimal rounded = clean.setScale(SHOWN_DECIMALS, RoundingMode.HALF_UP).stripTrailingZeros();
        if (rounded.scale() <= 0) {
            // 1.001 -> "1" looks exact, keep digits till it differs
            double gap = clean.subtract(rounded).abs().doubleValue();
            int decimals = (int) Math.ceil(-Math.log10(gap)) + 1;
            return clean.setScale(decimals, RoundingMode.HALF_UP).stripTrailingZeros().toPlainString();
        }
        return rounded.toPlainString();
    }

    // repeating decimal p/q w/ small q, null if not one (1/8 just rounds)
    private static String fraction(double value) {
        if (Math.abs(value) >= FRACTION_LIMIT) {
            return null;
        }
        for (long q = 2; q <= MAX_DENOMINATOR; q++) {
            double p = Math.rint(value * q);
            if (p != 0 && Math.abs(value - p / q) <= FRACTION_TOLERANCE) {
                return isTerminating(q) ? null : (long) p + "/" + q;
            }
        }
        return null;
    }

    // only 2s and 5s = decimal ends
    private static boolean isTerminating(long denominator) {
        long rest = denominator;
        while (rest % 2 == 0) {
            rest = rest / 2;
        }
        while (rest % 5 == 0) {
            rest = rest / 5;
        }
        return rest == 1;
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

    private static String formatInterval(String variable, Interval interval, String lowText, String highText) {
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
            return variable + highSign + highText;
        }
        if (!highBounded) {
            String sign = interval.isLowIncluded() ? " ≥ " : " > ";
            return variable + sign + lowText;
        }
        if (interval.isPoint()) {
            return variable + " = " + format((low + high) / 2);
        }
        return lowText + lowSign + variable + highSign + highText;
    }
}
