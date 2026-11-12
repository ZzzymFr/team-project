package entity;

/** Domain-independent display policy shared by History, Library and PDF rendering. */
public final class EquationDisplay {
    private EquationDisplay() { }

    /** A solution relating two equations is a transformation, not a new equality. */
    public static boolean isTransformation(String source, String result) {
        String left = source == null ? "" : source.trim().replaceFirst("\\s*=+\\s*$", "");
        String right = answer(result);
        return left.contains("=") && right.contains("=");
    }

    public static String formula(String source, String result) {
        String text = source == null ? "" : source.trim();
        if (result != null && !result.isBlank()) return text.replaceFirst("\\s*=+\\s*$", "").trim();
        return text;
    }

    public static String answer(String source) {
        return source == null ? "" : source.trim().replaceFirst("^\\s*=+\\s*", "").trim();
    }

    public static String separator(String source, String result) {
        return isTransformation(source, result) ? " ⇒ " : " = ";
    }

    /** JLaTeXMath rendering uses a LaTeX command for the implication symbol. */
    public static String latexSeparator(String source, String result) {
        return isTransformation(source, result) ? "\\Rightarrow" : "=";
    }

    public static String equation(String source, String result) {
        String right = answer(result);
        String left = formula(source, result);
        if (right.isBlank()) return left;
        return left.isBlank() ? "= " + right : left + separator(source, result) + right;
    }
}
