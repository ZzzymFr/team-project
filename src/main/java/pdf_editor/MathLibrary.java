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
    public Material addCalculation(String formula, String result) {
        Material item = new Material("Calculation " + (items.size() + 1),
                formula, result, null, 0, 0);
        return publish(item);
    }
    /** A calculated answer is linked to its original ink ID, so erasing that ink removes only its material. */
    public Material addSourceCalculation(long sourceId, String formula, String result) {
        // Recalculation replaces stale results, rather than accumulating duplicates.
        removeSource(sourceId);
        Material item = addCalculation(formula, result);
        calculationSources.put(sourceId, item.id());
        return item;
    }
    public void removeSource(long sourceId) {
        String id = calculationSources.remove(sourceId);
        if (id != null && items.removeIf(item -> item.id().equals(id))) notifyListeners();
    }
    public void retainActiveSources(Set<Long> activeSourceIds) {
        Objects.requireNonNull(activeSourceIds, "activeSourceIds");
        for (long sourceId : Set.copyOf(calculationSources.keySet()))
            if (!activeSourceIds.contains(sourceId)) removeSource(sourceId);
    }
    /** Clearing the original canvas invalidates its generated library materials. */
    public void clear() {
        if (items.isEmpty()) return;
        items.clear(); calculationSources.clear(); notifyListeners();
    }
    public Material addFormulaResult(String formula, String result) { return addCalculation(formula, result); }
    public Material addFormula(String formula) {
        return publish(new Material("Formula " + (items.size() + 1), formula, "", null, 0, 0));
    }
    public Material addGraph(String latex, BufferedImage image) {
        Objects.requireNonNull(image, "image");
        Material graph = publish(new Material("Graph " + (items.size() + 1), latex, "",
                toPng(image), image.getWidth(), image.getHeight()));
        // Keep the graph AND its source formula as independently draggable materials.
        if (latex != null && !latex.isBlank()) addFormula(latex);
        return graph;
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
