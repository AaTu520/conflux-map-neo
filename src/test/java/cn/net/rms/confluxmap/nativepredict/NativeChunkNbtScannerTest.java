package cn.net.rms.confluxmap.nativepredict;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.Arrays;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.NbtList;
import net.minecraft.nbt.NbtString;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

class NativeChunkNbtScannerTest {
    @Test
    void selectivelyReadsLegacySurfaceColumn() throws IOException {
        Assumptions.assumeTrue(NativeLib.initForTests(), "native library unavailable");
        final ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (DataOutputStream output = new DataOutputStream(bytes)) {
            NbtIo.write(generatedStoneChunk(), output);
        }

        final NativeChunkNbtScanner.Chunk chunk = NativeChunkNbtScanner.scan(bytes.toByteArray(), 4);

        assertNotNull(chunk);
        assertTrue(chunk.generated());
        assertEquals(1L, chunk.revision());
        assertEquals(1, chunk.samples().length);
        assertEquals(0, chunk.samples()[0].surfaceY());
        assertEquals(0, chunk.samples()[0].biomeId());
        assertEquals("minecraft:stone", chunk.samples()[0].surfaceBlock());
    }

    @Test
    void promotesCarpetAboveTheMotionBlockingSurface() throws IOException {
        Assumptions.assumeTrue(NativeLib.initForTests(), "native library unavailable");
        final ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (DataOutputStream output = new DataOutputStream(bytes)) {
            NbtIo.write(generatedCarpetChunk(), output);
        }

        final NativeChunkNbtScanner.Chunk chunk = NativeChunkNbtScanner.scan(
            bytes.toByteArray(), 4
        );

        assertNotNull(chunk);
        assertTrue(chunk.generated());
        assertEquals(1, chunk.samples()[0].surfaceY());
        assertEquals("minecraft:white_carpet", chunk.samples()[0].surfaceBlock());
    }

    @Test
    void descendsThroughGlassAndReportsItAsOverlay() throws IOException {
        Assumptions.assumeTrue(NativeLib.initForTests(), "native library unavailable");
        final ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (DataOutputStream output = new DataOutputStream(bytes)) {
            NbtIo.write(generatedChunk("minecraft:black_stained_glass", 2), output);
        }

        final NativeChunkNbtScanner.Chunk chunk = NativeChunkNbtScanner.scan(
            bytes.toByteArray(), 4
        );

        assertNotNull(chunk);
        assertTrue(chunk.generated());
        assertEquals(0, chunk.samples()[0].surfaceY());
        assertEquals("minecraft:stone", chunk.samples()[0].surfaceBlock());
        assertEquals("minecraft:black_stained_glass", chunk.samples()[0].overlayBlock());
    }

    @Test
    void standingDecorationAboveTheSurfaceBecomesTheOverlay() throws IOException {
        Assumptions.assumeTrue(NativeLib.initForTests(), "native library unavailable");
        final ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (DataOutputStream output = new DataOutputStream(bytes)) {
            NbtIo.write(generatedChunk("minecraft:wither_rose", 1), output);
        }

        final NativeChunkNbtScanner.Chunk chunk = NativeChunkNbtScanner.scan(
            bytes.toByteArray(), 4
        );

        assertNotNull(chunk);
        assertTrue(chunk.generated());
        assertEquals(0, chunk.samples()[0].surfaceY());
        assertEquals("minecraft:stone", chunk.samples()[0].surfaceBlock());
        assertEquals("minecraft:wither_rose", chunk.samples()[0].overlayBlock());
    }

    @Test
    void bareStringPalettesIntroducedIn263AreScanned() throws IOException {
        Assumptions.assumeTrue(NativeLib.initForTests(), "native library unavailable");
        final NbtList palette = new NbtList();
        palette.add(NbtString.of("minecraft:stone"));
        palette.add(NbtString.of("minecraft:black_stained_glass"));
        final ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (DataOutputStream output = new DataOutputStream(bytes)) {
            NbtIo.write(generatedChunk(palette, 2), output);
        }

        final NativeChunkNbtScanner.Chunk chunk = NativeChunkNbtScanner.scan(
            bytes.toByteArray(), 4
        );

        assertNotNull(chunk);
        assertTrue(chunk.generated());
        assertEquals(0, chunk.samples()[0].surfaceY());
        assertEquals("minecraft:stone", chunk.samples()[0].surfaceBlock());
        assertEquals("minecraft:black_stained_glass", chunk.samples()[0].overlayBlock());
    }

    @Test
    void wrappedMixedPalettesIntroducedIn263AreScanned() throws IOException {
        Assumptions.assumeTrue(NativeLib.initForTests(), "native library unavailable");
        final ByteArrayOutputStream wetBytes = new ByteArrayOutputStream();
        try (DataOutputStream output = new DataOutputStream(wetBytes)) {
            NbtIo.write(generatedChunk(mixedPalette263("true"), 2), output);
        }
        final NativeChunkNbtScanner.Chunk wet = NativeChunkNbtScanner.scan(
            wetBytes.toByteArray(), 4
        );

        assertNotNull(wet);
        assertTrue(wet.generated());
        assertEquals(1, wet.samples()[0].surfaceY());
        assertEquals("minecraft:oak_stairs", wet.samples()[0].surfaceBlock());
        assertEquals(1, wet.samples()[0].fluidKind());
        assertEquals(1, wet.samples()[0].fluidDepth());
        assertEquals("minecraft:stone", wet.samples()[0].floorBlock());

        final ByteArrayOutputStream dryBytes = new ByteArrayOutputStream();
        try (DataOutputStream output = new DataOutputStream(dryBytes)) {
            NbtIo.write(generatedChunk(mixedPalette263("false"), 2), output);
        }
        final NativeChunkNbtScanner.Chunk dry = NativeChunkNbtScanner.scan(
            dryBytes.toByteArray(), 4
        );

        assertNotNull(dry);
        assertEquals("minecraft:oak_stairs", dry.samples()[0].surfaceBlock());
        assertEquals(0, dry.samples()[0].fluidKind());
        assertEquals(0, dry.samples()[0].fluidDepth());
    }

