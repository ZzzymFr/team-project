package view.canvas;

import interface_adapter.MathLabState;
import interface_adapter.MathLabViewModel;
import interface_adapter.evaluate.EvaluateController;

import javax.swing.SwingUtilities;
import java.beans.PropertyChangeEvent;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

// background jobs for the answer page, callbacks land on the EDT
final class AnswerWorker {
    private static final String CANT_READ = "Couldn't read the formula.";
    private final EvaluateController controller;
    // one at a time so jobs don't fight over the view model
    private final ExecutorService executor = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "answer-worker");
        t.setDaemon(true);
        return t;
    });
    private Consumer<CalculationOutcome> waiting;  // job waiting on RESULT, worker thread only

    AnswerWorker(EvaluateController controller, MathLabViewModel viewModel) {
        this.controller = controller;
        viewModel.addPropertyChangeListener(this::viewModelChanged);
    }

    // done gets (latex, problem), one of them null
    void recognize(Callable<String> reader, BiConsumer<String, String> done) {
        executor.execute(() -> {
            String latex = null;
            String problem = null;
            try {
                latex = reader.call();  // fails if recognition is not connected
                if (latex == null || latex.isBlank()) {
                    problem = CANT_READ;
                }
            } catch (Throwable e) {  // native model can throw Errors too, dont show their guts
                problem = CANT_READ;
            }
            String l = latex;
            String p = problem;
            SwingUtilities.invokeLater(() -> done.accept(l, p));  // hop to EDT
        });
    }

    void calculate(String latex, Consumer<CalculationOutcome> done) {
        executor.execute(() -> {
            waiting = done;
            try {
                controller.execute(latex);
            } catch (Throwable e) {
                finish(CalculationOutcome.failed("Uncalculable"));
            }
        });
    }

    private void viewModelChanged(PropertyChangeEvent event) {
        if (!MathLabViewModel.RESULT.equals(event.getPropertyName()) || waiting == null) {
            return;
        }
        MathLabState state = (MathLabState) event.getNewValue();
        finish(new CalculationOutcome(state.getResultText(), state.getAnswerKind(), state.getAnswerText(),
                state.getAnswerRuns(), state.getErrorText(), state.isDefinition()));
    }

    private void finish(CalculationOutcome outcome) {
        Consumer<CalculationOutcome> done = waiting;
        waiting = null;
        SwingUtilities.invokeLater(() -> done.accept(outcome));
    }
}
