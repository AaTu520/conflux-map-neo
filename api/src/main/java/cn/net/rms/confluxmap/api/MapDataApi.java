package cn.net.rms.confluxmap.api;

import java.util.Optional;
import java.util.OptionalInt;
import java.util.concurrent.CompletableFuture;

/**
 * Read-only queries against the current map session. All methods are safe from any
 * thread and limit themselves to the session the player is currently in: a dimension
 * argument that does not match the active dimension yields empty/false results, and
 * every query goes empty once the session ends. Historical sessions are not readable
 * through this interface.
 */
@ApiStatus.Experimental
public interface MapDataApi {
    /** Resource id of the current dimension, e.g. {@code "minecraft:the_nether"}. */
    Optional<String> currentDimension();

    /** The mod's world identity for the current session (its per-world storage key). */
    Optional<String> currentWorldId();

    /** The mod's server identity for the current session (its per-server storage key). */
    Optional<String> currentServerId();

    /** Whether the mod holds real captured map data for this chunk of the given layer. */
    boolean hasExploredChunk(String dimensionId, ApiLayer layer, int chunkX, int chunkZ);

    /**
     * Highest sampled surface block Y at one column of the captured map. Empty when the
     * column is unknown or an authoritative void column.
     */
    OptionalInt surfaceYAt(String dimensionId, ApiLayer layer, int blockX, int blockZ);

    /**
     * Biome resource id at one column of the captured map (e.g. {@code "minecraft:plains"}),
     * sampled at the layer's recorded surface. Empty when the column is unknown.
     */
    Optional<String> biomeAt(String dimensionId, ApiLayer layer, int blockX, int blockZ);

    /**
     * Predicted biome at one position of the seed-prediction underlay, as a cubiomes
     * biome id. Only positions whose tile the fullscreen map has composed return a
     * value; open the map over the area first. Empty otherwise.
     */
    OptionalInt predictedBiomeIdAt(String dimensionId, int lod, int blockX, int blockZ);

    /** Predicted surface Y at one position of the seed-prediction underlay; same visibility rule. */
    OptionalInt predictedSurfaceYAt(String dimensionId, int lod, int blockX, int blockZ);

    /**
     * Fully composed ARGB pixels of one 256x256 tile of the current session, row-major
     * with {@code [0]} at the tile's north-west corner. Composes on a worker thread;
     * the future fails when the session changes before completion.
     *
     * @param lod 0 renders the raw 256-block tile; each step halves the block span
     */
    CompletableFuture<int[]> snapshotTile(ApiLayer layer, int lod, int tileX, int tileZ);

    /** Which vertical view of the world a query addresses. */
    enum ApiLayer {
        SURFACE,
        /** The cave layer's current automatic slice. */
        CAVE,
        NETHER,
        NETHER_CEILING,
        END
    }
}
