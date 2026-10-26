package entity;

import java.util.ArrayList;
import java.util.List;

public class MathLexer {
    private static final String[] PLAIN_NAMES = {"sqrt", "sin", "cos", "tan", "log", "ln", "pi"};

    private static final String SYMBOLS = "+-*/^(){}[]|!=_";

    public static List<String> tokenize(String text) {
        List<String> tokens = new ArrayList<>();
        int i = 0;
        while (i < text.length()) {
            char c = text.charAt(i);

            if (Character.isWhitespace(c)) {
                i++;
            } else if (isDigit(c) || c == '.') {
                int start = i;
                while (i < text.length() && (isDigit(text.charAt(i)) || text.charAt(i) == '.')) {
                    i++;
                }
                tokens.add(text.substring(start, i));
            } else if (c == '\\') {
                i = readCommand(text, i, tokens);
            } else if (c == '×' || c == '·') {
                tokens.add("*");
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
        return token.length() == 1 && isLetter(token.charAt(0));
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
            case "\\times":
            case "\\cdot":
            case "\\ast":
                tokens.add("*");
                break;
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
                tokens.add(command);
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

    private static boolean isLetter(char c) {
        return (c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z');
    }
}
