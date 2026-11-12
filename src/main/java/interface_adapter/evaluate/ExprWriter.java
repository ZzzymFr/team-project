package interface_adapter.evaluate;

import interface_adapter.TextRun;
import use_case.evaluate.ExprOutputData;

import java.math.BigDecimal;
import java.math.MathContext;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

// derivative answer -> latex (result text / pdf) or style A runs (canvas, exponents raised)
public final class ExprWriter {
    private static final Map<String, String> GREEK = Map.ofEntries(
            Map.entry("\\alpha", "α"), Map.entry("\\beta", "β"), Map.entry("\\gamma", "γ"),
            Map.entry("\\delta", "δ"), Map.entry("\\theta", "θ"), Map.entry("\\lambda", "λ"),
            Map.entry("\\mu", "μ"), Map.entry("\\phi", "φ"), Map.entry("\\sigma", "σ"),
            Map.entry("\\omega", "ω"), Map.entry("\\tau", "τ"), Map.entry("\\rho", "ρ"));
    private static final String SUBSCRIPTS = "₀₁₂₃₄₅₆₇₈₉";

    private final boolean tex;
    private final StringBuilder latex = new StringBuilder();
    private final List<TextRun> runs = new ArrayList<>();
    private int raised;

    private ExprWriter(boolean tex, int raised) {
        this.tex = tex;
        this.raised = raised;
    }

    public static String latex(ExprOutputData e) {
        ExprWriter w = new ExprWriter(true, 0);
        w.write(e);
        return w.latex.toString();
    }

    public static List<TextRun> runs(ExprOutputData e) {
        ExprWriter w = new ExprWriter(false, 0);
        w.write(e);
        return Collections.unmodifiableList(w.runs);
    }

    // ---- output

    private void emit(String texText, String runText) {
        if (tex) {
            latex.append(texText);
            return;
        }
        if (runText.isEmpty()) {
            return;
        }
        boolean up = raised > 0;
        int last = runs.size() - 1;
        if (last >= 0 && runs.get(last).isRaised() == up) {
            runs.set(last, new TextRun(runs.get(last).getText() + runText, up));
        } else {
            runs.add(new TextRun(runText, up));
        }
    }

    private void emit(String both) {
        emit(both, both);
    }

    private void append(ExprWriter w) {
        if (tex) {
            latex.append(w.latex);
            return;
        }
        for (TextRun run : w.runs) {
            boolean up = run.isRaised();
            int last = runs.size() - 1;
            if (last >= 0 && runs.get(last).isRaised() == up) {
                runs.set(last, new TextRun(runs.get(last).getText() + run.getText(), up));
            } else {
                runs.add(run);
            }
        }
    }

    private String flat() {
        return tex ? latex.toString() : TextRun.plain(runs);
    }

    private ExprWriter sub() {
        return new ExprWriter(tex, raised);
    }

    // ---- sums and terms

    private void write(ExprOutputData e) {
        if (e.kind() == ExprOutputData.Kind.ADD) {
            for (int i = 0; i < e.terms().size(); i++) {
                writeSigned(e.terms().get(i), i == 0);
            }
        } else {
            writeSigned(e, true);
        }
    }

    private void writeSigned(ExprOutputData term, boolean first) {
        if (hasNegativeCoefficient(term)) {
            emit("-", "−");
            writeTerm(negated(term));
        } else {
            if (!first) {
                emit("+");
            }
            writeTerm(term);
        }
    }

    private static boolean hasNegativeCoefficient(ExprOutputData term) {
        if (term.isNum()) {
            return term.isNegative();
        }
        return term.kind() == ExprOutputData.Kind.MUL && !term.terms().isEmpty() && term.terms().get(0).isNum()
                && term.terms().get(0).isNegative();
    }

