package pdf_editor.data_access;

import pdf_editor.domain.PdfDocument;
import pdf_editor.domain.PdfPreviewPage;
import javax.imageio.ImageIO;
import java.io.ByteArrayOutputStream;
import pdf_editor.use_case.PdfExportGateway;
import pdf_editor.use_case.PdfPreviewGateway;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.rendering.PDFRenderer;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/** Infrastructure implementation of the output port. Only this layer uses PDFBox. */
public final class PdfBoxOutputAdapter implements PdfExportGateway, PdfPreviewGateway {
    private final PdfExporter exporter = new PdfExporter();
    @Override public void export(PdfDocument document, Path destination) throws IOException {
        exporter.export(document, destination);
    }
    @Override public List<PdfPreviewPage> preview(PdfDocument document, int dpi) throws IOException {
        if (dpi <= 0) throw new IllegalArgumentException("DPI must be positive");
        try (PDDocument pdf = exporter.build(document)) {
            PDFRenderer renderer = new PDFRenderer(pdf);
            List<PdfPreviewPage> pages = new ArrayList<>();
            for (int i=0; i<pdf.getNumberOfPages(); i++) {
                ByteArrayOutputStream bytes = new ByteArrayOutputStream();
                if (!ImageIO.write(renderer.renderImageWithDPI(i, dpi), "png", bytes))
                    throw new IOException("PNG encoder is unavailable");
                pages.add(new PdfPreviewPage(bytes.toByteArray()));
            }
            return pages;
        }
    }
}
