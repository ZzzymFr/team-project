package use_case.evaluate;

import data_access.InMemoryFunctionDataAccessObject;
import entity.Interval;
import entity.value.Value;
import entity.value.Values;
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
        List<Value> values;
        List<Double> solutions;
        String text;

        @Override
        public void prepareValuesView(List<Value> values) {
            kind = "values";
            this.values = values;
        }

        @Override
        public void prepareSolutionsView(String variable, List<Double> solutions) {
            kind = "solutions";
            this.solutions = solutions;
        }

        @Override
        public void prepareIntervalsView(String variable, List<Interval> intervals) {
            kind = "intervals";
        }

        @Override
        public void prepareExcludedView(String variable, List<Double> excluded) {
            kind = "excluded";
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
        assertEquals("values", presenter.kind);
        assertEquals(2, Values.toNumber(presenter.values.get(0)), DELTA);
    }

    @Test
    void checksCorrectEquation() {
        run("5+7=12");
        assertEquals("values", presenter.kind);
        assertTrue(Values.toBoolean(presenter.values.get(0)));
    }

    @Test
    void checksWrongEquation() {
        run("5+7=13");
        assertEquals("values", presenter.kind);
        assertFalse(Values.toBoolean(presenter.values.get(0)));
    }

    @Test
    void roundingErrorsStillCountAsEqual() {
        run("0.1+0.2=0.3");
        assertTrue(Values.toBoolean(presenter.values.get(0)));
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
        assertEquals("values", presenter.kind);
        assertEquals(10, Values.toNumber(presenter.values.get(0)), DELTA);

        run("f(x)=10");
        assertEquals("defined", presenter.kind);
        run("f(5)=10");
        assertTrue(Values.toBoolean(presenter.values.get(0)));
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
        run("1=x=3");
        assertEquals("fail", presenter.kind);
        run("=5");
        assertEquals("fail", presenter.kind);
        run("2x+=7");
        assertEquals("fail", presenter.kind);
    }

    @Test
    void showsMathAnswersOnly() {
        assertEquals("true", Calc.once("5+7=12"));
        assertEquals("false", Calc.once("5+7=13"));
        assertEquals("= 2", Calc.once("1+1="));
        assertEquals("= undefined", Calc.once("1/0"));
        assertEquals("Uncalculable", Calc.withoutReason(Calc.once("10^{400}")));
        assertEquals("Uncalculable", Calc.withoutReason(Calc.once("200!")));
        assertEquals("no solution", Calc.once("x^2=-1"));
        assertEquals("true for all x", Calc.once("2(x+1)=2x+2"));
        assertEquals("Uncalculable", Calc.withoutReason(Calc.once("\\text{hi}")));
    }

    @Test
    void keepsExistingMessages() {
        assertEquals("Please enter an expression.", Calc.once(""));
        assertEquals("Please enter an expression.", Calc.once("="));
        assertEquals("There is nothing on the left of \"=\".", Calc.once("=5"));
        assertEquals("There is nothing on the right of \"=\".", Calc.once("5=="));
        assertEquals("Please use only one \"=\".", Calc.once("1=x=3"));
        assertEquals("false", Calc.once("1=2=3"));
        assertEquals("Unknown variable \"x\". To solve for it, write an equation such as x+1=3.",
                Calc.once("x+1"));
        assertEquals("This equation has more than one unknown (x, y). Only one unknown is supported.",
                Calc.once("x+y=3"));
        assertEquals("Please write a formula after \"f(x)=\".", Calc.once("f(x)=="));
        assertEquals("The formula of f(x) can only use the variable x, but it uses y.", Calc.once("f(x)=x+y"));
        assertEquals("x = -2 or x = 2", Calc.once("x^2=4"));
    }

    @Test
    void functionsWorkThroughCalc() {
        Calc calc = new Calc();
        assertEquals("Defined f(x) = x^2+1", calc.show("f(x)=x^2+1"));
        assertEquals("= 10", calc.show("f(3)"));
        assertEquals("Write f(...) to use the function f.", calc.show("f+1"));
        assertEquals("The formula of f(x) can only use the variable x, but it uses f.", calc.show("f(x)=f(x)+1"));
        assertEquals("x = -3 or x = 3", calc.show("f(x)+1=11"));
    }
}
