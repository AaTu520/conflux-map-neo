package cn.net.rms.confluxmap.core.api;

import cn.net.rms.confluxmap.api.ActionApi;
import cn.net.rms.confluxmap.api.ConfluxMapApi;
import cn.net.rms.confluxmap.api.ConfluxMapEvents;
import cn.net.rms.confluxmap.api.MapDataApi;
import cn.net.rms.confluxmap.api.WaypointApi;
import cn.net.rms.confluxmap.api.MarkerApi;
import cn.net.rms.confluxmap.bridge.GameBridge;
import cn.net.rms.confluxmap.bridge.PlayerView;
import cn.net.rms.confluxmap.core.color.DaylightModel;
import cn.net.rms.confluxmap.core.config.ConfluxConfig;
import cn.net.rms.confluxmap.core.model.ChunkSnapshot;
import cn.net.rms.confluxmap.core.model.DimensionId;
import cn.net.rms.confluxmap.core.model.MapLayer;
import cn.net.rms.confluxmap.core.model.SampleSource;
import cn.net.rms.confluxmap.core.model.SurfaceKind;
import cn.net.rms.confluxmap.core.model.WorldIdentity;
import cn.net.rms.confluxmap.core.predict.PredictionState;
import cn.net.rms.confluxmap.core.predict.PredictionTileService;
import cn.net.rms.confluxmap.core.store.MapWorldService;
import cn.net.rms.confluxmap.core.task.MapExecutors;
import cn.net.rms.confluxmap.core.task.SessionGuard;
import cn.net.rms.confluxmap.core.tile.TileService;
import cn.net.rms.confluxmap.core.waypoint.WaypointService;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Queue;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import org.apache.logging.log4j.LogManager;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Exercises the public API facade over the real core services with a scripted bridge. */
final class ConfluxMapApiImplTest {
    private static final WorldIdentity WORLD = new WorldIdentity("local", "world");
    private static final DimensionId OVERWORLD = DimensionId.OVERWORLD;
    private static final DimensionId NETHER = DimensionId.NETHER;

    /** Either answers "yes, this is the main thread" or queues tasks like the client would. */
    private static final class ScriptedBridge implements GameBridge {
        private final Queue<Runnable> queued = new ArrayDeque<>();
        private boolean mainThread = true;

        void actAsBackgroundThread() {
            mainThread = false;
        }

        void drain() {
            for (Runnable task = queued.poll(); task != null; task = queued.poll()) {
                task.run();
            }
        }

        @Override
        public SessionGuard.Session session() {
            return SessionGuard.Session.NONE;
        }

        @Override
        public Optional<PlayerView> player(final float tickDelta) {
            return Optional.of(new PlayerView(10, 64, -20, 79.5, 0f, OVERWORLD));
        }

        @Override
        public boolean isCameraDetached() {
            return false;
        }

        @Override
        public boolean isOnRenderThread() {
            return mainThread;
        }

        @Override
        public void runOnRenderThread(final Runnable task) {
            queued.add(task);
        }
    }

    private static final class NoopActions implements ActionApi {
        @Override
        public void openFullscreenMap() {
        }

        @Override
        public void closeFullscreenMap() {
        }

        @Override
        public void openWaypointList() {
        }

        @Override
        public void openWaypointEditor(final UUID waypointId) {
        }

        @Override
        public CompletableFuture<TeleportResult> requestTeleport(final UUID waypointId) {
            return CompletableFuture.completedFuture(TeleportResult.NO_SESSION);
        }

        @Override
        public LayerOverride layerOverride() {
            return LayerOverride.AUTO;
        }

        @Override
        public void setLayerOverride(final LayerOverride override) {
        }

        @Override
        public MapDataApi.ApiLayer activeLayer() {
            return MapDataApi.ApiLayer.SURFACE;
        }
    }

    @TempDir
    Path tempDir;

    private final ScriptedBridge bridge = new ScriptedBridge();
    private MapExecutors executors;
    private SessionGuard guard;
    private MapWorldService mapWorlds;
    private TileService tiles;
    private PredictionTileService prediction;
    private WaypointService waypoints;
    private CustomMarkerService markers;
    private ConfluxMapApiImpl api;

    @BeforeEach
    void setUp() {
        executors = new MapExecutors();
        guard = new SessionGuard();
        mapWorlds = new MapWorldService();
        final DaylightModel daylight = new DaylightModel();
        tiles = new TileService(mapWorlds, executors, new ConfluxConfig(), daylight);
        prediction = new PredictionTileService(guard, new PredictionState(), executors, tiles);
        waypoints = new WaypointService(tempDir, executors, LogManager.getLogger());
        markers = new CustomMarkerService(LogManager.getLogger());
        api = new ConfluxMapApiImpl(
            "test", bridge, waypoints, guard, mapWorlds, tiles, prediction, daylight,
            markers, new NoopActions()
        );
        waypoints.addChangeListener(api::onWaypointsChanged);
        api.install();
    }

