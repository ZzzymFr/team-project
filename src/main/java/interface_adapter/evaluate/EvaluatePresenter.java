package interface_adapter.evaluate;

import interface_adapter.AnswerKind;
import interface_adapter.MathLabState;
import interface_adapter.MathLabViewModel;
import interface_adapter.TextRun;
import use_case.evaluate.EvaluateOutputBoundary;
import use_case.evaluate.ExprOutputData;
import use_case.evaluate.FormulaShape;
import use_case.evaluate.IntervalOutputData;
import use_case.evaluate.InvalidOutputData;
import use_case.evaluate.UncalculableOutputData;
import use_case.evaluate.ValueOutputData;

import java.util.List;

// just fills the view model, text comes from AnswerFormatter
public class EvaluatePresenter implements EvaluateOutputBoundary {
    private static final int MAX_ANSWER_LENGTH = 60;  // derivative longer than this wont fit, numbers cant get there

    private final MathLabViewModel viewModel;
    private AnswerKind kind = AnswerKind.RIGHT;

    public EvaluatePresenter(MathLabViewModel viewModel) {
        this.viewModel = viewModel;
    }

    @Override
    public void prepareFormulaShape(FormulaShape shape) {
        kind = AnswerKind.of(shape);
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
            fail(UncalculableText.answerTooLong());
            return;
        }
        publish("= " + ExprWriter.latex(expression), AnswerFormatter.canvasRuns(kind, runs), "", false);
    }

    @Override
    public void prepareUncalculableView(UncalculableOutputData why) {
        fail(UncalculableText.of(why.getReason(), why.getDetail()));
    }

    @Override
    public void prepareInvalidView(InvalidOutputData why) {
        fail(InvalidText.of(why.getProblem(), why.getDetails()));
    }

    private void fail(String errorText) {
        publish("", null, errorText, false);
    }

    private void showResult(String text) {
        // 5×10^20 gets its 20 raised
        publish(text, PowerOfTen.runs(AnswerFormatter.canvasText(kind, text)), "", false);
    }

    // canvasRuns null = plain text answer
    private void publish(String resultText, List<TextRun> canvasRuns, String errorText, boolean definition) {
        MathLabState state = viewModel.getState();
        state.setResultText(resultText);
        state.setAnswerKind(kind);
        state.setAnswerText(AnswerFormatter.canvasText(kind, resultText));
        state.setAnswerRuns(canvasRuns);
        state.setErrorText(errorText);
        state.setDefinition(definition);
        viewModel.firePropertyChanged(MathLabViewModel.RESULT);
    }
}
