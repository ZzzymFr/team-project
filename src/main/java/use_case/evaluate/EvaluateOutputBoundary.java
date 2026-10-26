package use_case.evaluate;

import java.util.List;

public interface EvaluateOutputBoundary {
    void prepareNumberView(double value);

    void prepareCheckView(boolean isCorrect, double leftValue, double rightValue);

    void prepareSolutionsView(String variable, List<Double> solutions);

    void prepareAlwaysTrueView(String variable);

    void prepareFunctionDefinedView(String definition);

    void prepareFailView(String errorMessage);
}
