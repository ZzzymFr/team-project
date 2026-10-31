package use_case.evaluate;

import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTimeout;
import static org.junit.jupiter.api.Assertions.assertTrue;

class QuantifierTest {
    @Test
    void quantifiers() {
        assertEquals("true", Calc.once("\\forall x\\in\\mathbb{R}, x^2\\geq0"));
        assertEquals("false", Calc.once("\\forall x\\in\\mathbb{R}, x^2>0"));
        assertEquals("true", Calc.once("\\exists x\\in\\mathbb{R}, x^2=4"));
        assertEquals("true", Calc.once("\\exists x\\in\\mathbb{R}, x^2=2"));
        assertEquals("false", Calc.once("\\exists x\\in\\mathbb{R}, x^2=-1"));
        assertEquals("false", Calc.once("\\forall x\\in\\mathbb{R}, x^2\\neq2"));
        assertEquals("true", Calc.once("\\forall x\\in\\{1,2,3\\}, x>0"));
        assertEquals("true", Calc.once("\\exists x\\in\\{1,2,3\\}, x>2"));
        assertEquals("true", Calc.once("\\forall n\\in\\mathbb{N}, n\\geq0"));
        assertEquals("false", Calc.once("\\forall n\\in\\mathbb{Z}, n\\geq0"));
        assertEquals("true", Calc.once("\\exists n\\in\\mathbb{N}, n^2=49"));
        assertEquals("true", Calc.once("\\forall x, x+0=x"));
        assertEquals("true", Calc.once("\\forall x\\in\\mathbb{R}: x^2\\geq0"));
        assertEquals("true", Calc.once("\\forall x\\in\\mathbb{R}(x^2\\geq0)"));
        assertEquals("true", Calc.once("\\forall x\\in\\mathbb{R} x^2\\geq0"));
        assertEquals("true", Calc.once("\\neg\\forall x\\in\\mathbb{R}, x^2>0"));
        assertEquals("false", Calc.once("\\forall x\\in\\mathbb{R}, \\frac{1}{x}\\neq0"));
        assertEquals("Uncalculable", Calc.withoutReason(Calc.once("\\forall x\\in\\mathbb{R}^{n}")));
        assertEquals("Uncalculable", Calc.withoutReason(Calc.once("\\forall v\\in\\mathbb{R}^2, v=v")));
        assertTimeout(Duration.ofSeconds(2), () -> {
            assertEquals("true", Calc.once("\\forall x\\in\\mathbb{R}, \\exists y\\in\\mathbb{R}, y>x"));
            assertEquals("false", Calc.once("\\exists y\\in\\mathbb{R}, \\forall x\\in\\mathbb{R}, y>x"));
        });
    }

    @Test
    void freeVariableOutsideQuantifierIsSolvedFor() {
        String answer = Calc.once("\\forall x\\in\\mathbb{R}, x^2\\geq a");
        assertTrue(answer.equals("a ≤ 0") || answer.startsWith("Uncalculable"), answer);
    }

    @Test
    void neverClaimsWhatSamplingCannotShow() {
        assertEquals("Uncalculable", Calc.withoutReason(Calc.once("\\exists x\\in\\mathbb{Q}, x^2=2")));
        assertEquals("Uncalculable", Calc.withoutReason(Calc.once("\\forall x\\in\\mathbb{Q}, x^2\\neq2")));
        assertEquals("true", Calc.once("\\exists x\\in\\mathbb{Q}, x^2=4"));
        assertEquals("Uncalculable", Calc.withoutReason(Calc.once("\\exists z\\in\\mathbb{C}, z^2=-1")));
        assertEquals("true", Calc.once("\\exists z\\in\\mathbb{C}, z^2=4"));
        assertEquals("false", Calc.once("\\forall z\\in\\mathbb{C}, z^2>0"));
        assertEquals("Uncalculable", Calc.withoutReason(Calc.once("\\frac{1}{0}=1")));
        assertEquals("Uncalculable", Calc.withoutReason(Calc.once("\\forall x\\in5, x>0")));
    }

