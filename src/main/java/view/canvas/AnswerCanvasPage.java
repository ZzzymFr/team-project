package view.canvas;

import interface_adapter.AnswerKind;
import entity.Stroke;
import interface_adapter.TextRun;
import interface_adapter.MathLabViewModel;
import interface_adapter.evaluate.EvaluateController;
import interface_adapter.graph.GraphController;
import interface_adapter.graph.GraphViewModel;
import view.FormulaCapture;
import view.answer.Formula;
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
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.BiConsumer;
import java.util.function.Predicate;

// answer page: pause after an "=" -> popup with the read formula -> Calculate -> answer on the canvas
public class AnswerCanvasPage extends CanvasPage {
    static final int POPUP_DELAY_MILLIS = 1500;
    private static final int ERROR_MESSAGE_MILLIS = 7000;
    private static final int DEFINED_MESSAGE_MILLIS = 4000;

    /** Optional source-aware export callback. Older two-argument integrations remain supported. */
    @FunctionalInterface public interface FormulaResultSink {
        void accept(long sourceId, String latex, String result);
    }
    private final FormulaResultSink pdfResultSink;
    private final Map<Long, Long> exportedRevisions = new HashMap<>();
    private final AnswerWorker worker;
    private final AnswerPopup popup;
    private final Timer pauseTimer;
    private final Set<Stroke> session = new LinkedHashSet<>();  // loose ink since the last pause
    private final Set<Long> waiting = new LinkedHashSet<>();    // written in the same go, popup still owed
    private Stroke targetStroke;      // new ink to pop up for, or
    private long targetId = -1;       // an edited formula, or
    private boolean targetPick;       // the lasso pick
    private long generation;          // bumps on every user action, old results check it
    private FormulaCapture shown;     // formula the popup is about
    private int running;              // Calculate jobs still going
    private boolean ownMessage;       // one of them put up its own message
    private PopupGraphing graphing;   // one board only: Graph in the popup
    private PopupHover hover;         // mouse on an unanswered "=" brings the popup back
    private final Map<Long, Read> reads = new HashMap<>();  // last read per formula
    private int reading;              // recognize jobs still out
    private Predicate<String> graphable = latex -> false;
    private final PopupGate gate = new PopupGate();

    // no evaluate feature, just a placeholder
    public AnswerCanvasPage(SharedCanvas sharedCanvas) {
        super(sharedCanvas);
        this.pdfResultSink = (id, latex, result) -> {};
        this.worker = null;
        this.popup = null;
        this.pauseTimer = null;
        setFunctionArea(placeholder("Answer tools go here: evaluate or solve the expression on the canvas."));
    }

    public AnswerCanvasPage(SharedCanvas sharedCanvas, EvaluateController controller, MathLabViewModel viewModel) {
        this(sharedCanvas, controller, viewModel, (id, latex, result) -> {});
    }

    public AnswerCanvasPage(SharedCanvas sharedCanvas, EvaluateController controller, MathLabViewModel viewModel,
                            BiConsumer<String, String> pdfResultSink) {
        this(sharedCanvas, controller, viewModel,
                (sourceId, latex, result) -> pdfResultSink.accept(latex, result));
    }

    public AnswerCanvasPage(SharedCanvas sharedCanvas, EvaluateController controller, MathLabViewModel viewModel,
                            FormulaResultSink pdfResultSink) {
        super(sharedCanvas);
        this.pdfResultSink = java.util.Objects.requireNonNull(pdfResultSink);
        this.worker = new AnswerWorker(controller, viewModel);
        popup = new AnswerPopup(sharedCanvas::paintInkCopy, sharedCanvas.getDrawingCanvas()::contentVersion);
        addCanvasOverlay(popup);
        pauseTimer = new Timer(POPUP_DELAY_MILLIS, e -> paused());
        pauseTimer.setRepeats(false);

        sharedCanvas.addFormulaChangeListener(this::formulaChanged);
        sharedCanvas.addStrokeListener(this::strokeDone);
        sharedCanvas.addPickListener(this::picked);
        sharedCanvas.addUserActionListener(this::dismiss);
        sharedCanvas.addPageSwitchListener(this::dismiss);
        sharedCanvas.addViewListener(this::placePopup);
        hover = new PopupHover(sharedCanvas, getCanvasArea(), new HoverHost());
        getCanvasArea().addComponentListener(new ComponentAdapter() {
            @Override
            public void componentResized(ComponentEvent e) {
                placePopup();
            }
        });

        JLabel note = new JLabel("Write a formula with an \"=\" (or lasso one) — it will offer to calculate.");
        note.setFont(Fonts.ui(Font.PLAIN, 13f));
        note.setForeground(new Color(100, 116, 139));
        note.setBorder(BorderFactory.createEmptyBorder(6, 0, 6, 0));
        JPanel tools = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        tools.add(note);
        setFunctionArea(tools);
    }

