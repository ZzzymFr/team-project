package interface_adapter.evaluate;

import interface_adapter.MathLabState;
import interface_adapter.MathLabViewModel;
import interface_adapter.TextRun;
import use_case.evaluate.EvaluateOutputBoundary;
import use_case.evaluate.ExprOutputData;
import use_case.evaluate.IntervalOutputData;
import use_case.evaluate.UncalculableOutputData;
import use_case.evaluate.ValueOutputData;

import java.util.List;

// just fills the view model, text comes from AnswerFormatter
public class EvaluatePresenter implements EvaluateOutputBoundary {
    private static final int MAX_ANSWER_LENGTH = 60;  // more than this wont fit on the canvas

    private final MathLabViewModel viewModel;

    public EvaluatePresenter(MathLabViewModel viewModel) {
        this.viewModel = viewModel;
    }

    @Override
    public void prepareValuesView(List<ValueOutputData> values) {
        showResult(AnswerFormatter.values(values));
    }

    @Override
    public void prepareSolutionsView(String variable, List<Double> solutions) {
        showResult(AnswerFormatter.solutions(variable, solutions));
    }

    @Override
    public void prepareIntervalsView(String variable, List<IntervalOutputData> intervals) {
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
    public void prepareExpressionView(ExprOutputData expression) {
        List<TextRun> runs = ExprWriter.runs(expression);
        if (TextRun.plain(runs).length() > MAX_ANSWER_LENGTH) {
            prepareFailView(UncalculableText.answerTooLong());
            return;
        }
        publish("= " + ExprWriter.latex(expression), runs, "", false);
    }

    @Override
    public void prepareUncalculableView(UncalculableOutputData why) {
        prepareFailView(UncalculableText.of(why.getReason(), why.getDetail()));
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
