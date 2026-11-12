package view.answer;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

// zhang-suen thinning: filled pixels -> 1px wide centre line
final class Skeleton {
    private Skeleton() {
    }

    // row-major w*h grid, true = ink; returns a new grid
    static boolean[] thin(boolean[] grid, int w, int h) {
        boolean[] on = grid.clone();
        boolean[] kill = new boolean[on.length];
        boolean changed = true;
        while (changed) {
            changed = false;
            for (int pass = 0; pass < 2; pass++) {
                boolean any = false;
                for (int y = 1; y < h - 1; y++) {
                    for (int x = 1; x < w - 1; x++) {
                        if (on[y * w + x] && removable(on, w, x, y, pass == 0)) {
                            kill[y * w + x] = true;
                            any = true;
                        }
                    }
                }
                if (any) {
                    for (int i = 0; i < on.length; i++) {
                        if (kill[i]) {
                            on[i] = false;
                            kill[i] = false;
                        }
                    }
                    changed = true;
                }
            }
        }
        return on;
    }

    // cut short twigs hanging off a fork, thinning leaves them at sharp corners (middle of a "3")
    static boolean[] prune(boolean[] grid, int w, int h, int maxLen) {
        boolean[] on = grid.clone();
        for (int y = 1; y < h - 1; y++) {
            for (int x = 1; x < w - 1; x++) {
                if (on[y * w + x] && around(on, w, x, y)[0] == 1) {
                    List<Integer> twig = twig(on, w, h, y * w + x, maxLen);
                    for (int i : twig) {
                        on[i] = false;
                    }
                }
            }
        }
        return on;
    }

    // pixels from an end up to a fork, empty if no fork within maxLen
    private static List<Integer> twig(boolean[] on, int w, int h, int start, int maxLen) {
        List<Integer> path = new ArrayList<>();
        Set<Integer> seen = new HashSet<>();
        int prev = -1;
        int cur = start;
        while (path.size() <= maxLen) {
            int x = cur % w;
            int y = cur / w;
            if (x < 1 || y < 1 || x >= w - 1 || y >= h - 1) {
                return List.of();
            }
            if (cur != start && around(on, w, x, y)[1] >= 3) {
                return path;  // hit the fork, keep it
            }
            path.add(cur);
            seen.add(cur);
            int next = step(on, w, cur, prev, seen);
            if (next < 0) {
                return List.of();  // a short stroke on its own, not a twig
            }
            prev = cur;
            cur = next;
        }
        return List.of();
    }

    // next pixel along, straight before diagonal, skips ones that just hug the last step
    private static int step(boolean[] on, int w, int cur, int prev, Set<Integer> seen) {
        int fallback = -1;
        for (int[] d : DIRS) {
            int n = cur + d[1] * w + d[0];
            if (!on[n] || seen.contains(n)) {
                continue;
            }
            if (prev >= 0 && touching(n, prev, w)) {
                fallback = fallback < 0 ? n : fallback;
                continue;
            }
            return n;
        }
        return fallback;
    }

    private static boolean touching(int a, int b, int w) {
        return Math.abs(a % w - b % w) <= 1 && Math.abs(a / w - b / w) <= 1;
    }

    // {neighbours on, off->on flips going round}
    private static int[] around(boolean[] on, int w, int x, int y) {
        int count = 0;
        int flips = 0;
        for (int i = 0; i < 8; i++) {
            int[] a = RING[i];
            int[] b = RING[(i + 1) % 8];
            boolean pa = on[(y + a[1]) * w + x + a[0]];
            boolean pb = on[(y + b[1]) * w + x + b[0]];
            count += pa ? 1 : 0;
            flips += !pa && pb ? 1 : 0;
        }
        return new int[] {count, flips};
    }

    // clockwise from north
    private static final int[][] RING = {{0, -1}, {1, -1}, {1, 0}, {1, 1}, {0, 1}, {-1, 1}, {-1, 0}, {-1, -1}};
    private static final int[][] DIRS = {{0, -1}, {1, 0}, {0, 1}, {-1, 0}, {1, -1}, {1, 1}, {-1, 1}, {-1, -1}};

    private static boolean removable(boolean[] on, int w, int x, int y, boolean first) {
        // p2..p9 clockwise from north
        boolean[] p = {
            on[(y - 1) * w + x], on[(y - 1) * w + x + 1], on[y * w + x + 1], on[(y + 1) * w + x + 1],
            on[(y + 1) * w + x], on[(y + 1) * w + x - 1], on[y * w + x - 1], on[(y - 1) * w + x - 1],
        };
        int count = 0;
        int flips = 0;
        for (int i = 0; i < 8; i++) {
            count += p[i] ? 1 : 0;
            flips += !p[i] && p[(i + 1) % 8] ? 1 : 0;
        }
        if (count < 2 || count > 6 || flips != 1) {
            return false;
        }
        boolean n = p[0];
        boolean e = p[2];
        boolean s = p[4];
        boolean wst = p[6];
        return first ? !(n && e && s) && !(e && s && wst) : !(n && e && wst) && !(n && s && wst);
    }
}
