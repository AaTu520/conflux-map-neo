package cn.net.rms.confluxmap.core.api;

import cn.net.rms.confluxmap.api.MapDataApi;
import cn.net.rms.confluxmap.core.color.DaylightModel;
import cn.net.rms.confluxmap.core.model.DimensionId;
import cn.net.rms.confluxmap.core.model.TileKey;
import cn.net.rms.confluxmap.core.predict.PredictionTileService;
import cn.net.rms.confluxmap.core.store.MapWorld;
import cn.net.rms.confluxmap.core.store.MapWorldService;
import cn.net.rms.confluxmap.core.task.SessionGuard;
import cn.net.rms.confluxmap.core.tile.TileService;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.concurrent.CompletableFuture;

/** {@link MapDataApi} over the live map stores; see the interface contract. */
final class ApiMapData implements MapDataApi {
    private final SessionGuard guard;
    private final MapWorldService mapWorlds;
    private final TileService tiles;
    private final PredictionTileService prediction;
    private final DaylightModel daylight;

    ApiMapData(
        final SessionGuard guard,
        final MapWorldService mapWorlds,
        final TileService tiles,
        final PredictionTileService prediction,
        final DaylightModel daylight
    ) {
        this.guard = guard;
        this.mapWorlds = mapWorlds;
        this.tiles = tiles;
        this.prediction = prediction;
        this.daylight = daylight;
    }

    @Override
    public Optional<String> currentDimension() {
        final SessionGuard.Session session = guard.current();
        return session.active() ? Optional.of(session.dimension().toString()) : Optional.empty();
    }

    @Override
    public Optional<String> currentWorldId() {
        final SessionGuard.Session session = guard.current();
        return session.active() ? Optional.of(session.world().worldId()) : Optional.empty();
    }

    @Override
    public Optional<String> currentServerId() {
        final SessionGuard.Session session = guard.current();
        return session.active() ? Optional.of(session.world().serverId()) : Optional.empty();
    }

    @Override
    public boolean hasExploredChunk(
        final String dimensionId,
        final ApiLayer layer,
        final int chunkX,
        final int chunkZ
    ) {
        final MapWorld world = dimensionWorld(dimensionId);
        return world != null && world.store(ApiLayers.toCore(layer)).hasRealChunk(chunkX, chunkZ);
    }

    @Override
    public OptionalInt surfaceYAt(
        final String dimensionId,
        final ApiLayer layer,
        final int blockX,
        final int blockZ
    ) {
        final MapWorld world = dimensionWorld(dimensionId);
        return world == null
            ? OptionalInt.empty()
            : world.store(ApiLayers.toCore(layer)).surfaceAt(blockX, blockZ).surfaceY();
    }

    @Override
    public Optional<String> biomeAt(
        final String dimensionId,
        final ApiLayer layer,
        final int blockX,
        final int blockZ
    ) {
        final MapWorld world = dimensionWorld(dimensionId);
        return world == null
            ? Optional.empty()
            : world.store(ApiLayers.toCore(layer)).biomeAt(blockX, blockZ);
    }

    @Override
    public OptionalInt predictedBiomeIdAt(
        final String dimensionId,
        final int lod,
        final int blockX,
        final int blockZ
    ) {
        if (!dimensionMatches(dimensionId)) {
            return OptionalInt.empty();
        }
        return prediction.predictedBiomeAt(DimensionId.parse(dimensionId), lod, blockX, blockZ);
    }

    @Override
    public OptionalInt predictedSurfaceYAt(
        final String dimensionId,
        final int lod,
        final int blockX,
        final int blockZ
    ) {
        if (!dimensionMatches(dimensionId)) {
            return OptionalInt.empty();
        }
        return prediction.predictedSurfaceYAt(DimensionId.parse(dimensionId), lod, blockX, blockZ);
    }

    @Override
    public CompletableFuture<int[]> snapshotTile(
        final ApiLayer layer,
        final int lod,
        final int tileX,
        final int tileZ
    ) {
        final SessionGuard.Session session = guard.current();
        if (!session.active()) {
            return CompletableFuture.failedFuture(
                new IllegalStateException("No active map session")
            );
        }
        final TileKey key = new TileKey(
            session.world(),
            session.dimension(),
            ApiLayers.toCore(layer).cacheId(),
            lod,
            tileX,
            tileZ
        );
        return tiles.snapshotTile(key, true, daylight.factor());
    }

    private boolean dimensionMatches(final String dimensionId) {
        if (dimensionId == null) {
            return false;
        }
        final SessionGuard.Session session = guard.current();
        return session.active() && DimensionId.parse(dimensionId).equals(session.dimension());
    }

    private MapWorld dimensionWorld(final String dimensionId) {
        if (!dimensionMatches(dimensionId)) {
            return null;
        }
        return mapWorlds.current();
    }
}
