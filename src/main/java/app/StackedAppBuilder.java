package app;

import interface_adapter.recognize.FormulaRecognizer;
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

    public StackedAppBuilder addGraphPage() {
        pages.addPage("Graph", GRAPH_BLUE, new GraphCanvasPage(sharedCanvas));
        return this;
    }

    public StackedAppBuilder addPdfPage() {
        pages.addPage("PDF", PDF_ORANGE, new PdfCanvasPage(sharedCanvas));
        return this;
    }

    public StackedAppBuilder addAnswerPage() {
        pages.addPage("Answer", ANSWER_GREEN, new AnswerCanvasPage(sharedCanvas));
        return this;
    }

    /** An Answer page showing the given tools (e.g. the expression evaluator) above the canvas. */
    public StackedAppBuilder addAnswerPage(JComponent answerTools) {
        pages.addPage("Answer", ANSWER_GREEN, new AnswerCanvasPage(sharedCanvas, answerTools));
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
