package pdf_editor.data_access;

import pdf_editor.domain.PdfDocument;
import pdf_editor.use_case.PdfExportGateway;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Path;
import java.util.List;

/** Infrastructure implementation of the output port. Only this layer uses PDFBox. */
public final class PdfBoxOutputAdapter implements PdfExportGateway {
    private final PdfExporter exporter = new PdfExporter();
    @Override public void export(PdfDocument document, Path destination) throws IOException {
        exporter.export(document, destination);
    }
    @Override public void export(PdfDocument document, List<BufferedImage> pageOverlays, Path destination)
            throws IOException {
        exporter.export(document, pageOverlays, destination);
    }
}
