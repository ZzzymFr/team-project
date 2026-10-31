package entity.symbolic;

// exact p/q, always reduced, q > 0; overflow throws ArithmeticException
public final class Rational implements Comparable<Rational> {
    public static final Rational ZERO = new Rational(0, 1);
    public static final Rational ONE = new Rational(1, 1);

    private final long num;
    private final long den;

    private Rational(long num, long den) {
        this.num = num;
        this.den = den;
    }

    public static Rational of(long value) {
        return new Rational(value, 1);
    }

    public static Rational of(long num, long den) {
        if (den == 0) {
            throw new ArithmeticException("divide by zero");
        }
        if (den < 0) {
            num = Math.negateExact(num);
            den = Math.negateExact(den);
        }
        long g = gcd(Math.abs(num), den);
        return new Rational(num / g, den / g);
    }

    private static long gcd(long a, long b) {
        while (b != 0) {
            long t = a % b;
            a = b;
            b = t;
        }
        return a == 0 ? 1 : a;
    }

    public long getNum() {
        return num;
    }

    public long getDen() {
        return den;
    }

    public Rational add(Rational o) {
        return of(Math.addExact(Math.multiplyExact(num, o.den), Math.multiplyExact(o.num, den)),
                Math.multiplyExact(den, o.den));
    }

    public Rational sub(Rational o) {
        return add(o.neg());
    }

    public Rational mul(Rational o) {
        return of(Math.multiplyExact(num, o.num), Math.multiplyExact(den, o.den));
    }

    public Rational div(Rational o) {
        return of(Math.multiplyExact(num, o.den), Math.multiplyExact(den, o.num));
    }

    public Rational neg() {
        return new Rational(Math.negateExact(num), den);
    }

    public boolean isZero() {
        return num == 0;
    }

    public boolean isOne() {
        return num == 1 && den == 1;
    }

    public boolean isInteger() {
        return den == 1;
    }

    public boolean isNegative() {
        return num < 0;
    }

    public double toDouble() {
        return (double) num / den;
    }

    @Override
    public int compareTo(Rational o) {
        return Long.compare(Math.multiplyExact(num, o.den), Math.multiplyExact(o.num, den));
    }

    @Override
    public boolean equals(Object o) {
        return o instanceof Rational && ((Rational) o).num == num && ((Rational) o).den == den;
    }

    @Override
    public int hashCode() {
        return Long.hashCode(num) * 31 + Long.hashCode(den);
    }

    @Override
    public String toString() {
        return den == 1 ? Long.toString(num) : num + "/" + den;
    }
}