    @Test
    void innerSamplesReachPastOuterValues() {
        assertEquals("true", Calc.once("\\forall x\\in\\mathbb{Z}, \\exists y\\in\\mathbb{Z}, y>x"));
        assertEquals("true", Calc.once("\\forall n\\in\\mathbb{N}, \\exists m\\in\\mathbb{N}, m>n"));
        assertEquals("true", Calc.once("\\forall x\\in\\mathbb{R}, \\exists y\\in\\mathbb{R}, y>2x"));
        assertEquals("true", Calc.once("\\forall x, \\forall y, (x+y)-y=x"));
    }

    @Test
    void plusMinusInQuantifierIsUncalculable() {
        assertEquals("Uncalculable", Calc.withoutReason(Calc.once("\\forall x\\in\\mathbb{R}, x^2\\pm1\\geq0")));
        assertEquals("Uncalculable", Calc.withoutReason(Calc.once("\\exists x\\in\\{1\\pm1\\}, x=0")));
    }

    @Test
    void undecidableInnerVerdictsAreUncalculable() {
        assertEquals("Uncalculable", Calc.withoutReason(Calc.once("\\neg\\exists x\\in\\mathbb{R}, \\exists z\\in\\mathbb{C}, z^2=-1")));
        assertEquals("Uncalculable", Calc.withoutReason(Calc.once("\\forall x\\in\\mathbb{R}, \\exists z\\in\\mathbb{C}, z^2=x")));
        assertEquals("Uncalculable", Calc.withoutReason(Calc.once("\\exists x\\in\\{1,2\\}, \\exists z\\in\\mathbb{C}, z^2=-x")));
        assertEquals("Uncalculable", Calc.withoutReason(Calc.once("\\exists x\\in\\{2\\}, \\forall q\\in\\mathbb{Q}, q^2\\neq x")));
        assertEquals("Uncalculable", Calc.withoutReason(Calc.once("\\forall x\\in\\mathbb{R}, \\exists v\\in\\mathbb{R}^2, x=x")));
        assertEquals("Uncalculable", Calc.withoutReason(Calc.once("\\forall x\\in\\mathbb{R}, x+1")));
    }

    @Test
    void wideRootScanFindsFarWitnesses() {
        assertEquals("true", Calc.once("\\exists x\\in\\mathbb{R}, x=150"));
        assertEquals("true", Calc.once("\\forall x\\in\\mathbb{R}, \\exists y\\in\\mathbb{R}, y=2x"));
        assertEquals("true", Calc.once("\\forall x\\in\\mathbb{R}, \\exists y\\in\\mathbb{R}, y=x+5"));
        assertEquals("true", Calc.once("\\forall x\\in\\mathbb{R}, \\exists y\\in\\mathbb{R}, y>x^2"));
        assertEquals("true", Calc.once("\\forall x\\in\\mathbb{R}, \\exists y\\in\\mathbb{R}, y>x^3"));
        assertEquals("false", Calc.once("\\forall x\\in\\mathbb{R}, x<10^{9}"));
        assertEquals("false", Calc.once("\\forall n\\in\\mathbb{N}, n<2000"));
    }

    @Test
    void wideRootScanOverIntegers() {
        assertTimeout(Duration.ofSeconds(2), () -> {
            assertEquals("true", Calc.once("\\forall n\\in\\mathbb{N}, \\exists m\\in\\mathbb{N}, m=2n"));
            assertEquals("true", Calc.once("\\forall n\\in\\mathbb{N}, \\exists m\\in\\mathbb{N}, m>n^2"));
            assertEquals("true", Calc.once("\\forall x\\in\\mathbb{Z}, \\exists y\\in\\mathbb{Z}, y=x^2"));
        });
        assertTimeout(Duration.ofSeconds(2), () -> {
            String answer = Calc.once("\\forall x\\in\\mathbb{Z}, \\forall y\\in\\mathbb{Z}, \\exists z\\in\\mathbb{Z}, z=x+y");
            assertTrue(answer.equals("true") || answer.startsWith("Uncalculable"), answer);
        });
    }

