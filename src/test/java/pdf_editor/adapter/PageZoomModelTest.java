package pdf_editor.adapter;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class PageZoomModelTest {
    @Test void startsAtActualSize() { assertEquals(1.0, new PageZoomModel().zoom()); }
    @Test void supportsHalfSize() { assertEquals(.5, new PageZoomModel().set(.5)); }
    @Test void supportsDoubleSize() { assertEquals(2.0, new PageZoomModel().set(2.0)); }
    @Test void clampsTinyZoom() { assertEquals(PageZoomModel.MIN_ZOOM, new PageZoomModel().set(.02)); }
    @Test void clampsHugeZoom() { assertEquals(PageZoomModel.MAX_ZOOM, new PageZoomModel().set(100)); }
    @Test void rejectsNaN() { assertThrows(IllegalArgumentException.class, () -> new PageZoomModel().set(Double.NaN)); }
    @Test void rejectsInfinity() { assertThrows(IllegalArgumentException.class, () -> new PageZoomModel().set(Double.POSITIVE_INFINITY)); }
    @Test void wheelUpZoomsIn() { PageZoomModel z = new PageZoomModel(); assertTrue(z.adjustWheel(-2) > 1); }
    @Test void wheelDownZoomsOut() { PageZoomModel z = new PageZoomModel(); assertTrue(z.adjustWheel(2) < 1); }
    @Test void zeroWheelLeavesSizeUntouched() { PageZoomModel z = new PageZoomModel(); assertEquals(1, z.adjustWheel(0)); }
    @Test void fitUsesSmallerDimensionRatio() { assertEquals(.5, new PageZoomModel().fit(500, 450, 1000, 600)); }
    @Test void fitUsesHeightWhenHeightLimits() { assertEquals(.5, new PageZoomModel().fit(900, 300, 1000, 600)); }
    @Test void fitRejectsZeroViewport() { assertThrows(IllegalArgumentException.class, () -> new PageZoomModel().fit(0, 100, 400, 500)); }
    @Test void fitRejectsNegativePageDimension() { assertThrows(IllegalArgumentException.class, () -> new PageZoomModel().fit(100, 100, -1, 100)); }
    @Test void oneModelMaintainsZoomAcrossPages() { PageZoomModel z = new PageZoomModel(); z.set(1.75); assertEquals(1.75, z.zoom()); }
}
