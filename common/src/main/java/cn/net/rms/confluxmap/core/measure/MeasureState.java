package cn.net.rms.confluxmap.core.measure;

import cn.net.rms.confluxmap.core.model.DimensionId;
import cn.net.rms.confluxmap.core.model.WorldIdentity;
import cn.net.rms.confluxmap.core.task.SessionGuard;
import java.util.HashMap;
import java.util.Map;

/**
 * Holds one {@link MeasurePath} per (world, dimension) so measurements survive closing
 * and reopening the map, stay separate per dimension, and never leak across worlds.
 * Session-scratch only: a live session change drops everything instead of migrating it.
 */
public final class MeasureState {
    public record Key(WorldIdentity world, DimensionId dimension) {
    }

    private final Map<Key, MeasurePath> paths = new HashMap<>();

    public MeasurePath path(final WorldIdentity world, final DimensionId dimension) {
        return paths.computeIfAbsent(new Key(world, dimension), ignored -> new MeasurePath());
    }

    public void onSessionChanged(final SessionGuard.Session ignoredSession) {
        paths.clear();
    }
}
