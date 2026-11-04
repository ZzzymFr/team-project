package pdf_editor.use_case;

import pdf_editor.domain.PdfDocument;
import pdf_editor.domain.PdfPreviewPage;
import java.io.IOException;
import java.util.List;

/** Single-purpose output port for generating preview images. */
@FunctionalInterface
public interface PdfPreviewGateway {
    List<PdfPreviewPage> preview(PdfDocument document, int dpi) throws IOException;
}
