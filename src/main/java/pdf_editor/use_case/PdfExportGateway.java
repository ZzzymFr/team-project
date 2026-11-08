package pdf_editor.use_case;

import pdf_editor.domain.PdfDocument;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Path;
import java.util.List;

/** Single-purpose output port for saving a document. */
@FunctionalInterface
public interface PdfExportGateway {
    void export(PdfDocument document, Path destination) throws IOException;

    /**
     * Saves the document with {@code pageOverlays.get(i)} drawn over the whole of page i, on top of
     * its items (the handwriting on the canvas). Gateways that cannot draw overlays save without them.
     */
    default void export(PdfDocument document, List<BufferedImage> pageOverlays, Path destination) throws IOException {
        export(document, destination);
    }
}
