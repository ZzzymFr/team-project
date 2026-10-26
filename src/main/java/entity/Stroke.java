package entity;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** One continuous pen stroke: the points recorded from pen-down to pen-up. */
public class Stroke {
    private final List<InkPoint> points = new ArrayList<>();

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