    @Test
    void overflowingFarSamplesGiveNoVerdict() {
        assertEquals("true", Calc.once("\\forall x\\in\\mathbb{R}, e^x>0"));
        assertEquals("true", Calc.once("\\forall x, 2^x>0"));
        assertEquals("false", Calc.once("\\exists x\\in\\mathbb{R}, e^x=0"));
        assertEquals("true", Calc.once("\\forall x\\in\\mathbb{R}, \\ln(e^x)=x"));
    }

    @Test
    void rootProbesSitOutsideTheComparisonTolerance() {
        assertEquals("true", Calc.once("\\forall x\\in\\mathbb{R}, x>0\\Rightarrow x^2>0"));
        assertEquals("true", Calc.once("\\forall x\\in\\mathbb{R}, x\\neq0\\Rightarrow x^2>0"));
        assertEquals("true", Calc.once("\\forall x\\in\\mathbb{R}, x^2=0\\Rightarrow x=0"));
    }

    @Test
    void boundEShadowsEulersNumber() {
        assertEquals("true", Calc.once("\\exists e\\in\\mathbb{R}, e<0"));
        assertEquals("true", Calc.once("\\forall x\\in\\{1\\}, e>2"));
    }

    @Test
    void computedRootsAreEvidenceNotNoise() {
        assertEquals("true", Calc.once("\\exists x\\in\\mathbb{R}, x^2-2=0"));
        assertEquals("true", Calc.once("\\exists x\\in\\mathbb{R}, \\cos(x)=0"));
        assertEquals("true", Calc.once("\\exists x\\in\\mathbb{R}, x^3-x-1=0"));
        assertEquals("true", Calc.once("\\exists x\\in\\mathbb{R}, x^2-3x+1=0"));
        assertEquals("true", Calc.once("\\exists x\\in\\mathbb{R}, x^2-0.1=0"));
        assertEquals("true", Calc.once("\\exists x\\in\\mathbb{R}, x^2=0.1"));
        assertEquals("true", Calc.once("\\exists x\\in\\mathbb{R}, x-0.3=0"));
        assertEquals("true", Calc.once("\\exists x\\in\\mathbb{R}, 3x-1=0"));
        assertEquals("false", Calc.once("\\forall x\\in\\mathbb{R}, x^2-2\\neq0"));
        assertEquals("false", Calc.once("\\forall x\\in\\mathbb{R}, \\cos(x)\\neq0"));
    }

    @Test
    void zeroSidesAtFarRootsAreEvidence() {
        assertTimeout(Duration.ofSeconds(2), () -> {
            assertEquals("true", Calc.once("\\forall x\\in\\mathbb{R}, \\exists y\\in\\mathbb{R}, x+y=0"));
            assertEquals("true", Calc.once("\\forall x\\in\\{1000\\}, \\exists y\\in\\mathbb{R}, x+y=0"));
            assertEquals("true", Calc.once("\\forall x\\in\\mathbb{R}, \\exists y\\in\\mathbb{R}, x-y=0"));
            assertEquals("true", Calc.once("\\exists x\\in\\mathbb{R}, x-150=0"));
            assertEquals("true", Calc.once("\\exists x\\in\\mathbb{R}, x^2-40000=0"));
            assertEquals("true", Calc.once("\\exists n\\in\\mathbb{Z}, n-1500=0"));
            assertEquals("true", Calc.once("\\forall n\\in\\mathbb{N}, \\exists m\\in\\mathbb{N}, m-2n=0"));
            assertEquals("false", Calc.once("\\forall x\\in\\mathbb{R}, x-150\\neq0"));
        });
    }

    @Test
    void roundingNoiseIsNotARoot() {
        assertEquals("true", Calc.once("\\exists x\\in\\mathbb{R}, 10^{-12}x=1"));
        assertEquals("true", Calc.once("\\forall x, \\forall y, (x+y)^2-x^2-2xy-y^2=0"));
        assertEquals("false", Calc.once("\\exists x\\in\\mathbb{R}, e^{-x}=0"));
    }

