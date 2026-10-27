package view.stack;

import java.awt.Font;

/** Segoe UI on Windows; the standard sans-serif font elsewhere. */
public final class Fonts {
    private Fonts() {
    }

    public static Font ui(int style, float size) {
        Font font = new Font("Segoe UI", style, 1);
        if (!"Segoe UI".equals(font.getFamily())) {
            font = new Font(Font.SANS_SERIF, style, 1);
        }
        return font.deriveFont(size);
    }
}