    @AfterEach
    void tearDown() throws IOException {
        api.shutdown();
        executors.shutdown(2000L);
    }

    @Test
    void instanceHolderTracksInstallAndShutdown() {
        assertTrue(ConfluxMapApi.instance().isPresent());
        assertEquals("test", ConfluxMapApi.instance().get().version());
        api.shutdown();
        assertTrue(ConfluxMapApi.instance().isEmpty());
        api.install();
    }

    @Test
    void waypointCrudRoundTripAndChangeEvent() {
        beginSession(WORLD, OVERWORLD);
        final List<ConfluxMapEvents.WaypointChangeEvent> changes = new ArrayList<>();
        api.events().waypointsChanged().register(changes::add);

        final WaypointApi.WaypointMutation added = api.waypoints().add(
            edit("Base", "minecraft:overworld", 12, 70, -34)
        ).join();
        assertEquals(WaypointApi.WaypointMutation.Result.APPLIED, added.result());
        final UUID id = added.waypoint().id();
        assertEquals(1, api.waypoints().list().size());
        assertEquals(1, changes.size());
        assertEquals("Base", changes.get(0).waypoints().get(0).name());

        final WaypointApi.WaypointMutation updated = api.waypoints().update(
            id, editNamedOnly("Renamed")
        ).join();
        assertEquals(WaypointApi.WaypointMutation.Result.APPLIED, updated.result());
        assertEquals("Renamed", api.waypoints().get(id).orElseThrow().name());
        assertEquals(2, changes.size());

        final WaypointApi.WaypointMutation removed = api.waypoints().remove(id).join();
        assertEquals(WaypointApi.WaypointMutation.Result.APPLIED, removed.result());
        assertTrue(api.waypoints().list().isEmpty());
        assertEquals(3, changes.size());

        final WaypointApi.WaypointMutation missing = api.waypoints().remove(id).join();
        assertEquals(WaypointApi.WaypointMutation.Result.NOT_FOUND, missing.result());
        assertNull(missing.waypoint());
    }

    @Test
    void waypointAddWithoutSessionReportsNoSession() {
        final WaypointApi.WaypointMutation result = api.waypoints().add(
            edit("Nowhere", "minecraft:overworld", 0, 64, 0)
        ).join();
        assertEquals(WaypointApi.WaypointMutation.Result.NO_SESSION, result.result());
    }

    @Test
    void waypointAddFallsBackToViewpointPosition() {
        beginSession(WORLD, OVERWORLD);
        final WaypointApi.WaypointMutation added = api.waypoints().add(
            editNamedOnly("Here")
        ).join();
        assertEquals(WaypointApi.WaypointMutation.Result.APPLIED, added.result());
        assertEquals(10, added.waypoint().x());
        assertEquals(64, added.waypoint().y());
        assertEquals(-20, added.waypoint().z());
        assertEquals("minecraft:overworld", added.waypoint().dimensionId());
    }

    @Test
    void offThreadWritesMarshalThroughTheBridge() {
        beginSession(WORLD, OVERWORLD);
        bridge.actAsBackgroundThread();
        final CompletableFuture<WaypointApi.WaypointMutation> pending = api.waypoints().add(
            edit("Async", "minecraft:overworld", 1, 2, 3)
        );
        assertFalse(pending.isDone());
        bridge.drain();
        assertEquals(WaypointApi.WaypointMutation.Result.APPLIED, pending.join().result());
    }

    @Test
    void sessionEventsFollowWorldAndDimensionTransitions() {
        final List<String> fired = new ArrayList<>();
        api.events().sessionStarted().register(e -> fired.add("started:" + e.worldId()));
        api.events().sessionEnded().register(e -> fired.add("ended:" + e.worldId()));
        api.events().dimensionChanged().register(e -> fired.add("dim:" + e.dimensionId()));

        api.onSessionChanged(guard.begin(WORLD, OVERWORLD));
        api.onSessionChanged(guard.begin(WORLD, NETHER));
        api.onSessionChanged(guard.begin(new WorldIdentity("server", "map"), OVERWORLD));
        guard.end();
        api.onSessionChanged(guard.current());

        assertEquals(
            List.of(
                "started:world",
                "dim:minecraft:the_nether",
                "ended:world",
                "started:map",
                "ended:map"
            ),
            fired
        );
    }

