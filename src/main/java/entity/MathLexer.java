package entity;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class MathLexer {
    private static final String[] PLAIN_NAMES = {"sqrt", "sin", "cos", "tan", "log", "ln", "pi"};

    private static final String SYMBOLS = "+-*/^(){}[]|!=_,'<>%:;?";

    private static final Map<String, String> ALIASES = new HashMap<>();

    private static final Map<String, String> GREEK = new HashMap<>();

    private static final Map<Character, String> UNICODE = new HashMap<>();

    static {
        ALIASES.put("\\%", "%");
        ALIASES.put("\\le", "\\leq");
        ALIASES.put("\\ge", "\\geq");
        ALIASES.put("\\ne", "\\neq");
        ALIASES.put("\\to", "\\rightarrow");
        ALIASES.put("\\implies", "\\Rightarrow");
        ALIASES.put("\\iff", "\\Leftrightarrow");
        ALIASES.put("\\lnot", "\\neg");
        ALIASES.put("\\wedge", "\\land");
        ALIASES.put("\\vee", "\\lor");
        ALIASES.put("\\subseteq", "\\subset");
        ALIASES.put("\\dfrac", "\\frac");
        ALIASES.put("\\tfrac", "\\frac");
        ALIASES.put("\\cdots", "\\ldots");
    }

    static {
        GREEK.put("\\alpha", "α");
        GREEK.put("\\beta", "β");
        GREEK.put("\\gamma", "γ");
        GREEK.put("\\delta", "δ");
        GREEK.put("\\epsilon", "ϵ");
        GREEK.put("\\zeta", "ζ");
        GREEK.put("\\eta", "η");
        GREEK.put("\\theta", "θ");
        GREEK.put("\\iota", "ι");
        GREEK.put("\\kappa", "κ");
        GREEK.put("\\lambda", "λ");
        GREEK.put("\\mu", "μ");
        GREEK.put("\\nu", "ν");
        GREEK.put("\\xi", "ξ");
        GREEK.put("\\omicron", "ο");
        GREEK.put("\\rho", "ρ");
        GREEK.put("\\sigma", "σ");
        GREEK.put("\\tau", "τ");
        GREEK.put("\\upsilon", "υ");
        GREEK.put("\\phi", "φ");
        GREEK.put("\\chi", "χ");
        GREEK.put("\\psi", "ψ");
        GREEK.put("\\omega", "ω");
        GREEK.put("\\Gamma", "Γ");
        GREEK.put("\\Delta", "Δ");
        GREEK.put("\\Theta", "Θ");
        GREEK.put("\\Lambda", "Λ");
        GREEK.put("\\Xi", "Ξ");
        GREEK.put("\\Pi", "Π");
        GREEK.put("\\Sigma", "Σ");
        GREEK.put("\\Upsilon", "Υ");
        GREEK.put("\\Phi", "Φ");
        GREEK.put("\\Psi", "Ψ");
        GREEK.put("\\Omega", "Ω");
        GREEK.put("\\varepsilon", "ε");
        GREEK.put("\\varkappa", "ϰ");
        GREEK.put("\\varphi", "ϕ");
        GREEK.put("\\varpi", "ϖ");
        GREEK.put("\\varrho", "ϱ");
        GREEK.put("\\varsigma", "ς");
        GREEK.put("\\vartheta", "ϑ");
    }

    static {
        UNICODE.put('≤', "\\leq");
        UNICODE.put('≥', "\\geq");
        UNICODE.put('≠', "\\neq");
        UNICODE.put('≈', "\\approx");
        UNICODE.put('±', "\\pm");
        UNICODE.put('∞', "\\infty");
    }

    public static List<String> tokenize(String text) {
        List<String> tokens = new ArrayList<>();
        int i = 0;
        while (i < text.length()) {
            char c = text.charAt(i);

            if (Character.isWhitespace(c)) {
                i++;
            } else if (isDigit(c) || c == '.') {
                int start = i;
                boolean hasDigit = false;
                while (i < text.length() && (isDigit(text.charAt(i)) || text.charAt(i) == '.')) {
                    hasDigit = hasDigit || isDigit(text.charAt(i));
                    i++;
                }
                if (!hasDigit && i - start >= 2) {
                    tokens.add("\\ldots");
                } else {
                    tokens.add(text.substring(start, i));
                }
            } else if (c == '\\') {
                i = readCommand(text, i, tokens);
            } else if (c == '×') {
                tokens.add("\\times");
                i++;
            } else if (c == '·') {
                tokens.add("\\cdot");
                i++;
            } else if (UNICODE.containsKey(c)) {
                tokens.add(UNICODE.get(c));
                i++;
            } else if (c == '÷') {
                tokens.add("/");
                i++;
            } else if (c == '−') {
                tokens.add("-");
                i++;
            } else if (c == 'π') {
                tokens.add("\\pi");
                i++;
            } else if (c == '√') {
                tokens.add("\\sqrt");
                i++;
            } else if (isGreek(c)) {
                tokens.add(String.valueOf(c));
                i++;
            } else if (isLetter(c)) {
                String plainName = findPlainName(text, i);
                if (plainName != null) {
                    tokens.add("\\" + plainName);
                    i = i + plainName.length();
                } else {
                    tokens.add(String.valueOf(c));
                    i++;
                }
            } else if (SYMBOLS.indexOf(c) != -1) {
                tokens.add(String.valueOf(c));
                i++;
            } else {
                throw new MathSyntaxException("Unexpected character \"" + c + "\".");
            }
        }
        return tokens;
    }

    public static boolean isLetter(String token) {
        return token.length() == 1
                && (isLetter(token.charAt(0)) || isGreek(token.charAt(0)) || token.charAt(0) == '?');
    }

    private static int readCommand(String text, int start, List<String> tokens) {
        int i = start + 1;
        while (i < text.length() && isLetter(text.charAt(i))) {
            i++;
        }
        if (i == start + 1 && i < text.length()) {
            i++;
        }
        String command = text.substring(start, i);

        switch (command) {
            case "\\ast":
                tokens.add("*");
                break;
            case "\\text":
            case "\\begin":
            case "\\end":
            case "\\square":
                throw new UncalculableException();
            case "\\div":
                tokens.add("/");
                break;
            case "\\left":
            case "\\right":
            case "\\,":
            case "\\;":
            case "\\:":
            case "\\!":
            case "\\ ":
                break;
            default:
                if (ALIASES.containsKey(command)) {
                    tokens.add(ALIASES.get(command));
                } else if (GREEK.containsKey(command)) {
                    tokens.add(GREEK.get(command));
                } else {
                    tokens.add(command);
                }
        }
        return i;
    }

    private static String findPlainName(String text, int start) {
        for (String name : PLAIN_NAMES) {
            if (text.startsWith(name, start)) {
                return name;
            }
        }
        return null;
    }

    private static boolean isDigit(char c) {
        return c >= '0' && c <= '9';
    }

    private static boolean isGreek(char c) {
        return c >= '\u0370' && c <= '\u03FF';
    }

    private static boolean isLetter(char c) {
        return (c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z');
    }
}
