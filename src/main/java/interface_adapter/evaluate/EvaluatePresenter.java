package interface_adapter.evaluate;

import entity.Interval;
import entity.symbolic.TextRun;
import entity.value.Value;
import interface_adapter.MathLabState;
import interface_adapter.MathLabViewModel;
import use_case.evaluate.EvaluateOutputBoundary;

import java.util.List;

// just fills the view model, text comes from AnswerFormatter
public class EvaluatePresenter implements EvaluateOutputBoundary {
    private final MathLabViewModel viewModel;

    public EvaluatePresenter(MathLabViewModel viewModel) {
        this.viewModel = viewModel;
    }

    @Override
    public void prepareValuesView(List<Value> values) {
        showResult(AnswerFormatter.values(values));
    }

    @Override
    public void prepareSolutionsView(String variable, List<Double> solutions) {
        showResult(AnswerFormatter.solutions(variable, solutions));
    }

    @Override
    public void prepareIntervalsView(String variable, List<Interval> intervals) {
        showResult(AnswerFormatter.intervals(variable, intervals));
    }

    @Override
    public void prepareExcludedView(String variable, List<Double> excluded) {
        showResult(AnswerFormatter.excluded(variable, excluded));
    }

    @Override
    public void prepareAlwaysTrueView(String variable) {
        showResult(AnswerFormatter.alwaysTrue(variable));
    }

    @Override
    public void prepareFunctionDefinedView(String definition) {
        publish(AnswerFormatter.defined(definition), null, "", true);
    }

    @Override
    public void prepareExpressionView(String latex, List<TextRun> runs) {
        publish("= " + latex, runs, "", false);
    }

    @Override
    public void prepareFailView(String errorMessage) {
        publish("", null, errorMessage, false);
    }

    private void showResult(String text) {
        publish(text, null, "", false);
    }

    // runs null = plain text answer
    private void publish(String resultText, List<TextRun> runs, String errorText, boolean definition) {
        MathLabState state = viewModel.getState();
        state.setResultText(resultText);
        state.setResultRuns(runs);
        state.setErrorText(errorText);
        state.setDefinition(definition);
        viewModel.firePropertyChanged(MathLabViewModel.RESULT);
    }
}
