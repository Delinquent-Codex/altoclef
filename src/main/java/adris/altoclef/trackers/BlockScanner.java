package adris.altoclef.trackers;

import adris.altoclef.AltoClef;
import adris.altoclef.Debug;
import adris.altoclef.eventbus.EventBus;
import adris.altoclef.eventbus.events.BlockPlaceEvent;
import adris.altoclef.multiversion.blockpos.BlockPosVer;
import adris.altoclef.trackers.blacklisting.WorldLocateBlacklist;
import adris.altoclef.util.Dimension;
import adris.altoclef.util.helpers.BaritoneHelper;
import adris.altoclef.util.helpers.WorldHelper;
import adris.altoclef.util.time.TimerGame;
import adris.altoclef.stability.OperationBudget;
import java.util.*;
import java.util.function.Predicate;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.phys.Vec3;

public class BlockScanner {

    private static final boolean LOG = false;
    private static final int RESCAN_TICK_DELAY = 4 * 20;
    private static final int CACHED_POSITIONS_PER_BLOCK = 40;
    private static final int CLOSE_RADIUS = 8;
    private static final int CLOSE_HEIGHT = 16;
    private static final int CLOSE_DIAMETER = CLOSE_RADIUS * 2 + 1;
    private static final int CLOSE_SCAN_SIZE = CLOSE_DIAMETER * CLOSE_DIAMETER * CLOSE_HEIGHT;
    private static final int CLOSE_SCAN_BUDGET = 1024;
    private static final int ASYNC_SCAN_CHUNK_BUDGET = 64;
    private static final int ASYNC_SCAN_VISIT_BUDGET = 512;
    private static final int ASYNC_SCAN_RADIUS = 12;


    private final AltoClef mod;
    private final TimerGame rescanTimer = new TimerGame(1);

    private final HashMap<Block, HashSet<BlockPos>> trackedBlocks = new HashMap<>();
    private HashMap<Block, HashSet<BlockPos>> scannedBlocks = new HashMap<>();
    private HashMap<ChunkPos, Long> scannedChunks = new HashMap<>();
    private final WorldLocateBlacklist blacklist = new WorldLocateBlacklist();
    // used while scanning
    private HashMap<Block, HashSet<BlockPos>> cachedScannedBlocks = new HashMap<>();
    private Dimension scanDimension = Dimension.OVERWORLD;
    private Level scanWorld = null;

    private boolean scanning = false;
    private volatile long scanGeneration;
    private volatile long lastCloseScanNanos;
    private volatile long lastAsyncScanNanos;
    private volatile int lastAsyncChunksVisited;
    private volatile int lastClosePositionsVisited;
    private HashMap<Block, HashSet<BlockPos>> closePublishedBlocks = new HashMap<>();
    private HashMap<Block, HashSet<BlockPos>> closeWorkingBlocks = new HashMap<>();
    private BlockPos closeScanOrigin;
    private Level closeScanWorld;
    private int closeScanCursor;
    private Thread scanThread;


    public BlockScanner(AltoClef mod) {
        this.mod = mod;

        EventBus.subscribe(BlockPlaceEvent.class, evt -> addBlock(evt.blockState.getBlock(), evt.blockPos));
    }


    public void addBlock(Block block, BlockPos pos) {
        if (!isBlockAtPosition(pos, block)) {
            Debug.logInternal("INVALID SET: " + block + " " + pos);
            return;
        }

        if (trackedBlocks.containsKey(block)) {
            trackedBlocks.get(block).add(pos);
        } else {
            HashSet<BlockPos> set = new HashSet<>();
            set.add(pos);

            trackedBlocks.put(block, set);
        }
        closePublishedBlocks.computeIfAbsent(block, ignored -> new HashSet<>()).add(pos);
    }


    public void requestBlockUnreachable(BlockPos pos, int allowedFailures) {
        blacklist.blackListItem(mod, pos, allowedFailures);
    }

    //TODO replace four with config
    public void requestBlockUnreachable(BlockPos pos) {
        blacklist.blackListItem(mod, pos, 4);
    }


    public boolean isUnreachable(BlockPos pos) {
        return blacklist.unreachable(pos);
    }

    public void clearTemporaryUnreachable() {
        blacklist.clear();
    }

    public void requestRescan() {
        rescanTimer.forceElapse();
    }

    public List<BlockPos> getKnownLocations(Block... blocks) {
        List<BlockPos> locations = new LinkedList<>();

        for (Block block : blocks) {
            if (!trackedBlocks.containsKey(block)) continue;

            locations.addAll(trackedBlocks.get(block));
        }
        locations.removeIf(this::isUnreachable);

        return locations;
    }

