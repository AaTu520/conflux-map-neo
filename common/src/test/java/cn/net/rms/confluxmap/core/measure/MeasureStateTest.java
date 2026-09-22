package cn.net.rms.confluxmap.core.measure;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import cn.net.rms.confluxmap.core.annotation.AnnotationPoint;
import cn.net.rms.confluxmap.core.model.DimensionId;
import cn.net.rms.confluxmap.core.model.WorldIdentity;
import org.junit.jupiter.api.Test;

final class MeasureStateTest {
    @Test
    void pathsAreIsolatedPerWorldAndDimension() {
        final MeasureState state = new MeasureState();
        final WorldIdentity first = WorldIdentity.singleplayer("first");
        final WorldIdentity second = WorldIdentity.singleplayer("second");

        state.path(first, DimensionId.OVERWORLD).add(new AnnotationPoint(0.0, 0.0));
        state.path(first, DimensionId.NETHER).add(new AnnotationPoint(10.0, 10.0));
        state.path(second, DimensionId.OVERWORLD).add(new AnnotationPoint(20.0, 20.0));

        assertEquals(1, state.path(first, DimensionId.OVERWORLD).size());
        assertEquals(1, state.path(first, DimensionId.NETHER).size());
        assertEquals(1, state.path(second, DimensionId.OVERWORLD).size());
    }

    @Test
    void sessionChangeDropsAllScratchPaths() {
        final MeasureState state = new MeasureState();
        final WorldIdentity world = WorldIdentity.singleplayer("world");
        state.path(world, DimensionId.OVERWORLD).add(new AnnotationPoint(0.0, 0.0));

        state.onSessionChanged(null);

        assertTrue(state.path(world, DimensionId.OVERWORLD).isEmpty());
    }
}
