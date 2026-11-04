package pdf_editor.domain;

import java.util.Objects;

/** Immutable PNG bytes for one preview page. Application code does not depend on Swing/Java2D. */
public final class PdfPreviewPage {
    private final byte[] png;
    public PdfPreviewPage(byte[] png) {
        this.png = Objects.requireNonNull(png, "png").clone();
        if (png.length == 0) throw new IllegalArgumentException("Empty preview image");
    }
    public byte[] png() { return png.clone(); }
}
