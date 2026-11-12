package pdf_editor.use_case;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class PreviewZoomModelTest {
    @Test void fitsPortraitPageWithinViewport() {
        double z = PreviewZoomModel.fit(1200, 1700, 850, 680);
        assertTrue(1200 * z <= 850);
        assertTrue(1700 * z <= 680);
    }
    @Test void fitsWidePageByWidth() {
        double z = PreviewZoomModel.fit(1800, 600, 500, 800);
        assertTrue(1800 * z <= 500);
    }
    @Test void clampedToMinimumAndMaximum() {
        assertEquals(0.1, PreviewZoomModel.clamp(0.001));
        assertEquals(3.0, PreviewZoomModel.clamp(100));
    }
    @Test void invalidSizesReject() {
        assertThrows(IllegalArgumentException.class, () -> PreviewZoomModel.fit(0, 200, 100, 100));
        assertThrows(IllegalArgumentException.class, () -> PreviewZoomModel.clamp(Double.NaN));
    }
}
