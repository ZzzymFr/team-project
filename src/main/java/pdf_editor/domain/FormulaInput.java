package pdf_editor.domain;

import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Converts common keyboard math notation to renderable LaTeX; never evaluates expressions. */
public final class FormulaInput {
    private static final Pattern FUNCTION = Pattern.compile(
            "(?<![\\\\A-Za-z])(?:arcsin|arccos|arctan|sinh|cosh|tanh|sqrt|sin|cos|tan|cot|sec|csc|log|ln|exp|pi)(?![A-Za-z])");
    private FormulaInput() { }

    public static String asLatex(String input, boolean keyboardMode) {
        Objects.requireNonNull(input, "input");
        if (!keyboardMode) return input;
        String value = input.replace('×', '*').replace('÷', '/').replace('π', 'π');
        // Substituting longest supported function names first preserves arctan vs tan.
        Matcher m = FUNCTION.matcher(value);
        StringBuffer out = new StringBuffer();
        while (m.find()) {
            String name = m.group();
            m.appendReplacement(out, Matcher.quoteReplacement("\\" + name));
        }
        m.appendTail(out);
        return out.toString().replace("π", "\\pi ");
    }
}
