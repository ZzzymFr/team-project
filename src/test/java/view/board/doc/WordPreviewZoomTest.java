package view.board.doc;

import org.junit.jupiter.api.Test;
import java.awt.Dimension;
import java.awt.image.BufferedImage;
import static org.junit.jupiter.api.Assertions.*;
class WordPreviewZoomTest {
    @Test void zoomResizesPreviewWithoutPrecomputingAnotherImage() {
        var view=new WordPreviewDialog.PreviewPage();
        BufferedImage source=new BufferedImage(600,800,BufferedImage.TYPE_INT_RGB);
        view.setPage(source);
        assertEquals(new Dimension(644,844),view.getPreferredSize());
        view.setZoom(0.5);
        assertEquals(new Dimension(344,444),view.getPreferredSize());
    }
    @Test void scrollDistanceIsUsefulForLargePages() {
        var view = new WordPreviewDialog.PreviewPage();
        assertTrue(view.getScrollableUnitIncrement(new java.awt.Rectangle(0,0,500,600),1,1)>=60);
        assertTrue(view.getScrollableBlockIncrement(new java.awt.Rectangle(0,0,500,600),1,1)>=300);
    }
    @Test void monochromeIconHasNoBlueBackgroundTile() {
        var image=DocumentGlyphs.image(24);
        assertEquals(0,image.getRGB(0,0)>>>24);
        assertNotEquals(0,image.getRGB(12,4)>>>24);
    }
}
