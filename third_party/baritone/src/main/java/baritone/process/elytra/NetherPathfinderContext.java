/*
 * This file is part of Baritone.
 *
 * Baritone is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * Baritone is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with Baritone.  If not, see <https://www.gnu.org/licenses/>.
 */

package baritone.process.elytra;

import baritone.Baritone;
import baritone.api.event.events.BlockChangeEvent;
import baritone.utils.accessor.IPalettedContainer;
import dev.babbaj.pathfinder.NetherPathfinder;
import dev.babbaj.pathfinder.Octree;
import dev.babbaj.pathfinder.PathSegment;
import net.minecraft.core.BlockPos;
import net.minecraft.util.BitStorage;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.level.chunk.PaletteResize;
import net.minecraft.world.level.chunk.PalettedContainer;
import net.minecraft.world.phys.Vec3;

import java.lang.ref.SoftReference;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/**
 * @author Brady
 */
public final class NetherPathfinderContext {

    private static final BlockState AIR_BLOCK_STATE = Blocks.AIR.defaultBlockState();
    private static final int NETHER_PATHFINDER_HEIGHT = 128;
    // This lock must be held while there are active pointers to chunks in java,
    // but we just hold it for the entire tick so we don't have to think much about it.
    public final Object cullingLock = new Object();

    // Visible for access in BlockStateOctreeInterface
    final long context;
    private final long seed;
    private final ExecutorService executor;

    public NetherPathfinderContext(long seed) {
        this.context = NetherPathfinder.newContext(seed, null, NetherPathfinder.DIMENSION_NETHER, NETHER_PATHFINDER_HEIGHT, true);
        this.seed = seed;
        this.executor = Executors.newSingleThreadExecutor(runnable -> {
            Thread thread = new Thread(runnable, "Baritone Nether Cache");
            thread.setDaemon(true);
            return thread;
        });
    }

    public boolean hasChunk(ChunkPos pos) {
        return NetherPathfinder.hasChunkFromJava(this.context, pos.x(), pos.z());
    }

    public void queueCacheCulling(int chunkX, int chunkZ, int maxDistanceBlocks, BlockStateOctreeInterface boi) {
        this.executor.execute(() -> {
            synchronized (this.cullingLock) {
                boi.chunkPtr = 0L;
                NetherPathfinder.cullFarChunks(this.context, chunkX, chunkZ, maxDistanceBlocks);
            }
        });
    }

    public void queueForPacking(final LevelChunk chunkIn) {
        final SoftReference<LevelChunk> ref = new SoftReference<>(chunkIn);
        this.executor.execute(() -> {
            // TODO: Prioritize packing recent chunks and/or ones that the path goes through,
            //       and prune the oldest chunks per chunkPackerQueueMaxSize
            final LevelChunk chunk = ref.get();
            if (chunk != null) {
                writeChunkData(this.context, chunk);
            }
        });
    }

    public void queueBlockUpdate(BlockChangeEvent event) {
        this.executor.execute(() -> {
            ChunkPos chunkPos = event.getChunkPos();
            long ptr = NetherPathfinder.getChunk(this.context, chunkPos.x(), chunkPos.z());
            if (ptr == 0) return; // this shouldn't ever happen
            event.getBlocks().forEach(pair -> {
                BlockPos pos = pair.first();
                if (pos.getY() < 0 || pos.getY() >= NETHER_PATHFINDER_HEIGHT) return;
                boolean isSolid = pair.second() != AIR_BLOCK_STATE;
                Octree.setBlock(ptr, pos.getX() & 15, pos.getY(), pos.getZ() & 15, isSolid);
            });
            NetherPathfinder.setChunkState(this.context, chunkPos.x(), chunkPos.z(), true);
        });
    }

    public CompletableFuture<PathSegment> pathFindAsync(final BlockPos src, final BlockPos dst) {
        return CompletableFuture.supplyAsync(() -> {
            final PathSegment segment = NetherPathfinder.pathFind(
                    this.context,
                    src.getX(), src.getY(), src.getZ(),
                    dst.getX(), dst.getY(), dst.getZ(),
                    true,
                    false,
                    10000,
                    !Baritone.settings().elytraPredictTerrain.value,
                    0.0D
            );
            if (segment == null) {
                throw new PathCalculationException("Path calculation failed");
            }
            return segment;
        }, this.executor);
    }

    /**
     * Performs a raytrace from the given start position to the given end position, returning {@code true} if there is
     * visibility between the two points.
     *
     * @param startX The start X coordinate
     * @param startY The start Y coordinate
     * @param startZ The start Z coordinate
     * @param endX   The end X coordinate
     * @param endY   The end Y coordinate
     * @param endZ   The end Z coordinate
     * @return {@code true} if there is visibility between the points
     */
    public boolean raytrace(final double startX, final double startY, final double startZ,
                            final double endX, final double endY, final double endZ) {
        return NetherPathfinder.isVisible(this.context, NetherPathfinder.CACHE_MISS_SOLID, startX, startY, startZ, endX, endY, endZ);
    }

