package app;

import data_access.InMemoryFunctionDataAccessObject;
import data_access.QuickChartGraphRenderer;
import entity.LatexFunctionParser;
import interface_adapter.MathLabViewModel;
import interface_adapter.evaluate.EvaluateController;
import interface_adapter.evaluate.EvaluatePresenter;
import interface_adapter.graph.GraphController;
import interface_adapter.graph.GraphPresenter;
import interface_adapter.graph.GraphViewModel;
import interface_adapter.recognize.FormulaRecognizer;
import use_case.evaluate.EvaluateInteractor;
import use_case.graph_function.GraphFunctionInteractor;
import view.DrawingCanvas;
import view.canvas.AnswerCanvasPage;
import view.canvas.CanvasPage;
import view.canvas.GraphCanvasPage;
import view.canvas.PdfCanvasPage;
import view.canvas.SharedCanvas;
import view.stack.StackedPages;
import view.stack.WindowChrome;

import javax.swing.JComponent;
import javax.swing.JFrame;
import javax.swing.WindowConstants;
import java.awt.Color;

/**
 * Builds the stacked-pages window: Graph, PDF and Answer pages sharing one handwriting canvas.
 */
public class StackedAppBuilder {
    private static final Color GRAPH_BLUE = new Color(37, 99, 235);
    private static final Color PDF_ORANGE = new Color(234, 88, 12);
    private static final Color ANSWER_GREEN = new Color(22, 163, 74);

    private final SharedCanvas sharedCanvas = new SharedCanvas(new DrawingCanvas());
    private final StackedPages pages = new StackedPages();

    /** The Graph page, wired to the graph use case: LaTeX -> parse and sample -> QuickChart -> graph card. */
    public StackedAppBuilder addGraphPage() {
        GraphViewModel viewModel = new GraphViewModel();
        GraphFunctionInteractor interactor = new GraphFunctionInteractor(
                new LatexFunctionParser(), new QuickChartGraphRenderer(), new GraphPresenter(viewModel));
        GraphController controller = new GraphController(interactor);
        pages.addPage("Graph", GRAPH_BLUE, new GraphCanvasPage(sharedCanvas, controller, viewModel));
        return this;
    }

    public StackedAppBuilder addPdfPage() {
        pages.addPage("PDF", PDF_ORANGE, new PdfCanvasPage(sharedCanvas));
        return this;
    }

    // answer page -> evaluate use case
    public StackedAppBuilder addAnswerPage() {
        MathLabViewModel viewModel = new MathLabViewModel();
        EvaluateInteractor interactor = new EvaluateInteractor(
                new InMemoryFunctionDataAccessObject(), new EvaluatePresenter(viewModel));
        EvaluateController controller = new EvaluateController(interactor);
        pages.addPage("Answer", ANSWER_GREEN, new AnswerCanvasPage(sharedCanvas, controller, viewModel));
        return this;
    }

    /** Connects handwriting recognition once the machine-learning part is ready. */
    public StackedAppBuilder setFormulaRecognizer(FormulaRecognizer recognizer) {
        sharedCanvas.setFormulaRecognizer(recognizer);
        return this;
    }

    public SharedCanvas getSharedCanvas() {
        return sharedCanvas;
    }

    public JFrame build() {
        // The one canvas follows whichever page is in front.
        attachCanvasToFrontPage();
        pages.addChangeListener(e -> attachCanvasToFrontPage());

        JFrame frame = new JFrame("Math Lab");  // the title still shows in the taskbar
        frame.setDefaultCloseOperation(WindowConstants.EXIT_ON_CLOSE);
        WindowChrome.install(frame, pages);
        frame.setSize(980, 660);
        frame.setLocationRelativeTo(null);
        return frame;
    }

    private void attachCanvasToFrontPage() {
        JComponent front = pages.getSelectedComponent();
        if (front instanceof CanvasPage) {
            sharedCanvas.attachTo((CanvasPage) front);
        }
    }
}
