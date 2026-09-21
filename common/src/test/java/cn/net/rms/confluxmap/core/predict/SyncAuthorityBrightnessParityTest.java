package cn.net.rms.confluxmap.core.predict;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import cn.net.rms.confluxmap.core.color.DaylightModel;
import cn.net.rms.confluxmap.core.color.LightTint;
import cn.net.rms.confluxmap.core.color.MapColorStyle;
import cn.net.rms.confluxmap.core.color.MaterialDetailProfile;
import cn.net.rms.confluxmap.core.color.ShadingPipeline;
import cn.net.rms.confluxmap.core.color.XaeroMapStyle;
import cn.net.rms.confluxmap.core.config.ConfluxConfig;
import cn.net.rms.confluxmap.core.model.ChunkSnapshot;
import cn.net.rms.confluxmap.core.model.DimensionId;
import cn.net.rms.confluxmap.core.model.MapLayer;
import cn.net.rms.confluxmap.core.model.SampleSource;
import cn.net.rms.confluxmap.core.model.SurfaceKind;
import cn.net.rms.confluxmap.core.model.TileKey;
import cn.net.rms.confluxmap.core.model.WorldIdentity;
import cn.net.rms.confluxmap.core.net.PatchCodec;
import cn.net.rms.confluxmap.core.net.Proto;
import cn.net.rms.confluxmap.core.store.MapWorld;
import cn.net.rms.confluxmap.core.store.MapWorldService;
import cn.net.rms.confluxmap.core.task.MapExecutors;
import cn.net.rms.confluxmap.core.task.SessionGuard;
import cn.net.rms.confluxmap.core.tile.TileService;
import cn.net.rms.confluxmap.core.util.Argb;
import cn.net.rms.confluxmap.nativepredict.McVersions;
import cn.net.rms.confluxmap.nativepredict.NativeLib;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Anchors the "synchronized map looks darker than the authoritative map" report at the pipeline
 * level: for semantically identical Nether-roof bedrock columns, the authoritative capture path
 * ({@link TileService}) and the synchronized-correction path ({@link CorrectionStore} samples
 * composed by {@link PredictedTileComposer}) must produce the same pixel, at LOD0 and inside a
 * coarse-LOD region, with the roof's block-light plane applied on both sides.
 *
 * <p>The synchronized side composes the stored correction tile directly, deliberately before
 * {@code TileService#maskPredictedPixels}: that mask is layer delegation (a synchronized pixel
 * over a locally captured chunk yields so the capture shows), not a color transform, so the
 * brightness comparison must happen on the pre-mask source pixels. Brightness drift the report
 * showed that survives these tests therefore lives in the runtime data (patch contents,
 * deployment versions), not in the two composition pipelines.
 */
final class SyncAuthorityBrightnessParityTest {
    private static final DimensionId DIM = DimensionId.NETHER;
    private static final WorldIdentity WORLD = WorldIdentity.singleplayer("sync-authority-parity");
    private static final String MATERIAL = "minecraft:bedrock";
    private static final int ROOF_Y = 127;
    private static final long SEED = 146008555L;
    private static final int MC_VERSION = McVersions.toCubiomes("1.17").orElseThrow();
    private static final int ROOF_MAP_COLOR_ID = PredictionDimensions.NETHER_ROOF_MAP_COLOR_ID;
    /** An arbitrary non-grey material color, so channel-specific drift cannot hide. */
    private static final int MATERIAL_COLOR = 0xFF8A7A66;
    /**
     * Interior of the one captured region inside a LOD2 tile: relief stencils near the region
     * boundary read the empty neighboring regions on the authoritative side, while the
     * prediction's uniform baseline has no such hole, so only stencil-free pixels are comparable
     * there (a LOD2 pixel spans 4 blocks; one source-pixel stencil is 4 output pixels). LOD0
     * tiles cover exactly the captured region and compare fully.
     */
    private static final int COARSE_COMPARABLE_MIN = 16;
    private static final int COARSE_COMPARABLE_MAX = 48;