    /**
     * A 26.3 mixed palette exactly as it lies on disk: ListTag#wrapIfNeeded serialized the
     * default-state stone as {"" : id} beside the non-default oak_stairs compound, and only a
     * raw-byte parser sees this wrapped form.
     */
    private static NbtList mixedPalette263(final String waterlogged) {
        final NbtCompound stone = new NbtCompound();
        stone.putString("", "minecraft:stone");
        final NbtCompound properties = new NbtCompound();
        properties.putString("waterlogged", waterlogged);
        final NbtCompound stairs = new NbtCompound();
        stairs.putString("id", "minecraft:oak_stairs");
        stairs.put("properties", properties);
        final NbtList palette = new NbtList();
        palette.add(stone);
        palette.add(stairs);
        return palette;
    }

    @Test
    void malformedNbtFailsWithoutEscapingNativeParser() {
        Assumptions.assumeTrue(NativeLib.initForTests(), "native library unavailable");
        final java.util.Random random = new java.util.Random(0xC0FFEE);
        for (int size = 0; size < 512; size++) {
            final byte[] bytes = new byte[size];
            random.nextBytes(bytes);
            NativeChunkNbtScanner.scan(bytes, 4);
        }
    }

    private static NbtCompound generatedStoneChunk() {
        return generatedChunk(false);
    }

    private static NbtCompound generatedCarpetChunk() {
        return generatedChunk(true);
    }

    /**
     * @param topBlockName palette entry placed at every column of local layer 1
     * @param motionHeight the stored motion-blocking height: one above layer 0 (the top block is
     *        a non-colliding decoration above stone) or one above layer 1 (it is a light-permeable
     *        cover the scan must descend through)
     */
    private static NbtCompound generatedChunk(
        final String topBlockName, final int motionHeight
    ) {
        final NbtCompound stone = new NbtCompound();
        stone.putString("Name", "minecraft:stone");
        final NbtCompound top = new NbtCompound();
        top.putString("Name", topBlockName);
        final NbtList palette = new NbtList();
        palette.add(stone);
        palette.add(top);
        return generatedChunk(palette, motionHeight);
    }

    private static NbtCompound generatedChunk(final NbtList palette, final int motionHeight) {
        final NbtCompound level = new NbtCompound();
        level.putString("Status", "full");
        level.putLong("LastUpdate", 1L);

        final long[] heights = new long[(256 + 6) / 7];
        long packedOnes = 0L;
        for (int i = 0; i < 7; i++) {
            packedOnes |= (long) motionHeight << (i * 9);
        }
        Arrays.fill(heights, packedOnes);
        final NbtCompound heightmaps = new NbtCompound();
        heightmaps.putLongArray("MOTION_BLOCKING", heights);
        level.put("Heightmaps", heightmaps);
        level.putIntArray("Biomes", new int[1_024]);

        final NbtCompound section = new NbtCompound();
        section.putByte("Y", (byte) 0);
        section.put("Palette", palette);
        // 4-bit palette indices, 16 per long: local layer 1 (block indexes 256..511, longs
        // 16..31) becomes the top block everywhere, layer 0 stays stone (index 0).
        final long[] states = new long[256];
        Arrays.fill(states, 16, 32, 0x1111111111111111L);
        section.putLongArray("BlockStates", states);
        final NbtList sections = new NbtList();
        sections.add(section);
        level.put("Sections", sections);

        final NbtCompound root = new NbtCompound();
        root.put("Level", level);
        return root;
    }

    private static NbtCompound generatedChunk(final boolean carpetCover) {
        final NbtCompound level = new NbtCompound();
        level.putString("Status", "full");
        level.putLong("LastUpdate", 1L);

        final long[] heights = new long[(256 + 6) / 7];
        long packedOnes = 0L;
        for (int i = 0; i < 7; i++) {
            packedOnes |= 1L << (i * 9);
        }
        Arrays.fill(heights, packedOnes);
        final NbtCompound heightmaps = new NbtCompound();
        heightmaps.putLongArray("MOTION_BLOCKING", heights);
        level.put("Heightmaps", heightmaps);
        level.putIntArray("Biomes", new int[1_024]);

        final NbtCompound stone = new NbtCompound();
        stone.putString("Name", "minecraft:stone");
        final NbtList palette = new NbtList();
        palette.add(stone);
        if (carpetCover) {
            final NbtCompound carpet = new NbtCompound();
            carpet.putString("Name", "minecraft:white_carpet");
            palette.add(carpet);
        }
        final NbtCompound section = new NbtCompound();
        section.putByte("Y", (byte) 0);
        section.put("Palette", palette);
        if (carpetCover) {
            final long[] states = new long[256];
            Arrays.fill(states, 16, 32, 0x1111111111111111L);
            section.putLongArray("BlockStates", states);
        }
        final NbtList sections = new NbtList();
        sections.add(section);
        level.put("Sections", sections);

        final NbtCompound root = new NbtCompound();
        root.put("Level", level);
        return root;
    }
}
