package pdf_editor.adapter;

import entity.Stroke;
import pdf_editor.MathLibrary;
import view.DrawingCanvas;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** UI-adapter mapping of library material IDs to their original strokes. */
public final class SelectedInkRegistry {
    private final DrawingCanvas canvas;
    private final MathLibrary library;
    private final Map<String, List<List<Stroke>>> sources = new HashMap<>();
    public SelectedInkRegistry(DrawingCanvas canvas, MathLibrary library) {
        this.canvas = Objects.requireNonNull(canvas);
        this.library = Objects.requireNonNull(library);
        canvas.addInkChangeListener(this::synchronize);
    }
    public void track(String materialId, List<Stroke> ink) {
        if (canvas.containsAllInk(ink))
            sources.computeIfAbsent(materialId, id -> new java.util.ArrayList<>()).add(List.copyOf(ink));
    }
    public void synchronize() {
        for (String id : List.copyOf(sources.keySet())) {
            boolean present = sources.get(id).stream().anyMatch(canvas::containsAllInk);
            if (present) library.resume(id);
            else library.suspend(id);
        }
    }
}
