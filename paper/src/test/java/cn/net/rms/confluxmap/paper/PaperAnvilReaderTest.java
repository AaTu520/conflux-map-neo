package cn.net.rms.confluxmap.paper;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import cn.net.rms.confluxmap.core.model.SurfaceKind;
import cn.net.rms.confluxmap.core.net.SummaryCodec;
import cn.net.rms.confluxmap.core.util.ChunkRegionSlice;
import cn.net.rms.confluxmap.server.ChunkColumnSummarizer;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.function.IntUnaryOperator;
import java.util.zip.DeflaterOutputStream;
import net.querz.nbt.io.NBTOutputStream;
import net.querz.nbt.io.NamedTag;
import net.querz.nbt.tag.CompoundTag;
import net.querz.nbt.tag.ListTag;
import net.querz.nbt.tag.StringTag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class PaperAnvilReaderTest {
    @Test
    void readsOnlyTheRequestedCropFromARealMcaContainer(@TempDir final Path temporary)
        throws IOException {
        writeRegion(temporary.resolve("r.0.0.mca"), generatedStoneChunk(), 0, 0, 1, 0);
        final ChunkColumnSummarizer summarizer = new ChunkColumnSummarizer(name -> 11);

        final SummaryCodec.SampledRegion region = new PaperAnvilReader().scanRegion(
            temporary,
            4,
            new ChunkRegionSlice(0, 0, 0, 0, 0, 0),
            summarizer
        );

        assertNotNull(region);
        assertTrue(region.chunks()[0].generated());
        assertFalse(region.chunks()[1].generated());
        assertEquals(1, region.chunks()[0].columns().length);
        assertEquals(SurfaceKind.LAND.ordinal(), region.chunks()[0].columns()[0].kind());
        assertEquals(11, region.chunks()[0].columns()[0].mapColorId());
        assertEquals(13, region.chunks()[0].columns()[0].blockLight());
    }

    @Test
    void missingRegionIsAnAuthoritativeEmptyPage(@TempDir final Path temporary) {
        final SummaryCodec.SampledRegion region = new PaperAnvilReader().scanRegion(
            temporary,
            2,
            new ChunkRegionSlice(-1, 3, 0, 0, 15, 15),
            new ChunkColumnSummarizer(name -> 11)
        );

        assertNotNull(region);
        assertEquals(-1, region.rx());
        assertEquals(3, region.rz());
        assertEquals(4, region.sampleStride());
        assertFalse(region.chunks()[0].generated());
    }

    @Test
    void readsModernNegativeHeightChunkNbt(@TempDir final Path temporary) throws IOException {
        writeRegion(temporary.resolve("r.-1.0.mca"), modernStoneChunk(), 31, 0);

        final SummaryCodec.SampledRegion region = new PaperAnvilReader().scanRegion(
            temporary,
            0,
            new ChunkRegionSlice(-1, 0, 15, 0, 15, 0),
            new ChunkColumnSummarizer(name -> 11)
        );

        final SummaryCodec.SampledChunk chunk = region.chunks()[15];
        assertTrue(chunk.generated());
        assertEquals(42L, chunk.revision());
        assertEquals(0, chunk.columns()[0].surfaceY());
    }

    @Test
    void bareStringPalettesIntroducedIn263AreSummarized(@TempDir final Path temporary)
        throws IOException {
        final ListTag<StringTag> palette = new ListTag<>(StringTag.class);
        palette.add(new StringTag("minecraft:water"));
        palette.add(new StringTag("minecraft:air"));
        palette.add(new StringTag("minecraft:stone"));
        writeRegion(temporary.resolve("r.-1.0.mca"), modernOceanChunk(palette), 31, 0);

        final SummaryCodec.SampledChunk chunk = scannedOceanChunk(temporary);

        assertTrue(chunk.generated());
        assertEquals(62, chunk.columns()[0].surfaceY());
        assertEquals(SurfaceKind.LAND.ordinal(), chunk.columns()[0].kind());
    }

    @Test
    void wrappedMixedPalettesIntroducedIn263AreSummarized(@TempDir final Path temporary)
        throws IOException {
        // Raw-byte shape of a 26.3 mixed palette: ListTag#wrapIfNeeded serialized the bare
        // default-state strings as {"" : id} inside the compound-typed list. Vanilla's reader
        // unwraps them on load; querz shows the bytes exactly as written.
        final ListTag<CompoundTag> wetPalette = new ListTag<>(CompoundTag.class);
        wetPalette.add(wrappedEntry("minecraft:water"));
        wetPalette.add(wrappedEntry("minecraft:air"));
        wetPalette.add(idStoneEntry("true"));
        writeRegion(temporary.resolve("r.-1.0.mca"), modernOceanChunk(wetPalette), 31, 0);
        final SummaryCodec.SampledChunk wetChunk = scannedOceanChunk(temporary);

        assertTrue(wetChunk.generated());
        assertEquals(62, wetChunk.columns()[0].surfaceY());
        assertEquals(SurfaceKind.WATER.ordinal(), wetChunk.columns()[0].kind());

        final ListTag<CompoundTag> dryPalette = new ListTag<>(CompoundTag.class);
        dryPalette.add(wrappedEntry("minecraft:water"));
        dryPalette.add(wrappedEntry("minecraft:air"));
        dryPalette.add(idStoneEntry("false"));
        writeRegion(temporary.resolve("r.-1.0.mca"), modernOceanChunk(dryPalette), 31, 0);
        assertEquals(SurfaceKind.LAND.ordinal(), scannedOceanChunk(temporary).columns()[0].kind());
    }

    private static SummaryCodec.SampledChunk scannedOceanChunk(final Path temporary) {
        return new PaperAnvilReader()
            .scanRegion(
                temporary,
                0,
                new ChunkRegionSlice(-1, 0, 15, 0, 15, 0),
                new ChunkColumnSummarizer(name -> 11)
            )
            .chunks()[15];
    }

    private static void writeRegion(
        final Path path,
        final CompoundTag chunk,
        final int... coordinates
    ) throws IOException {
        final ByteArrayOutputStream raw = new ByteArrayOutputStream();
        try (NBTOutputStream output = new NBTOutputStream(raw)) {
            output.writeTag(new NamedTag("", chunk), 512);
        }
        final ByteArrayOutputStream compressed = new ByteArrayOutputStream();
        try (DeflaterOutputStream deflater = new DeflaterOutputStream(compressed)) {
            deflater.write(raw.toByteArray());
        }
        final byte[] payload = compressed.toByteArray();
        final int recordLength = Integer.BYTES + 1 + payload.length;
        final int sectorsPerChunk = (recordLength + 4_095) / 4_096;
        final byte[] file = new byte[(2 + sectorsPerChunk * (coordinates.length / 2)) * 4_096];
        final ByteBuffer header = ByteBuffer.wrap(file);
        int sector = 2;
        for (int i = 0; i < coordinates.length; i += 2) {
            final int localX = coordinates[i];
            final int localZ = coordinates[i + 1];
            header.putInt((localZ * 32 + localX) * 4, (sector << 8) | sectorsPerChunk);
            final ByteBuffer record = ByteBuffer.wrap(
                file, sector * 4_096, sectorsPerChunk * 4_096
            );
            record.putInt(payload.length + 1);
            record.put((byte) 2);
            record.put(payload);
            sector += sectorsPerChunk;
        }
        Files.write(path, file);
    }

    private static CompoundTag generatedStoneChunk() {
        final CompoundTag level = new CompoundTag();
        level.putString("Status", "full");
        level.putLong("LastUpdate", 42L);
        final long[] heights = new long[(256 + 6) / 7];
        long packedOnes = 0L;
        for (int i = 0; i < 7; i++) {
            packedOnes |= 1L << (i * 9);
        }
        Arrays.fill(heights, packedOnes);
        final CompoundTag heightmaps = new CompoundTag();
        heightmaps.putLongArray("MOTION_BLOCKING", heights);
        level.put("Heightmaps", heightmaps);
        level.putIntArray("Biomes", new int[1_024]);

        final CompoundTag stone = new CompoundTag();
        stone.putString("Name", "minecraft:stone");
        final ListTag<CompoundTag> palette = new ListTag<>(CompoundTag.class);
        palette.add(stone);
        final CompoundTag section = new CompoundTag();
        section.putByte("Y", (byte) 0);
        section.put("Palette", palette);
        final byte[] blockLight = new byte[2_048];
        Arrays.fill(blockLight, (byte) 0xDD);
        section.putByteArray("BlockLight", blockLight);
        final ListTag<CompoundTag> sections = new ListTag<>(CompoundTag.class);
        sections.add(section);
        level.put("Sections", sections);

        final CompoundTag root = new CompoundTag();
        root.put("Level", level);
        return root;
    }

    private static CompoundTag modernStoneChunk() {
        final CompoundTag root = new CompoundTag();
        root.putString("Status", "minecraft:full");
        root.putLong("LastUpdate", 42L);
        root.putInt("yPos", -4);
        final long[] heights = new long[(256 + 6) / 7];
        long packed = 0L;
        for (int i = 0; i < 7; i++) {
            packed |= 65L << (i * 9);
        }
        Arrays.fill(heights, packed);
        final CompoundTag heightmaps = new CompoundTag();
        heightmaps.putLongArray("MOTION_BLOCKING", heights);
        root.put("Heightmaps", heightmaps);

        final CompoundTag stone = new CompoundTag();
        stone.putString("Name", "minecraft:stone");
        final ListTag<CompoundTag> blockPalette = new ListTag<>(CompoundTag.class);
        blockPalette.add(stone);
        final CompoundTag blockStates = new CompoundTag();
        blockStates.put("palette", blockPalette);
        final ListTag<StringTag> biomePalette = new ListTag<>(StringTag.class);
        biomePalette.add(new StringTag("minecraft:plains"));
        final CompoundTag biomes = new CompoundTag();
        biomes.put("palette", biomePalette);
        final CompoundTag section = new CompoundTag();
        section.putByte("Y", (byte) 0);
        section.put("block_states", blockStates);
        section.put("biomes", biomes);
        final ListTag<CompoundTag> sections = new ListTag<>(CompoundTag.class);
        sections.add(section);
        root.put("sections", sections);
        return root;
    }

    /**
     * Modern ocean column mirroring the Fabric ChunkSummarizerTest fixture: water below, the
     * palette's third entry (a stone state) at local layer 14 (world y 62), air above, and
     * heightmaps that make y 62 the motion-blocking surface.
     */
    private static CompoundTag modernOceanChunk(final ListTag<?> blockPalette) {
        final CompoundTag root = new CompoundTag();
        root.putString("Status", "minecraft:full");
        root.putLong("LastUpdate", 42L);
        root.putInt("yPos", -4);
        final CompoundTag heightmaps = new CompoundTag();
        heightmaps.putLongArray("MOTION_BLOCKING", pack(9, 256, ignored -> 127));
        heightmaps.putLongArray("OCEAN_FLOOR", pack(9, 256, ignored -> 114));
        root.put("Heightmaps", heightmaps);

        final CompoundTag blockStates = new CompoundTag();
        blockStates.put("palette", blockPalette);
        blockStates.putLongArray("data", pack(4, 4096, index -> {
            final int localY = index >>> 8;
            if (localY == 15) {
                return 1;
            }
            return localY == 14 ? 2 : 0;
        }));
        final CompoundTag section = new CompoundTag();
        section.putByte("Y", (byte) 3);
        section.put("block_states", blockStates);
        final ListTag<StringTag> biomePalette = new ListTag<>(StringTag.class);
        biomePalette.add(new StringTag("minecraft:plains"));
        final CompoundTag biomes = new CompoundTag();
        biomes.put("palette", biomePalette);
        section.put("biomes", biomes);
        final ListTag<CompoundTag> sections = new ListTag<>(CompoundTag.class);
        sections.add(section);
        root.put("sections", sections);
        return root;
    }

    private static CompoundTag wrappedEntry(final String name) {
        final CompoundTag entry = new CompoundTag();
        entry.putString("", name);
        return entry;
    }

    private static CompoundTag idStoneEntry(final String waterlogged) {
        final CompoundTag properties = new CompoundTag();
        properties.putString("waterlogged", waterlogged);
        final CompoundTag stone = new CompoundTag();
        stone.putString("id", "minecraft:stone");
        stone.put("properties", properties);
        return stone;
    }

    private static long[] pack(final int bits, final int count, final IntUnaryOperator values) {
        final int perWord = 64 / bits;
        final long[] words = new long[(count + perWord - 1) / perWord];
        final long mask = (1L << bits) - 1L;
        for (int i = 0; i < count; i++) {
            words[i / perWord] |= ((long) values.applyAsInt(i) & mask) << ((i % perWord) * bits);
        }
        return words;
    }
}
