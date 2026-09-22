package cn.net.rms.confluxmap.core.measure;

import cn.net.rms.confluxmap.core.annotation.AnnotationPoint;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * One click-placed measurement path in horizontal X/Z world coordinates. Client-side
 * scratch state only - never persisted, and distances intentionally ignore Y so a
 * map-plane measurement stays meaningful regardless of terrain relief.
 */
public final class MeasurePath {
    private final List<AnnotationPoint> points = new ArrayList<>();

    /** Appends a point unless it is identical to the current last one (double-click guard). */
    public boolean add(final AnnotationPoint point) {
        if (!points.isEmpty() && points.get(points.size() - 1).equals(point)) {
            return false;
        }
        return points.add(point);
    }

    /** Removes the most recent point; returns false when the path was already empty. */
    public boolean undo() {
        if (points.isEmpty()) {
            return false;
        }
        points.remove(points.size() - 1);
        return true;
    }

    public void clear() {
        points.clear();
    }

    public boolean isEmpty() {
        return points.isEmpty();
    }

    public int size() {
        return points.size();
    }

    public List<AnnotationPoint> points() {
        return Collections.unmodifiableList(points);
    }

    /** Length in blocks of the segment between points {@code index} and {@code index + 1}. */
    public double segmentLength(final int index) {
        if (index < 0 || index >= points.size() - 1) {
            throw new IndexOutOfBoundsException("no measure segment " + index);
        }
        return distance(points.get(index), points.get(index + 1));
    }

    public double totalLength() {
        double total = 0.0;
        for (int index = 1; index < points.size(); index++) {
            total += distance(points.get(index - 1), points.get(index));
        }
        return total;
    }

    public static double distance(final AnnotationPoint start, final AnnotationPoint end) {
        return Math.hypot(end.x() - start.x(), end.z() - start.z());
    }
}