    /**
     * Scans a radius for the closest block of a given type .
     *
     * @param pos    The center of this radius
     * @param range  Radius to scan for
     * @param blocks What blocks to check for
     */
    public Optional<BlockPos> getNearestWithinRange(Vec3 pos, double range, Block... blocks) {
        Optional<BlockPos> nearest = getNearestBlock(pos, blocks);

        if (nearest.isEmpty() || nearest.get().closerToCenterThan(pos, range)) return nearest;

        return Optional.empty();
    }

    public Optional<BlockPos> getNearestWithinRange(BlockPos pos, double range, Block... blocks) {
        return getNearestWithinRange(new Vec3(pos.getX(), pos.getY(), pos.getZ()), range, blocks);
    }


    public boolean anyFound(Block... blocks) {
        return anyFound((block) -> true, blocks);
    }


    public boolean anyFound(Predicate<BlockPos> isValidTest, Block... blocks) {
        for (Block block : blocks) {
            if (!trackedBlocks.containsKey(block)) continue;

            for (BlockPos pos : trackedBlocks.get(block)) {
                if (isValidTest.test(pos) && mod.getWorld().getBlockState(pos).getBlock().equals(block) && !this.isUnreachable(pos))
                    return true;
            }
        }

        return false;
    }

    public Optional<BlockPos> getNearestBlock(Block... blocks) {
        // Add juuust a little, to prevent digging down all the time/bias towards blocks BELOW the player
        return getNearestBlock(mod.getPlayer().position().add(0, 0.6f, 0), blocks);
    }

    public Optional<BlockPos> getNearestBlock(Vec3 pos, Block... blocks) {
        return getNearestBlock(pos, p -> true, blocks);
    }

    public Optional<BlockPos> getNearestBlock(Predicate<BlockPos> isValidTest, Block... blocks) {
        return getNearestBlock(mod.getPlayer().position().add(0, 0.6f, 0), isValidTest, blocks);
    }

    public Optional<BlockPos> getNearestBlock(Vec3 pos, Predicate<BlockPos> isValidTest, Block... blocks) {
        Optional<BlockPos> closest = Optional.empty();

        for (Block block : blocks) {
            Optional<BlockPos> p = getNearestBlock(block, isValidTest, pos);

            if (p.isPresent()) {
                if (closest.isEmpty()) closest = p;
                else {
                    if (BaritoneHelper.calculateGenericHeuristic(pos, WorldHelper.toVec3d(closest.get())) > BaritoneHelper.calculateGenericHeuristic(pos, WorldHelper.toVec3d(p.get()))) {
                        closest = p;
                    }
                }
            }
        }

        return closest;
    }

    public Optional<BlockPos> getNearestBlock(Block block, Vec3 fromPos) {
        return getNearestBlock(block, (pos) -> true, fromPos);
    }

    public Optional<BlockPos> getNearestBlock(Block block, Predicate<BlockPos> isValidTest, Vec3 fromPos) {
        BlockPos pos = null;
        double nearest = Double.POSITIVE_INFINITY;

        if (!trackedBlocks.containsKey(block)) {
            return Optional.empty();
        }

        for (BlockPos p : trackedBlocks.get(block)) {
            //ensure the block is there (can change upon rescan)
            if (!mod.getWorld().getBlockState(p).getBlock().equals(block)) continue;
            if (!isValidTest.test(p) || isUnreachable(p)) continue;

            double dist = BaritoneHelper.calculateGenericHeuristic(fromPos, WorldHelper.toVec3d(p));

            if (dist < nearest) {
                nearest = dist;
                pos = p;
            }
        }

        return pos != null ? Optional.of(pos) : Optional.empty();
    }

    public boolean anyFoundWithinDistance(double distance, Block... blocks) {
        return anyFoundWithinDistance(mod.getPlayer().position().add(0, 0.6f, 0), distance, blocks);
    }

    public boolean anyFoundWithinDistance(Vec3 pos, double distance, Block... blocks) {
        Optional<BlockPos> blockPos = getNearestBlock(blocks);
        return blockPos.map(value -> value.closerToCenterThan(pos, distance)).orElse(false);
    }

    public double distanceToClosest(Block... blocks) {
        return distanceToClosest(mod.getPlayer().position().add(0, 0.6f, 0), blocks);
    }

