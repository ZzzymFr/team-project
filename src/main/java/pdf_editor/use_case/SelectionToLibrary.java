package pdf_editor.use_case;

import pdf_editor.MathLibrary;
import java.awt.image.BufferedImage;
import java.util.Objects;

/** A formula is added only by an explicit user selection command, not by background recognition. */
public final class SelectionToLibrary {
    private final SelectedInkRecognitionGateway recognizer;
    private final MathLibrary library;
    public SelectionToLibrary(SelectedInkRecognitionGateway recognizer, MathLibrary library) {
        this.recognizer = Objects.requireNonNull(recognizer);
        this.library = Objects.requireNonNull(library);
    }
    public String recognize(BufferedImage selectedInk) throws Exception {
        String latex = recognizer.recognize(Objects.requireNonNull(selectedInk));
        if (latex == null || latex.isBlank()) throw new IllegalArgumentException("No formula was recognized");
        return latex.trim();
    }
    public MathLibrary.Material addFormula(String latex) { return library.addFormula(latex); }
    public MathLibrary.Material addInk(BufferedImage image) {
        return library.addInk("Selected handwriting", Objects.requireNonNull(image));
    }
}
