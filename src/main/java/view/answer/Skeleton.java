package view.answer;

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
