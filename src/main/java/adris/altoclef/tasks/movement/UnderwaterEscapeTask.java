package adris.altoclef.tasks.movement;

import adris.altoclef.AltoClef;
import adris.altoclef.stability.WaterEscapePlanner;
import adris.altoclef.tasks.construction.DestroyBlockTask;
import adris.altoclef.tasksystem.Task;
import adris.altoclef.util.helpers.LookHelper;
import adris.altoclef.util.helpers.WorldHelper;
import baritone.api.utils.input.Input;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

public class UnderwaterEscapeTask extends Task {
    private static final int SEARCH_DEPTH = 8;
    private static final int FAILED_EXIT_COOLDOWN_TICKS = 200;
    private static final int STAGNANT_EXIT_TICKS = 60;

    private final Map<BlockPos, Long> failedExits = new HashMap<>();
    private BlockPos airTarget;
    private BlockPos lastPlayerPosition;
    private int bestAir;
    private int stagnantTicks;
    private long tick;

    @Override
    protected void onStart() {
        AltoClef mod = AltoClef.getInstance();
        airTarget = null;
        lastPlayerPosition = mod.getPlayer().blockPosition();
        bestAir = mod.getPlayer().getAirSupply();
        stagnantTicks = 0;
    }

    @Override
    protected Task onTick() {
        AltoClef mod = AltoClef.getInstance();
        tick++;
        failedExits.entrySet().removeIf(entry -> entry.getValue() <= tick);

        if (isFinished()) {
            mod.getInputControls().release(Input.JUMP);
            return null;
        }

        BlockPos playerPos = mod.getPlayer().blockPosition();
        int air = mod.getPlayer().getAirSupply();
        if (!playerPos.equals(lastPlayerPosition) || air > bestAir) {
            lastPlayerPosition = playerPos;
            bestAir = Math.max(bestAir, air);
            stagnantTicks = 0;
            mod.getStabilityDiagnostics().markProgress();
        } else {
            stagnantTicks++;
        }

        if (airTarget == null || failedExits.containsKey(airTarget) || !isAir(mod, airTarget)) {
            airTarget = findAir(mod, playerPos).orElse(null);
            stagnantTicks = 0;
        }

        if (airTarget != null) {
            setDebugState("Escaping toward air at " + airTarget.toShortString());
            LookHelper.lookAt(mod, Vec3.atCenterOf(airTarget));
            if (airTarget.getY() >= playerPos.getY()) {
                mod.getInputControls().hold(Input.JUMP);
            }
            if (stagnantTicks >= STAGNANT_EXIT_TICKS) {
                failedExits.put(airTarget, tick + FAILED_EXIT_COOLDOWN_TICKS);
                mod.getStabilityDiagnostics().setRecentFailure("water exit stalled at " + airTarget.toShortString());
                mod.getClientBaritone().getPathingBehavior().forceCancel();
                airTarget = null;
                stagnantTicks = 0;
                return null;
            }
            return new GetToBlockTask(airTarget);
        }

        mod.getInputControls().hold(Input.JUMP);
        BlockPos breakTarget = findSafeBreakTarget(mod, playerPos);
        if (breakTarget != null) {
            setDebugState("Breaking underwater escape at " + breakTarget.toShortString());
            return new DestroyBlockTask(breakTarget);
        }
        setDebugState("Swimming vertically while searching for air");
        return null;
    }

    private Optional<BlockPos> findAir(AltoClef mod, BlockPos start) {
        WaterEscapePlanner.GridPos gridStart = toGrid(start);
        return WaterEscapePlanner.nearestAir(gridStart, SEARCH_DEPTH,
                        pos -> cellAt(mod, toBlock(pos)), pos -> failedExits.containsKey(toBlock(pos)))
                .map(UnderwaterEscapeTask::toBlock);
    }

    private static WaterEscapePlanner.Cell cellAt(AltoClef mod, BlockPos pos) {
        BlockState state = mod.getWorld().getBlockState(pos);
        if (state.isAir()) return WaterEscapePlanner.Cell.AIR;
        if (!state.getFluidState().isEmpty()) return WaterEscapePlanner.Cell.WATER;
        return WaterEscapePlanner.Cell.BLOCKED;
    }

    private static BlockPos findSafeBreakTarget(AltoClef mod, BlockPos playerPos) {
        BlockPos[] candidates = {
                playerPos.above(2), playerPos.above(),
                playerPos.above().north(), playerPos.above().south(),
                playerPos.above().east(), playerPos.above().west()
        };
        for (BlockPos candidate : candidates) {
            BlockState state = mod.getWorld().getBlockState(candidate);
            if (!state.isAir() && state.getFluidState().isEmpty() && WorldHelper.canBreak(candidate)) {
                return candidate;
            }
        }
        return null;
    }

    private static boolean isAir(AltoClef mod, BlockPos pos) {
        return mod.getWorld().getBlockState(pos).isAir();
    }

    private static WaterEscapePlanner.GridPos toGrid(BlockPos pos) {
        return new WaterEscapePlanner.GridPos(pos.getX(), pos.getY(), pos.getZ());
    }

    private static BlockPos toBlock(WaterEscapePlanner.GridPos pos) {
        return new BlockPos(pos.x(), pos.y(), pos.z());
    }

    @Override
    protected void onStop(Task interruptTask) {
        AltoClef.getInstance().getInputControls().release(Input.JUMP);
    }

    @Override
    protected boolean isEqual(Task other) {
        return other instanceof UnderwaterEscapeTask;
    }

    @Override
    protected String toDebugString() {
        return "Underwater escape";
    }

    @Override
    public boolean isFinished() {
        AltoClef mod = AltoClef.getInstance();
        return !mod.getPlayer().isUnderWater()
                && mod.getPlayer().getAirSupply() >= mod.getPlayer().getMaxAirSupply();
    }
}
