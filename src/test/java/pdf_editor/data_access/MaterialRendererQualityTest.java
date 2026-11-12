package pdf_editor.data_access;

import org.junit.jupiter.api.Test;
import pdf_editor.MathLibrary;
import java.awt.Color;
import java.awt.image.BufferedImage;
import static org.junit.jupiter.api.Assertions.*;

/** Rendering regressions at different target sizes; no dependence on UI monitor DPI. */
class MaterialRendererQualityTest {
    private static int darkPixels(BufferedImage image) {
        int count = 0;
        for (int y = 0; y < image.getHeight(); y++)
            for (int x = 0; x < image.getWidth(); x++) {
                int rgb = image.getRGB(x, y);
                if (((rgb >>> 24) & 255) > 128 && (rgb & 255) < 160 &&
                        ((rgb >>> 8) & 255) < 160 && ((rgb >>> 16) & 255) < 160) count++;
            }
        return count;
    }
    @Test void formulaReTypesetsAtDestinationResolution() {
        var formula = new MathLibrary().addFormula("\\frac{x^2+1}{y+2}");
        BufferedImage normal = MaterialRenderer.render(formula, 300, 100);
        BufferedImage zoomed = MaterialRenderer.render(formula, 900, 300);
        assertEquals(900, zoomed.getWidth());
        assertEquals(300, zoomed.getHeight());
        assertTrue(darkPixels(normal) > 0);
        assertTrue(darkPixels(zoomed) > darkPixels(normal) * 4,
                "Increasing raster resolution must typeset more pixels, not upscale a fixed bitmap");
    }
    @Test void resultAndFormulaRenderTogetherAtHighResolution() {
        var calculation = new MathLibrary().addCalculation("x+1=", "=2");
        assertTrue(darkPixels(MaterialRenderer.render(calculation, 1000, 320)) > 100);
    }
    @Test void formulaWithoutResultStillRenders() {
        assertTrue(darkPixels(MaterialRenderer.render(new MathLibrary().addFormula("x^2"), 500, 140)) > 0);
    }
    @Test void malformedLatexFallsBackToReadableText() {
        assertTrue(darkPixels(MaterialRenderer.render(new MathLibrary().addFormula("\\invalidCommandZ"), 400, 110)) > 0);
    }
    @Test void tinyTargetIsClampedWithoutException() {
        var img = MaterialRenderer.render(new MathLibrary().addFormula("1"), 0, -10);
        assertEquals(8, img.getWidth());
        assertEquals(8, img.getHeight());
    }
    @Test void graphKeepsAspectRatioAndDoesNotIntroduceNewContentInMargins() {
        var source = new BufferedImage(80, 40, BufferedImage.TYPE_INT_RGB);
        for (int y=0; y<40; y++) for (int x=0; x<80; x++) source.setRGB(x,y,Color.RED.getRGB());
        var graph = MathLibrary.region("plot", source);
        BufferedImage result = MaterialRenderer.render(graph, 400, 400);
        assertEquals(Color.RED.getRGB(), result.getRGB(200, 200));
        assertEquals(0, result.getRGB(200, 10) >>> 24, "Letterbox should remain transparent");
    }
    @Test void imageBytesAreImmutableAcrossRendering() {
        var source = new BufferedImage(40, 40, BufferedImage.TYPE_INT_RGB);
        var graph = MathLibrary.region("plot", source);
        byte[] before = graph.imagePng();
        MaterialRenderer.render(graph, 400, 300);
        MaterialRenderer.render(graph, 80, 60);
        assertArrayEquals(before, graph.imagePng());
    }
}