    @Test
    void correctedRoofMatchesAuthoritativeCaptureAtLod0(@TempDir final Path tempDir) throws Exception {
        final Harness harness = new Harness(tempDir);
        try {
            harness.captureAuthoritativeRegion(0);
            harness.applyFullCoverCorrection(0, 0, 0, 42L);
            assertPixelsEqual(
                harness.authoritativePixels(0, 0),
                harness.synchronizedPixels(0, 0),
                0, 0, 256,
                "zero-light roof pixels must be identical"
            );
        } finally {
            harness.close();
        }
    }

    @Test
    void correctedRoofMatchesAuthoritativeCaptureInsideCoarseRegion(@TempDir final Path tempDir) throws Exception {
        final Harness harness = new Harness(tempDir);
        try {
            harness.captureAuthoritativeRegion(0);
            harness.applyFullCoverCorrection(0, 2, 0, 42L);
            assertPixelsEqual(
                harness.authoritativePixels(0, 2),
                harness.synchronizedPixels(0, 2),
                2, COARSE_COMPARABLE_MIN, COARSE_COMPARABLE_MAX,
                "coarse-LOD roof pixels must not drift from the captured map"
            );
        } finally {
            harness.close();
        }
    }

    @Test
    void roofLightPlaneAppliesIdenticallyOnBothPipelines(@TempDir final Path tempDir) throws Exception {
        final Harness harness = new Harness(tempDir);
        try {
            harness.captureAuthoritativeRegion(14);
            harness.applyFullCoverCorrection(0, 0, 14, 42L);
            assertPixelsEqual(
                harness.authoritativePixels(0, 0),
                harness.synchronizedPixels(0, 0),
                0, 0, 256,
                "the synchronized block-light plane must relight corrections exactly like captures"
            );
        } finally {
            harness.close();
        }
    }

    /**
     * Quantifies the Overworld-surface night mechanism behind the same report: a server column
     * whose block light was lost (Paper exposes only emitted light) renders at the readability
     * floor while the locally captured torch-lit column stays near full brightness. The test
     * pins the exact ratio so the darkening is a documented consequence of missing light data
     * rather than a hidden pipeline asymmetry.
     */
    @Test
    void surfaceNightScaleQuantifiesTheSyncDarkening() {
        final int color = 0xFF7FA0C8;
        final float nightFactor = 0f;
        final float serverScale = ShadingPipeline.daylightScale(nightFactor, 0);
        final float capturedScale = ShadingPipeline.daylightScale(nightFactor, 14);
        final int serverPixel = ShadingPipeline.applyDaylight(color, nightFactor, 0, 0f);
        final int capturedPixel = ShadingPipeline.applyDaylight(color, nightFactor, 14, 0f);

        assertEquals(0.30f, serverScale, 1e-4f, "lost server block light must sit on the floor");
        assertEquals(0.3f + 0.7f * (14f / 15f), capturedScale, 1e-4f);
        assertEquals(
            Math.round(Argb.red(color) * serverScale), Argb.red(serverPixel),
            "the server pixel must be scaled to the floor"
        );
        assertEquals(
            Math.round(Argb.red(color) * capturedScale), Argb.red(capturedPixel),
            "the captured torch-lit pixel must keep its block-light lift"
        );
        assertTrue(
            Argb.red(serverPixel) < Argb.red(capturedPixel) / 3,
            "at night a block-light-less synchronized column is roughly a third of the captured one"
        );
    }

    private static void assertPixelsEqual(
        final int[] authoritative,
        final int[] synchronizedPixels,
        final int lod,
        final int minInclusive,
        final int maxExclusive,
        final String message
    ) {
        assertEquals(authoritative.length, synchronizedPixels.length, "tile sizes must match");
        final List<String> differences = new ArrayList<>();
        int compared = 0;
        for (int z = minInclusive; z < maxExclusive; z++) {
            for (int x = minInclusive; x < maxExclusive; x++) {
                final int i = z * 256 + x;
                compared++;
                if (authoritative[i] != synchronizedPixels[i]) {
                    if (differences.size() < 5) {
                        differences.add(String.format(
                            "pixel(%d,%d) authoritative=%08x synchronized=%08x",
                            x, z, authoritative[i], synchronizedPixels[i]
                        ));
                    }
                }
            }
        }
        assertTrue(differences.isEmpty(), message + " at LOD" + lod + ": " + differences);
        assertTrue(compared > 0, "the comparable window must not be empty");
    }

