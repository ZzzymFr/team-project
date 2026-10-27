package interface_adapter.recognize;

import java.awt.image.BufferedImage;

/**
 * Handwriting recognition (US1): turns a picture of a handwritten formula into LaTeX.
 *
 * <p>Not implemented yet. The machine-learning part implements this, e.g. by saving the image
 * to a temporary PNG, running the Python model on it and returning the LaTeX it prints.
 * The image comes from {@code DrawingCanvas.toFormulaImage()}: dark ink on white, cropped to the formula.
 */
public interface FormulaRecognizer {
    /**
     * May be slow (runs the model), so call it off the Swing event thread.
     *
     * @param formulaImage the handwritten formula, dark ink on a white background
     * @return the recognised LaTeX, e.g. {@code y=x^{2}+1}
     * @throws Exception if recognition fails; its message is suitable for the user
     */
    String recognize(BufferedImage formulaImage) throws Exception;
}
