package use_case.evaluate;

import java.util.List;
import java.util.Set;

// how the written formula ends/looks, presenter turns this into a canvas spot
public enum FormulaShape {
    ENDS_WITH_EQUALS, RELATION, PLAIN;

    private static final Set<String> RELATIONS = Set.of("=", "<", ">", "\\leq", "\\geq", "\\neq",
            "\\approx", "\\in", "\\notin", "\\subset", "\\perp");

    static FormulaShape of(List<String> tokens) {
        if (!tokens.isEmpty() && "=".equals(tokens.get(tokens.size() - 1))) {
            return ENDS_WITH_EQUALS;
        }
        int depth = 0;
        for (String t : tokens) {
            depth += "{".equals(t) ? 1 : "}".equals(t) ? -1 : 0;
            if (depth == 0 && RELATIONS.contains(t)) {
                return RELATION;  // skips the i=1 under a sum
            }
        }
        return PLAIN;
    }
}
