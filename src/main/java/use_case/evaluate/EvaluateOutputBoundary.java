package use_case.evaluate;

import java.util.List;

public interface EvaluateOutputBoundary {
    // comes before the answer
    void prepareFormulaShape(FormulaShape shape);

    void prepareValuesView(List<ValueOutputData> values);

    void prepareSolutionsView(String variable, List<Double> solutions);

    void prepareIntervalsView(String variable, List<IntervalOutputData> intervals);

    void prepareExcludedView(String variable, List<Double> excluded);

    void prepareAlwaysTrueView(String variable);

    void prepareFunctionDefinedView(String definition);

    void prepareExpressionView(ExprOutputData expression);

    void prepareUncalculableView(UncalculableOutputData why);

    void prepareInvalidView(InvalidOutputData why);
}