    private static int countOpaque(final int[] pixels) {
        int count = 0;
        for (final int pixel : pixels) {
            if ((pixel >>> 24) != 0) {
                count++;
            }
        }
        return count;
    }

    /** Wires one Nether session with both composition pipelines over the same correction store. */
    private static final class Harness {
        private final MapExecutors executors = new MapExecutors();
        private final SessionGuard sessionGuard = new SessionGuard();
        private final MapWorldService worlds;
        private final CorrectionStore corrections;
        private final TileService tiles;

        Harness(final Path tempDir) {
            Assumptions.assumeTrue(NativeLib.initForTests(), "native prediction library unavailable");
            final SessionGuard.Session session = sessionGuard.begin(WORLD, DIM);
            worlds = new MapWorldService();
            worlds.switchSession(session);
            tiles = new TileService(worlds, executors, new ConfluxConfig(), new DaylightModel());
            corrections = new CorrectionStore(tempDir);
        }

        /**
         * Stores a full 16x16-chunk region of one uniform bedrock-roof column as the locally
         * captured authority, with the ambient tint already baked in like a live capture.
         */
        void captureAuthoritativeRegion(final int light) {
            final MapWorld world = worlds.current();
            final int ambientBaked = Argb.multiply(MATERIAL_COLOR, LightTint.multiplier(0, 0, true));
            for (int chunkZ = 0; chunkZ < 16; chunkZ++) {
                for (int chunkX = 0; chunkX < 16; chunkX++) {
                    world.put(MapLayer.NETHER_CEILING, roofSnapshot(chunkX, chunkZ, ambientBaked, light),
                        SampleSource.REAL_LIVE);
                }
            }
        }

        /** Applies one absolute server patch whose samples cover every pixel of the tile. */
        void applyFullCoverCorrection(final int tile, final int lod, final int light, final long revision) {
            final List<PatchCodec.Sample> samples = new ArrayList<>(PatchCodec.PIXELS);
            for (int pixel = 0; pixel < PatchCodec.PIXELS; pixel++) {
                samples.add(new PatchCodec.Sample(
                    pixel, 1, ROOF_Y, SurfaceKind.BEDROCK_CEILING.ordinal(),
                    ROOF_MAP_COLOR_ID, 0, 255, MATERIAL, ""
                ));
            }
            final PatchCodec.Patch patch;
            if (light == 0) {
                patch = new PatchCodec.Patch(samples);
            } else {
                final byte[] evaluated = new byte[PatchCodec.MASK_BYTES];
                final byte[] blockLight = new byte[PatchCodec.PIXELS];
                for (int i = 0; i < PatchCodec.MASK_BYTES; i++) {
                    evaluated[i] = (byte) 0xFF;
                }
                java.util.Arrays.fill(blockLight, (byte) light);
                patch = new PatchCodec.Patch(
                    evaluated, samples, unknownRevisions(), blockLight
                );
            }
            final byte[] presence = new byte[Proto.PATCH_PRESENCE_BYTES];
            for (int i = 0; i < presence.length; i++) {
                presence[i] = (byte) 0xFF;
            }
            assertTrue(corrections.apply(
                new CorrectionStore.Key(DIM.toString(), lod, tile, tile),
                revision, presence, patch
            ), "the correction store must accept a full-cover absolute patch");
        }

        /** Authoritative pixels through the live capture pipeline. */
        int[] authoritativePixels(final int tile, final int lod) throws Exception {
            return tiles.snapshotTile(
                new TileKey(WORLD, DIM, MapLayer.NETHER_CEILING.cacheId(), lod, tile, tile),
                true, 1f
            ).get(30, TimeUnit.SECONDS);
        }

