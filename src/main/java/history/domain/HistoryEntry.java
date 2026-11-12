package history.domain;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.Arrays;
import java.util.Objects;

/** Immutable, portable calculation/graph history; does not depend on Swing or storage. */
public record HistoryEntry(String id, Kind kind, String title, String formula, String result,
                           byte[] png, int imageWidth, int imageHeight, Instant createdAt) {
    public enum Kind { FORMULA, CALCULATION, GRAPH }
    public static final int MAX_PNG_BYTES = 12 * 1024 * 1024;

    public HistoryEntry {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(kind, "kind");
        Objects.requireNonNull(title, "title");
        Objects.requireNonNull(formula, "formula");
        Objects.requireNonNull(result, "result");
        Objects.requireNonNull(createdAt, "createdAt");
        if (id.isBlank() || id.length() > 100) throw new IllegalArgumentException("Invalid history ID");
        if (title.isBlank() || title.length() > 200 || formula.length() > 32768 || result.length() > 32768)
            throw new IllegalArgumentException("History text is empty or too long");
        if (kind == Kind.GRAPH) {
            if (png == null || png.length == 0 || png.length > MAX_PNG_BYTES
                    || imageWidth <= 0 || imageHeight <= 0 || imageWidth > 16384 || imageHeight > 16384)
                throw new IllegalArgumentException("Invalid graph PNG or dimensions");
            // At minimum, refuse arbitrary bytes mislabeled as a PNG graph.
            byte[] signature = {(byte) 137, 80, 78, 71, 13, 10, 26, 10};
            if (png.length < signature.length ||
                    !Arrays.equals(Arrays.copyOf(png, signature.length), signature))
                throw new IllegalArgumentException("Graph bytes must have a PNG signature");
        } else if (png != null || imageWidth != 0 || imageHeight != 0) {
            throw new IllegalArgumentException("Only graphs may contain image bytes");
        }
        if (kind == Kind.FORMULA && (formula.isBlank() || !result.isBlank()))
            throw new IllegalArgumentException("Formula entry requires LaTeX without a result");
        if (kind == Kind.CALCULATION && formula.isBlank())
            throw new IllegalArgumentException("Calculation requires a formula");
        png = png == null ? null : png.clone();
    }

    @Override public byte[] png() { return png == null ? null : png.clone(); }

    /** Same mathematical content = same key, even after restarting the application. */
    public String contentKey() {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            update(digest, kind.name().getBytes(StandardCharsets.UTF_8));
            update(digest, formula.trim().getBytes(StandardCharsets.UTF_8));
            update(digest, result.trim().getBytes(StandardCharsets.UTF_8));
            if (png != null) {
                update(digest, ByteBuffer.allocate(8).putInt(imageWidth).putInt(imageHeight).array());
                update(digest, png);
            }
            return java.util.HexFormat.of().formatHex(digest.digest());
        } catch (NoSuchAlgorithmException impossible) { throw new IllegalStateException(impossible); }
    }

    private static void update(MessageDigest digest, byte[] bytes) {
        digest.update(ByteBuffer.allocate(4).putInt(bytes.length).array());
        digest.update(bytes);
    }
}
