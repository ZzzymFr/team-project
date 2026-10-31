package use_case.evaluate;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

// the model writes f' as f^{'} or f^{\prime} sometimes
class PrimeSpellingTest {
    private Calc defined() {
        Calc c = new Calc();
        c.show("f(y)=3y^{2}");
        return c;
    }

    @Test
    void superscriptPrimeIsADerivative() {
        assertEquals("= 12", defined().show("f^{'}(2)="));
        assertEquals("= 6", defined().show("f^{''}(2)="));
    }

    @Test
    void primeCommandIsADerivative() {
        assertEquals("= 12", defined().show("f^{\\prime}(2)="));
        assertEquals("= 12", defined().show("f^\\prime(2)="));
        assertEquals("= 6", defined().show("f^{\\prime\\prime}(2)="));
        assertEquals("= 12", defined().show("f\\prime(2)="));
    }

    @Test
    void stillNeedsAPoint() {
        assertEquals(Calc.once("f'(y)="), Calc.once("f^{'}(y)="));
        String withF = defined().show("f^{'}(y)=");
        assertEquals(defined().show("f'(y)="), withF);
    }

    @Test
    void plainPowersUntouched() {
        assertEquals("= 9", Calc.once("3^{2}="));
    }
}
