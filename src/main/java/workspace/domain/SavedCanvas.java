package workspace.domain;

import java.time.Instant;
import java.util.Objects;

/** Named workspace snapshot with stable identity for Save vs Save As. */
public record SavedCanvas(String id, String name, Instant modifiedAt, CanvasState state) {
    public SavedCanvas {
        Objects.requireNonNull(id);
        Objects.requireNonNull(name);
        Objects.requireNonNull(modifiedAt);
        Objects.requireNonNull(state);
        if (!id.matches("[0-9a-fA-F-]{36}") || name.isBlank() || name.length() > 100
                || name.indexOf('\n') >= 0 || name.indexOf('\r') >= 0)
            throw new IllegalArgumentException("Invalid saved canvas identifier or name");
    }
}
