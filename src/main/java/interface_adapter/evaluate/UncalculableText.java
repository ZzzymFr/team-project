package interface_adapter.evaluate;

import java.util.Map;

// popup words for each reason, short + plain, no latex
final class UncalculableText {
    private static final Map<String, String> WORDS = Map.ofEntries(
            Map.entry("TEXT", "Contains text"),
            Map.entry("ELLIPSIS", "Has \"…\""),
            Map.entry("NOT_CALCULATION", "Not a calculation"),
            Map.entry("INTEGRAL", "Integrals not supported"),
            Map.entry("PARTIAL", "∂ needs a function"),
            Map.entry("NOT_DEFINED", "Function not defined"),
            Map.entry("NOT_AN_INPUT", "Not a function input"),
            Map.entry("MAX_DERIVATIVE", "Max 4th derivative"),
            Map.entry("SUM_RANGE", "Sum needs a range"),
            Map.entry("TOO_MANY_TERMS", "Over 1,000,000 terms"),
            Map.entry("MAX_PLUS_MINUS", "Max 4 ± signs"),
            Map.entry("PLUS_MINUS_HERE", "± not allowed here"),
            Map.entry("TOO_BIG", "Number too big"),
            Map.entry("WRONG_KIND", "Wrong kind of value"),
            Map.entry("NEEDS_BOOLEAN", "Logic needs true/false"),
            Map.entry("VECTOR_SIZES", "Vector sizes differ"),
            Map.entry("CROSS_3D", "× needs 3D vectors"),
            Map.entry("INFINITE_SET", "Infinite set"),
            Map.entry("SET_ALONE", "A set has no value"),
            Map.entry("UNKNOWN_SET", "Unknown set"),
            Map.entry("RATIONAL", "Can't tell if rational"),
            Map.entry("NOTHING_TO_CHECK", "Nothing to check"),
            Map.entry("OVER_RN", "Can't check over ℝⁿ"),
            Map.entry("TOO_MANY_SOLUTIONS", "Too many solutions"),
            Map.entry("TOO_FAST", "Changes too fast"),
            Map.entry("BEYOND_RANGE", "Answer beyond ±100"),
            Map.entry("LIMIT", "Limit unclear"),
            Map.entry("SERIES", "Series too slow to tell"),
            Map.entry("DERIVATIVE", "Derivative unstable"),
            Map.entry("TOO_MUCH_WORK", "Formula too complex"),
            Map.entry("UNDECIDED", "Can't decide"),
            Map.entry("SETS_TOO_LARGE", "Sets too large"),
            Map.entry("UNDEFINED_PART", "Has an undefined part"),
            Map.entry("SUM_BOUNDS", "Bounds must be integers"),
            Map.entry("ZERO_VECTOR", "Zero vector has no direction"),
            Map.entry("ABS_DERIVATIVE", "Can't differentiate |x|"),
            Map.entry("FACTORIAL_DERIVATIVE", "Can't differentiate n!"),
            Map.entry("NOT_DIFFERENTIABLE", "Can't differentiate this"),
            Map.entry("ANSWER_TOO_LONG", "Answer too long"));

    private UncalculableText() {
    }

    static String of(String reason, String detail) {
        if ("INVERSE_AMBIGUOUS".equals(reason)) {
            return "Uncalculable — " + detail.substring(1) + "⁻¹ is ambiguous";  // e.g. sin⁻¹
        }
        String words = reason == null ? null : WORDS.get(reason);
        return words == null ? "Uncalculable" : "Uncalculable — " + words;
    }
}
