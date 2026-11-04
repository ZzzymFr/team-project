package pdf_editor.adapter;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class PageCoordinateMapperTest {
    @Test void originMapsToZero() { assertEquals(0, PageCoordinateMapper.screenToPage(28, 28, 1), .001); }
    @Test void normalSizeClickMapsToPage() { assertEquals(125, PageCoordinateMapper.screenToPage(153, 28, 1), .001); }
    @Test void doubleZoomClickMapsToSamePagePoint() { assertEquals(125, PageCoordinateMapper.screenToPage(278, 28, 2), .001); }
    @Test void halfZoomClickMapsToSamePagePoint() { assertEquals(125, PageCoordinateMapper.screenToPage(90.5, 28, .5), .001); }
    @Test void tripleZoomClickMapsToSamePagePoint() { assertEquals(125, PageCoordinateMapper.screenToPage(403, 28, 3), .001); }
    @Test void zoomDragIsRelativeToDocumentScale() { assertEquals(20, PageCoordinateMapper.deltaToPage(40, 2), .001); }
    @Test void reverseDragHasNegativeDelta() { assertEquals(-20, PageCoordinateMapper.deltaToPage(-10, .5), .001); }
    @Test void pointRoundTrip() {
        double x = PageCoordinateMapper.pageToScreen(123.75, 28, 1.9);
        assertEquals(123.75, PageCoordinateMapper.screenToPage(x, 28, 1.9), .001);
    }
    @Test void leftOfPageIsNegative() { assertEquals(-10, PageCoordinateMapper.screenToPage(18, 28, 1), .001); }
    @Test void rejectsZeroZoom() { assertThrows(IllegalArgumentException.class, () -> PageCoordinateMapper.screenToPage(100, 28, 0)); }
    @Test void rejectsNegativeZoom() { assertThrows(IllegalArgumentException.class, () -> PageCoordinateMapper.deltaToPage(10, -1)); }
    @Test void rejectsNaN() { assertThrows(IllegalArgumentException.class, () -> PageCoordinateMapper.pageToScreen(2, 28, Double.NaN)); }
}
