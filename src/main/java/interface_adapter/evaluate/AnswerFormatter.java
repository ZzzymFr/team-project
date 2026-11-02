package interface_adapter.evaluate;

import interface_adapter.AnswerKind;
import interface_adapter.TextRun;
import use_case.evaluate.IntervalOutputData;
import use_case.evaluate.ValueOutputData;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;

// answer text only, the presenter just pushes it to the view model
public final class AnswerFormatter {
    private static final int MAX_SOLUTIONS_SHOWN = 10;
    // answers: 2 dp, or a fraction if its p/q w/ q <= 100
    private static final int SHOWN_DECIMALS = 2;
    private static final long MAX_DENOMINATOR = 100;
    private static final double FRACTION_TOLERANCE = 1e-8;
    private static final double FRACTION_LIMIT = 1e6;
    private static final double TINY = 0.005;

    private AnswerFormatter() {
    }

    // what gets drawn: drop the "= " right after the user's =, real minus sign
    static String canvasText(AnswerKind kind, String result) {
        String text = kind == AnswerKind.AFTER_EQUALS && result.startsWith("= ") ? result.substring(2) : result;
        return text.replace('-', '−');
    }

    // "= " in front unless it goes right after the user's =
    static List<TextRun> canvasRuns(AnswerKind kind, List<TextRun> runs) {
        if (kind == AnswerKind.AFTER_EQUALS) {
            return runs;
        }
        List<TextRun> out = new ArrayList<>(runs);
        if (!out.isEmpty() && !out.get(0).isRaised()) {
            out.set(0, new TextRun("= " + out.get(0).getText(), false));
        } else {
            out.add(0, new TextRun("= ", false));
        }
        return out;
    }

    // booleans alone get no "= "
    public static String values(List<ValueOutputData> values) {
        String text = "";
        boolean onlyBooleans = true;
        for (int i = 0; i < values.size(); i++) {
            if (i > 0) {
                text = text + " or ";
            }
            text = text + format(values.get(i));
            if (values.get(i).getKind() != ValueOutputData.Kind.BOOLEAN) {
                onlyBooleans = false;
            }
        }
        return onlyBooleans ? text : "= " + text;
    }

    public static String solutions(String variable, List<Double> solutions) {
        if (solutions.isEmpty()) {
            return "no solution";
        }
        List<String> shown = formatApart(solutions);
        List<String> parts = new ArrayList<>();
        for (String number : shown) {
            parts.add(variable + " = " + number);
        }
        return firstFew(parts, " or ");
    }

    public static String intervals(String variable, List<IntervalOutputData> intervals) {
        if (intervals.isEmpty()) {
            return "no solution";
        }
        // all ends together so x < 1 or x > 1.0001 stays apart
        List<Double> ends = new ArrayList<>();
        for (IntervalOutputData interval : intervals) {
            ends.add(interval.getLow());
            ends.add(interval.getHigh());
        }
        List<String> shown = formatApart(ends);
        List<String> parts = new ArrayList<>();
        for (int i = 0; i < intervals.size(); i++) {
            parts.add(interval(variable, intervals.get(i), shown.get(2 * i), shown.get(2 * i + 1)));
        }
        return firstFew(parts, " or ");
    }

    public static String excluded(String variable, List<Double> excluded) {
        if (excluded.isEmpty()) {
            return alwaysTrue(variable);
        }
        List<String> parts = new ArrayList<>();
        for (String number : formatApart(excluded)) {
            parts.add(variable + " ≠ " + number);
        }
        return firstFew(parts, " and ");
    }

    public static String alwaysTrue(String variable) {
        return "true for all " + variable;
    }

    public static String defined(String definition) {
        return "Defined " + definition;
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

    public static String format(ValueOutputData value) {
        switch (value.getKind()) {
            case NUMBER:
                return format(value.getNumbers().get(0));
            case BOOLEAN:
                return value.isTrue() ? "true" : "false";
            case SET:
                return "{" + formatList(value.getNumbers()) + "}";
            case VECTOR:
                return "(" + formatList(value.getNumbers()) + ")";
            default:
                // a set alone has no value to show
                return "Uncalculable";
        }
    }

    // first 10 joined, then "(and n more)"
    private static String firstFew(List<String> parts, String separator) {
        StringBuilder text = new StringBuilder();
        for (int i = 0; i < parts.size() && i < MAX_SOLUTIONS_SHOWN; i++) {
            if (i > 0) {
                text.append(separator);
            }
            text.append(parts.get(i));
        }
        return text + more(parts.size());
    }

    // "n more" note for long lists
    private static String more(int count) {
        if (count <= MAX_SOLUTIONS_SHOWN) {
            return "";
        }
        return " (and " + (count - MAX_SOLUTIONS_SHOWN) + " more)";
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

    private static String interval(String variable, IntervalOutputData interval, String lowText, String highText) {
        double low = interval.getLow();
        double high = interval.getHigh();
        String lowSign = interval.isLowIncluded() ? " ≤ " : " < ";
        String highSign = interval.isHighIncluded() ? " ≤ " : " < ";
        boolean lowBounded = low != Double.NEGATIVE_INFINITY;
        boolean highBounded = high != Double.POSITIVE_INFINITY;
        if (!lowBounded && !highBounded) {
            return alwaysTrue(variable);
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
