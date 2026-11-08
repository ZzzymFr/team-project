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
import view.canvas.CanvasShell;
import view.canvas.FormulaGrapher;
import view.canvas.SharedCanvas;
import view.glass.GlassBar;
import view.ribbon.Ribbon;
import view.stack.Fonts;
import pdf_editor.PdfStudioLauncher;

import javax.swing.JFrame;
import javax.swing.WindowConstants;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Frame;

/**
 * Builds the window: a Word-like ribbon across the top and one handwriting canvas of A4 sheets
 * under it. The drawing tools are on the Draw tab and work only while it is chosen; on the other
 * tabs the canvas is a plain pointer. Circling a formula with the lasso opens the Math tab, with Calculate and Graph;
 * calculated formulas and graphs are collected in the library, to be placed on the sheets beside
 * the ink and exported as a PDF from the title row of the ribbon.
 */
public class AppBuilder {
    private static final Dimension MINIMUM_SIZE = new Dimension(900, 520);

    private final SharedCanvas sharedCanvas = new SharedCanvas(new DrawingCanvas());
    private final PdfStudioLauncher pdfStudio = new PdfStudioLauncher();

    /** The graph use case: LaTeX -> parse and sample -> QuickChart -> graph card, and on to the PDF editor. */
    private FormulaGrapher buildGrapher() {
        GraphViewModel viewModel = new GraphViewModel();
        viewModel.addPropertyChangeListener(event -> {
            if (GraphViewModel.STATE_PROPERTY.equals(event.getPropertyName())) {
                interface_adapter.graph.GraphState state = viewModel.getState();
                if (state.getError() == null && state.getGraphImage() != null) {
                    pdfStudio.addGraph(state.getLatex(), state.getGraphImage());
                }
            }
        });
        GraphFunctionInteractor interactor = new GraphFunctionInteractor(
                new LatexFunctionParser(), new QuickChartGraphRenderer(), new GraphPresenter(viewModel));
        return new FormulaGrapher(sharedCanvas, new GraphController(interactor), viewModel);
    }

    // the page -> evaluate use case, graph use case
    private AnswerCanvasPage buildPage() {
        MathLabViewModel viewModel = new MathLabViewModel();
        EvaluateInteractor interactor = new EvaluateInteractor(
                new InMemoryFunctionDataAccessObject(), new EvaluatePresenter(viewModel));
        EvaluateController controller = new EvaluateController(interactor);
        return new AnswerCanvasPage(sharedCanvas, controller, viewModel, pdfStudio::addCalculation, buildGrapher());
    }

    /** Connects handwriting recognition once the machine-learning part is ready. */
    public AppBuilder setFormulaRecognizer(FormulaRecognizer recognizer) {
        sharedCanvas.setFormulaRecognizer(recognizer);
        return this;
    }

    public SharedCanvas getSharedCanvas() {
        return sharedCanvas;
    }

    public JFrame build() {
        AnswerCanvasPage page = buildPage();
        CanvasShell shell = new CanvasShell(sharedCanvas, page);
        Ribbon ribbon = new Ribbon("Math Lab", sharedCanvas);  // frosted glass over the canvas
        pdfStudio.install(shell, sharedCanvas, ribbon);  // file commands and the Edit tab
        shell.addDrawTab(ribbon);  // pen, eraser and lasso: they only work while it is chosen
        page.addMathTab(ribbon);
        shell.setHeader(ribbon);

        JFrame frame = new JFrame("Math Lab");
        frame.setDefaultCloseOperation(WindowConstants.EXIT_ON_CLOSE);
        shell.install(frame);
        frame.setMinimumSize(MINIMUM_SIZE);
        frame.setSize(980, 660);
        frame.setLocationRelativeTo(null);
        frame.setExtendedState(frame.getExtendedState() | Frame.MAXIMIZED_BOTH);
        GlassBar.warmUp(Fonts.ui(Font.PLAIN, 15f));
        return frame;
    }
}
