package entity;

/** Pure presentation policy: a calculated answer introduces exactly one equality sign. */
public final class EquationDisplay {
    private EquationDisplay() { }
    public static String formula(String source, String result) {
        String text = source == null ? "" : source.trim();
        if (result != null && !result.isBlank()) return text.replaceFirst("\\s*=+\\s*$", "").trim();
        return text;
    }
    public static String answer(String source) {
        return source == null ? "" : source.trim().replaceFirst("^\\s*=+\\s*", "").trim();
    }
    public static String equation(String formula, String result) {
        String answer = answer(result);
        if (answer.isBlank()) return formula(formula, "");
        String left = formula(formula, result);
        return left.isEmpty() ? "= " + answer : left + " = " + answer;
    }
}
