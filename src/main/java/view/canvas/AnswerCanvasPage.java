package view.canvas;

import interface_adapter.AnswerKind;
import entity.Stroke;
import interface_adapter.TextRun;
import interface_adapter.MathLabViewModel;
import interface_adapter.evaluate.EvaluateController;
import view.FormulaCapture;
import view.stack.Fonts;

import javax.swing.BorderFactory;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;
import javax.swing.Timer;
import java.awt.Color;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.Rectangle;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.BiConsumer;

// answer page: pause -> popup with the read formula -> Calculate -> answer on the canvas
public class AnswerCanvasPage extends CanvasPage {
    static final int POPUP_DELAY_MILLIS = 1500;
    private static final int ERROR_MESSAGE_MILLIS = 7000;
    private static final int DEFINED_MESSAGE_MILLIS = 4000;

    private final BiConsumer<String, String> pdfResultSink;
    private final Map<Long, Long> exportedRevisions = new HashMap<>();
    private final AnswerWorker worker;
    private final AnswerPopup popup;
    private final Timer pauseTimer;
    private final Set<Stroke> session = new LinkedHashSet<>();  // loose ink since the last pause
    private Stroke targetStroke;      // new ink to pop up for, or
    private long targetId = -1;       // an edited formula
    private long generation;          // bumps on every user action, old results check it
    private FormulaCapture shown;     // formula the popup is about
    private int running;              // Calculate jobs still going
    private boolean ownMessage;       // one of them put up its own message

    // no evaluate feature, just a placeholder
    public AnswerCanvasPage(SharedCanvas sharedCanvas) {
        super(sharedCanvas);
        this.pdfResultSink = (latex, result) -> {};
        this.worker = null;
        this.popup = null;
        this.pauseTimer = null;
        setFunctionArea(placeholder("Answer tools go here: evaluate or solve the expression on the canvas."));
    }

    public AnswerCanvasPage(SharedCanvas sharedCanvas, EvaluateController controller, MathLabViewModel viewModel) {
        this(sharedCanvas, controller, viewModel, (latex, result) -> {});
    }

    public AnswerCanvasPage(SharedCanvas sharedCanvas, EvaluateController controller, MathLabViewModel viewModel,
                            BiConsumer<String, String> pdfResultSink) {
        super(sharedCanvas);
        this.pdfResultSink = java.util.Objects.requireNonNull(pdfResultSink);
        this.worker = new AnswerWorker(controller, viewModel);
        popup = new AnswerPopup(sharedCanvas::paintInkCopy);
        addCanvasOverlay(popup);
        pauseTimer = new Timer(POPUP_DELAY_MILLIS, e -> paused());
        pauseTimer.setRepeats(false);

        sharedCanvas.addFormulaChangeListener(this::formulaChanged);
        sharedCanvas.addStrokeListener(this::strokeDone);
        sharedCanvas.addUserActionListener(this::dismiss);
        sharedCanvas.addPageSwitchListener(this::dismiss);
        sharedCanvas.addViewListener(this::placePopup);
        getCanvasArea().addComponentListener(new ComponentAdapter() {
            @Override
            public void componentResized(ComponentEvent e) {
                placePopup();
            }
        });

        JLabel note = new JLabel("Write a formula, then pause — it will offer to calculate.");
        note.setFont(Fonts.ui(Font.PLAIN, 13f));
        note.setForeground(new Color(100, 116, 139));
        note.setBorder(BorderFactory.createEmptyBorder(6, 0, 6, 0));
        JPanel tools = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        tools.add(note);
        setFunctionArea(tools);
    }

    // tests use a short wait
    void setPopupDelay(int millis) {
        pauseTimer.setInitialDelay(millis);
    }

    // null when hidden
    AnswerPopup visiblePopup() {
        return popup != null && popup.isVisible() ? popup : null;
    }

    private boolean inFront() {
        return getSharedCanvas().isAttachedTo(this);
    }

    // pen lifted; bound strokes already came in through formulaChanged
    private void strokeDone(Stroke s) {
        if (!inFront() || getSharedCanvas().isBound(s)) {
            return;
        }
        session.add(s);
        targetStroke = s;
        targetId = -1;
        restartPause();
    }

    // ink of a formula changed: answer is gone already, offer again after a pause
    private void formulaChanged(long id) {
        if (!inFront()) {
            return;
        }
        targetStroke = null;
        targetId = id;
        restartPause();
    }

    private void restartPause() {
        hidePopup();
        generation++;
        pauseTimer.restart();
    }

    // user went on to do something else
    private void dismiss() {
        pauseTimer.stop();
        targetStroke = null;
        targetId = -1;
        generation++;
        hidePopup();
    }

    private void hidePopup() {
        shown = null;
        if (popup.isVisible()) {
            popup.setVisible(false);
            getCanvasArea().repaint();
        }
    }

    private void paused() {
        if (!inFront()) {
            return;
        }
        if (getSharedCanvas().isBusy()) {
            pauseTimer.restart();  // mouse still down, wait more
            return;
        }
        FormulaCapture capture = null;
        if (targetStroke != null) {
            capture = getSharedCanvas().registerNewInk(targetStroke, session);
            session.clear();
        } else if (targetId >= 0) {
            capture = getSharedCanvas().captureForRecalculation(targetId);
        }
        targetStroke = null;
        targetId = -1;
        if (capture != null) {
            recognize(capture, generation);
        }
    }

    private void recognize(FormulaCapture capture, long gen) {
        worker.recognize(() -> recognizeFormula(capture.getImage()),
                (latex, problem) -> recognized(capture, gen, latex, problem));
    }

    private void recognized(FormulaCapture capture, long gen, String latex, String problem) {
        // user moved on, or ink changed while we were reading it
        if (gen != generation || !inFront() || !getSharedCanvas().isCurrent(capture.getId(), capture.getRevision())) {
            return;
        }
        if (problem != null) {
            popup.showProblem(problem);
        } else {
            popup.showFormula(latex, () -> calculate(capture, latex));
        }
        shown = capture;
        popup.setVisible(true);
        placePopup();
    }

    private void placePopup() {
        if (shown == null || !popup.isVisible()) {
            return;
        }
        Rectangle world = getSharedCanvas().formulaBounds(shown.getId());
        if (world == null) {
            hidePopup();
            return;
        }
        JComponent area = getCanvasArea();
        Rectangle f = SwingUtilities.convertRectangle(getSharedCanvas().getDrawingCanvas(),
                getSharedCanvas().worldToScreen(world).getBounds(), area);
        popup.setBounds(popup.spotBeside(f, area.getWidth(), area.getHeight()));
        area.repaint();
    }

    // Calculate in the popup: use the latex we already have, no second read
    private void calculate(FormulaCapture capture, String latex) {
        hidePopup();
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
            // popup only, nothing drawn
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
