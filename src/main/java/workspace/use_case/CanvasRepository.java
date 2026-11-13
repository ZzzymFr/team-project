package workspace.use_case;

import workspace.domain.SavedCanvas;
import java.io.IOException;
import java.util.List;
import java.util.Optional;

/** Persistence port; application logic never depends on a file format or Swing. */
public interface CanvasRepository {
    List<SavedCanvas> list() throws IOException;
    Optional<SavedCanvas> find(String id) throws IOException;
    void write(SavedCanvas canvas) throws IOException;
}
