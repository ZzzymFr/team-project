package view.glass;

import org.junit.jupiter.api.Test;

import javax.swing.SwingUtilities;
import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GlassBarTest {
    private static final Font FONT = new Font(Font.SANS_SERIF, Font.PLAIN, 14);

    /** A plain backdrop with a dark bar across it, counting how often it is painted. */
    private static final class Picture implements GlassBackdrop {
        long version;
        int paints;
        boolean dark;

        @Override
        public void paintBackdrop(Graphics2D g) {
            paints++;
            g.setColor(Glass.CANVAS);
            g.fillRect(0, 0, 400, 200);
            if (dark) {
                g.setColor(Color.BLACK);
                g.fillRect(0, 0, 400, 200);
            }
        }

        @Override
        public long backdropVersion() {
            return version;
        }
    }

    private static BufferedImage paint(GlassBar bar) {
        BufferedImage image = new BufferedImage(bar.getWidth(), bar.getHeight(), BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = image.createGraphics();
        bar.paint(g);
        g.dispose();
        return image;
    }

    @Test
    void aSegmentIsSelectedWhenItsActionAgrees() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            GlassBar bar = new GlassBar(new Picture(), FONT);
            List<String> ran = new ArrayList<>();
            GlassBar.Item a = bar.segment("A", null, 0, () -> ran.add("a"));
            GlassBar.Item b = bar.segment("B", null, 0, () -> {
                ran.add("b");
                return false;  // e.g. a colour dialog that was cancelled
            });
            bar.finish();
            bar.select(a);

            bar.activate(b);
            assertEquals(List.of("b"), ran);
            assertTrue(bar.isSelected(a), "the action said no, so the selection stays");
        });
    }

    @Test
    void aDisabledActionDoesNotRun() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            GlassBar bar = new GlassBar(new Picture(), FONT);
            List<String> ran = new ArrayList<>();
            GlassBar.Item undo = bar.action("Undo", GlassIcons::undo, () -> ran.add("undo"), () -> false);
            bar.finish();
            bar.activate(undo);
            assertTrue(ran.isEmpty());
            assertFalse(bar.isEnabled(undo));
        });
    }

    @Test
    void aHiddenBarTakesNoMouseAndPaintsNothing() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            GlassBar bar = new GlassBar(new Picture(), FONT);
            bar.segment("A", null, 0, () -> true);
            bar.finish();
            int cx = bar.getWidth() / 2;
            int cy = bar.getHeight() / 2;
            assertTrue(bar.contains(cx, cy));

            bar.setShownNow(false);
            assertFalse(bar.isShown());
            assertFalse(bar.contains(cx, cy));
            assertEquals(0, paint(bar).getRGB(cx, cy));
        });
    }

    @Test
    void theBackdropIsOnlyReadAgainWhenItsVersionChanges() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            Picture picture = new Picture();
            GlassBar bar = new GlassBar(picture, FONT);
            bar.segment("A", null, 0, () -> true);
            bar.finish();

            paint(bar);
            int afterFirst = picture.paints;
            paint(bar);
            assertEquals(afterFirst, picture.paints, "nothing changed: the cached glass is reused");

            picture.version++;
            paint(bar);
            assertEquals(afterFirst + 1, picture.paints);
        });
    }

    @Test
    void theBackdropShowsThroughTheGlass() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            Picture picture = new Picture();
            GlassBar bar = new GlassBar(picture, FONT);
            bar.action("A", null, () -> { }, () -> true);
            bar.finish();
            int x = GlassBar.MARGIN + 20;
            int y = GlassBar.MARGIN + GlassBar.CAPSULE_HEIGHT - 8;  // the lower part of the capsule, clear of the label
            int light = paint(bar).getRGB(x, y);

            picture.dark = true;
            picture.version++;
            int dark = paint(bar).getRGB(x, y);
            assertNotEquals(light, dark);
            assertTrue((dark & 0xFF) < 128, "dark ink under the glass looks dark, was " + Integer.toHexString(dark));
        });
    }

    @Test
    void refractionPullsThePictureOnlyAlongTheRim() {
        // left half white, right half black; the capsule's middle is 0..60 wide of glass
        BufferedImage src = new BufferedImage(200, 60, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = src.createGraphics();
        g.setColor(Color.WHITE);
        g.fillRect(0, 0, 100, 60);
        g.setColor(Color.BLACK);
        g.fillRect(100, 0, 100, 60);
        g.dispose();

        BufferedImage flat = GlassOptics.refract(src, 1, Glass.rounded(10, 10, 180, 40), 0f, 0, 0);
        assertEquals(0, flat.getRGB(2, 30) >>> 24, "nothing outside the capsule");
        assertEquals(Color.WHITE.getRGB(), flat.getRGB(60, 30));
        assertEquals(Color.BLACK.getRGB(), flat.getRGB(140, 30));

        // a black picture on the outside of a white one: the rim of the glass reaches out for it
        BufferedImage outside = new BufferedImage(60, 60, BufferedImage.TYPE_INT_ARGB);
        g = outside.createGraphics();
        g.setColor(Color.WHITE);
        g.fillRect(0, 0, 60, 60);
        g.setColor(Color.BLACK);
        g.fillRect(0, 0, 60, 8);
        g.dispose();
        BufferedImage lensed = GlassOptics.refract(outside, 1, Glass.rounded(0, 10, 60, 40), 1f, 0, 0);
        assertEquals(Color.BLACK.getRGB(), lensed.getRGB(30, 11), "the rim shows what is beyond it");
        assertEquals(Color.WHITE.getRGB(), lensed.getRGB(30, 30), "the middle is left alone");
    }

    @Test
    void glassHasSoftCornersNotPillEnds() {
        java.awt.geom.RoundRectangle2D bar = Glass.rounded(0, 0, 200, GlassBar.CAPSULE_HEIGHT);
        assertTrue(bar.getArcHeight() < bar.getHeight() * 0.6, "a rounded rectangle, not a capsule");
        assertTrue(bar.contains(5, 5), "the corner is only slightly rounded");
        assertTrue(!bar.contains(1, 1), "but it is rounded");
        java.awt.geom.RoundRectangle2D tiny = Glass.rounded(0, 0, 40, 6);
        assertEquals(6, tiny.getArcHeight(), 1e-9, "very small glass is never rounder than half its height");
    }

    @Test
    void edgeDistanceFollowsTheRoundedCorner() {
        double[] normal = new double[2];
        // 100 x 40 centred on the origin, corners of radius 10
        assertEquals(-20, GlassOptics.edgeDistance(0, 0, 50, 20, 10, normal), 1e-9);
        assertEquals(0, normal[0], 1e-9);
        assertEquals(1, normal[1], 1e-9);
        assertEquals(-2, GlassOptics.edgeDistance(-48, 0, 50, 20, 10, normal), 1e-9);
        assertEquals(-1, normal[0], 1e-9);
        double corner = GlassOptics.edgeDistance(50, 20, 50, 20, 10, normal);
        assertEquals(Math.sqrt(200) - 10, corner, 1e-9, "the square corner lies outside the arc");
        assertEquals(Math.sqrt(0.5), normal[0], 1e-9);
    }
}
