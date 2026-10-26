package entity.calculus;

import java.math.BigDecimal;
import java.math.MathContext;

public final class Rounding {
    private Rounding() {
    }

    public static double toSignificant(double value, int digits) {
        if (Double.isNaN(value) || Double.isInfinite(value) || value == 0) {
            return value;
        }
        return new BigDecimal(value).round(new MathContext(digits)).doubleValue();
    }
}
