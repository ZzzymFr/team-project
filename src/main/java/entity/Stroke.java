package entity;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** One continuous pen stroke: the points recorded from pen-down to pen-up. */
public class Stroke {
    private final List<InkPoint> points = new ArrayList<>();
    private final int color;

    /** A stroke in black ink. */
    public Stroke() {
        this(0x000000);
    }

    /** A stroke in the given ink colour, as 0xRRGGBB. */
    public Stroke(int color) {
        this.color = color;
    }

    /** The ink colour as 0xRRGGBB. */
    public int getColor() {
        return color;
    }

    public void addPoint(InkPoint point) {
        points.add(point);
    }

    public List<InkPoint> getPoints() {
        return Collections.unmodifiableList(points);
    }

    public boolean isEmpty() {
        return points.isEmpty();
    }
}