    @Test
    void absorbedSidesAtFarSamplesGiveNoVerdict() {
        assertEquals("true", Calc.once("\\forall x\\in\\mathbb{R}, x+1>x"));
        assertEquals("true", Calc.once("\\forall x\\in\\mathbb{R}, x^2+1>x^2"));
        assertEquals("false", Calc.once("\\exists x\\in\\mathbb{R}, x+1=x"));
        assertEquals("true", Calc.once("\\forall x\\in\\mathbb{R}, 10^4x^2+1>10^4x^2"));
        assertEquals("true", Calc.once("\\forall x\\in\\mathbb{R}, 10^6x\\neq10^6x+10^{-3}"));
        assertEquals("true", Calc.once("\\forall x, x=x"));
        assertEquals("true", Calc.once("\\forall x, x+1-x=1"));
    }

    @Test
    void absorbedSumsAtNearSamplesGiveNoVerdict() {
        assertEquals("true", Calc.once("\\forall x\\in\\mathbb{R}, e^{x}+1>e^{x}"));
        assertEquals("false", Calc.once("\\exists x\\in\\mathbb{R}, e^x+1=e^x"));
        assertEquals("false", Calc.once("\\exists x\\in\\mathbb{R}, 1+e^{-x}=1"));
        assertEquals("true", Calc.once("\\forall x\\in\\mathbb{R}, 1+e^{-x}>1"));
    }

    @Test
    void absorptionLeavesExactIdentitiesDecided() {
        assertEquals("true", Calc.once("\\forall x, x+1>x"));
        assertEquals("false", Calc.once("\\exists x, x+1=x"));
        assertEquals("true", Calc.once("\\forall x, x=x"));
        assertEquals("true", Calc.once("\\forall x\\in\\mathbb{R}, \\sin(x)^2+\\cos(x)^2=1"));
        assertEquals("true", Calc.once("\\forall x, 0.1x+0.2x=0.3x"));
        assertEquals("true", Calc.once("\\forall x, e^x>0"));
        assertEquals("false", Calc.once("\\exists x, e^x=0"));
        assertEquals("true", Calc.once("\\exists x, x^2=2"));
        assertEquals("false", Calc.once("\\forall n\\in\\mathbb{N}, n<2000"));
        assertTimeout(Duration.ofSeconds(2), () -> {
            assertEquals("true", Calc.once("\\forall x, \\forall y, (x+y)-y=x"));
            assertEquals("true", Calc.once("\\forall x, \\exists y, x+y=0"));
        });
    }

    @Test
    void absorbedSetMembersGiveNoVerdict() {
        assertEquals("Uncalculable", Calc.withoutReason(Calc.once("\\exists x\\in\\{10^{20}\\}, x+1=x")));
        assertEquals("Uncalculable", Calc.withoutReason(Calc.once("\\forall x\\in\\{10^{20}\\}, x+1>x")));
        assertEquals("false", Calc.once("\\forall x\\in\\{10^{20}, 0\\}, x+1=x"));
    }

    @Test
    void absorptionInsideAnInnerQuantifierStaysThere() {
        assertEquals("true", Calc.once("\\exists x\\in\\mathbb{R}, \\forall y\\in\\mathbb{R}, x+y>y-1"));
        assertEquals("true", Calc.once("\\forall x\\in\\{0, 1\\}, \\forall y\\in\\mathbb{R}, x+y>y-1"));
        assertEquals("false", Calc.once("\\forall x\\in\\mathbb{R}, \\exists y\\in\\mathbb{R}, x+y>y-1"));
    }

    @Test
    void underflowAndOverflowAtNearSamplesGiveNoVerdict() {
        assertEquals("false", Calc.once("\\exists x\\in\\mathbb{R}, 1+e^{-x^2}=1"));
        assertEquals("true", Calc.once("\\forall x\\in\\mathbb{R}, 1+e^{-x^2}>1"));
        assertEquals("false", Calc.once("\\exists x\\in\\mathbb{R}, e^{-x^2}=0"));
        assertEquals("true", Calc.once("\\forall x\\in\\mathbb{R}, e^{-x^2}>0"));
        assertEquals("true", Calc.once("\\forall x\\in\\mathbb{R}, \\frac{1}{e^{x^2}}>0"));
    }
}
