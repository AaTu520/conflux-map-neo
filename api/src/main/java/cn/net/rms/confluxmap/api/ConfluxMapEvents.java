package cn.net.rms.confluxmap.api;

import java.util.List;

/**
 * Mod lifecycle events. Every event fires on the client main thread; payloads are
 * immutable snapshots taken at notification time.
 */
@ApiStatus.Experimental
public interface ConfluxMapEvents {
    /** Fired when the player joins a world (singleplayer or server). */
    Event<SessionEvent> sessionStarted();

    /** Fired when the player leaves the world; also fires before a switch to another world. */
    Event<SessionEvent> sessionEnded();

    /** Fired when the player changes dimension within the same world. */
    Event<SessionEvent> dimensionChanged();

    /** Fired when the fullscreen map screen opens. */
    Event<MapScreenEvent> fullscreenMapOpened();

    /** Fired when the fullscreen map screen closes, whatever close path was used. */
    Event<MapScreenEvent> fullscreenMapClosed();

    /** Fired after any waypoint mutation, carrying the full new waypoint list. */
    Event<WaypointChangeEvent> waypointsChanged();

    /** Where the player currently is. Ids mirror the mod's own storage identities. */
    record SessionEvent(String serverId, String worldId, String dimensionId) {
    }

    /** The dimension the fullscreen map was showing. */
    record MapScreenEvent(String dimensionId) {
    }

    /** The complete waypoint list after one mutation (the mod does not report the delta). */
    record WaypointChangeEvent(String worldId, List<WaypointApi.ApiWaypoint> waypoints) {
        public WaypointChangeEvent {
            waypoints = List.copyOf(waypoints);
        }
    }
}
