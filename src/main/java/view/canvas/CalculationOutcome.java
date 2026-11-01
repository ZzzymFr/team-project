package view.canvas;

import interface_adapter.TextRun;

import java.util.List;

// what one Calculate came back with
final class CalculationOutcome {
    final String result;
    final List<TextRun> runs;  // null = plain text
    final String error;
    final boolean definition;

    CalculationOutcome(String result, List<TextRun> runs, String error, boolean definition) {
        this.result = result;
        this.runs = runs;
        this.error = error;
        this.definition = definition;
    }
}
