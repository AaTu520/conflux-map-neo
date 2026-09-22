package cn.net.rms.confluxmap.core.measure;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import cn.net.rms.confluxmap.core.annotation.AnnotationPoint;
import org.junit.jupiter.api.Test;

final class MeasurePathTest {
    @Test
    void emptyPathHasNoLengthAndNothingToUndo() {
        final MeasurePath path = new MeasurePath();

        assertTrue(path.isEmpty());
        assertEquals(0, path.size());
        assertEquals(0.0, path.totalLength());
        assertFalse(path.undo());
    }

    @Test
    void addAppendsAndIgnoresImmediateDuplicate() {
        final MeasurePath path = new MeasurePath();

        assertTrue(path.add(new AnnotationPoint(0.5, 0.5)));
        assertFalse(path.add(new AnnotationPoint(0.5, 0.5)));
        assertTrue(path.add(new AnnotationPoint(3.5, 0.5)));
        assertEquals(2, path.size());
    }

    @Test
    void threeFourFivePathSumsAxisAndDiagonalSegments() {
        final MeasurePath path = new MeasurePath();
        path.add(new AnnotationPoint(0.0, 0.0));
        path.add(new AnnotationPoint(3.0, 0.0));
        path.add(new AnnotationPoint(3.0, 4.0));

        assertEquals(3.0, path.segmentLength(0));
        assertEquals(4.0, path.segmentLength(1));
        assertEquals(7.0, path.totalLength());
    }

    @Test
    void diagonalSegmentIsEuclidean() {
        final MeasurePath path = new MeasurePath();
        path.add(new AnnotationPoint(1.0, 1.0));
        path.add(new AnnotationPoint(2.0, 2.0));

        assertEquals(Math.sqrt(2.0), path.segmentLength(0), 1e-12);
        assertEquals(Math.sqrt(2.0), path.totalLength(), 1e-12);
    }

    @Test
    void undoRemovesNewestPointFirst() {
        final MeasurePath path = new MeasurePath();
        path.add(new AnnotationPoint(0.0, 0.0));
        path.add(new AnnotationPoint(3.0, 0.0));
        path.add(new AnnotationPoint(3.0, 4.0));

        assertTrue(path.undo());
        assertEquals(3.0, path.totalLength());
        assertTrue(path.undo());
        assertEquals(0.0, path.totalLength());
        assertTrue(path.undo());
        assertTrue(path.isEmpty());
        assertFalse(path.undo());
    }

    @Test
    void segmentLengthRejectsIndexWithoutFollowingPoint() {
        final MeasurePath path = new MeasurePath();
        path.add(new AnnotationPoint(0.0, 0.0));

        assertThrows(IndexOutOfBoundsException.class, () -> path.segmentLength(0));
    }
}
