package adris.altoclef.multiversion.entity;

import adris.altoclef.multiversion.Pattern;
import net.fabricmc.loader.impl.lib.sat4j.core.Vec;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.phys.Vec3;
import adris.altoclef.mixins.EntityAccessor;

public class EntityVer {


    @Pattern
    public boolean isInNetherPortal(Entity entity) {
        return adris.altoclef.multiversion.entity.EntityHelper.isInNetherPortal(entity);
    }

    @Pattern
    public int getPortalCooldown(Entity entity) {
        return entity.getPortalCooldown();
    }


    @Pattern
    public BlockPos getLandingPos(Entity entity) {
        return entity.getOnPos();
    }

    @Pattern
    private static float getPitch(Entity player) {
        return player.getXRot();
    }

    @Pattern
    private static float getYaw(Entity player) {
        return player.getYRot();
    }

    @Pattern
    private static void setPitch(Entity player, float value) {
        player.setXRot(value);
    }

    @Pattern
    private static void setYaw(Entity player, float value) {
        player.setYRot(value);
    }

    @Pattern
    private static Vec3 getEyePos(Entity entity) {
        return entity.getEyePosition();
    }

    @Pattern
    private static ChunkPos getChunkPos(Entity entity) {
        return entity.chunkPosition();
    }

    @Pattern
    private static int getBlockX(Entity entity) {
        return entity.getBlockX();
    }

    @Pattern
    private static int getBlockY(Entity entity) {
        return entity.getBlockY();
    }

    @Pattern
    private static int getBlockZ(Entity entity) {
        return entity.getBlockZ();
    }

}
