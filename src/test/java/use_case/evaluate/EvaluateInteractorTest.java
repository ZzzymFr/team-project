package use_case.evaluate;

import data_access.InMemoryFunctionDataAccessObject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EvaluateInteractorTest {
    private static final double DELTA = 1e-6;

    private static class FakePresenter implements EvaluateOutputBoundary {
        String kind = "";
        double value;
        boolean isCorrect;
        List<Double> solutions;
        String text;

        @Override
        public void prepareNumberView(double value) {
            kind = "number";
            this.value = value;
        }

        @Override
        public void prepareCheckView(boolean isCorrect, double leftValue, double rightValue) {
            kind = "check";
            this.isCorrect = isCorrect;
        }

        @Override
        public void prepareSolutionsView(String variable, List<Double> solutions) {
            kind = "solutions";
            this.solutions = solutions;
        }

        @Override
        public void prepareAlwaysTrueView(String variable) {
            kind = "alwaysTrue";
        }

        @Override
        public void prepareFunctionDefinedView(String definition) {
            kind = "defined";
            text = definition;
        }

        @Override
        public void prepareFailView(String errorMessage) {
            kind = "fail";
            text = errorMessage;
        }
    }

    private FakePresenter presenter;
    private EvaluateInteractor interactor;

    @BeforeEach
    void setUp() {
        presenter = new FakePresenter();
        interactor = new EvaluateInteractor(new InMemoryFunctionDataAccessObject(), presenter);
    }

    private void run(String expression) {
        interactor.execute(new EvaluateInputData(expression));
    }

    @Test
    void calculatesExpression() {
        run("1+1=");
        assertEquals("number", presenter.kind);
        assertEquals(2, presenter.value, DELTA);
    }

    @Test
    void checksCorrectEquation() {
        run("5+7=12");
        assertEquals("check", presenter.kind);
        assertTrue(presenter.isCorrect);
    }

    @Test
    void checksWrongEquation() {
        run("5+7=13");
        assertEquals("check", presenter.kind);
        assertFalse(presenter.isCorrect);
    }

    @Test
    void roundingErrorsStillCountAsEqual() {
        run("0.1+0.2=0.3");
        assertTrue(presenter.isCorrect);
    }

    @Test
    void solvesLinearEquation() {
        run("2x+3=7");
        assertEquals("solutions", presenter.kind);
        assertEquals(1, presenter.solutions.size());
        assertEquals(2, presenter.solutions.get(0), DELTA);
    }

    @Test
    void solvesQuadraticEquation() {
        run("x^2=4");
        assertEquals(2, presenter.solutions.size());
        assertEquals(-2, presenter.solutions.get(0), DELTA);
        assertEquals(2, presenter.solutions.get(1), DELTA);
    }

    @Test
    void solutionBetweenGridPoints() {
        run("3x=1");
        assertEquals(1, presenter.solutions.size());
        assertEquals(1.0 / 3, presenter.solutions.get(0), DELTA);
    }

    @Test
    void findsSolutionThatOnlyTouchesZero() {
        run("(x-1)^2=0");
        assertEquals(1, presenter.solutions.size());
        assertEquals(1, presenter.solutions.get(0), DELTA);
    }

    @Test
    void flatCurveGivesOneSolution() {
        run("x^{10}=0");
        assertEquals(1, presenter.solutions.size());
        assertEquals(0, presenter.solutions.get(0), DELTA);
    }

    @Test
    void jumpIsNotASolution() {
        run("\\frac{1}{x}=0");
        assertEquals("solutions", presenter.kind);
        assertTrue(presenter.solutions.isEmpty());
    }

    @Test
    void manySolutions() {
        run("\\sin(x)=0");
        assertEquals(63, presenter.solutions.size());
    }

    @Test
    void equationTrueForEveryNumber() {
        run("2(x+1)=2x+2");
        assertEquals("alwaysTrue", presenter.kind);
    }

    @Test
    void definesAndUsesFunction() {
        run("f(x)=x^2+1");
        assertEquals("defined", presenter.kind);
        assertEquals("f(x) = x^2+1", presenter.text);

        run("f(3)");
        assertEquals("number", presenter.kind);
        assertEquals(10, presenter.value, DELTA);

        run("f(x)=10");
        assertEquals("defined", presenter.kind);
        run("f(5)=10");
        assertTrue(presenter.isCorrect);
    }

    @Test
    void solvesEquationWithFunction() {
        run("g(t)=2t");
        run("g(y)+1=9");
        assertEquals("solutions", presenter.kind);
        assertEquals(4, presenter.solutions.get(0), DELTA);
    }

    @Test
    void functionCannotUseItself() {
        run("f(x)=x+1");
        run("f(x)=f(x)+1");
        assertEquals("fail", presenter.kind);
    }

    @Test
    void functionCanOnlyUseItsParameter() {
        run("f(x)=x+y");
        assertEquals("fail", presenter.kind);
    }

    @Test
    void unknownVariableWithoutEquation() {
        run("x+1");
        assertEquals("fail", presenter.kind);
        assertNotNull(presenter.text);
    }

    @Test
    void tooManyUnknowns() {
        run("x+y=3");
        assertEquals("fail", presenter.kind);
    }

    @Test
    void badInput() {
        run("");
        assertEquals("fail", presenter.kind);
        run("1=2=3");
        assertEquals("fail", presenter.kind);
        run("=5");
        assertEquals("fail", presenter.kind);
        run("2x+=7");
        assertEquals("fail", presenter.kind);
    }
}
