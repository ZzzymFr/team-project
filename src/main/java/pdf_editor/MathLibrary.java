package pdf_editor;

import pdf_editor.domain.PdfMaterial;
import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.List;
import java.util.Objects;

/** In-memory material repository. Listeners and image encoding stay outside the domain. */
public final class MathLibrary {
    public static final class Material extends PdfMaterial {
        private Material(String title, String formula, String result,
                         byte[] image, int width, int height) {
            super(title, formula, result, image, width, height);
        }
    }

    private final List<Material> items = new ArrayList<>();
    private final List<Runnable> listeners = new ArrayList<>();
    private final Map<Long, String> calculationSources = new HashMap<>();
    private final Map<String, Material> suspended = new HashMap<>();
    private final Map<String, Long> manuallyDeletedSource = new HashMap<>();
    private final java.util.Deque<Material> deleted = new java.util.ArrayDeque<>();
    /** Subscribe to changes. The returned handle releases the listener when a window closes. */
    public Runnable onChange(Runnable listener) {
        listeners.add(Objects.requireNonNull(listener));
        return () -> listeners.remove(listener);
    }
    public List<Material> items() { return List.copyOf(items); }
    public Material find(String id) {
        for (Material m : items) if (m.id().equals(id)) return m;
        return null;
    }
    /** Explicit user deletion; auto-pruning invalid ink does not become an undoable action. */
    public boolean delete(String id) {
        Material material = find(id);
        if (material == null) return false;
        items.remove(material);
        calculationSources.entrySet().stream().filter(e -> e.getValue().equals(id))
                .findFirst().ifPresent(e -> manuallyDeletedSource.put(id, e.getKey()));
        calculationSources.values().removeIf(v -> v.equals(id));
        deleted.push(material);
        while (deleted.size() > 30) deleted.removeLast();
        notifyListeners();
        return true;
    }
    public boolean undoDelete() {
        if (deleted.isEmpty()) return false;
        Material restored = deleted.pop();
        if (find(restored.id()) != null) return false;
        items.add(restored);
        Long source = manuallyDeletedSource.remove(restored.id());
        if (source != null) calculationSources.put(source, restored.id());
        notifyListeners();
        return true;
    }
    public Material addCalculation(String formula, String result) {
        Material item = new Material("Calculation " + (items.size() + 1),
                formula, result, null, 0, 0);
        return publish(item);
    }
    /** A calculated answer is linked to its original ink ID, so erasing that ink removes only its material. */
    public Material addSourceCalculation(long sourceId, String formula, String result) {
        // Recalculation replaces stale results, rather than accumulating duplicates.
        forgetSource(sourceId);
        Material item = addCalculation(formula, result);
        calculationSources.put(sourceId, item.id());
        return item;
    }
    /** Automatic invalidation is not a user delete and must not enter the undo stack. */
    public boolean invalidate(String id) {
        Material item = find(id);
        if (item == null) return false;
        items.remove(item);
        calculationSources.values().removeIf(v -> v.equals(id));
        notifyListeners();
        return true;
    }
    /** Hide material when the linked ink is erased; undo can restore the same object. */
    public boolean suspend(String id) {
        Material material = find(id);
        if (material == null) return false;
        items.remove(material);
        suspended.put(id, material);
        notifyListeners();
        return true;
    }
    public boolean resume(String id) {
        Material material = suspended.remove(id);
        if (material == null) return false;
        if (find(id) == null) items.add(material);
        notifyListeners();
        return true;
    }
    /** A new calculation supersedes an earlier one from this particular source. */
    private void forgetSource(long sourceId) {
        String id = calculationSources.remove(sourceId);
        if (id == null) return;
        suspended.remove(id);
        if (items.removeIf(item -> item.id().equals(id))) notifyListeners();
    }
    public void removeSource(long sourceId) {
        String id = calculationSources.get(sourceId);
        if (id != null) suspend(id);
    }
    public void retainActiveSources(Set<Long> activeSourceIds) {
        Objects.requireNonNull(activeSourceIds, "activeSourceIds");
        for (var source : Map.copyOf(calculationSources).entrySet()) {
            if (activeSourceIds.contains(source.getKey())) resume(source.getValue());
            else suspend(source.getValue());
        }
    }
    /** Clearing the original canvas invalidates its generated library materials. */
    public void clear() {
        boolean hadItems = !items.isEmpty();
        items.clear(); calculationSources.clear(); suspended.clear();
        manuallyDeletedSource.clear(); deleted.clear();
        if (hadItems) notifyListeners();
    }
    public Material addFormulaResult(String formula, String result) { return addCalculation(formula, result); }
    public Material addFormula(String formula) {
        if (formula == null || formula.isBlank()) throw new IllegalArgumentException("Empty formula");
        for (Material existing : items) {
            if (!existing.isGraph() && existing.result().isBlank() && existing.formula().equals(formula.trim()))
                return existing;
        }
        return publish(new Material("Formula " + (items.size() + 1), formula.trim(), "", null, 0, 0));
    }
    public Material addGraph(String latex, BufferedImage image) {
        Objects.requireNonNull(image, "image");
        Material graph = publish(new Material("Graph " + (items.size() + 1), latex, "",
                toPng(image), image.getWidth(), image.getHeight()));
        return graph;
    }
    public Material addInk(String title, BufferedImage image) {
        return publish(region(title, image));
    }
    public static Material region(String title, BufferedImage image) {
        Objects.requireNonNull(image, "image");
        return new Material(title, "", "", toPng(image), image.getWidth(), image.getHeight());
    }
    /** Compatibility helpers; expression-splitting policy is owned by the domain. */
    @Deprecated
    public static Material detachedFormula(PdfMaterial source) {
        PdfMaterial segment = PdfMaterial.formulaPart(source);
        return new Material(segment.title(), segment.formula(), segment.result(), null, 0, 0);
    }
    @Deprecated
    public static Material detachedResult(PdfMaterial source) {
        PdfMaterial segment = PdfMaterial.resultPart(source);
        return new Material(segment.title(), segment.formula(), segment.result(), null, 0, 0);
    }
    private Material publish(Material item) {
        items.add(item);
        notifyListeners();
        return item;
    }
    private void notifyListeners() {
        for (Runnable listener : List.copyOf(listeners)) listener.run();
    }
    private static byte[] toPng(BufferedImage image) {
        try {
            ByteArrayOutputStream buffer = new ByteArrayOutputStream();
            if (!ImageIO.write(image, "png", buffer)) throw new IOException("PNG encoder unavailable");
            return buffer.toByteArray();
        } catch (IOException error) { throw new IllegalStateException("Could not encode image", error); }
    }
}
