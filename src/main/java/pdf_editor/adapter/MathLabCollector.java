package pdf_editor.adapter;

import pdf_editor.MathLibrary;

import javax.swing.SwingUtilities;
import java.awt.image.BufferedImage;

public final class MathLabCollector {
    private final MathLibrary library;
    private final history.use_case.HistoryInteractor history;
    private final history.use_case.CanvasHistoryLifecycle linkedHistory;
    private void persist(HistoryCommand command) {
        if (history == null) return;
        try { command.execute(); }
        catch (java.io.IOException | IllegalArgumentException e) {
            System.err.println("Math Lab history save failed: " + e.getMessage());
        }
    }
    @FunctionalInterface private interface HistoryCommand { void execute() throws java.io.IOException; }
    public MathLabCollector(MathLibrary library) { this(library, null, null); }
    public MathLabCollector(MathLibrary library, history.use_case.HistoryInteractor history) {
        this(library, history, null);
    }
    public MathLabCollector(MathLibrary library, history.use_case.HistoryInteractor history,
                            history.use_case.CanvasHistoryLifecycle linkedHistory) {
        this.library = library;
        this.history = history;
        this.linkedHistory = linkedHistory;
    }
    public void acceptCalculation(String latex, String result) {
        if (latex != null && !latex.trim().isEmpty())
            onEdt(() -> {
                library.addCalculation(latex, result == null ? "" : result);
                persist(() -> history.recordCalculation(latex, result));
            });
    }
    public void acceptCalculation(long sourceId, String latex, String result) {
        if (latex != null && !latex.isBlank())
            onEdt(() -> {
                library.addSourceCalculation(sourceId, latex, result == null ? "" : result);
                if (linkedHistory != null) persist(() -> linkedHistory.recordCalculation(sourceId, latex, result));
                else persist(() -> history.recordCalculation(latex, result));
            });
    }
    public void acceptGraph(String title, BufferedImage image) {
        if (image != null) onEdt(() -> {
            MathLibrary.Material material = library.addGraph(title == null ? "Graph" : title, image);
            persist(() -> history.recordGraph(title, material.imagePng(),
                    material.imageWidth(), material.imageHeight()));
        });
    }
    private static void onEdt(Runnable task) {
        if (SwingUtilities.isEventDispatchThread()) task.run();
        else SwingUtilities.invokeLater(task);
    }
}
