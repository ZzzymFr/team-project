package entity.symbolic;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

// Expr -> latex (result text / pdf) or style A runs (canvas, exponents raised)
public final class ExprWriter {
    private static final int DECIMALS = 2;
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

    public static String latex(Expr e) {
        ExprWriter w = new ExprWriter(true, 0);
        w.write(e);
        return w.latex.toString();
    }

    public static List<TextRun> runs(Expr e) {
        ExprWriter w = new ExprWriter(false, 0);
        w.write(e);
        return Collections.unmodifiableList(w.runs);
    }

    public static String plain(List<TextRun> runs) {
        StringBuilder text = new StringBuilder();
        for (TextRun run : runs) {
            text.append(run.getText());
        }
        return text.toString();
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
        return tex ? latex.toString() : plain(runs);
    }

    private ExprWriter sub() {
        return new ExprWriter(tex, raised);
    }

    // ---- sums and terms

    private void write(Expr e) {
        if (e.kind() == Expr.Kind.ADD) {
            for (int i = 0; i < e.terms().size(); i++) {
                writeSigned(e.terms().get(i), i == 0);
            }
        } else {
            writeSigned(e, true);
        }
    }

    private void writeSigned(Expr term, boolean first) {
        Rational c = coefficient(term);
        if (c != null && c.isNegative()) {
            emit("-", "−");
            writeTerm(negated(term));
        } else {
            if (!first) {
                emit("+");
            }
            writeTerm(term);
        }
    }

    private static Rational coefficient(Expr term) {
        if (term.isNum()) {
            return term.value();
        }
        if (term.kind() == Expr.Kind.MUL && !term.terms().isEmpty() && term.terms().get(0).isNum()) {
            return term.terms().get(0).value();
        }
        return null;
    }

    private static Expr negated(Expr term) {
        if (term.isNum()) {
            return Expr.num(term.value().neg(), term.isDecimal());
        }
        List<Expr> factors = new ArrayList<>(term.terms());
        Expr c = factors.get(0);
        Rational flipped = c.value().neg();
        if (flipped.isOne() && !c.isDecimal() && factors.size() > 1) {
            factors.remove(0);
        } else {
            factors.set(0, Expr.num(flipped, c.isDecimal()));
        }
        return factors.size() == 1 ? factors.get(0) : Expr.mul(factors);
    }

    // splits into top/bottom so x^-1 and 1/2 go under a fraction
    private void writeTerm(Expr term) {
        List<Expr> factors = term.kind() == Expr.Kind.MUL ? term.terms() : List.of(term);
        List<Expr> top = new ArrayList<>();
        List<Expr> bottom = new ArrayList<>();
        for (Expr f : factors) {
            if (f.isNum() && !f.isDecimal() && !f.value().isInteger()) {
                if (f.value().getNum() != 1) {
                    top.add(Expr.num(f.value().getNum()));
                }
                bottom.add(Expr.num(f.value().getDen()));
            } else if (f.kind() == Expr.Kind.POW && f.exponent().isNum() && f.exponent().value().isNegative()) {
                Rational flipped = f.exponent().value().neg();
                bottom.add(flipped.isOne() ? f.base() : Expr.pow(f.base(), Expr.num(flipped, f.exponent().isDecimal())));
            } else {
                top.add(f);
            }
        }
        if (bottom.isEmpty()) {
            writeFactors(top);
        } else if (tex) {
            emit("\\frac{");
            writeTexFractionPart(top.isEmpty() ? List.of(Expr.num(1)) : top);
            emit("}{");
            writeTexFractionPart(bottom);
            emit("}");
        } else {
            writeFractionPart(top.isEmpty() ? List.of(Expr.num(1)) : top);
            emit("/");
            writeFractionPart(bottom);
        }
    }

    // lone sum needs no parens inside \frac
    private void writeTexFractionPart(List<Expr> factors) {
        if (factors.size() == 1 && factors.get(0).kind() == Expr.Kind.ADD) {
            write(factors.get(0));
        } else {
            writeFactors(factors);
        }
    }

    private void writeFractionPart(List<Expr> factors) {
        boolean parens = factors.size() > 1;  // a lone sum brings its own
        if (parens) {
            emit("(");
        }
        writeFactors(factors);
        if (parens) {
            emit(")");
        }
    }

    private void writeFactors(List<Expr> factors) {
        String before = "";
        for (int i = 0; i < factors.size(); i++) {
            Expr f = factors.get(i);
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

    private void separate(String before, String text, Expr next) {
        if (text.isEmpty()) {
            return;
        }
        char c = text.charAt(0);
        if (Character.isDigit(c) || c == '.') {
            emit("\\cdot ", "·");
        } else if (!tex && (isFunction(next) || next.kind() == Expr.Kind.DVAR)) {
            emit(" ");
        } else if (tex && Character.isLetter(c) && before.matches(".*\\\\[a-zA-Z]+")) {
            emit(" ");  // \pi x, not \pix
        }
    }

    private static boolean isFunction(Expr e) {
        return e.kind() == Expr.Kind.FN || e.kind() == Expr.Kind.LOG;
    }

    // ---- single factors

    private void writeFactor(Expr f, boolean bare) {
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

    private void writeGroup(Expr e) {
        emit("(");
        write(e);
        emit(")");
    }

    private void writeNumber(Expr n) {
        Rational r = n.value();
        if (n.isDecimal()) {
            BigDecimal shown = BigDecimal.valueOf(r.getNum())
                    .divide(BigDecimal.valueOf(r.getDen()), DECIMALS, RoundingMode.HALF_UP).stripTrailingZeros();
            emit(shown.signum() == 0 ? "0" : shown.toPlainString().replace("-", tex ? "-" : "−"));
        } else if (r.isInteger()) {
            emit(Long.toString(r.getNum()).replace("-", tex ? "-" : "−"));
        } else {
            writeGroup(n);  // stray fraction, write() splits it
        }
    }

    private void writePower(Expr p) {
        Expr exp = p.exponent();
        if (exp.isNum() && !exp.isDecimal() && exp.value().getNum() == 1 && exp.value().getDen() > 1) {
            writeRoot(p.base(), exp.value().getDen());
            return;
        }
        Expr base = p.base();
        if (isSymbol(base) && !(base.isNum() && base.value().isNegative())) {
            writeFactor(base, true);
        } else {
            writeGroup(base);
        }
        writeRaised(exp);
    }

    private void writeRoot(Expr base, long degree) {
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

    private void writeRaised(Expr exp) {
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

    private void writeFunction(Expr f, boolean bare) {
        if (f.kind() == Expr.Kind.FN) {
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
        Expr arg = f.arg();
        if (bare && isSymbol(arg) && !(arg.isNum() && arg.value().isNegative())) {
            emit(" ");
            writeFactor(arg, true);
        } else {
            writeGroup(arg);
        }
    }

    private String subscript(Expr base) {
        if (base.isNum() && base.value().isInteger() && !base.value().isNegative()) {
            StringBuilder s = new StringBuilder();
            for (char c : Long.toString(base.value().getNum()).toCharArray()) {
                s.append(SUBSCRIPTS.charAt(c - '0'));
            }
            return s.toString();
        }
        ExprWriter w = sub();
        w.writeGroup(base);
        return "_" + w.flat();
    }

    private static boolean isSymbol(Expr e) {
        switch (e.kind()) {
            case VAR:
            case E:
            case PI:
                return true;
            case NUM:
                return e.isDecimal() || e.value().isInteger();
            default:
                return false;
        }
    }
}
