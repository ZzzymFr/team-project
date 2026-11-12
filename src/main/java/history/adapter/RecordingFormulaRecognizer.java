package history.adapter;

import history.use_case.HistoryInteractor;
import interface_adapter.recognize.FormulaRecognizer;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.util.Objects;

/** Decorator preserving the recognizer contract; successful recognition becomes persistent. */
public final class RecordingFormulaRecognizer implements FormulaRecognizer {
    private final FormulaRecognizer delegate;
    private final HistoryInteractor history;
    private final java.util.function.Consumer<String> sessionCollector;
    public RecordingFormulaRecognizer(FormulaRecognizer delegate, HistoryInteractor history) {
        this(delegate, history, latex -> { });
    }
    public RecordingFormulaRecognizer(FormulaRecognizer delegate, HistoryInteractor history,
                                      java.util.function.Consumer<String> sessionCollector) {
        this.delegate = Objects.requireNonNull(delegate);
        this.history = Objects.requireNonNull(history);
        this.sessionCollector = Objects.requireNonNull(sessionCollector);
    }
    @Override public String recognize(BufferedImage image) throws Exception {
        String latex = delegate.recognize(image);
        if (latex != null && !latex.isBlank()) {
            sessionCollector.accept(latex);
            try { history.recordFormula(latex); }
            catch (IOException e) { System.err.println("Math Lab history save failed: " + e.getMessage()); }
        }
        return latex;
    }
}
