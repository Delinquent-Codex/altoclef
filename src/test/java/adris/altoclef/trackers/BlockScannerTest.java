package adris.altoclef.trackers;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.HashMap;
import java.util.HashSet;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Block;
import org.junit.jupiter.api.Test;

class BlockScannerTest {
    @Test
    void publishedSnapshotIsIndependentFromLiveMapMutations() {
        HashMap<Block, HashSet<BlockPos>> live = new HashMap<>();
        live.put(null, new HashSet<>(java.util.Set.of(new BlockPos(1, 2, 3))));

        HashMap<Block, HashSet<BlockPos>> snapshot = BlockScanner.snapshotBlockMap(live);
        live.get(null).add(new BlockPos(4, 5, 6));
        live.clear();

        assertEquals(java.util.Set.of(new BlockPos(1, 2, 3)), snapshot.get(null));
    }
}
