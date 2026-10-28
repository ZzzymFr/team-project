package view.canvas;

import entity.AnswerKind;
import interface_adapter.MathLabState;
import interface_adapter.MathLabViewModel;
import interface_adapter.evaluate.EvaluateController;
import view.FormulaCapture;
import view.glass.GlassButton;
import view.stack.Fonts;

import javax.swing.Box;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;
import javax.swing.Timer;
import java.awt.Color;
import java.awt.FlowLayout;
import java.awt.Font;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BiConsumer;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

// answer page: recognize ink -> evaluate -> write the answer on the canvas
public class AnswerCanvasPage extends CanvasPage {
    static final int RECALCULATE_DELAY_MILLIS = 2000;
    private static final int ERROR_MESSAGE_MILLIS = 7000;
    private static final int DEFINED_MESSAGE_MILLIS = 4000;

    private final EvaluateController controller;
    private final BiConsumer<String, String> pdfResultSink;
    private final Map<Long, Long> exportedRevisions = new HashMap<>();
    private final MathLabViewModel viewModel;
    private final GlassButton calculateButton = new GlassButton("Calculate", 40);
    private final ExecutorService worker;
    private final Map<Long, Timer> recalcTimers = new HashMap<>();
    private int recalculateDelay = RECALCULATE_DELAY_MILLIS;
    private int manualLeft;          // Calculate jobs still running
    private boolean manualMessage;   // a Calculate job put up its own message

    // no evaluate feature, just a placeholder
    public AnswerCanvasPage(SharedCanvas sharedCanvas) {
        super(sharedCanvas);
        this.controller = null;
        this.pdfResultSink = (latex, result) -> {};
        this.viewModel = null;
        this.worker = null;
        setFunctionArea(placeholder("Answer tools go here: evaluate or solve the expression on the canvas."));
    }

    public AnswerCanvasPage(SharedCanvas sharedCanvas, EvaluateController controller, MathLabViewModel viewModel) {
        this(sharedCanvas, controller, viewModel, (latex, result) -> {});
    }

    public AnswerCanvasPage(SharedCanvas sharedCanvas, EvaluateController controller, MathLabViewModel viewModel,
                            BiConsumer<String, String> pdfResultSink) {
        super(sharedCanvas);
        this.pdfResultSink = java.util.Objects.requireNonNull(pdfResultSink);
        this.controller = controller;
        this.viewModel = viewModel;
        // one at a time so jobs don't fight over the view model
        this.worker = Executors.newSingleThreadExecutor(r -> {
            Thread t = new Thread(r, "answer-worker");
            t.setDaemon(true);
            return t;
        });
        sharedCanvas.addFormulaChangeListener(this::formulaChanged);

        calculateButton.setFont(Fonts.ui(Font.PLAIN, 14f));
        calculateButton.addActionListener(e -> calculate());
        JLabel note = new JLabel("Several formulas? Circle one with the Lasso first.");
        note.setFont(Fonts.ui(Font.PLAIN, 13f));
        note.setForeground(new Color(100, 116, 139));
        JPanel tools = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        tools.add(calculateButton);
        tools.add(Box.createHorizontalStrut(6));
        tools.add(note);
        setFunctionArea(tools);
    }

    // tests use a short wait
    void setRecalculateDelay(int millis) {
        recalculateDelay = millis;
    }

    // calc the lassoed formula, else new ink, else everything again
    void calculate() {
        List<FormulaCapture> captures;
        try {
            captures = getSharedCanvas().takeFormulasToCalculate();
        } catch (IllegalStateException e) {
            showMessage(e.getMessage(), true, ERROR_MESSAGE_MILLIS);  // the canvas is empty
            return;
        }
        if (manualLeft == 0) {
            manualMessage = false;
        }
        manualLeft += captures.size();
        showMessage("Reading the formula…", false, 0);
        for (FormulaCapture capture : captures) {
            submit(capture, true);
        }
    }

    // ink of a formula changed: recalc once the user stops for a bit
    private void formulaChanged(long id) {
        Timer timer = recalcTimers.get(id);
        if (timer == null) {
            timer = new Timer(recalculateDelay, e -> recalculate(id));
            timer.setRepeats(false);
            recalcTimers.put(id, timer);
        }
        timer.setInitialDelay(recalculateDelay);
        timer.restart();
    }

    private void recalculate(long id) {
        recalcTimers.remove(id);
        FormulaCapture capture = getSharedCanvas().captureForRecalculation(id);
        if (capture != null) {
            submit(capture, false);
        }
    }

    private void submit(FormulaCapture capture, boolean manual) {
        worker.execute(() -> {
            String latex = null;
            String result = "";
            String error = "";
            try {
                latex = recognizeFormula(capture.getImage());  // fails if recognition is not connected
            } catch (Throwable e) {  // native model can throw Errors too
                error = reason(e);
            }
            if (latex != null) {
                if (manual) {
                    String shown = latex;
                    SwingUtilities.invokeLater(() -> progress("Calculating " + shown + "…"));
                }
                try {
                    controller.execute(latex);  // presenter is sync, state is ready after
                    MathLabState state = viewModel.getState();
                    result = state.getResultText();
                    error = state.getErrorText();
                } catch (Throwable e) {
                    error = reason(e);
                }
            }
            AnswerKind kind = AnswerKind.of(latex);
            String r = result;
            String err = error;
            String recognizedLatex = latex;
            SwingUtilities.invokeLater(() -> deliver(capture, recognizedLatex, kind, r, err, manual));  // hop to EDT
        });
    }

    private void deliver(FormulaCapture capture, String latex, AnswerKind kind, String result, String error, boolean manual) {
        if (!error.isEmpty()) {
            // popup only, nothing drawn
            if (getSharedCanvas().isCurrent(capture.getId(), capture.getRevision())) {
                ownMessage(manual);
                showMessage(error, true, ERROR_MESSAGE_MILLIS);
            }
        } else if (result.startsWith("Defined ")) {
            ownMessage(manual);
            showMessage(result, false, DEFINED_MESSAGE_MILLIS);  // nothing to draw
        } else {
            boolean shown = getSharedCanvas().showAnswer(capture.getId(), capture.getRevision(), kind, kind.answerText(result), false);
            if (shown && latex != null && !latex.isBlank()
                    && !java.util.Objects.equals(exportedRevisions.get(capture.getId()), capture.getRevision())) {
                exportedRevisions.put(capture.getId(), capture.getRevision());
                pdfResultSink.accept(latex, result);
            }
        }
        if (manual && --manualLeft == 0 && !manualMessage) {
            hideMessage();
        }
    }

    private void ownMessage(boolean manual) {
        if (manual) {
            manualMessage = true;
        }
    }

    // progress only while no result message is up
    private void progress(String text) {
        if (manualLeft > 0 && !manualMessage) {
            showMessage(text, false, 0);
        }
    }

    private static String reason(Throwable e) {
        return e.getMessage() == null ? e.toString() : e.getMessage();
    }
}
