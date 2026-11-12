package view.answer;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SkeletonTest {
    private static final int W = 40;
    private static final int H = 40;

    private static void line(boolean[] g, int x1, int y1, int x2, int y2) {
        int n = Math.max(Math.abs(x2 - x1), Math.abs(y2 - y1));
        for (int i = 0; i <= n; i++) {
            g[(y1 + (y2 - y1) * i / n) * W + x1 + (x2 - x1) * i / n] = true;
        }
    }

    @Test
    void shortTwigOffAForkIsCut() {
        boolean[] g = new boolean[W * H];
        line(g, 5, 20, 35, 20);   // long bar
        line(g, 20, 21, 20, 23);  // 3px twig under it
        boolean[] out = Skeleton.prune(g, W, H, 5);
        assertFalse(out[23 * W + 20]);
        assertFalse(out[21 * W + 20]);
        assertTrue(out[20 * W + 20], "fork stays");
        assertTrue(out[20 * W + 5] && out[20 * W + 35], "bar ends stay");
    }

    @Test
    void longArmStays() {
        boolean[] g = new boolean[W * H];
        line(g, 5, 20, 35, 20);
        line(g, 20, 21, 20, 35);  // real arm, way past the limit
        boolean[] out = Skeleton.prune(g, W, H, 5);
        assertTrue(out[35 * W + 20]);
    }

    @Test
    void shortStrokeOnItsOwnStays() {
        boolean[] g = new boolean[W * H];
        line(g, 10, 10, 13, 10);  // like a dot or tiny dash
        boolean[] out = Skeleton.prune(g, W, H, 5);
        assertTrue(out[10 * W + 10] && out[10 * W + 13]);
    }
}
