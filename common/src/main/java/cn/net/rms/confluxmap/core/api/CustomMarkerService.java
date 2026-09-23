package cn.net.rms.confluxmap.core.api;

import cn.net.rms.confluxmap.api.EventRegistration;
import cn.net.rms.confluxmap.api.MarkerApi;
import cn.net.rms.confluxmap.core.model.DimensionId;
import cn.net.rms.confluxmap.core.waypoint.Waypoint;
import cn.net.rms.confluxmap.core.waypoint.WaypointRenderEntry;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicBoolean;
import org.apache.logging.log4j.Logger;

/**
 * Owns the third-party marker providers and their latest sampled snapshot. Providers are
 * registered from any thread (the plugin bootstrap), sampled on the client main thread
 * by a periodic {@link #tick()}, and read from the render thread through the volatile
 * {@link #markers()} snapshot - the same copy-on-read shape as {@code EntityRadarScanner}.
 *
 * <p>For rendering, each {@code ApiMarker} converts into a synthetic
 * {@link WaypointRenderEntry} with a name-based UUID (prefix {@code custom:}), so both map
 * surfaces can reuse the waypoint marker pipeline wholesale without touching the real
 * waypoint catalog.
 */
public final class CustomMarkerService implements MarkerApi {
    private static final int SAMPLE_INTERVAL_TICKS = 10;
    private static final String ID_PREFIX = "custom:";

    private record Provider(String ownerId, CustomMarkerProvider provider) {
    }

    private final Logger logger;
    private final List<Provider> providers = new CopyOnWriteArrayList<>();
    private final Map<String, AtomicBoolean> pendingResample = new ConcurrentHashMap<>();
    private volatile List<ApiMarker> snapshot = List.of();
    private int tickCounter;

    public CustomMarkerService(final Logger logger) {
        this.logger = logger;
    }

    @Override
    public EventRegistration registerProvider(final String ownerId, final CustomMarkerProvider provider) {
        if (ownerId == null || ownerId.isBlank() || provider == null) {
            throw new IllegalArgumentException("ownerId and provider are required");
        }
        final Provider entry = new Provider(ownerId, provider);
        providers.add(entry);
        pendingResample.put(ownerId, new AtomicBoolean(true));
        final class Removal implements Runnable {
            @Override
            public void run() {
                providers.remove(entry);
                pendingResample.remove(ownerId);
                // Leave the stale markers in place; the next tick drops them so removal
                // takes effect even while the client is between ticks.
                resampleNow();
            }
        }
        return EventRegistration.of(new Removal());
    }

    @Override
    public List<ApiMarker> markers() {
        return snapshot;
    }

    /** Client main thread; samples every {@link #SAMPLE_INTERVAL_TICKS} ticks. */
    public void tick() {
        if (providers.isEmpty()) {
            if (!snapshot.isEmpty()) {
                snapshot = List.of();
            }
            return;
        }
        if (++tickCounter < SAMPLE_INTERVAL_TICKS && !pendingResample()) {
            return;
        }
        tickCounter = 0;
        resampleNow();
    }

    private boolean pendingResample() {
        for (final AtomicBoolean flag : pendingResample.values()) {
            if (flag.getAndSet(false)) {
                return true;
            }
        }
        return false;
    }

    private void resampleNow() {
        final List<ApiMarker> merged = new ArrayList<>();
        for (final Provider entry : providers) {
            try {
                final Collection<ApiMarker> provided = entry.provider().markers();
                if (provided != null) {
                    for (final ApiMarker marker : provided) {
                        if (marker != null && marker.id() != null && !marker.id().isBlank()) {
                            merged.add(marker);
                        }
                    }
                }
            } catch (final Throwable t) {
                logger.error("Conflux Map marker provider {} threw while sampling", entry.ownerId(), t);
            }
        }
        merged.sort(Comparator.comparingInt(ApiMarker::priority));
        snapshot = List.copyOf(merged);
    }

    /**
     * Render-thread conversion into the waypoint marker pipeline, filtered to one
     * surface and dimension. Priority order is preserved (higher priority last, on top).
     */
    public List<WaypointRenderEntry> renderEntries(final DimensionId dimension, final boolean minimap) {
        final List<WaypointRenderEntry> out = new ArrayList<>();
        for (final ApiMarker marker : snapshot) {
            if (minimap ? !marker.showOnMinimap() : !marker.showOnFullscreen()) {
                continue;
            }
            if (!dimension.toString().equals(marker.dimensionId())) {
                continue;
            }
            out.add(new WaypointRenderEntry(
                stableId(marker),
                marker.label() == null || marker.label().isBlank() ? "?" : marker.label(),
                dimension,
                marker.x(),
                marker.y(),
                marker.z(),
                marker.colorArgb(),
                marker.iconItemId() == null ? "" : marker.iconItemId(),
                "",
                Waypoint.Type.NORMAL,
                WaypointRenderEntry.Source.LOCAL,
                false
            ));
        }
        return out;
    }

    private static UUID stableId(final ApiMarker marker) {
        return UUID.nameUUIDFromBytes(
            (ID_PREFIX + marker.dimensionId() + ":" + marker.id()).getBytes(StandardCharsets.UTF_8)
        );
    }
}
