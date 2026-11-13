package app;

import data_access.InMemoryFunctionDataAccessObject;
import history.data_access.FileHistoryRepository;
import history.use_case.HistoryInteractor;
import history.adapter.HistoryBoardPresenter;
import view.board.PdfPanel;
import pdf_editor.view.CanvasPdfDragController;
import pdf_editor.view.CanvasRegionCaptureController;
import pdf_editor.view.CanvasLibrarySelectionMenu;
import pdf_editor.adapter.SelectedInkRegistry;
import pdf_editor.use_case.SelectionToLibrary;
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
import view.board.CanvasWorkspaceDialogs;
import workspace.use_case.CanvasWorkspace;
import workspace.data_access.FileCanvasRepository;
import pdf_editor.view.CanvasHistoryEditMenu;
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
    private final CanvasWorkspace canvases = new CanvasWorkspace(
            new FileCanvasRepository(FileCanvasRepository.defaultDirectory()));

    {
        pdfStudio.setSourceCanvas(sharedCanvas);
        sharedCanvas.addFormulaChangeListener(id -> pdfStudio.synchronizeCanvas(sharedCanvas));
        drawing.addInkChangeListener(() -> SwingUtilities.invokeLater(
                () -> pdfStudio.synchronizeCanvas(sharedCanvas)));
        sharedCanvas.addUserActionListener(() -> SwingUtilities.invokeLater(
                () -> pdfStudio.synchronizeCanvas(sharedCanvas)));
    }

    public StackedAppBuilder setFormulaRecognizer(FormulaRecognizer recognizer) {
        sharedCanvas.setFormulaRecognizer(recognizer);
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
        drawing.setMathAssetRenderer((latex, result, width, height) ->
                pdf_editor.data_access.MaterialRenderer.render(
                        new pdf_editor.domain.PdfMaterial("Canvas History", latex, result, null, 0, 0) { },
                        width, height));
        CanvasPdfDragController.install(drawing);
        CanvasHistoryEditMenu.install(drawing, (latex, result) ->
                pdf_editor.data_access.MaterialRenderer.render(
                        new pdf_editor.domain.PdfMaterial("Edited Math", latex, result, null, 0, 0) { },
                        800, 190));
        CanvasLibrarySelectionMenu.install(drawing,
                new SelectionToLibrary(sharedCanvas::recognizeFormula, pdfStudio.getLibrary()),
                new SelectedInkRegistry(drawing, pdfStudio.getLibrary()));
        AnswerCanvasPage page = new AnswerCanvasPage(sharedCanvas, evaluate, mathViewModel, results,
                graph, graphViewModel, graphCheck::canGraph, BoardTheme.CANVAS, ToolbarSpot.TOP_Y);
        sharedCanvas.attachTo(page);
        PdfPanel pdfPanel = pdfStudio.createPanel();
        // Use the live selection (lasso or whole ink), not an outdated startup screenshot.
        pdfPanel.setCanvasCapture(() -> {
            try { return drawing.toFormulaImage(); }
            catch (RuntimeException empty) { return null; }
        });
        pdfPanel.setRegionCapture(() -> CanvasRegionCaptureController.begin(
                page.boardArea(), drawing, image -> {
                    pdfStudio.getLibrary().addInk("Canvas screenshot", image);
                    pdfPanel.editor().showLibrary();
                }));
        // Save/Open are separate use-case commands; the board knows only the callbacks.
        java.util.concurrent.atomic.AtomicReference<CanvasWorkspaceDialogs> dialogs = new java.util.concurrent.atomic.AtomicReference<>();
        BoardWindow board = new BoardWindow(page, drawing,
                host -> new HistoryPanel(historyViewModel, host, historyPresenter::undoDelete),
                pdfPanel, historyPresenter::delete,
                () -> dialogs.get().save(), () -> dialogs.get().open());
        dialogs.set(new CanvasWorkspaceDialogs(canvases, drawing, board));
        FloatingToolbar toolbar = new FloatingToolbar(drawing, board);
        board.addFloatingToolbar(toolbar.component());
        pdfPanel.setFullscreenAction(() -> board.setPdfFullscreen(!board.isPdfFullscreen()));
        pdfStudio.onOpen(() -> board.setPdfOpen(true));
        dialogs.get().restoreLatestAtStartup();

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
        root.registerKeyboardAction(e -> board.setPdfFullscreen(false),
                KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0), JComponent.WHEN_IN_FOCUSED_WINDOW);
        frame.setContentPane(board);
        frame.setDefaultCloseOperation(WindowConstants.EXIT_ON_CLOSE);
        frame.setSize(1280, 790);
        frame.addWindowListener(new java.awt.event.WindowAdapter() {
            @Override public void windowOpened(java.awt.event.WindowEvent e) { toolbar.relayout(); }
        });
        frame.addWindowListener(new java.awt.event.WindowAdapter() {
            @Override public void windowClosed(java.awt.event.WindowEvent e) { pdfPanel.close(); }
        });
        frame.setLocationRelativeTo(null);
        return frame;
    }
}
