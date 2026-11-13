package workspace.use_case;

import workspace.domain.CanvasState;
import workspace.domain.SavedCanvas;
import java.io.IOException;
import java.util.List;
import java.util.Optional;

/** View-facing input boundary for named canvas documents. */
public interface CanvasWorkspaceBoundary {
    Optional<String> currentId();
    List<SavedCanvas> all() throws IOException;
    Optional<SavedCanvas> mostRecent() throws IOException;
    SavedCanvas open(String id) throws IOException;
    SavedCanvas save(CanvasState state, String name, boolean saveAs) throws IOException;
}
