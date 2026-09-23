package cn.net.rms.confluxmap.api;

import java.util.Optional;

/**
 * Root of the Conflux Map client API. The interface is deliberately Minecraft-free: one
 * published artifact serves every supported Minecraft version, positions are plain
 * doubles/ints, dimensions are resource-id strings such as {@code "minecraft:the_nether"},
 * and icons are item-id strings such as {@code "minecraft:red_banner"}.
 *
 * <p>Threading contract:
 * <ul>
 *   <li>Read-like methods return immutable snapshots and are safe from any thread.</li>
 *   <li>Write-like methods return {@link java.util.concurrent.CompletableFuture} and marshal
 *       themselves onto the client main thread when called from elsewhere.</li>
 *   <li>Events fire on the client main thread.</li>
 * </ul>
 *
 * <p>Obtain the instance either through the {@code "confluxmap"} entrypoint (declare
 * {@code "entrypoints": {"confluxmap": ["com.example.YourPlugin"]}} in your
 * {@code fabric.mod.json} and implement {@link ConfluxMapPlugin}) or lazily via
 * {@link #instance()}, which is empty until the mod's client services have started.
 */
@ApiStatus.Experimental
public interface ConfluxMapApi {
    /** The mod version providing this API, identical to the mod's own version string. */
    String version();

    /** Lifecycle events; see {@link ConfluxMapEvents}. */
    ConfluxMapEvents events();

    /** Waypoint read/write access and the waypoints-changed event surface. */
    WaypointApi waypoints();

    /** Read-only queries against the current map session's captured and predicted data. */
    MapDataApi mapData();

    /** Registration of third-party map markers. */
    MarkerApi markers();

    /** Screen, layer, and teleport actions. */
    ActionApi actions();

    /** The installed instance, or empty before client init and after client shutdown. */
    static Optional<ConfluxMapApi> instance() {
        return Optional.ofNullable(Holder.INSTANCE);
    }

    /** @ApiStatus.Internal Called once by Conflux Map when its client services are ready. */
    @ApiStatus.Internal
    static void install(final ConfluxMapApi api) {
        Holder.INSTANCE = api;
    }

    /** @ApiStatus.Internal Called by Conflux Map on client shutdown. */
    @ApiStatus.Internal
    static void uninstall() {
        Holder.INSTANCE = null;
    }

    final class Holder {
        private static volatile ConfluxMapApi INSTANCE;

        private Holder() {
        }
    }
}
