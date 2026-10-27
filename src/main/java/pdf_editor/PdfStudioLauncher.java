package pdf_editor;

import javax.swing.SwingUtilities;
import java.awt.image.BufferedImage;

public final class PdfStudioLauncher implements Us4InputBoundary {
    private final MathLibrary library = new MathLibrary();
    private final MathLabCollector collector = new MathLabCollector(library);
    public MathLibrary getLibrary() { return library; }
    public MathLabCollector getCollector() { return collector; }
    public void addCalculation(String latex, String result) { collector.acceptCalculation(latex, result); }
    public void addFormulaResult(String latex, String result) { addCalculation(latex, result); }
    public void addRecognizedFormula(String latex) { collector.acceptRecognizedFormula(latex); }
    public void addGraph(String title, BufferedImage image) { collector.acceptGraph(title, image); }

    public void addGraphPng(String title, java.nio.file.Path png) throws java.io.IOException {
        BufferedImage image = javax.imageio.ImageIO.read(png.toFile());
        if (image == null) throw new java.io.IOException("Not a readable PNG image: " + png);
        addGraph(title, image);
    }
    public void addGraphPng(String title, byte[] png) throws java.io.IOException {
        BufferedImage image = javax.imageio.ImageIO.read(new java.io.ByteArrayInputStream(png));
        if (image == null) throw new java.io.IOException("Not a readable PNG image");
        addGraph(title, image);
    }
    public void openEditor() {
        Runnable open = () -> new PdfEditorFrame(library).setVisible(true);
        if (SwingUtilities.isEventDispatchThread()) open.run();
        else SwingUtilities.invokeLater(open);
    }
}
