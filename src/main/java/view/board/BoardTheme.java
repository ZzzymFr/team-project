package view.board;

import java.awt.Color;
import java.awt.Font;
import java.awt.GraphicsEnvironment;
import java.awt.font.TextAttribute;
import java.util.List;
import java.util.Map;

// light theme from the glass demo
public final class BoardTheme {
    public static final Color CANVAS = new Color(0xF2F2F7);
    public static final Color LABEL = new Color(0, 0, 0, 220);
    public static final Color LABEL_DISABLED = new Color(0, 0, 0, 70);
    public static final Color LABEL_SELECTED = new Color(0x007AFF);
    public static final Color PLATTER = new Color(0, 0, 0, 18);
    public static final Color TINT = new Color(255, 255, 255, 38);
    public static final int GLOSS = 28;
    public static final int RIM_TOP = 230;
    public static final int RIM_BOTTOM = 110;
    public static final Color EDGE = new Color(0, 0, 0, 24);
    public static final float SHADOW = 0.12f;
    public static final Color HALO = new Color(242, 242, 247, 170);
    public static final Color PANEL = new Color(0xE9E9EF);
    public static final Color[] SWATCHES = {new Color(0x1C1C1E), new Color(255, 59, 48), new Color(0x007AFF),
        new Color(52, 199, 89), new Color(255, 149, 0)};

    public static final boolean MAC = System.getProperty("os.name", "").toLowerCase().startsWith("mac");
    public static final int TITLEBAR_H = 28;  // drag strip on mac
    public static final int TOP = 18;         // capsules from the top edge
    public static final int BOTTOM = 22;      // toolbar from the bottom edge
    public static final int SIDE = 18;
    public static final int HISTORY_LEFT = 80; // clear of the traffic lights

    private static final String FAMILY = pickFamily();

    private BoardTheme() {
    }

    private static String pickFamily() {
        String[] candidates = {"SF Pro Text", "SF Pro", "Helvetica Neue", "Segoe UI", "Inter"};
        try {
            List<String> installed = List.of(GraphicsEnvironment.getLocalGraphicsEnvironment()
                    .getAvailableFontFamilyNames());
            for (String c : candidates) {
                if (installed.contains(c)) {
                    return c;
                }
            }
        } catch (RuntimeException | Error ignored) {
            // no font list, use the default
        }
        return "Dialog";
    }

    public static Font font(float size) {
        return new Font(Map.of(TextAttribute.FAMILY, FAMILY, TextAttribute.SIZE, size,
                TextAttribute.WEIGHT, TextAttribute.WEIGHT_MEDIUM));
    }
}
