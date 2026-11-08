package view.canvas;

import interface_adapter.AnswerKind;
import interface_adapter.TextRun;
import interface_adapter.MathLabViewModel;
import interface_adapter.evaluate.EvaluateController;
import view.FormulaCapture;
import view.glass.GlassIcons;
import view.ribbon.Ribbon;
import view.ribbon.RibbonButton;

import javax.swing.JComponent;
import java.awt.image.BufferedImage;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

/**
 * The page: circle a formula with the lasso and it is read at once; the Math tab of the ribbon then
 * shows it, with Calculate (the answer is written on the canvas) and Graph (a card beside it).
 * Writing alone reads nothing.
 */
public class AnswerCanvasPage extends CanvasPage {
    private static final int ERROR_MESSAGE_MILLIS = 7000;
    private static final int DEFINED_MESSAGE_MILLIS = 4000;

    private final BiConsumer<String, String> pdfResultSink;
    private final FormulaGrapher grapher;
    private final Map<Long, Long> exportedRevisions = new HashMap<>();
    private final AnswerWorker worker;
    private final FormulaPreview preview = new FormulaPreview();
    private RibbonButton calculateButton;
    private RibbonButton graphButton;
    private long generation;            // bumps on every new selection, old readings check it
    private String selectedLatex;       // what the circled ink was read as, once known
    private String selectedProblem;     // or why it could not be read
    private Consumer<String> waiting;   // Calculate or Graph pressed before the reading was done
    private int running;                // Calculate jobs still going
    private boolean ownMessage;         // one of them put up its own message

    // no evaluate feature, just a placeholder
    public AnswerCanvasPage(SharedCanvas sharedCanvas) {
        super(sharedCanvas);
        this.pdfResultSink = (latex, result) -> {};
        this.grapher = null;
        this.worker = null;
        setFunctionArea(placeholder("Answer tools go here: evaluate or solve the expression on the canvas."));
    }

    public AnswerCanvasPage(SharedCanvas sharedCanvas, EvaluateController controller, MathLabViewModel viewModel) {
        this(sharedCanvas, controller, viewModel, (latex, result) -> {}, null);
    }

    public AnswerCanvasPage(SharedCanvas sharedCanvas, EvaluateController controller, MathLabViewModel viewModel,
                            BiConsumer<String, String> pdfResultSink) {
        this(sharedCanvas, controller, viewModel, pdfResultSink, null);
    }

    // grapher null = no Graph to press
    public AnswerCanvasPage(SharedCanvas sharedCanvas, EvaluateController controller, MathLabViewModel viewModel,
                            BiConsumer<String, String> pdfResultSink, FormulaGrapher grapher) {
        super(sharedCanvas);
        this.pdfResultSink = java.util.Objects.requireNonNull(pdfResultSink);
        this.grapher = grapher;
        this.worker = new AnswerWorker(controller, viewModel);
        sharedCanvas.addSelectionListener(this::inkSelected);
        sharedCanvas.addPageSwitchListener(this::forgetSelection);
        if (grapher != null) {
            grapher.setMessages(this::showMessage);
        }
    }

    /**
     * Adds the Math tab to the ribbon: the circled formula, Calculate and Graph, and the graph's
     * ranges. It is greyed out until ink is circled with the lasso.
     */
    public Ribbon.Tab addMathTab(Ribbon ribbon) {
        Ribbon.Tab tab = ribbon.addTab("Math", this::hasFormulaSelected);
        tab.addControl(preview);
        calculateButton = tab.addButton("Calculate", GlassIcons::equalCircle, this::calculateSelected,
                this::canCalculate);
        graphButton = tab.addButton("Graph", GlassIcons::chart, this::graphSelected, this::canGraph);
        if (grapher != null) {
            for (JComponent control : grapher.controls()) {
                tab.addControl(control);
            }
        }
        return tab;
    }

    /** Whether ink is circled with the lasso on this page, so the Math tab can be used. */
    public boolean hasFormulaSelected() {
        return inFront() && getSharedCanvas().hasSelectedInk();
    }

    private boolean canCalculate() {
        return worker != null && hasFormulaSelected() && selectedProblem == null;
    }

    private boolean canGraph() {
        return grapher != null && canCalculate();
    }