    private static ExprOutputData negated(ExprOutputData term) {
        if (term.isNum()) {
            return ExprOutputData.num(Math.negateExact(term.num()), term.den(), term.isDecimal());
        }
        List<ExprOutputData> factors = new ArrayList<>(term.terms());
        ExprOutputData c = factors.get(0);
        long flipped = Math.negateExact(c.num());
        if (flipped == 1 && c.den() == 1 && !c.isDecimal() && factors.size() > 1) {
            factors.remove(0);
        } else {
            factors.set(0, ExprOutputData.num(flipped, c.den(), c.isDecimal()));
        }
        return factors.size() == 1 ? factors.get(0) : ExprOutputData.mul(factors);
    }

    // splits into top/bottom so x^-1 and 1/2 go under a fraction
    private void writeTerm(ExprOutputData term) {
        List<ExprOutputData> factors = term.kind() == ExprOutputData.Kind.MUL ? term.terms() : List.of(term);
        List<ExprOutputData> top = new ArrayList<>();
        List<ExprOutputData> bottom = new ArrayList<>();
        for (ExprOutputData f : factors) {
            if (f.isNum() && !f.isDecimal() && !f.isInteger()) {
                if (f.num() != 1) {
                    top.add(ExprOutputData.num(f.num()));
                }
                bottom.add(ExprOutputData.num(f.den()));
            } else if (f.kind() == ExprOutputData.Kind.POW && f.exponent().isNum() && f.exponent().isNegative()) {
                ExprOutputData exp = f.exponent();
                long flipped = Math.negateExact(exp.num());
                bottom.add(flipped == 1 && exp.den() == 1 ? f.base()
                        : ExprOutputData.pow(f.base(), ExprOutputData.num(flipped, exp.den(), exp.isDecimal())));
            } else {
                top.add(f);
            }
        }
        if (bottom.isEmpty()) {
            writeFactors(top);
        } else if (tex) {
            emit("\\frac{");
            writeTexFractionPart(top.isEmpty() ? List.of(ExprOutputData.num(1)) : top);
            emit("}{");
            writeTexFractionPart(bottom);
            emit("}");
        } else {
            writeFractionPart(top.isEmpty() ? List.of(ExprOutputData.num(1)) : top);
            emit("/");
            writeFractionPart(bottom);
        }
    }

    // lone sum needs no parens inside \frac
    private void writeTexFractionPart(List<ExprOutputData> factors) {
        if (factors.size() == 1 && factors.get(0).kind() == ExprOutputData.Kind.ADD) {
            write(factors.get(0));
        } else {
            writeFactors(factors);
        }
    }

    private void writeFractionPart(List<ExprOutputData> factors) {
        boolean parens = factors.size() > 1;  // a lone sum brings its own
        if (parens) {
            emit("(");
        }
        writeFactors(factors);
        if (parens) {
            emit(")");
        }
    }

    private void writeFactors(List<ExprOutputData> factors) {
        String before = "";
        for (int i = 0; i < factors.size(); i++) {
            ExprOutputData f = factors.get(i);
            boolean last = i == factors.size() - 1;
            boolean bare = last || isFunction(factors.get(i + 1));
            ExprWriter w = sub();
            w.writeFactor(f, bare);
            String text = w.flat();
            if (i > 0) {
                separate(before, text, f);
            }
            append(w);
            before = text;
        }
    }

    private void separate(String before, String text, ExprOutputData next) {
        if (text.isEmpty()) {
            return;
        }
        char c = text.charAt(0);
        if (Character.isDigit(c) || c == '.') {
            emit("\\cdot ", "·");
        } else if (!tex && (isFunction(next) || next.kind() == ExprOutputData.Kind.DVAR)) {
            emit(" ");
        } else if (tex && Character.isLetter(c) && before.matches(".*\\\\[a-zA-Z]+")) {
            emit(" ");  // \pi x, not \pix
        }
    }

    private static boolean isFunction(ExprOutputData e) {
        return e.kind() == ExprOutputData.Kind.FN || e.kind() == ExprOutputData.Kind.LOG;
    }

    // ---- single factors

