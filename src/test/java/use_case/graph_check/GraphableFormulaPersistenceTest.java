package use_case.graph_check;

import entity.LatexFunctionParser;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/** A graphable relation may be retained even if it has no numerical Evaluate result. */
class GraphableFormulaPersistenceTest {
    @Test void linearGraphCanBeRecognizedAsValidFormula() {
        var graph = new GraphCheckInteractor(new LatexFunctionParser());
        assertTrue(graph.canGraph("y=3x"));
        assertTrue(graph.canGraph("y=3x+2"));
    }
    @Test void invalidGraphAndRandomDotAreNotAccepted() {
        var graph = new GraphCheckInteractor(new LatexFunctionParser());
        assertFalse(graph.canGraph("."));
        assertFalse(graph.canGraph("y="));
    }
}
