package interface_adapter.evaluate;

import java.util.ArrayList;
import java.util.List;

// popup words for a badly written formula, short + plain, no latex
final class InvalidText {
    private static final String HEAD = "Invalid formula — ";

    private InvalidText() {
    }

    static String of(String problem, List<String> raw) {
        List<String> d = new ArrayList<>();
        for (String r : raw) {
            d.add(ReadableLatex.of(r));
        }
        return HEAD + words(problem, d);
    }

    private static String words(String problem, List<String> d) {
        switch (problem) {
            case "EMPTY":
                return "Nothing to calculate";
            case "UNEXPECTED":
                return "Unexpected \"" + d.get(0) + "\"";
            case "NOT_SUPPORTED":
                return "\"" + d.get(0) + "\" not supported";
            case "BAD_NUMBER":
                return d.get(0).matches(".*[0-9].*") ? "\"" + d.get(0) + "\" isn't a number" : "Stray \"" + d.get(0) + "\"";
            case "EXPECTED":
                return "Missing \"" + d.get(0) + "\"";
            case "ENDS_EARLY":
                return "Formula is unfinished";
            case "EMPTY_SUBSCRIPT":
                return "Empty subscript on " + d.get(0);
            case "NEEDS_BRACKETS":
                return "Write " + d.get(0) + "(…) to use " + d.get(0);
            case "SET_DIMENSION":
                return "Dimension must be a whole number ≥ 1";
            case "UNKNOWN_VARIABLE":
                return d.get(0) + " has no value";
            case "CALLS_ITSELF":
                return (d.isEmpty() ? "Function" : d.get(0)) + " calls itself forever";
            case "WRONG_INPUTS":
                return d.get(0) + " needs " + d.get(1) + ("1".equals(d.get(1)) ? " input" : " inputs");
            case "NOTHING_LEFT":
                return "Nothing before \"=\"";
            case "NOTHING_RIGHT":
                return "Nothing after \"=\"";
            case "MANY_EQUALS":
                return "Only one \"=\" allowed";
            case "MANY_UNKNOWNS":
                return "Too many unknowns: " + String.join(", ", d);
            case "NO_BODY":
                return "Nothing after \"" + d.get(0) + "=\"";
            case "BODY_VARIABLE":
                return d.get(0) + " can only use " + d.get(1) + ", not " + d.get(2);
            default:
                return "Can't read it";
        }
    }
}