    /**
     * Performs a raytrace from the given start position to the given end position, returning {@code true} if there is
     * visibility between the two points.
     *
     * @param start The starting point
     * @param end   The ending point
     * @return {@code true} if there is visibility between the points
     */
    public boolean raytrace(final Vec3 start, final Vec3 end) {
        return NetherPathfinder.isVisible(this.context, NetherPathfinder.CACHE_MISS_SOLID, start.x, start.y, start.z, end.x, end.y, end.z);
    }

    public boolean raytrace(final int count, final double[] src, final double[] dst, final int visibility) {
        switch (visibility) {
            case Visibility.ALL:
                return NetherPathfinder.isVisibleMulti(this.context, NetherPathfinder.CACHE_MISS_SOLID, count, src, dst, false) == -1;
            case Visibility.NONE:
                return NetherPathfinder.isVisibleMulti(this.context, NetherPathfinder.CACHE_MISS_SOLID, count, src, dst, true) == -1;
            case Visibility.ANY:
                return NetherPathfinder.isVisibleMulti(this.context, NetherPathfinder.CACHE_MISS_SOLID, count, src, dst, true) != -1;
            default:
                throw new IllegalArgumentException("lol");
        }
    }

    public void raytrace(final int count, final double[] src, final double[] dst, final boolean[] hitsOut, final double[] hitPosOut) {
        NetherPathfinder.raytrace(this.context, NetherPathfinder.CACHE_MISS_SOLID, count, src, dst, hitsOut, hitPosOut);
    }

    public void cancel() {
        NetherPathfinder.cancel(this.context);
    }

    public void destroy() {
        this.cancel();
        this.executor.shutdownNow();

        boolean terminated = false;
        try {
            terminated = this.executor.awaitTermination(5, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        if (terminated) {
            NetherPathfinder.freeContext(this.context);
        } else {
            System.err.println("Baritone nether cache worker did not terminate; retaining its native context");
        }
    }

    public long getSeed() {
        return this.seed;
    }

    private static void writeChunkData(long context, LevelChunk chunk) {
        try {
            LevelChunkSection[] chunkInternalStorageArray = chunk.getSections();
            boolean[] data = new boolean[NETHER_PATHFINDER_HEIGHT * 16 * 16];
            for (int y0 = 0; y0 < NETHER_PATHFINDER_HEIGHT; y0 += 16) {
                int sectionIndex = chunk.getSectionIndex(y0);
                if (sectionIndex < 0 || sectionIndex >= chunkInternalStorageArray.length) {
                    continue;
                }
                final LevelChunkSection extendedblockstorage = chunkInternalStorageArray[sectionIndex];
                if (extendedblockstorage == null) {
                    continue;
                }
                final PalettedContainer<BlockState> bsc = extendedblockstorage.getStates();
                IPalettedContainer<BlockState> iPalettedContainer = (IPalettedContainer<BlockState>) bsc;
                int airId = -1;
                if (iPalettedContainer.getPalette().maybeHas(state -> state.equals(AIR_BLOCK_STATE))) {
                    airId = iPalettedContainer.getPalette().idFor(AIR_BLOCK_STATE, PaletteResize.noResizeExpected());
                }
                // pasted from FasterWorldScanner
                final BitStorage array = iPalettedContainer.getStorage();
                if (array == null) continue;
                final long[] longArray = array.getRaw();
                final int arraySize = array.getSize();
                int bitsPerEntry = array.getBits();
                long maxEntryValue = (1L << bitsPerEntry) - 1L;

                for (int i = 0, idx = 0; i < longArray.length && idx < arraySize; ++i) {
                    long l = longArray[i];
                    for (int offset = 0; offset <= (64 - bitsPerEntry) && idx < arraySize; offset += bitsPerEntry, ++idx) {
                        int value = (int) ((l >> offset) & maxEntryValue);
                        int x = (idx & 15);
                        int y = y0 + (idx >> 8);
                        int z = ((idx >> 4) & 15);
                        data[(y << 8) | (z << 4) | x] = value != airId;
                    }
                }
            }
            NetherPathfinder.insertChunkData(context, chunk.getPos().x(), chunk.getPos().z(), data);
        } catch (Exception e) {
            e.printStackTrace();
            throw new RuntimeException(e);
        }
    }

    public static final class Visibility {

        public static final int ALL = 0;
        public static final int NONE = 1;
        public static final int ANY = 2;

        private Visibility() {}
    }

    public static boolean isSupported() {
        return NetherPathfinder.isThisSystemSupported();
    }
}