    // one board: popup has Calculate + Graph, no function area
    public AnswerCanvasPage(SharedCanvas sharedCanvas, EvaluateController controller, MathLabViewModel viewModel,
                            FormulaResultSink resultSink, GraphController graphController,
                            GraphViewModel graphViewModel, Predicate<String> graphable,
                            java.awt.Color background, int messageTop) {
        this(sharedCanvas, controller, viewModel, resultSink);
        this.graphable = graphable;
        graphing = new PopupGraphing(sharedCanvas, graphController, graphViewModel, this::showMessage,
                getCanvasArea(), this::publishFormulaOnly);
        addCanvasOverlay(graphing.rangeBar());
        useBoardLook(background, messageTop);
    }

    // the area the board floats things over (same coords as the canvas)
    public JComponent boardArea() {
        return getCanvasArea();
    }

    public void addBoardOverlay(JComponent overlay) {
        addCanvasOverlay(overlay);
    }

    // tests type ranges like the user
    void setRangeText(String x, String y) {
        graphing.setRangeText(x, y);
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
        targetPick = false;
        restartPause();
    }

    // ink of a formula changed: answer is gone already, offer again after a pause
    private void formulaChanged(long id) {
        if (!inFront()) {
            return;
        }
        targetStroke = null;
        targetId = id;
        targetPick = false;
        restartPause();
    }

    // lasso/box let go on some ink: offer it as one formula, no "=" needed
    private void picked() {
        if (!inFront()) {
            return;
        }
        targetStroke = null;
        targetId = -1;
        targetPick = true;
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
        targetPick = false;
        generation++;
        hidePopup();
    }

    private void hidePopup() {
        shown = null;
        hover.forget();
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
        if (targetPick) {
            capture = getSharedCanvas().registerSelection();
            if (capture != null) {
                gate.picked(capture.getId());
            }
        } else if (targetStroke != null) {
            capture = getSharedCanvas().registerNewInk(targetStroke, session);
            queueRest(capture);
            session.clear();
        } else if (targetId >= 0) {
            capture = getSharedCanvas().captureForRecalculation(targetId);
        }
        targetStroke = null;
        targetId = -1;
        targetPick = false;
        if (capture == null) {
            capture = nextWaiting();
        }
        if (capture != null) {
            recognize(capture, generation);
        }
    }

    // other formulas of this round got bound too, only the last one gets the popup now
    private void queueRest(FormulaCapture offered) {
        for (Stroke s : session) {
            long id = getSharedCanvas().formulaIdOf(s);
            if (id >= 0 && (offered == null || id != offered.getId())) {
                waiting.add(id);
            }
        }
    }

    // oldest one still there w/o an answer, null if none
    private FormulaCapture nextWaiting() {
        FormulaCapture capture = null;
        Iterator<Long> ids = waiting.iterator();
        while (capture == null && ids.hasNext()) {
            capture = getSharedCanvas().captureForRecalculation(ids.next());
            ids.remove();
        }
        return capture;
    }

    private void recognize(FormulaCapture capture, long gen) {
        reading++;
        worker.recognize(() -> recognizeFormula(capture.getImage()),
                (latex, problem) -> recognized(capture, gen, latex, problem));
    }

    private void recognized(FormulaCapture capture, long gen, String latex, String problem) {
        reading--;
        if (!getSharedCanvas().isCurrent(capture.getId(), capture.getRevision())) {
            return;  // ink changed while we were reading it
        }
        reads.put(capture.getId(), new Read(capture.getRevision(), latex, problem));  // for hovering later
        if (gen != generation || !inFront()) {
            return;  // user moved on
        }
        if (!gate.allows(formula(capture.getId()), latex)) {
            hover.forget();
            FormulaCapture next = nextWaiting();  // no "=" yet, quietly try the next one
            if (next != null) {
                recognize(next, gen);
            }
            return;
        }
        present(capture, latex, problem);
    }