    public double distanceToClosest(Vec3 pos, Block... blocks) {
        Optional<BlockPos> blockPos = getNearestBlock(blocks);
        return blockPos.map(value ->  Math.sqrt(BlockPosVer.getSquaredDistance(value, pos))).orElse(Double.POSITIVE_INFINITY);
    }

    // Checks if 'pos' one of 'blocks' block
    // Returns false if incorrect or undetermined/unsure
    public boolean isBlockAtPosition(BlockPos pos, Block... blocks) {
        if (isUnreachable(pos)) {
            return false;
        }

        if (!mod.getChunkTracker().isChunkLoaded(pos)) {
            return false;
        }

        ClientLevel world = Minecraft.getInstance().level;
        if (world == null) {
            return false;
        }
        try {
            for (Block block : blocks) {
                if (world.isEmptyBlock(pos) && WorldHelper.isAir(block)) {
                    return true;
                }
                BlockState state = world.getBlockState(pos);
                if (state.getBlock() == block) {
                    return true;
                }
            }
            return false;
        } catch (NullPointerException e) {
            // Probably out of chunk. This means we can't judge its state.
            return false;
        }
    }

    public void reset() {
        scanGeneration++;
        if (scanThread != null) {
            scanThread.interrupt();
            scanThread = null;
        }
        scanning = false;
        trackedBlocks.clear();
        scannedBlocks.clear();
        scannedChunks.clear();
        cachedScannedBlocks.clear();
        closePublishedBlocks.clear();
        closeWorkingBlocks.clear();
        closeScanOrigin = null;
        closeScanWorld = null;
        closeScanCursor = 0;
        rescanTimer.forceElapse();
        blacklist.clear();
    }

    public void tick() {
        if (mod.getWorld() == null || mod.getPlayer() == null) return;
        //be maximally aware of the closest blocks around you
        long closeScanStart = System.nanoTime();
        scanCloseBlocks();
        lastCloseScanNanos = System.nanoTime() - closeScanStart;
        if (!rescanTimer.elapsed() || scanning) return;

        if (scanDimension != WorldHelper.getCurrentDimension() || mod.getWorld() != scanWorld) {
            if (LOG) {
                mod.log("BlockScanner: new dimension or world detected, resetting data!");
            }
            reset();
            scanWorld = mod.getWorld();
            scanDimension = WorldHelper.getCurrentDimension();
            return;
        }

        cachedScannedBlocks = new HashMap<>(scannedBlocks.size());
        for (Map.Entry<Block, HashSet<BlockPos>> entry : scannedBlocks.entrySet()) {
            cachedScannedBlocks.put(entry.getKey(), new HashSet<>(entry.getValue()));
        }

        if (LOG) {
            mod.log("Updating BlockScanner.. size: " + trackedBlocks.size() + " : " + cachedScannedBlocks.size());
        }

        ClientLevel world = mod.getWorld();
        ChunkPos playerChunkPos = mod.getPlayer().chunkPosition();
        Vec3 playerPos = mod.getPlayer().position();
        HashMap<Block, HashSet<BlockPos>> workingScannedBlocks = copyScannedBlocks(scannedBlocks);
        HashMap<ChunkPos, Long> workingScannedChunks = new HashMap<>(scannedChunks);
        long generation = ++scanGeneration;

        scanning = true;
        scanThread = new Thread(() -> {
            boolean completed = false;
            try {
                completed = rescan(world, playerChunkPos, playerPos, workingScannedBlocks, workingScannedChunks,
                        ASYNC_SCAN_CHUNK_BUDGET, ASYNC_SCAN_RADIUS, generation);
            } catch (Exception e) {
                if (generation == scanGeneration) {
                    e.printStackTrace();
                }
            } finally {
                boolean publish = completed;
                Minecraft.getInstance().execute(() -> finishScan(world, workingScannedBlocks, workingScannedChunks, generation, publish));
            }
        }, "AltoClef Block Scanner");
        scanThread.setDaemon(true);
        scanThread.start();
    }