    @Test
    void mapDataQueriesReadCapturedColumns() {
        final SessionGuard.Session session = guard.begin(WORLD, OVERWORLD);
        mapWorlds.switchSession(session);
        mapWorlds.current().put(
            MapLayer.SURFACE, chunkSnapshot(0, 0, session.token()), SampleSource.REAL_LIVE
        );

        final MapDataApi mapData = api.mapData();
        assertEquals(Optional.of("minecraft:overworld"), mapData.currentDimension());
        assertEquals(Optional.of("world"), mapData.currentWorldId());
        assertTrue(mapData.hasExploredChunk("minecraft:overworld", MapDataApi.ApiLayer.SURFACE, 0, 0));
        assertFalse(mapData.hasExploredChunk("minecraft:overworld", MapDataApi.ApiLayer.SURFACE, 32, 32));
        assertEquals(64, mapData.surfaceYAt("minecraft:overworld", MapDataApi.ApiLayer.SURFACE, 5, 7).getAsInt());
        assertEquals(
            Optional.of("minecraft:plains"),
            mapData.biomeAt("minecraft:overworld", MapDataApi.ApiLayer.SURFACE, 5, 7)
        );
        // A different dimension is a different session's data; nothing to read there.
        assertFalse(mapData.hasExploredChunk("minecraft:the_nether", MapDataApi.ApiLayer.SURFACE, 0, 0));
    }

    @Test
    void markersSampleFilterAndUnregister() {
        final MarkerApi.ApiMarker overworld = new MarkerApi.ApiMarker(
            "machine-1", "minecraft:overworld", 4, 64, 8, "Pump", 0xFF3498DB,
            "minecraft:redstone", true, true, 0
        );
        final MarkerApi.ApiMarker nether = new MarkerApi.ApiMarker(
            "portal", "minecraft:the_nether", 0, 64, 0, "Portal", 0xFF9B59B6,
            "", false, true, 0
        );
        try (var ignored = api.markers().registerProvider(
            "example", () -> List.of(overworld, nether)
        )) {
            api.markers().tick();
            assertEquals(2, api.markers().markers().size());
            assertEquals(1, api.markers().renderEntries(OVERWORLD, true).size());
            assertEquals(1, api.markers().renderEntries(OVERWORLD, false).size());
            // The nether marker is fullscreen-only and a different dimension entirely.
            assertEquals(0, api.markers().renderEntries(NETHER, true).size());
            assertEquals(1, api.markers().renderEntries(NETHER, false).size());
        }
        api.markers().tick();
        assertTrue(api.markers().markers().isEmpty());
    }

    @Test
    void failingMarkerProviderIsContained() {
        try (var ignored = api.markers().registerProvider("broken", () -> {
            throw new IllegalStateException("boom");
        })) {
            api.markers().tick();
            assertTrue(api.markers().markers().isEmpty());
        }
    }

    private void beginSession(final WorldIdentity world, final DimensionId dimension) {
        final SessionGuard.Session session = guard.begin(world, dimension);
        mapWorlds.switchSession(session);
        waypoints.onSessionChanged(session);
        api.onSessionChanged(session);
    }

    private static ChunkSnapshot chunkSnapshot(final int chunkX, final int chunkZ, final long token) {
        final short[] surfaceY = new short[ChunkSnapshot.COLUMNS];
        final String[] biomeId = new String[ChunkSnapshot.COLUMNS];
        final byte[] fluidDepth = new byte[ChunkSnapshot.COLUMNS];
        final int[] baseArgb = new int[ChunkSnapshot.COLUMNS];
        final int[] tintArgb = new int[ChunkSnapshot.COLUMNS];
        final int[] overlayArgb = new int[ChunkSnapshot.COLUMNS];
        final byte[] kind = new byte[ChunkSnapshot.COLUMNS];
        final byte[] light = new byte[ChunkSnapshot.COLUMNS];
        java.util.Arrays.fill(surfaceY, (short) 64);
        java.util.Arrays.fill(biomeId, "minecraft:plains");
        java.util.Arrays.fill(baseArgb, 0xFF708090);
        java.util.Arrays.fill(kind, (byte) SurfaceKind.LAND.ordinal());
        java.util.Arrays.fill(light, (byte) 15);
        return new ChunkSnapshot(
            chunkX, chunkZ, token, 1L, surfaceY, biomeId, fluidDepth,
            baseArgb, tintArgb, overlayArgb, kind, light
        );
    }


    private static WaypointApi.WaypointEdit edit(
        final String name, final String dimension, final double x, final double y, final double z
    ) {
        return WaypointApi.WaypointEdit.builder()
            .name(name).dimensionId(dimension).position(x, y, z).build();
    }

    private static WaypointApi.WaypointEdit editNamedOnly(final String name) {
        return WaypointApi.WaypointEdit.builder().name(name).build();
    }
}
