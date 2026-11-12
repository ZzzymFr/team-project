package use_case.graph_check;

import entity.LatexFunctionParser;

// graphable = written as y = ... or f(x) = ... and the graph parser takes it
public class GraphCheckInteractor implements GraphCheckInputBoundary {
    private static final java.util.regex.Pattern FUNCTION_LEFT =
            java.util.regex.Pattern.compile("^\\s*(y|[a-zA-Z]\\s*\\(\\s*x\\s*\\))\\s*=.*", java.util.regex.Pattern.DOTALL);

    private final LatexFunctionParser parser;

    public GraphCheckInteractor(LatexFunctionParser parser) {
        this.parser = parser;
    }

    @Override
    public boolean canGraph(String latex) {
        if (latex == null || !FUNCTION_LEFT.matcher(latex).matches()) {
            return false;
        }
        try {
            parser.parse(latex);
            return true;
        } catch (Exception e) {
            return false;
        }
    }
}
