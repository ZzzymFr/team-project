package entity;

/** One sampled point of a pen stroke, in canvas pixels, with the time it was recorded. */
public final class InkPoint {
    private final int x;
    private final int y;
    private final long timeMillis;

    public InkPoint(int x, int y, long timeMillis) {
        this.x = x;
        this.y = y;
        this.timeMillis = timeMillis;
    }

    public int getX() {
        return x;
    }

    public int getY() {
        return y;
    }

    public long getTimeMillis() {
        return timeMillis;
    }
}
