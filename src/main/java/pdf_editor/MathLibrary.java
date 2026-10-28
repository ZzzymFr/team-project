package pdf_editor;

import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

public final class MathLibrary {
    public static final class Material {
        public final String id;
        public final String title;
        public final String formula;
        public final String result;
        public final BufferedImage graph;
        private Material(String title, String formula, String result, BufferedImage graph) {
            this.id = UUID.randomUUID().toString();
            this.title = title;
            this.formula = formula;
            this.result = result;
            this.graph = graph;
        }
        public boolean isGraph() { return graph != null; }
    }
    public static Material detachedFormula(Material source) {
        return new Material(source.title + " - Formula", source.formula, "", null);
    }
    public static Material detachedResult(Material source) {
        return new Material(source.title + " - Result", source.result, "", null);
    }
    private final List<Material> items = new ArrayList<>();
    private final List<Runnable> listeners = new ArrayList<>();
    public void onChange(Runnable listener) { listeners.add(listener); }
    public List<Material> items() { return Collections.unmodifiableList(items); }
    public Material addCalculation(String formula, String result) {
        Material item = new Material("Calculation " + (items.size() + 1), formula, result, null);
        items.add(item); changed(); return item;
    }

    public Material addFormulaResult(String formula, String result) {
        return addCalculation(formula, result);
    }
    public Material addFormula(String formula) {
        Material item = new Material("Formula " + (items.size() + 1), formula, "", null);
        items.add(item); changed(); return item;
    }
    public Material addGraph(String latex, BufferedImage image) {
        if (image == null) throw new IllegalArgumentException("Image is required");
        Material item = new Material("Graph " + (items.size() + 1), latex, "", image);
        items.add(item); changed(); return item;
    }
    private void changed() { for (Runnable listener : new ArrayList<>(listeners)) listener.run(); }
}
