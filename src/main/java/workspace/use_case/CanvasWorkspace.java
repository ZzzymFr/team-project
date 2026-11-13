package workspace.use_case;

import workspace.domain.CanvasState;
import workspace.domain.SavedCanvas;
import java.io.IOException;
import java.time.Clock;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/** Save/Save As/Open operations; IDs are never derived from user-supplied file names. */
public final class CanvasWorkspace implements CanvasWorkspaceBoundary {
    private final CanvasRepository repository;
    private final Clock clock;
    private String currentId;
    public CanvasWorkspace(CanvasRepository repository) { this(repository, Clock.systemUTC()); }
    public CanvasWorkspace(CanvasRepository repository, Clock clock) {
        this.repository = Objects.requireNonNull(repository);
        this.clock = Objects.requireNonNull(clock);
    }
    public Optional<String> currentId() { return Optional.ofNullable(currentId); }
    public List<SavedCanvas> all() throws IOException {
        return repository.list().stream().sorted(Comparator.comparing(SavedCanvas::modifiedAt).reversed()).toList();
    }
    public Optional<SavedCanvas> mostRecent() throws IOException {
        return all().stream().findFirst();
    }
    public SavedCanvas open(String id) throws IOException {
        SavedCanvas found = repository.find(Objects.requireNonNull(id)).orElseThrow(
                () -> new IOException("Saved canvas was not found"));
        currentId = found.id();
        return found;
    }
    public SavedCanvas save(CanvasState state, String name, boolean saveAs) throws IOException {
        Objects.requireNonNull(state);
        String id = saveAs || currentId == null ? UUID.randomUUID().toString() : currentId;
        if (name == null || name.isBlank()) throw new IllegalArgumentException("A document name is required");
        SavedCanvas next = new SavedCanvas(id, name.trim(), clock.instant(), state);
        repository.write(next);
        currentId = id;
        return next;
    }
    public void forgetCurrent() { currentId = null; }
}
