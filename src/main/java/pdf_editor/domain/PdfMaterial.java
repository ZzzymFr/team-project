package pdf_editor.domain;

import java.util.Objects;
import java.util.UUID;

/** Immutable domain value representing a typeset formula, result, or image. No Swing/PDFBox dependency. */
public class PdfMaterial {
    private final String id;
    private final String title;
    private final String formula;
    private final String result;
    private final byte[] imagePng;
    private final int imageWidth;
    private final int imageHeight;

    protected PdfMaterial(String title, String formula, String result,
                          byte[] imagePng, int imageWidth, int imageHeight) {
        this(UUID.randomUUID().toString(), title, formula, result, imagePng, imageWidth, imageHeight);
    }

    protected PdfMaterial(String id, String title, String formula, String result,
                          byte[] imagePng, int imageWidth, int imageHeight) {
        this.id = Objects.requireNonNull(id, "id");
        this.title = Objects.requireNonNull(title, "title");
        this.formula = formula == null ? "" : formula;
        this.result = result == null ? "" : result;
        this.imagePng = imagePng == null ? null : imagePng.clone();
        this.imageWidth = imageWidth;
        this.imageHeight = imageHeight;
        if (imagePng != null && (imageWidth <= 0 || imageHeight <= 0))
            throw new IllegalArgumentException("Invalid image dimensions");
    }

    /** Domain rule: split a calculation without duplicating the equals sign. */
    public static PdfMaterial formulaPart(PdfMaterial source) {
        Objects.requireNonNull(source, "source");
        String formula = source.formula().trim().replaceFirst("\\s*=+\\s*$", "");
        return new PdfMaterial(source.title() + " - Formula", formula, "", null, 0, 0);
    }
    public static PdfMaterial resultPart(PdfMaterial source) {
        Objects.requireNonNull(source, "source");
        String result = source.result().trim().replaceFirst("^\\s*=+\\s*", "");
        return new PdfMaterial(source.title() + " - Result", "", "=" + result, null, 0, 0);
    }

    public final String id() { return id; }
    public final String title() { return title; }
    public final String formula() { return formula; }
    public final String result() { return result; }
    public final boolean isGraph() { return imagePng != null; }
    public final int imageWidth() { return imageWidth; }
    public final int imageHeight() { return imageHeight; }
    public final byte[] imagePng() { return imagePng == null ? null : imagePng.clone(); }
    public final boolean hasResult() { return !result.isBlank(); }
}
