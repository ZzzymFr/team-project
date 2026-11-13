package entity;

import java.util.List;

public class MathSyntaxException extends RuntimeException {
    private final SyntaxProblem problem;  // null for uncalculable
    private final List<String> details;   // raw tokens/names, in the order the words need them

    public MathSyntaxException(SyntaxProblem problem, String... details) {
        super(problem + (details.length == 0 ? "" : " " + List.of(details)));
        this.problem = problem;
        this.details = List.of(details);
    }

    protected MathSyntaxException(String message) {
        super(message);
        this.problem = null;
        this.details = List.of();
    }

    public SyntaxProblem getProblem() {
        return problem;
    }

    public List<String> getDetails() {
        return details;
    }
}
