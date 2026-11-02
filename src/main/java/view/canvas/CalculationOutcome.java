package view.canvas;

import interface_adapter.TextRun;
import interface_adapter.AnswerKind;

import java.util.List;

// what one Calculate came back with
final class CalculationOutcome {
    final String result;
    final AnswerKind kind;
    final String answerText;
    final List<TextRun> answerRuns;  // null = plain text
    final String error;
    final boolean definition;

    CalculationOutcome(String result, AnswerKind kind, String answerText, List<TextRun> answerRuns, String error,
                       boolean definition) {
        this.result = result;
        this.kind = kind;
        this.answerText = answerText;
        this.answerRuns = answerRuns;
        this.error = error;
        this.definition = definition;
    }

    static CalculationOutcome failed(String error) {
        return new CalculationOutcome("", AnswerKind.RIGHT, "", null, error, false);
    }
}