    /** What the circled ink was read as; null while it is being read or could not be. */
    String getSelectedLatex() {
        return selectedLatex;
    }

    FormulaPreview getPreview() {
        return preview;
    }

    RibbonButton getCalculateButton() {
        return calculateButton;
    }

    RibbonButton getGraphButton() {
        return graphButton;
    }

    private boolean inFront() {
        return getSharedCanvas().isAttachedTo(this);
    }

    // a lasso closed around ink: read it now, so it is ready when the Math tab is used
    private void inkSelected() {
        if (!inFront() || worker == null) {
            return;
        }
        long gen = ++generation;
        selectedLatex = null;
        selectedProblem = null;
        waiting = null;
        preview.showReading();
        BufferedImage image = captureFormula();
        worker.recognize(() -> recognizeFormula(image), (latex, problem) -> selectionRecognized(gen, latex, problem));
    }

    private void selectionRecognized(long gen, String latex, String problem) {
        // a newer selection, or none left
        if (gen != generation || !inFront() || !getSharedCanvas().hasSelectedInk()) {
            return;
        }
        Consumer<String> then = waiting;
        waiting = null;
        if (problem != null) {
            selectedProblem = problem;
            preview.showProblem(problem);
            if (then != null) {
                showMessage(problem, true, ERROR_MESSAGE_MILLIS);
            }
            return;
        }
        selectedLatex = latex;
        preview.showFormula(latex);
        if (then != null) {
            then.accept(latex);
        }
    }

    // the canvas went to another page
    private void forgetSelection() {
        generation++;
        selectedLatex = null;
        selectedProblem = null;
        waiting = null;
        preview.clear();
    }

    /** Calculate on the Math tab: the answer goes beside the circled formula. */
    void calculateSelected() {
        whenRead(this::calculateSelection);
    }

    /** Graph on the Math tab: a card beside the circled formula. */
    void graphSelected() {
        if (grapher != null) {
            whenRead(this::graphSelection);
        }
    }

    // runs at once if the circled ink is read, else as soon as it is
    private void whenRead(Consumer<String> action) {
        if (!canCalculate()) {
            return;
        }
        if (selectedLatex != null) {
            action.accept(selectedLatex);
        } else {
            waiting = action;
            showMessage("Reading the formula…", false, 0);
        }
    }

    private void graphSelection(String latex) {
        if (grapher.graph(latex, getSharedCanvas().getFormulaBounds())) {
            getSharedCanvas().clearSelection();
        }
    }

    // only now does the selection become a formula: just circling ink must not cost it its answer
    private void calculateSelection(String latex) {
        FormulaCapture capture = getSharedCanvas().takeFormulasToCalculate().get(0);
        getSharedCanvas().clearSelection();
        calculate(capture, latex);
    }

    private void calculate(FormulaCapture capture, String latex) {
        if (running == 0) {
            ownMessage = false;
        }
        running++;
        showMessage("Calculating " + latex + "…", false, 0);
        worker.calculate(latex, outcome -> deliver(capture, latex, outcome));
    }

    private void deliver(FormulaCapture capture, String latex, CalculationOutcome outcome) {
        AnswerKind kind = outcome.kind;
        String result = outcome.result;
        List<TextRun> runs = outcome.answerRuns;
        String error = outcome.error;
        if (!error.isEmpty()) {
            // a message only, nothing drawn
            if (getSharedCanvas().isCurrent(capture.getId(), capture.getRevision())) {
                ownMessage = true;
                showMessage(error, true, ERROR_MESSAGE_MILLIS);
            }
        } else if (outcome.definition) {
            ownMessage = true;
            showMessage(result, false, DEFINED_MESSAGE_MILLIS);  // nothing to draw
        } else {
            boolean drawn = runs == null
                    ? getSharedCanvas().showAnswer(capture.getId(), capture.getRevision(), kind, outcome.answerText, false)
                    : getSharedCanvas().showAnswer(capture.getId(), capture.getRevision(), kind, runs, false);
            if (drawn && latex != null && !latex.isBlank()
                    && !java.util.Objects.equals(exportedRevisions.get(capture.getId()), capture.getRevision())) {
                exportedRevisions.put(capture.getId(), capture.getRevision());
                pdfResultSink.accept(latex, result);
            }
        }
        if (--running == 0 && !ownMessage) {
            hideMessage();
        }
    }
}
