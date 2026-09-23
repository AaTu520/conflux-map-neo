package cn.net.rms.confluxmap.core.api;

import cn.net.rms.confluxmap.api.ActionApi;
import cn.net.rms.confluxmap.api.ConfluxMapApi;
import cn.net.rms.confluxmap.api.ConfluxMapEvents;
import cn.net.rms.confluxmap.api.Event;
import cn.net.rms.confluxmap.api.MapDataApi;
import cn.net.rms.confluxmap.api.WaypointApi;
import cn.net.rms.confluxmap.bridge.GameBridge;
import cn.net.rms.confluxmap.core.color.DaylightModel;
import cn.net.rms.confluxmap.core.model.DimensionId;
import cn.net.rms.confluxmap.core.predict.PredictionTileService;
import cn.net.rms.confluxmap.core.store.MapWorldService;
import cn.net.rms.confluxmap.core.task.SessionGuard;
import cn.net.rms.confluxmap.core.tile.TileService;
import cn.net.rms.confluxmap.core.waypoint.Waypoint;
import cn.net.rms.confluxmap.core.waypoint.WaypointService;
import cn.net.rms.confluxmap.core.waypoint.WaypointStore;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;

/**
 * Assembles the public API over the live client services. Constructed (and
 * {@link #install()}ed) by the Fabric composition root once every service is wired; the
 * session tracker drives {@link #onSessionChanged} as the last listener of its chain so
 * plugins observe a session only after the mod's own listeners have applied it.
 */
public final class ConfluxMapApiImpl implements ConfluxMapApi {
    private final String version;
    private final WaypointService waypointService;
    private final WaypointApi waypoints;
    private final MapDataApi mapData;
    private final CustomMarkerService markers;
    private final ActionApi actions;
    private final EventBus events = new EventBus();
    private SessionGuard.Session lastSeen = SessionGuard.Session.NONE;

    public ConfluxMapApiImpl(
        final String version,
        final GameBridge bridge,
        final WaypointService waypointService,
        final SessionGuard guard,
        final MapWorldService mapWorlds,
        final TileService tiles,
        final PredictionTileService prediction,
        final DaylightModel daylight,
        final CustomMarkerService markers,
        final ActionApi actions
    ) {
        this.version = version;
        this.waypointService = waypointService;
        this.waypoints = new ApiWaypoints(bridge, waypointService);
        this.mapData = new ApiMapData(guard, mapWorlds, tiles, prediction, daylight);
        this.markers = markers;
        this.actions = actions;
    }

    @Override
    public String version() {
        return version;
    }

    @Override
    public ConfluxMapEvents events() {
        return events;
    }

    @Override
    public WaypointApi waypoints() {
        return waypoints;
    }

    @Override
    public MapDataApi mapData() {
        return mapData;
    }

    @Override
    public CustomMarkerService markers() {
        return markers;
    }

    @Override
    public ActionApi actions() {
        return actions;
    }

    public void install() {
        ConfluxMapApi.install(this);
    }

    public void shutdown() {
        ConfluxMapApi.uninstall();
    }

    /** Session-tracker listener (main thread, last in the chain). */
    public void onSessionChanged(final SessionGuard.Session session) {
        final SessionGuard.Session before = lastSeen;
        lastSeen = session;
        if (before.active() && !session.active()) {
            events.fireSessionEnded(before);
        } else if (!before.active() && session.active()) {
            events.fireSessionStarted(session);
        } else if (before.active()) {
            if (!before.world().equals(session.world())) {
                events.fireSessionEnded(before);
                events.fireSessionStarted(session);
            } else if (!before.dimension().equals(session.dimension())) {
                events.dimensionChanged().fire(eventOf(session));
            }
        }
    }

    /** WaypointService change listener (main thread). */
    public void onWaypointsChanged(final List<Waypoint> waypoints) {
        final WaypointStore store = waypointService.current();
        final String worldId = store == null ? "" : store.world().worldId();
        events.waypointsChanged().fire(
            new ConfluxMapEvents.WaypointChangeEvent(worldId, ApiMappers.toApi(waypoints))
        );
    }

    /** Fullscreen map screen lifecycle hooks (render thread). */
    public void fireFullscreenMapOpened(final DimensionId dimension) {
        events.fullscreenMapOpened().fire(
            new ConfluxMapEvents.MapScreenEvent(dimension.toString())
        );
    }

    public void fireFullscreenMapClosed(final DimensionId dimension) {
        events.fullscreenMapClosed().fire(
            new ConfluxMapEvents.MapScreenEvent(dimension.toString())
        );
    }

    /** Runs {@code body} on the main thread; completes synchronously when already there. */
    public static <T> CompletableFuture<T> callOnMain(final GameBridge bridge, final Supplier<T> body) {
        if (bridge.isOnRenderThread()) {
            try {
                return CompletableFuture.completedFuture(body.get());
            } catch (final Throwable t) {
                return CompletableFuture.failedFuture(t);
            }
        }
        final CompletableFuture<T> result = new CompletableFuture<>();
        bridge.runOnRenderThread(() -> {
            try {
                result.complete(body.get());
            } catch (final Throwable t) {
                result.completeExceptionally(t);
            }
        });
        return result;
    }

    private static ConfluxMapEvents.SessionEvent eventOf(final SessionGuard.Session session) {
        return new ConfluxMapEvents.SessionEvent(
            session.world().serverId(), session.world().worldId(), session.dimension().toString()
        );
    }

    private static final class EventBus implements ConfluxMapEvents {
        private final Event<SessionEvent> sessionStarted = new Event<>();
        private final Event<SessionEvent> sessionEnded = new Event<>();
        private final Event<SessionEvent> dimensionChanged = new Event<>();
        private final Event<MapScreenEvent> fullscreenMapOpened = new Event<>();
        private final Event<MapScreenEvent> fullscreenMapClosed = new Event<>();
        private final Event<WaypointChangeEvent> waypointsChanged = new Event<>();

        void fireSessionStarted(final SessionGuard.Session session) {
            sessionStarted.fire(eventOf(session));
        }

        void fireSessionEnded(final SessionGuard.Session session) {
            sessionEnded.fire(eventOf(session));
        }

        @Override
        public Event<SessionEvent> sessionStarted() {
            return sessionStarted;
        }

        @Override
        public Event<SessionEvent> sessionEnded() {
            return sessionEnded;
        }

        @Override
        public Event<SessionEvent> dimensionChanged() {
            return dimensionChanged;
        }

        @Override
        public Event<MapScreenEvent> fullscreenMapOpened() {
            return fullscreenMapOpened;
        }

        @Override
        public Event<MapScreenEvent> fullscreenMapClosed() {
            return fullscreenMapClosed;
        }

        @Override
        public Event<WaypointChangeEvent> waypointsChanged() {
            return waypointsChanged;
        }
    }
}
