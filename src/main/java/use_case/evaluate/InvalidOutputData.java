package use_case.evaluate;

import java.util.List;

// problem = SyntaxProblem name, details = raw tokens. presenter picks the words
public final class InvalidOutputData {
    private final String problem;
    private final List<String> details;

    public InvalidOutputData(String problem, List<String> details) {
        this.problem = problem;
        this.details = List.copyOf(details);
    }

    public String getProblem() {
        return problem;
    }

    public List<String> getDetails() {
        return details;
    }
}
