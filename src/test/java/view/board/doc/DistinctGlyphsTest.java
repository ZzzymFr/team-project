package view.board.doc;

import org.junit.jupiter.api.Test;
import view.board.Icons;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import javax.swing.JComponent;
import static org.junit.jupiter.api.Assertions.*;

/** Each action uses a meaningful, vector-drawn icon at the target display resolution. */
class DistinctGlyphsTest {
    private static BufferedImage paint(JComponent icon) {
        icon.setSize(icon.getPreferredSize());
        BufferedImage image = new BufferedImage(icon.getWidth(), icon.getHeight(), BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = image.createGraphics();
        icon.paint(g);
        g.dispose();
        return image;
    }

    @Test void previewIsAnEyeNotAnotherDocumentOrHistoryClock() {
        var preview = paint(DocumentGlyphs.component(48, Icons::preview));
        var document = paint(DocumentGlyphs.component(48, Icons::document));
        var history = paint(DocumentGlyphs.component(48, Icons::history));
        assertNotEquals(java.util.Arrays.hashCode(preview.getRGB(0,0,48,48,null,0,48)),
                java.util.Arrays.hashCode(document.getRGB(0,0,48,48,null,0,48)));
        assertNotEquals(java.util.Arrays.hashCode(preview.getRGB(0,0,48,48,null,0,48)),
                java.util.Arrays.hashCode(history.getRGB(0,0,48,48,null,0,48)));
    }

    @Test void libraryIconIsDistinctAndContainsVisibleStrokesOnTransparentBackground() {
        var library = paint(DocumentGlyphs.component(32, Icons::library));
        assertEquals(0, library.getRGB(0,0) >>> 24);
        int ink = 0;
        for(int y=0;y<library.getHeight();y++) for(int x=0;x<library.getWidth();x++)
            if((library.getRGB(x,y) >>> 24) > 120) ink++;
        assertTrue(ink > 15);
    }

    @Test void pageActionsHaveDifferentGlyphs() {
        assertNotEquals(signature(DocIcons::pageBreak), signature(DocIcons::deletePage));
        assertNotEquals(signature(DocIcons::deletePage), signature(DocIcons::movePage));
        assertNotEquals(signature(DocIcons::movePage), signature(DocIcons::layers));
    }

    private static int signature(Icons.Glyph glyph) {
        return java.util.Arrays.hashCode(paint(DocumentGlyphs.component(40, glyph))
                .getRGB(0, 0, 40, 40, null, 0, 40));
    }
}
