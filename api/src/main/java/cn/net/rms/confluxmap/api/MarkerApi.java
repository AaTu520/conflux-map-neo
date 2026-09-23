package cn.net.rms.confluxmap.api;

import java.util.Collection;
import java.util.List;

/**
 * Registration of third-party map markers. A provider is polled on the client main
 * thread roughly every half second; whatever it returns replaces its previous markers
 * on the minimap and the fullscreen map. Providers must answer quickly and must not
 * throw - a failing provider is skipped for that sampling round and logged by the mod.
 */
@ApiStatus.Experimental
public interface MarkerApi {
    /**
     * Registers a provider under {@code ownerId} (your mod id). The returned handle
     * unregisters it; markers vanish after at most one more sampling round.
     */
    EventRegistration registerProvider(String ownerId, CustomMarkerProvider provider);

    /** Current merged snapshot of every registered provider; safe from any thread. */
    List<ApiMarker> markers();

    /** Source of data-driven markers. */
    @FunctionalInterface
    interface CustomMarkerProvider {
        Collection<ApiMarker> markers();
    }

    /**
     * One marker on the map surfaces. Rendered with the mod's waypoint marker visuals:
     * a plate in {@code colorArgb}, the item icon of {@code iconItemId} when set (else
     * the first character of {@code label}), drawn on top of waypoints.
     *
     * @param id stable identifier of the marker within its provider
     * @param priority markers with higher priority draw above lower ones
     */
    record ApiMarker(
        String id,
        String dimensionId,
        double x,
        double y,
        double z,
        String label,
        int colorArgb,
        String iconItemId,
        boolean showOnMinimap,
        boolean showOnFullscreen,
        int priority
    ) {
    }
}