    private void scanCloseBlocks() {
        trackedBlocks.clear();
        for (Map.Entry<Block, HashSet<BlockPos>> entry : cachedScannedBlocks.entrySet()) {
            trackedBlocks.computeIfAbsent(entry.getKey(), ignored -> new HashSet<>()).addAll(entry.getValue());
        }
        for (Map.Entry<Block, HashSet<BlockPos>> entry : closePublishedBlocks.entrySet()) {
            trackedBlocks.computeIfAbsent(entry.getKey(), ignored -> new HashSet<>()).addAll(entry.getValue());
        }

        BlockPos origin = mod.getPlayer().blockPosition();
        Level world = mod.getPlayer().level();
        if (closeScanWorld != world || closeScanOrigin == null || !closeScanOrigin.equals(origin)) {
            closeScanWorld = world;
            closeScanOrigin = origin;
            closeWorkingBlocks = new HashMap<>();
            closeScanCursor = 0;
        }

        OperationBudget budget = new OperationBudget(CLOSE_SCAN_BUDGET);
        while (closeScanCursor < CLOSE_SCAN_SIZE && budget.tryAcquire()) {
            int index = closeScanCursor++;
            int x = index % CLOSE_DIAMETER;
            index /= CLOSE_DIAMETER;
            int z = index % CLOSE_DIAMETER;
            int y = index / CLOSE_DIAMETER;
            BlockPos position = origin.offset(x - CLOSE_RADIUS, y - CLOSE_RADIUS, z - CLOSE_RADIUS);
            BlockState state = world.getBlockState(position);
            if (!state.isAir()) {
                closeWorkingBlocks.computeIfAbsent(state.getBlock(), ignored -> new HashSet<>()).add(position);
            }
        }
        lastClosePositionsVisited = budget.used();
        if (closeScanCursor >= CLOSE_SCAN_SIZE) {
            closePublishedBlocks = closeWorkingBlocks;
            closeWorkingBlocks = new HashMap<>();
            closeScanCursor = 0;
            for (Map.Entry<Block, HashSet<BlockPos>> entry : closePublishedBlocks.entrySet()) {
                trackedBlocks.computeIfAbsent(entry.getKey(), ignored -> new HashSet<>()).addAll(entry.getValue());
            }
        }
    }

    private boolean rescan(ClientLevel world, ChunkPos playerChunkPos, Vec3 playerPos,
                           HashMap<Block, HashSet<BlockPos>> workingScannedBlocks,
                           HashMap<ChunkPos, Long> workingScannedChunks,
                           int maxCount, int cutOffRadius, long generation) {
        long ms = System.currentTimeMillis();
        long scanStart = System.nanoTime();

        HashSet<ChunkPos> visited = new HashSet<>();
        Queue<Node> queue = new ArrayDeque<>();
        queue.add(new Node(playerChunkPos, 0));

        int scannedCount = 0;
        while (!queue.isEmpty() && scannedCount < maxCount && visited.size() < ASYNC_SCAN_VISIT_BUDGET
                && isScanCurrent(world, generation)) {
            Node node = queue.poll();

            if (node.distance > cutOffRadius || visited.contains(node.pos) || !world.getChunkSource().hasChunk(node.pos.x(), node.pos.z()))
                continue;

            visited.add(node.pos);
            queue.add(new Node(new ChunkPos(node.pos.x() + 1, node.pos.z()), node.distance + 1));
            queue.add(new Node(new ChunkPos(node.pos.x() - 1, node.pos.z()), node.distance + 1));
            queue.add(new Node(new ChunkPos(node.pos.x(), node.pos.z() + 1), node.distance + 1));
            queue.add(new Node(new ChunkPos(node.pos.x(), node.pos.z() - 1), node.distance + 1));

            boolean isPriorityChunk = getChunkDist(node.pos, playerChunkPos) <= 2;
            if (!isPriorityChunk && workingScannedChunks.containsKey(node.pos) && world.getGameTime() - workingScannedChunks.get(node.pos) < RESCAN_TICK_DELAY)
                continue;

            if (!scanChunk(world, node.pos, playerChunkPos, workingScannedBlocks, workingScannedChunks, generation)) {
                return false;
            }
            scannedCount++;
        }
        if (!isScanCurrent(world, generation)) {
            return false;
        }

        lastAsyncChunksVisited = scannedCount;
        lastAsyncScanNanos = System.nanoTime() - scanStart;

        for (Iterator<ChunkPos> iterator = workingScannedChunks.keySet().iterator(); iterator.hasNext(); ) {
            ChunkPos pos = iterator.next();
            int distance = getChunkDist(pos, playerChunkPos);

            if (distance > cutOffRadius) {
                iterator.remove();
            }
        }

        for (HashSet<BlockPos> set : workingScannedBlocks.values()) {
            if (set.size() < CACHED_POSITIONS_PER_BLOCK) {
                continue;
            }

            getFirstFewPositions(set, playerPos);
        }

        if (LOG) {
            mod.log("Rescanned in: " + (System.currentTimeMillis() - ms) + " ms; visited: " + visited.size() + " chunks");
        }
        return true;
    }

