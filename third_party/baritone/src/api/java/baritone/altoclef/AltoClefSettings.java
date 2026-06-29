package baritone.altoclef;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.BiFunction;
import java.util.function.BiPredicate;
import java.util.function.Predicate;

public final class AltoClefSettings {
    private static final AltoClefSettings INSTANCE = new AltoClefSettings();

    private final Object breakMutex = new Object();
    private final Object placeMutex = new Object();
    private final Object propertiesMutex = new Object();
    private final Object globalHeuristicMutex = new Object();

    private final Set<BlockPos> blocksToAvoidBreaking = new HashSet<>();
    private final List<Predicate<BlockPos>> breakAvoiders = new ArrayList<>();
    private final List<Predicate<BlockPos>> placeAvoiders = new ArrayList<>();
    private final Set<Item> protectedItems = new HashSet<>();
    private final List<Predicate<BlockPos>> forceWalkOnPredicates = new ArrayList<>();
    private final List<Predicate<BlockPos>> forceAvoidWalkThroughPredicates = new ArrayList<>();
    private final List<BiPredicate<BlockState, ItemStack>> forceUseToolPredicates = new ArrayList<>();
    private final List<BiPredicate<BlockState, ItemStack>> forceSaveToolPredicates = new ArrayList<>();
    private final List<BiFunction<Double, BlockPos, Double>> globalHeuristics = new ArrayList<>();
    private final List<Predicate<Entity>> projectileDodgeAvoiders = new ArrayList<>();

    private volatile boolean interactionPaused;
    private volatile boolean flowingWaterPassAllowed;
    private volatile boolean swimThroughLavaAllowed;
    private volatile boolean canWalkOnEndPortal;
    private volatile boolean placeBucketButDontFall;

    private AltoClefSettings() {
    }

    public static AltoClefSettings getInstance() {
        return INSTANCE;
    }

    public Object getBreakMutex() {
        return breakMutex;
    }

    public Object getPlaceMutex() {
        return placeMutex;
    }

    public Object getPropertiesMutex() {
        return propertiesMutex;
    }

    public Object getGlobalHeuristicMutex() {
        return globalHeuristicMutex;
    }

    public Set<BlockPos> getBlocksToAvoidBreaking() {
        return blocksToAvoidBreaking;
    }

    public List<Predicate<BlockPos>> getBreakAvoiders() {
        return breakAvoiders;
    }

    public List<Predicate<BlockPos>> getPlaceAvoiders() {
        return placeAvoiders;
    }

    public Set<Item> getProtectedItems() {
        return protectedItems;
    }

    public List<Predicate<BlockPos>> getForceWalkOnPredicates() {
        return forceWalkOnPredicates;
    }

    public List<Predicate<BlockPos>> getForceAvoidWalkThroughPredicates() {
        return forceAvoidWalkThroughPredicates;
    }

    public List<BiPredicate<BlockState, ItemStack>> getForceUseToolPredicates() {
        return forceUseToolPredicates;
    }

    public List<BiPredicate<BlockState, ItemStack>> getForceSaveToolPredicates() {
        return forceSaveToolPredicates;
    }

    public List<BiFunction<Double, BlockPos, Double>> getGlobalHeuristics() {
        return globalHeuristics;
    }

    public List<Predicate<Entity>> getProjectileDodgeAvoiders() {
        return projectileDodgeAvoiders;
    }

    public void avoidBlockBreak(Predicate<BlockPos> predicate) {
        synchronized (breakMutex) {
            breakAvoiders.add(predicate);
        }
    }

    public void avoidBlockPlace(Predicate<BlockPos> predicate) {
        synchronized (placeMutex) {
            placeAvoiders.add(predicate);
        }
    }

    public boolean shouldAvoidBreaking(BlockPos pos) {
        synchronized (breakMutex) {
            if (blocksToAvoidBreaking.contains(pos)) {
                return true;
            }
            return breakAvoiders.stream().anyMatch(predicate -> predicate.test(pos));
        }
    }

    public boolean shouldAvoidPlacingAt(BlockPos pos) {
        synchronized (placeMutex) {
            return placeAvoiders.stream().anyMatch(predicate -> predicate.test(pos));
        }
    }

    public boolean isInteractionPaused() {
        return interactionPaused;
    }

    public void setInteractionPaused(boolean interactionPaused) {
        this.interactionPaused = interactionPaused;
    }

    public boolean isFlowingWaterPassAllowed() {
        return flowingWaterPassAllowed;
    }

    public void setFlowingWaterPass(boolean flowingWaterPassAllowed) {
        this.flowingWaterPassAllowed = flowingWaterPassAllowed;
    }

    public boolean isSwimThroughLavaAllowed() {
        return swimThroughLavaAllowed;
    }

    public void allowSwimThroughLava(boolean swimThroughLavaAllowed) {
        this.swimThroughLavaAllowed = swimThroughLavaAllowed;
    }

    public boolean isCanWalkOnEndPortal() {
        return canWalkOnEndPortal;
    }

    public void canWalkOnEndPortal(boolean canWalkOnEndPortal) {
        this.canWalkOnEndPortal = canWalkOnEndPortal;
    }

    public boolean shouldPlaceBucketButDontFall() {
        return placeBucketButDontFall;
    }

    public void configurePlaceBucketButDontFall(boolean placeBucketButDontFall) {
        this.placeBucketButDontFall = placeBucketButDontFall;
    }
}
