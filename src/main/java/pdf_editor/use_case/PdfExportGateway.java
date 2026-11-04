package pdf_editor.use_case;

import pdf_editor.domain.PdfDocument;
import java.io.IOException;
import java.nio.file.Path;

/** Single-purpose output port for saving a document. */
@FunctionalInterface
public interface PdfExportGateway {
    void export(PdfDocument document, Path destination) throws IOException;
}
