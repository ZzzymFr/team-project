package pdf_editor.use_case;

import pdf_editor.domain.PdfDocument;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.Objects;

/** Application use case: exports the document through a replaceable output port. */
public final class PdfExportInteractor {
    private final PdfExportGateway exporter;
    public PdfExportInteractor(PdfExportGateway exporter) {
        this.exporter = Objects.requireNonNull(exporter);
    }
    public void export(PdfDocument document, Path destination) throws IOException {
        exporter.export(Objects.requireNonNull(document).copy(), Objects.requireNonNull(destination));
    }
    /** Exports with a picture drawn over each page, e.g. the handwriting on it; missing ones are skipped. */
    public void export(PdfDocument document, List<BufferedImage> pageOverlays, Path destination) throws IOException {
        exporter.export(Objects.requireNonNull(document).copy(), List.copyOf(pageOverlays),
                Objects.requireNonNull(destination));
    }
}
