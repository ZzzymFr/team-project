package pdf_editor;

import pdf_editor.adapter.PdfEditorController;
import pdf_editor.data_access.PdfBoxOutputAdapter;
import pdf_editor.use_case.AutoLayoutInteractor;
import pdf_editor.adapter.CanvasSnapshotGateway;
import pdf_editor.adapter.MathLabCollector;
import pdf_editor.data_access.DrawingCanvasSnapshotAdapter;
import pdf_editor.use_case.DocumentEditor;
import pdf_editor.use_case.PdfExportInteractor;
import pdf_editor.view.PdfEditorFrame;
import view.canvas.SharedCanvas;

import javax.imageio.ImageIO;
import javax.swing.SwingUtilities;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.file.Path;

/** Composition root for US4: wires incoming team events, use cases and external adapters. */
public final class PdfStudioLauncher implements Us4InputBoundary {
    private final MathLibrary library = new MathLibrary();
    private final MathLabCollector collector = new MathLabCollector(library);
    private CanvasSnapshotGateway snapshotGateway = () -> null;

    /** Team Canvas is only read through its adapter; it is never modified by PDF Studio. */
    public void setSourceCanvas(SharedCanvas canvas) {
        snapshotGateway = canvas == null ? () -> null
                : new DrawingCanvasSnapshotAdapter(canvas);
    }
    public MathLibrary getLibrary() { return library; }
    public MathLabCollector getCollector() { return collector; }
    @Override public void addCalculation(String latex, String result) { collector.acceptCalculation(latex, result); }
    public void addFormulaResult(String latex, String result) { addCalculation(latex, result); }
    public void addRecognizedFormula(String latex) { collector.acceptRecognizedFormula(latex); }
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
        Runnable open = () -> {
            DocumentEditor editing = new DocumentEditor();
            PdfBoxOutputAdapter pdfOutput = new PdfBoxOutputAdapter();
            PdfEditorController controller = new PdfEditorController(library, editing,
                    new AutoLayoutInteractor(editing), new PdfExportInteractor(pdfOutput, pdfOutput));
            new PdfEditorFrame(controller, snapshotGateway.capture()).setVisible(true);
        };
        if (SwingUtilities.isEventDispatchThread()) open.run();
        else SwingUtilities.invokeLater(open);
    }
}
