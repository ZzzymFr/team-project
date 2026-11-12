package app;

import data_access.InMemoryFunctionDataAccessObject;
import history.data_access.FileHistoryRepository;
import history.use_case.HistoryInteractor;
import history.adapter.HistoryBoardPresenter;
import history.adapter.RecordingFormulaRecognizer;
import view.board.PdfPanel;
import pdf_editor.view.CanvasPdfDragController;
import data_access.QuickChartGraphRenderer;
import entity.LatexFunctionParser;
import interface_adapter.MathLabViewModel;
import interface_adapter.evaluate.EvaluateController;
import interface_adapter.evaluate.EvaluatePresenter;
import interface_adapter.graph.GraphController;
import interface_adapter.graph.GraphPresenter;
import interface_adapter.graph.GraphState;
import interface_adapter.graph.GraphViewModel;
import interface_adapter.graph_check.GraphCheckController;
import interface_adapter.history.HistoryViewModel;
import interface_adapter.recognize.FormulaRecognizer;
import pdf_editor.PdfStudioLauncher;
import use_case.evaluate.EvaluateInteractor;
import use_case.graph_check.GraphCheckInteractor;
import use_case.graph_function.GraphFunctionInteractor;
import view.DrawingCanvas;
import view.board.BoardTheme;
import view.board.BoardWindow;
import view.board.FloatingToolbar;
import view.board.HistoryPanel;
import view.board.ToolbarSpot;
import view.canvas.AnswerCanvasPage;
import view.canvas.SharedCanvas;

import javax.swing.JComponent;
import javax.swing.JFrame;
import javax.swing.JRootPane;
import javax.swing.KeyStroke;
import javax.swing.SwingUtilities;
import javax.swing.WindowConstants;
import java.awt.Toolkit;
import java.awt.event.KeyEvent;

/** Builds the one-board window: canvas, History and PDF panels, popup with Calculate and Graph. */
public class StackedAppBuilder {
    private final DrawingCanvas drawing = new DrawingCanvas();
    private final SharedCanvas sharedCanvas = new SharedCanvas(drawing);
    private final HistoryInteractor history = new HistoryInteractor(
            new FileHistoryRepository(FileHistoryRepository.defaultDirectory()));
    private final PdfStudioLauncher pdfStudio = new PdfStudioLauncher(history);

    {
        pdfStudio.setSourceCanvas(sharedCanvas);
        sharedCanvas.addFormulaChangeListener(id -> pdfStudio.synchronizeCanvas(sharedCanvas));
        sharedCanvas.addUserActionListener(() -> SwingUtilities.invokeLater(
                () -> pdfStudio.synchronizeCanvas(sharedCanvas)));
    }

    public StackedAppBuilder setFormulaRecognizer(FormulaRecognizer recognizer) {
        sharedCanvas.setFormulaRecognizer(new RecordingFormulaRecognizer(recognizer, history, pdfStudio::addRecognizedFormula));
        return this;
    }

    public SharedCanvas getSharedCanvas() {
        return sharedCanvas;
    }

    public JFrame build() {
        // evaluate
        MathLabViewModel mathViewModel = new MathLabViewModel();
        EvaluateController evaluate = new EvaluateController(new EvaluateInteractor(
                new InMemoryFunctionDataAccessObject(), new EvaluatePresenter(mathViewModel)));

        // graph, graphs also go to PDF Studio like before
        GraphViewModel graphViewModel = new GraphViewModel();
        graphViewModel.addPropertyChangeListener(event -> {
            if (GraphViewModel.STATE_PROPERTY.equals(event.getPropertyName())) {
                GraphState state = graphViewModel.getState();
                if (state.getError() == null && state.getGraphImage() != null) {
                    pdfStudio.addGraph(state.getLatex(), state.getGraphImage());
                }
            }
        });
        GraphController graph = new GraphController(new GraphFunctionInteractor(
                new LatexFunctionParser(), new QuickChartGraphRenderer(), new GraphPresenter(graphViewModel)));
        GraphCheckController graphCheck = new GraphCheckController(new GraphCheckInteractor(new LatexFunctionParser()));

        // history
        HistoryViewModel historyViewModel = new HistoryViewModel();
        HistoryBoardPresenter historyPresenter = new HistoryBoardPresenter(history, historyViewModel);
        historyPresenter.refresh();

        AnswerCanvasPage.FormulaResultSink results = (id, latex, result) -> {
            pdfStudio.addCalculationWithSource(id, latex, result);
            // MathLabCollector persists this calculation when it receives the same event.
        };
        drawing.setBackground(BoardTheme.CANVAS);
        drawing.setGridVisible(false);
        CanvasPdfDragController.install(drawing);
        AnswerCanvasPage page = new AnswerCanvasPage(sharedCanvas, evaluate, mathViewModel, results,
                graph, graphViewModel, graphCheck::canGraph, BoardTheme.CANVAS, ToolbarSpot.TOP_Y);
        sharedCanvas.attachTo(page);
        FloatingToolbar toolbar = new FloatingToolbar(drawing, page.boardArea());
        page.addBoardOverlay(toolbar.component());

        PdfPanel pdfPanel = pdfStudio.createPanel();
        // Use the live selection (lasso or whole ink), not an outdated startup screenshot.
        pdfPanel.setCanvasCapture(() -> {
            try { return drawing.toFormulaImage(); }
            catch (RuntimeException empty) { return null; }
        });
        BoardWindow board = new BoardWindow(page, drawing,
                host -> new HistoryPanel(historyViewModel, host), pdfPanel, historyPresenter::delete);
        pdfStudio.onOpen(() -> { board.setPdfOverlay(false); board.setPdfOpen(true); });

        JFrame frame = new JFrame("Math Lab");
        JRootPane root = frame.getRootPane();
        if (BoardTheme.MAC) {  // before the window is shown
            root.putClientProperty("apple.awt.fullWindowContent", true);
            root.putClientProperty("apple.awt.transparentTitleBar", true);
            root.putClientProperty("apple.awt.windowTitleVisible", false);
        }
        int shortcut = Toolkit.getDefaultToolkit().getMenuShortcutKeyMaskEx();
        root.registerKeyboardAction(e -> drawing.undo(), KeyStroke.getKeyStroke(KeyEvent.VK_Z, shortcut),
                JComponent.WHEN_IN_FOCUSED_WINDOW);
        frame.setContentPane(board);
        frame.setDefaultCloseOperation(WindowConstants.EXIT_ON_CLOSE);
        frame.setSize(1280, 790);
        frame.addWindowListener(new java.awt.event.WindowAdapter() {
            @Override public void windowClosed(java.awt.event.WindowEvent e) { pdfPanel.close(); }
        });
        frame.setLocationRelativeTo(null);
        return frame;
    }
}
