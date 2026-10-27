package pdf_editor;

import javax.swing.SwingUtilities;
import java.awt.image.BufferedImage;

public final class MathLabCollector {
    private final MathLibrary library;
    public MathLabCollector(MathLibrary library) { this.library = library; }
    public void acceptCalculation(String latex, String result) {
        if (latex != null && !latex.trim().isEmpty())
            onEdt(() -> library.addCalculation(latex, result == null ? "" : result));
    }
    public void acceptRecognizedFormula(String latex) {
        if (latex != null && !latex.trim().isEmpty()) onEdt(() -> library.addFormula(latex));
    }
    public void acceptGraph(String title, BufferedImage image) {
        if (image != null) onEdt(() -> library.addGraph(title == null ? "Graph" : title, image));
    }
    private static void onEdt(Runnable task) {
        if (SwingUtilities.isEventDispatchThread()) task.run();
        else SwingUtilities.invokeLater(task);
    }
}
