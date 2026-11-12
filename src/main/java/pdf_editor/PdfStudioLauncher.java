package pdf_editor;

import pdf_editor.adapter.PdfEditorController;
import pdf_editor.data_access.PdfBoxOutputAdapter;
import pdf_editor.use_case.AutoLayoutInteractor;
import pdf_editor.adapter.CanvasSnapshotGateway;
import pdf_editor.adapter.MathLabCollector;
import pdf_editor.data_access.DrawingCanvasSnapshotAdapter;
import pdf_editor.use_case.DocumentEditor;
import pdf_editor.use_case.PdfExportInteractor;
import view.board.PdfPanel;
import view.canvas.SharedCanvas;

import javax.imageio.ImageIO;
import javax.swing.SwingUtilities;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.file.Path;
import java.util.Set;
import java.util.stream.Collectors;

/** Composition root for US4: wires incoming team events, use cases and external adapters. */
public final class PdfStudioLauncher implements Us4InputBoundary {
    private final MathLibrary library = new MathLibrary();
    private final MathLabCollector collector;
    private final history.use_case.CanvasHistoryLifecycle canvasHistory;
    private final PdfEditorController controller;
    private Runnable showPdf = () -> { };

    public PdfStudioLauncher() { this(null); }
    public PdfStudioLauncher(history.use_case.HistoryInteractor history) {
        canvasHistory = history == null ? null : new history.use_case.CanvasHistoryLifecycle(history);
        collector = new MathLabCollector(library, history, canvasHistory);
        DocumentEditor editing = new DocumentEditor();
        PdfBoxOutputAdapter pdfOutput = new PdfBoxOutputAdapter();
        controller = new PdfEditorController(library, editing, new AutoLayoutInteractor(editing),
                new PdfExportInteractor(pdfOutput, pdfOutput));
    }
    public PdfEditorController controller() { return controller; }
    public void onOpen(Runnable show) { showPdf = java.util.Objects.requireNonNull(show); }
    /** The document model is shared across overlay/split mode changes and stays editable. */
    public PdfPanel createPanel() {
        PdfPanel panel = new PdfPanel(controller);
        panel.setCanvasCapture(() -> snapshotGateway.capture());
        return panel;
    }
    private CanvasSnapshotGateway snapshotGateway = () -> null;

    /** Team Canvas is only read through its adapter; it is never modified by PDF Studio. */
    public void setSourceCanvas(SharedCanvas canvas) {
        snapshotGateway = canvas == null ? () -> null
                : new DrawingCanvasSnapshotAdapter(canvas);
    }
    public MathLibrary getLibrary() { return library; }
    public MathLabCollector getCollector() { return collector; }
    @Override public void addCalculation(String latex, String result) { collector.acceptCalculation(latex, result); }
    public void addCalculationWithSource(long sourceId, String latex, String result) {
        collector.acceptCalculation(sourceId, latex, result);
    }
    public void synchronizeCanvas(SharedCanvas canvas) {
        if (canvas == null) return;
        Runnable prune = () -> {
            Set<Long> active = canvas.getDrawingCanvas().getFormulas().stream()
                    .filter(formula -> formula.getAnswer() != null)
                    .map(formula -> formula.getId()).collect(Collectors.toSet());
            library.retainActiveSources(active);
            if (canvasHistory != null) {
                try { canvasHistory.synchronize(active); }
                catch (IOException ex) { System.err.println("Canvas/history synchronization failed: " + ex.getMessage()); }
            }
        };
        if (SwingUtilities.isEventDispatchThread()) prune.run();
        else SwingUtilities.invokeLater(prune);
    }
    public void addFormulaResult(String latex, String result) { addCalculation(latex, result); }
    @Override public void addGraph(String latex, BufferedImage image) { collector.acceptGraph(latex, image); }
    public void addGraphPng(String latex, Path png) throws IOException {
        BufferedImage image = ImageIO.read(png.toFile());
        if (image == null) throw new IOException("Not a readable PNG image: " + png);
        addGraph(latex, image);
    }
    public void addGraphPng(String latex, byte[] png) throws IOException {
        BufferedImage image = ImageIO.read(new ByteArrayInputStream(png));
        if (image == null) throw new IOException("Not a readable PNG image");
        addGraph(latex, image);
    }
    @Override public void openEditor() {
        if (SwingUtilities.isEventDispatchThread()) showPdf.run();
        else SwingUtilities.invokeLater(showPdf);
    }
}
