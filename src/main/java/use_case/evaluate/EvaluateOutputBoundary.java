package use_case.evaluate;

import entity.Interval;
import entity.symbolic.TextRun;
import entity.value.Value;

import java.util.List;

public interface EvaluateOutputBoundary {
    void prepareValuesView(List<Value> values);

    void prepareSolutionsView(String variable, List<Double> solutions);

    void prepareIntervalsView(String variable, List<Interval> intervals);

    void prepareExcludedView(String variable, List<Double> excluded);

    void prepareAlwaysTrueView(String variable);

    void prepareFunctionDefinedView(String definition);

    void prepareFailView(String errorMessage);

    // written-out derivative; old presenters cant show it
    default void prepareExpressionView(String latex, List<TextRun> runs) {
        prepareFailView("Uncalculable");
    }
}