    private void writeFactor(ExprOutputData f, boolean bare) {
        switch (f.kind()) {
            case NUM:
                writeNumber(f);
                break;
            case VAR:
                emit(f.name(), GREEK.getOrDefault(f.name(), f.name().replace("\\", "")));
                break;
            case E:
                emit("e");
                break;
            case PI:
                emit("\\pi", "π");
                break;
            case POW:
                writePower(f);
                break;
            case FN:
            case LOG:
                writeFunction(f, bare);
                break;
            case DVAR:
                emit("\\frac{d" + f.name() + "}{d" + f.withRespectTo() + "}",
                        "d" + f.name() + "/d" + f.withRespectTo());
                break;
            default:
                writeGroup(f);
        }
    }

    private void writeGroup(ExprOutputData e) {
        emit("(");
        write(e);
        emit(")");
    }

    private void writeNumber(ExprOutputData n) {
        if (n.isDecimal()) {
            // same rounding as a plain answer, 0.004 stays 0.004
            BigDecimal exact = BigDecimal.valueOf(n.num()).divide(BigDecimal.valueOf(n.den()), MathContext.DECIMAL64);
            BigDecimal shown = AnswerFormatter.shown(exact).stripTrailingZeros();
            emit(shown.signum() == 0 ? "0" : shown.toPlainString().replace("-", tex ? "-" : "−"));
        } else if (n.isInteger()) {
            emit(Long.toString(n.num()).replace("-", tex ? "-" : "−"));
        } else {
            writeGroup(n);  // stray fraction, write() splits it
        }
    }

    private void writePower(ExprOutputData p) {
        ExprOutputData exp = p.exponent();
        if (exp.isNum() && !exp.isDecimal() && exp.num() == 1 && exp.den() > 1) {
            writeRoot(p.base(), exp.den());
            return;
        }
        ExprOutputData base = p.base();
        if (isSymbol(base) && !(base.isNum() && base.isNegative())) {
            writeFactor(base, true);
        } else {
            writeGroup(base);
        }
        writeRaised(exp);
    }

    private void writeRoot(ExprOutputData base, long degree) {
        if (tex) {
            emit(degree == 2 ? "\\sqrt{" : "\\sqrt[" + degree + "]{");
            write(base);
            emit("}");
            return;
        }
        if (degree != 2) {
            raised++;
            emit(Long.toString(degree));
            raised--;
        }
        emit("√");
        if (isSymbol(base)) {
            writeFactor(base, true);
        } else {
            writeGroup(base);
        }
    }

    private void writeRaised(ExprOutputData exp) {
        if (tex) {
            emit("^{");
            write(exp);
            emit("}");
            return;
        }
        raised++;
        if (raised > 1) {
            // can't raise twice, fall back to ^
            emit("^");
            if (isSymbol(exp)) {
                write(exp);
            } else {
                writeGroup(exp);
            }
        } else {
            write(exp);
        }
        raised--;
    }

    private void writeFunction(ExprOutputData f, boolean bare) {
        if (f.kind() == ExprOutputData.Kind.FN) {
            emit("\\" + f.name(), f.name());
        } else if (f.logBase() == null) {
            emit("\\log", "log");
        } else {
            emit("\\log_{", "log");
            if (tex) {
                write(f.logBase());
            } else {
                emit(subscript(f.logBase()));
            }
            emit("}", "");
        }
        ExprOutputData arg = f.arg();
        if (bare && isSymbol(arg) && !(arg.isNum() && arg.isNegative())) {
            emit(" ");
            writeFactor(arg, true);
        } else {
            writeGroup(arg);
        }
    }

    private String subscript(ExprOutputData base) {
        if (base.isNum() && base.isInteger() && !base.isNegative()) {
            StringBuilder s = new StringBuilder();
            for (char c : Long.toString(base.num()).toCharArray()) {
                s.append(SUBSCRIPTS.charAt(c - '0'));
            }
            return s.toString();
        }
        ExprWriter w = sub();
        w.writeGroup(base);
        return "_" + w.flat();
    }

    private static boolean isSymbol(ExprOutputData e) {
        switch (e.kind()) {
            case VAR:
            case E:
            case PI:
                return true;
            case NUM:
                return e.isDecimal() || e.isInteger();
            default:
                return false;
        }
    }
}