    private int getChunkDist(ChunkPos pos1, ChunkPos pos2) {
        return Math.abs(pos1.x() - pos2.x()) + Math.abs(pos1.z() - pos2.z());
    }


    //TODO rename
    private void getFirstFewPositions(HashSet<BlockPos> set, Vec3 playerPos) {
        Queue<BlockPos> queue = new PriorityQueue<>(Comparator.comparingDouble((pos) -> -BaritoneHelper.calculateGenericHeuristic(playerPos, WorldHelper.toVec3d(pos))));

        for (BlockPos pos : set) {
            queue.add(pos);

            if (queue.size() > CACHED_POSITIONS_PER_BLOCK) {
                queue.poll();
            }
        }

        set.clear();

        for (int i = 0; i < CACHED_POSITIONS_PER_BLOCK && !queue.isEmpty(); i++) {
            set.add(queue.poll());
        }
    }

    /**
     * scans a chunk and adds block positions corresponding to a specific block in a list
     *
     * @param chunkPos position of the scanned chunk
     */
    private boolean scanChunk(ClientLevel world, ChunkPos chunkPos, ChunkPos playerChunkPos,
                              HashMap<Block, HashSet<BlockPos>> workingScannedBlocks,
                              HashMap<ChunkPos, Long> workingScannedChunks, long generation) {
        LevelChunk chunk = world.getChunk(chunkPos.x(), chunkPos.z());
        workingScannedChunks.put(chunkPos, world.getGameTime());

        boolean isPriorityChunk = getChunkDist(chunkPos, playerChunkPos) <= 2;

        for (int x = chunkPos.getMinBlockX(); x <= chunkPos.getMaxBlockX(); x++) {
            for (int y = world.getMinY(); y < world.getMaxY(); y++) {
                if (!isScanCurrent(world, generation)) {
                    return false;
                }
                for (int z = chunkPos.getMinBlockZ(); z <= chunkPos.getMaxBlockZ(); z++) {
                    BlockPos p = new BlockPos(x, y, z);
                    if (this.isUnreachable(p) || world.isOutsideBuildHeight(p)) continue;

                    BlockState state = chunk.getBlockState(p);
                    if (state.isAir()) continue;

                    Block block = state.getBlock();
                    if (workingScannedBlocks.containsKey(block)) {
                        HashSet<BlockPos> set = workingScannedBlocks.get(block);

                        if ((set.size() > CACHED_POSITIONS_PER_BLOCK * 750 && !isPriorityChunk)) continue;

                        set.add(p);
                    } else {
                        HashSet<BlockPos> set = new HashSet<>();
                        set.add(p);
                        workingScannedBlocks.put(block, set);
                    }
                }
            }
        }
        return true;
    }

    private boolean isScanCurrent(ClientLevel world, long generation) {
        return !Thread.currentThread().isInterrupted() && generation == scanGeneration && mod.getWorld() == world;
    }

    private void finishScan(ClientLevel world, HashMap<Block, HashSet<BlockPos>> workingScannedBlocks,
                            HashMap<ChunkPos, Long> workingScannedChunks, long generation, boolean publish) {
        if (generation != scanGeneration) {
            return;
        }
        if (mod.getWorld() != world) {
            scanThread = null;
            scanning = false;
            return;
        }
        if (publish) {
            scannedBlocks = workingScannedBlocks;
            scannedChunks = workingScannedChunks;
        }
        scanThread = null;
        scanning = false;
        rescanTimer.reset();
    }

    private static HashMap<Block, HashSet<BlockPos>> copyScannedBlocks(Map<Block, HashSet<BlockPos>> source) {
        HashMap<Block, HashSet<BlockPos>> copy = new HashMap<>(source.size());
        for (Map.Entry<Block, HashSet<BlockPos>> entry : source.entrySet()) {
            copy.put(entry.getKey(), new HashSet<>(entry.getValue()));
        }
        return copy;
    }

    public long getLastCloseScanNanos() {
        return lastCloseScanNanos;
    }

    public long getLastAsyncScanNanos() {
        return lastAsyncScanNanos;
    }

    public int getLastAsyncChunksVisited() {
        return lastAsyncChunksVisited;
    }

    public int getLastClosePositionsVisited() {
        return lastClosePositionsVisited;
    }

    public boolean isScanning() {
        return scanning;
    }

    private record Node(ChunkPos pos, int distance) {
    }


}
