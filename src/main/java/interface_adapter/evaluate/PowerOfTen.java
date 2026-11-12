package interface_adapter.evaluate;

import interface_adapter.TextRun;

import java.math.BigDecimal;
import java.math.MathContext;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

// huge numbers as m×10^n, canvas gets the n raised
final class PowerOfTen {
    private static final String TIMES_TEN = "×10";
    private static final Pattern EXPONENT = Pattern.compile("(?<=" + TIMES_TEN + ")\\^([-−]?\\d+)");

    private PowerOfTen() {
    }

    // 2432902008176640000 -> 2.432902008×10^18
    static String write(BigDecimal value, int digits) {
        BigDecimal rounded = value.round(new MathContext(digits));
        int exponent = rounded.precision() - rounded.scale() - 1;
        return rounded.movePointLeft(exponent).stripTrailingZeros().toPlainString() + TIMES_TEN + "^" + exponent;
    }

    // text cut at each ^n, null if there's none
    static List<TextRun> runs(String text) {
        Matcher exponent = EXPONENT.matcher(text);
        List<TextRun> runs = new ArrayList<>();
        int done = 0;
        while (exponent.find()) {
            runs.add(new TextRun(text.substring(done, exponent.start()), false));
            runs.add(new TextRun(exponent.group(1), true));
            done = exponent.end();
        }
        if (runs.isEmpty()) {
            return null;
        }
        if (done < text.length()) {
            runs.add(new TextRun(text.substring(done), false));
        }
        return runs;
    }
}
