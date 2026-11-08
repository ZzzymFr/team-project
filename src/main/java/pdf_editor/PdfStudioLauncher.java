package pdf_editor;

import pdf_editor.adapter.MathLabCollector;
import pdf_editor.adapter.PdfEditorController;
import pdf_editor.data_access.PdfBoxOutputAdapter;
import pdf_editor.use_case.AutoLayoutInteractor;
import pdf_editor.use_case.DocumentEditor;
import pdf_editor.use_case.PdfExportInteractor;
import pdf_editor.view.DocumentRibbon;
import pdf_editor.view.LibraryPanel;
import pdf_editor.view.PaperItemsLayer;
import view.canvas.CanvasShell;
import view.canvas.SharedCanvas;
import view.ribbon.Ribbon;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.file.Path;

/**
 * Composition root for US4: wires incoming team events, use cases and external adapters. The PDF
 * document lives on the canvas's own A4 sheets ({@link #install}), with its commands on the ribbon
 * across the top of the window and the material library in a panel at the left.
 */
public final class PdfStudioLauncher implements Us4InputBoundary {
    private final MathLibrary library = new MathLibrary();
    private final MathLabCollector collector = new MathLabCollector(library);
    private DocumentRibbon ribbon;
    private PaperItemsLayer items;

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

    /**
     * Puts the PDF document on the canvas's paper (its items drawn under the ink, moved with the
     * lasso tool, undone with Undo), its file commands and Edit tab on the ribbon and the library at
     * the left, hidden until the Library button opens it. Call once, on the Swing event thread.
     */
    public void install(CanvasShell shell, SharedCanvas canvas, Ribbon ribbonBar) {
        if (ribbon != null) throw new IllegalStateException("Already installed");
        DocumentEditor editing = new DocumentEditor();
        PdfBoxOutputAdapter pdfOutput = new PdfBoxOutputAdapter();
        PdfEditorController controller = new PdfEditorController(library, editing,
                new AutoLayoutInteractor(editing), new PdfExportInteractor(pdfOutput));
        items = new PaperItemsLayer(controller);
        canvas.getDrawingCanvas().setPaperLayer(items);
        LibraryPanel[] panel = new LibraryPanel[1];
        panel[0] = new LibraryPanel(library, items::insert, () -> panel[0].setVisible(false));
        panel[0].setVisible(false);
        ribbon = new DocumentRibbon(ribbonBar, canvas, controller, items, panel[0]);
        shell.setSidePanel(panel[0]);
    }

    /** The document's commands on the ribbon, once installed. */
    public DocumentRibbon getRibbon() { return ribbon; }

    /** The document's items on the paper, once installed. */
    public PaperItemsLayer getItems() { return items; }
}