    private Formula formula(long id) {
        for (Formula f : getSharedCanvas().getFormulas()) {
            if (f.getId() == id) {
                return f;
            }
        }
        return null;
    }

    private void present(FormulaCapture capture, String latex, String problem) {
        if (problem != null) {
            popup.showProblem(problem);
        } else if (graphing != null) {
            popup.showFormula(latex, () -> calculate(capture, latex), () -> graph(capture, latex),
                    graphable.test(latex));
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
        hidePopup();  // hover the "=" to get it back
        if (!waiting.isEmpty()) {
            restartPause();  // this one's handled, offer the next after a beat
        }
        if (running == 0) {
            ownMessage = false;
        }
        running++;
        showMessage("Calculating " + latex + "…", false, 0);
        worker.calculate(latex, outcome -> deliver(capture, latex, outcome));
    }

    // Graph in the popup: same latex, no second read
    private void graph(FormulaCapture capture, String latex) {
        hidePopup();
        Rectangle world = getSharedCanvas().formulaBounds(capture.getId());
        graphing.graph(latex, world, capture);
    }

    /** Publish only after an explicit Calculate action, never while merely recognizing ink. */
    private void publishFormulaOnly(FormulaCapture capture, String latex) {
        if (latex != null && !latex.isBlank()
                && getSharedCanvas().isCurrent(capture.getId(), capture.getRevision())
                && !java.util.Objects.equals(exportedRevisions.get(capture.getId()), capture.getRevision())) {
            exportedRevisions.put(capture.getId(), capture.getRevision());
            pdfResultSink.accept(capture.getId(), latex, "");
        }
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
                // y=3x is a valid graphable relation, but the numeric evaluator
                // rejects its two variables. A user explicitly asked to Calculate;
                // retain the recognized formula without inventing a numerical answer.
                if (latex != null && graphable.test(latex)) publishFormulaOnly(capture, latex);
            }
        } else if (outcome.definition) {
            ownMessage = true;
            showMessage(result, false, DEFINED_MESSAGE_MILLIS);
            // Definitions have no numeric answer but are still explicitly requested work.
            publishFormulaOnly(capture, latex);
        } else {
            boolean drawn = runs == null
                    ? getSharedCanvas().showAnswer(capture.getId(), capture.getRevision(), kind, outcome.answerText, false)
                    : getSharedCanvas().showAnswer(capture.getId(), capture.getRevision(), kind, runs, false);
            if (drawn && latex != null && !latex.isBlank()
                    && !java.util.Objects.equals(exportedRevisions.get(capture.getId()), capture.getRevision())) {
                exportedRevisions.put(capture.getId(), capture.getRevision());
                pdfResultSink.accept(capture.getId(), latex, result);
            }
        }
        if (--running == 0 && !ownMessage) {
            hideMessage();
        }
    }

    // what the popup said last for a formula
    private static final class Read {
        final long revision;
        final String latex;
        final String problem;

        Read(long revision, String latex, String problem) {
            this.revision = revision;
            this.latex = latex;
            this.problem = problem;
        }
    }

    private final class HoverHost implements PopupHover.Host {
        @Override
        public boolean canOffer() {
            return inFront() && !popup.isVisible() && !pauseTimer.isRunning() && reading == 0
                    && !getSharedCanvas().isBusy();
        }

        // read it again only if the ink changed since
        @Override
        public void offer(long id) {
            FormulaCapture capture = getSharedCanvas().captureForRecalculation(id);
            if (capture == null) {
                hover.forget();  // answered or gone meanwhile
                return;
            }
            Read r = reads.get(id);
            generation++;
            if (r != null && r.revision == capture.getRevision()) {
                present(capture, r.latex, r.problem);
            } else {
                recognize(capture, generation);
            }
        }

        @Override
        public void withdraw() {
            generation++;  // drops a read still on its way
            hidePopup();
        }

        @Override
        public boolean wanted(Formula f) {
            return gate.allows(f, latexOf(f.getId(), f.getRevision()));
        }

        @Override
        public Rectangle popupBounds() {
            return popup.isVisible() ? popup.visibleBounds() : null;
        }

        @Override
        public String latexOf(long id, long revision) {
            Read r = reads.get(id);
            return r != null && r.revision == revision ? r.latex : null;
        }
    }
}
