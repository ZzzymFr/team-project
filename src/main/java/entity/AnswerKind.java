package entity;

import java.util.List;
import java.util.Set;

// where an answer goes, picked from the formula's latex
public enum AnswerKind {
    AFTER_EQUALS, BELOW, RIGHT;

    private static final Set<String> RELATIONS = Set.of("=", "<", ">", "\\leq", "\\geq", "\\neq",
            "\\approx", "\\in", "\\notin", "\\subset", "\\perp");

    public static AnswerKind of(String latex) {
        if (latex == null) {
            return RIGHT;
        }
        try {
            List<String> tokens = MathLexer.tokenize(latex);
            if (!tokens.isEmpty() && "=".equals(tokens.get(tokens.size() - 1))) {
                return AFTER_EQUALS;
            }
            int depth = 0;
            for (String t : tokens) {
                depth += "{".equals(t) ? 1 : "}".equals(t) ? -1 : 0;
                if (depth == 0 && RELATIONS.contains(t)) {
                    return BELOW;  // skips the i=1 under a sum
                }
            }
            return RIGHT;
        } catch (RuntimeException e) {
            // lexer choked, guess from the raw text
            if (latex.trim().endsWith("=")) {
                return AFTER_EQUALS;
            }
            return RELATIONS.stream().anyMatch(latex::contains) ? BELOW : RIGHT;
        }
    }

    public String answerText(String result) {
        String text = this == AFTER_EQUALS && result.startsWith("= ") ? result.substring(2) : result;
        return text.replace('-', '−');
    }

    public boolean isBelow() {
        return this == BELOW;
    }
}
