package pdf_editor.use_case;

import pdf_editor.domain.PdfDocument;
import pdf_editor.domain.PdfPreviewPage;
import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.Objects;

/** Application use case orchestrating preview and export through independently replaceable ports. */
public final class PdfExportInteractor {
    private final PdfExportGateway exporter;
    private final PdfPreviewGateway previewer;
    public PdfExportInteractor(PdfExportGateway exporter, PdfPreviewGateway previewer) {
        this.exporter = Objects.requireNonNull(exporter);
        this.previewer = Objects.requireNonNull(previewer);
    }
    public void export(PdfDocument document, Path destination) throws IOException {
        exporter.export(Objects.requireNonNull(document).copy(), Objects.requireNonNull(destination));
    }
    public List<PdfPreviewPage> preview(PdfDocument document) throws IOException {
        return List.copyOf(previewer.preview(Objects.requireNonNull(document).copy(), 90));
    }
}
