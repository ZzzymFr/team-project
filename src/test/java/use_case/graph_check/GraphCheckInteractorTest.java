package use_case.graph_check;

import entity.LatexFunctionParser;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GraphCheckInteractorTest {
    private final GraphCheckInteractor check = new GraphCheckInteractor(new LatexFunctionParser());

    @Test
    void functionsCanBeGraphed() {
        assertTrue(check.canGraph("y=x^2"));
        assertTrue(check.canGraph("f(x)=\\sin x"));
    }

    @Test
    void equationsAndPlainSumsCannot() {
        assertFalse(check.canGraph("x^2-4=0"));
        assertFalse(check.canGraph("1+3"));
        assertFalse(check.canGraph(""));
        assertFalse(check.canGraph(null));
    }
}