        /**
         * Synchronized pixels composed from the stored correction tile, replicating
         * {@code PredictionTileService#composeTile}'s Nether-roof inputs (native roof plane,
         * fixed roof map color, no absolute-height wash, nether ambient tint) and its roof
         * relighting pass.
         */
        int[] synchronizedPixels(final int tile, final int lod) {
            final CorrectionTile stored = corrections.get(
                new CorrectionStore.Key(DIM.toString(), lod, tile, tile)
            );
            assertNotNull(stored, "the correction tile must be present");
            assertTrue(stored.hasCommittedState(), "the correction tile must hold the applied patch");
            final BaselineGrid grid = LodSampling.sampleNetherRoof(
                new NativeBaselineSampler(MC_VERSION, SEED, PredictionDimensions.nativeDim(DIM), 0),
                lod, 0, 0
            );
            assertNotNull(grid, "the nether roof baseline must sample");
            final DerivedGrid derived = BaselineDeriver.derive(grid);
            final SyncedMaterialPalette materials = new SyncedMaterialPalette();
            materials.put(MATERIAL, new SyncedMaterialPalette.Sample(
                MATERIAL_COLOR, MaterialDetailProfile.flat(),
                SyncedMaterialPalette.Tint.NONE, 0, 0
            ));
            final int[] pixels = PredictedTileComposer.compose(
                derived, grid, PredictionPalette.defaults(), stored,
                PredictionViewMode.EVERYWHERE, lod,
                ROOF_MAP_COLOR_ID, derived, grid, ROOF_MAP_COLOR_ID,
                false, LightTint.multiplier(0, 0, true),
                materials, MapColorStyle.CONFLUX, XaeroMapStyle.shadowFor(DIM)
            );
            final byte[] blockLight = stored.copyBlockLight();
            for (int i = 0; i < pixels.length; i++) {
                pixels[i] = LightTint.applyBlockLightOverAmbient(
                    pixels[i], blockLight[i] & 0xFF, true, 0f
                );
            }
            assertTrue(countOpaque(pixels) == pixels.length, "every synchronized pixel must render");
            return pixels;
        }

        void close() {
            executors.shutdown(2000L);
        }

        private static ChunkSnapshot roofSnapshot(final int chunkX, final int chunkZ, final int baseArgb, final int light) {
            return new ChunkSnapshot(
                chunkX, chunkZ, 1L, 1L,
                fillShort(new short[ChunkSnapshot.COLUMNS], (short) ROOF_Y),
                fillString(new String[ChunkSnapshot.COLUMNS], "minecraft:crimson_forest"),
                new byte[ChunkSnapshot.COLUMNS],
                fillInt(new int[ChunkSnapshot.COLUMNS], baseArgb),
                fillInt(new int[ChunkSnapshot.COLUMNS], baseArgb),
                fillInt(new int[ChunkSnapshot.COLUMNS], 0xFFFFFFFF),
                fillInt(new int[ChunkSnapshot.COLUMNS], Argb.TRANSPARENT),
                fillInt(new int[ChunkSnapshot.COLUMNS], Argb.TRANSPARENT),
                fillByte(new byte[ChunkSnapshot.COLUMNS], (byte) SurfaceKind.BEDROCK_CEILING.ordinal()),
                fillByte(new byte[ChunkSnapshot.COLUMNS], (byte) light)
            );
        }

        private static long[] unknownRevisions() {
            final long[] revisions = new long[PatchCodec.PIXELS];
            java.util.Arrays.fill(revisions, Long.MIN_VALUE);
            return revisions;
        }

        private static short[] fillShort(final short[] array, final short value) {
            java.util.Arrays.fill(array, value);
            return array;
        }

        private static String[] fillString(final String[] array, final String value) {
            java.util.Arrays.fill(array, value);
            return array;
        }

        private static int[] fillInt(final int[] array, final int value) {
            java.util.Arrays.fill(array, value);
            return array;
        }

        private static byte[] fillByte(final byte[] array, final byte value) {
            java.util.Arrays.fill(array, value);
            return array;
        }
    }
}
