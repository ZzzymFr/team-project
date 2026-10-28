package use_case.evaluate;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class UncalculableReasonTest {
    private static void reason(String reason, String... inputs) {
        for (String input : inputs) {
            assertEquals("Uncalculable — " + reason, Calc.once(input), input);
        }
    }

    @Test
    void notMath() {
        reason("Contains text", "\\text{Let }A", "\\square", "\\square+1=3", "\\begin{pmatrix}1\\end{pmatrix}");
        reason("Has \"…\"", "1+...+n", "v=(x_1,...x_n)");
        reason("Not a calculation", "f:\\mathbb{R}\\rightarrow\\mathbb{R}", "1;2", "a:b");
        reason("Integrals not supported", "\\int_0^1 x dx");
        reason("∂ needs a function", "\\partial x");
    }

    @Test
    void derivatives() {
        reason("Function not defined", "z'(1)", "f'(x)=\\cos(e^{x})");
        Calc calc = new Calc();
        calc.show("f(x)=x^2");
        assertEquals("Uncalculable — Max 4th derivative", calc.show("f^{(5)}(3)"));
        reason("Max 4th derivative", "\\frac{d^5}{dx^5}x=1");
        reason("sin⁻¹ is ambiguous", "\\sin^{-1} 1");
    }

    @Test
    void sums() {
        reason("Sum needs a range", "\\sum x_i", "\\prod");
        reason("Bounds must be integers", "\\sum_{i=1.5}^{3}i", "\\sum_{i=1}^{3.5}i");
        reason("Over 1,000,000 terms", "\\sum_{i=1}^{1000001}1");
        reason("Series too slow to tell", "\\sum_{n=1}^{\\infty}\\frac{1}{n^{1.01}}");
    }

    @Test
    void plusMinus() {
        reason("Max 4 ± signs", "1\\pm1\\pm1\\pm1\\pm1\\pm1");
        reason("± not allowed here", "\\sum_{i=1}^{3\\pm1}i", "g(x)=x\\pm1", "\\forall x, x\\pm1>0");
    }

    @Test
    void numbers() {
        reason("Number too big", "10^{400}", "200!", "\\prod_{i=1}^{200}i");
    }

    @Test
    void wrongKinds() {
        reason("Wrong kind of value", "\\{1\\}+1", "\\top+1", "(1,2)^2", "\\sqrt{(1,2)}");
        reason("Logic needs true/false", "3\\land2", "1=1\\land2", "1\\oplus2", "1\\rightarrow2");
        reason("Vector sizes differ", "(1,2)+(1,2,3)");
        reason("× needs 3D vectors", "(1,2)\\times(3,4)");
        reason("Zero vector has no direction", "\\hat{(0,0)}");
        reason("Has an undefined part", "(\\frac{1}{0},1)", "\\{\\sqrt{-1}\\}");
    }

    @Test
    void sets() {
        reason("Infinite set", "\\{1\\}\\cup\\mathbb{N}");
        reason("A set has no value", "\\mathbb{R}");
        reason("Unknown set", "\\mathbb{X}", "\\forall x\\in\\mathbb{R}^{n}");
        reason("Can't tell if rational", "\\pi\\in\\mathbb{Q}");
    }

    @Test
    void quantifiers() {
        reason("Nothing to check", "\\exists x\\in\\mathbb{R}", "\\forall x\\in\\mathbb{R},");
        reason("Can't check over ℝⁿ", "\\forall x\\in\\mathbb{R}^2, x=x");
    }

    @Test
    void solving() {
        reason("Too many solutions", "\\sin x>0");
        reason("Changes too fast", "\\sin(100\\pi x)=0");
        reason("Answer beyond ±100", "x>500", "x<-500");
    }

    @Test
    void workAndPrecision() {
        reason("Formula too complex", "\\sum_{i=1}^{1000000}\\sum_{j=1}^{1000000}1");
        reason("Sets too large", "\\{x,1,2,3,4\\}=\\{1,2,3,4,5\\}");
        reason("Can't decide", "\\forall x\\in\\mathbb{Q}, x^2\\neq2");
        Calc calc = new Calc();
        calc.show("s(x)=\\sin x");
        assertEquals("Uncalculable — Derivative unstable", calc.show("s'(10^{12})"));
        calc.show("T(x,y)=x+y");
        assertEquals("Uncalculable — Not a function input", calc.show("\\frac{\\partial T}{\\partial z}(1,2)"));
    }

    @Test
    void limits() {
        reason("Limit unclear", "\\lim_{x\\rightarrow\\infty}\\ln\\ln x");
    }
}
