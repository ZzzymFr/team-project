package view.canvas;

/**
 * PDF page: exports the formulas written on the canvas as a LaTeX PDF (US4).
 *
 * <p>The function area is a placeholder for now; it will hold this page's controls.
 * Use {@link #captureFormula()} or {@link #recognizeFormula()} to get what was written.
 */
public class PdfCanvasPage extends CanvasPage {
    public PdfCanvasPage(SharedCanvas sharedCanvas) {
        super(sharedCanvas);
        setFunctionArea(placeholder("PDF tools go here: export the formulas on the canvas as a LaTeX PDF."));
    }
}
