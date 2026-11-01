package use_case.evaluate;

import java.util.List;

public interface EvaluateOutputBoundary {
    void prepareValuesView(List<ValueOutputData> values);

    void prepareSolutionsView(String variable, List<Double> solutions);

    void prepareIntervalsView(String variable, List<IntervalOutputData> intervals);

    void prepareExcludedView(String variable, List<Double> excluded);

    void prepareAlwaysTrueView(String variable);

    void prepareFunctionDefinedView(String definition);

    void prepareExpressionView(ExpressionOutputData expression);

    void prepareFailView(String errorMessage);
}
