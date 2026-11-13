package interface_adapter.evaluate;

import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

// raw token/name -> something a student can read, \cdot -> ·, x_{12} -> x₁₂
final class ReadableLatex {
    private static final Map<String, String> SYMBOLS = Map.ofEntries(
            Map.entry("\\leq", "≤"), Map.entry("\\geq", "≥"), Map.entry("\\neq", "≠"), Map.entry("\\approx", "≈"),
            Map.entry("\\times", "×"), Map.entry("\\cdot", "·"), Map.entry("\\div", "÷"), Map.entry("\\pm", "±"),
            Map.entry("\\mp", "∓"), Map.entry("\\land", "∧"), Map.entry("\\lor", "∨"), Map.entry("\\neg", "¬"),
            Map.entry("\\oplus", "⊕"), Map.entry("\\Rightarrow", "⇒"), Map.entry("\\Leftrightarrow", "⇔"),
            Map.entry("\\leftrightarrow", "↔"), Map.entry("\\rightarrow", "→"), Map.entry("\\leftarrow", "←"),
            Map.entry("\\to", "→"), Map.entry("\\mapsto", "↦"), Map.entry("\\in", "∈"), Map.entry("\\notin", "∉"),
            Map.entry("\\subset", "⊂"), Map.entry("\\subseteq", "⊆"), Map.entry("\\supset", "⊃"),
            Map.entry("\\supseteq", "⊇"), Map.entry("\\cup", "∪"), Map.entry("\\cap", "∩"),
            Map.entry("\\emptyset", "∅"), Map.entry("\\perp", "⊥"), Map.entry("\\forall", "∀"),
            Map.entry("\\exists", "∃"), Map.entry("\\infty", "∞"), Map.entry("\\sqrt", "√"),
            Map.entry("\\sum", "Σ"), Map.entry("\\prod", "Π"), Map.entry("\\int", "∫"), Map.entry("\\partial", "∂"),
            Map.entry("\\nabla", "∇"), Map.entry("\\equiv", "≡"), Map.entry("\\sim", "∼"), Map.entry("\\pi", "π"),
            Map.entry("\\langle", "⟨"), Map.entry("\\rangle", "⟩"), Map.entry("\\lfloor", "⌊"),
            Map.entry("\\rfloor", "⌋"), Map.entry("\\lceil", "⌈"), Map.entry("\\rceil", "⌉"),
            Map.entry("\\{", "{"), Map.entry("\\}", "}"), Map.entry("\\ldots", "…"), Map.entry("\\cdots", "⋯"),
            Map.entry("\\dot", "dot accent"), Map.entry("\\ddot", "double dot accent"));
    // \bar{x} -> x̄ etc, combining marks
    private static final Map<String, String> ACCENTS = Map.of("bar", "̄", "hat", "̂", "vec", "⃗",
            "tilde", "̃", "dot", "̇");
    private static final Pattern ACCENT = Pattern.compile("\\\\(bar|hat|vec|tilde|dot)\\{([^{}]*)}");
    private static final Pattern SUBSCRIPT = Pattern.compile("_\\{?([0-9]+)}?");
    private static final String SUBSCRIPT_DIGITS = "₀₁₂₃₄₅₆₇₈₉";

    private ReadableLatex() {
    }

    static String of(String raw) {
        String known = SYMBOLS.get(raw);
        if (known != null) {
            return known;
        }
        if (raw.length() == 1) {
            return raw;  // lone { or } is the answer itself
        }
        String s = accents(raw);
        s = subscripts(s);
        return s.replaceAll("\\\\([A-Za-z]+)", "$1").replace("{", "").replace("}", "");  // \foo -> foo
    }

    private static String accents(String s) {
        Matcher m = ACCENT.matcher(s);
        StringBuilder out = new StringBuilder();
        while (m.find()) {
            m.appendReplacement(out, Matcher.quoteReplacement(m.group(2) + ACCENTS.get(m.group(1))));
        }
        m.appendTail(out);
        return out.toString();
    }

    private static String subscripts(String s) {
        Matcher m = SUBSCRIPT.matcher(s);
        StringBuilder out = new StringBuilder();
        while (m.find()) {
            StringBuilder digits = new StringBuilder();
            for (char c : m.group(1).toCharArray()) {
                digits.append(SUBSCRIPT_DIGITS.charAt(c - '0'));
            }
            m.appendReplacement(out, digits.toString());
        }
        m.appendTail(out);
        return out.toString();
    }
}
