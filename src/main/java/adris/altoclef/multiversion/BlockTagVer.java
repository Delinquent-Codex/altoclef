package adris.altoclef.multiversion;

import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.block.Block;

public class BlockTagVer {


    public static boolean isWool(Block block) {
        return block.defaultBlockState().is(BlockTags.WOOL);
    }

}
