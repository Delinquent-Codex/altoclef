package adris.altoclef.stability;

import adris.altoclef.AltoClef;
import adris.altoclef.tasksystem.Task;
import adris.altoclef.tasksystem.TaskChain;
import adris.altoclef.util.helpers.StorageHelper;
import baritone.api.behavior.IPathingBehavior;
import baritone.api.event.events.PathEvent;
import baritone.api.pathing.path.IPathExecutor;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.StringJoiner;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.client.Minecraft;
import net.minecraft.world.item.ItemStack;

public final class StabilityDiagnostics {
    private static final int SAMPLE_INTERVAL_TICKS = 20;
    private static final int HISTORY_CAPACITY = 256;
    private static final long RATE_WINDOW_NANOS = Duration.ofSeconds(30).toNanos();

    private final AltoClef mod;
    private final boolean recordingEnabled = Boolean.getBoolean("altoclef.stabilityDiagnostics");
    private final BoundedHistory<DiagnosticSample> samples = new BoundedHistory<>(HISTORY_CAPACITY);
    private final BoundedHistory<Long> pathCalculations = new BoundedHistory<>(HISTORY_CAPACITY);
    private final RollingTiming tickTimings = new RollingTiming(600);
    private final RollingTiming closeScanTimings = new RollingTiming(600);
    private long tick;
    private long lastTickNanos;
    private long lastProgressNanos = System.nanoTime();
    private String lastTaskChain = "idle";
    private String recoveryStage = "none";
    private String survivalOverride = "none";
    private String inventoryReservations = "none";
    private String recentFailure = "none";
    private int repeatedTransitions;
    private int stuckActivations;

    public StabilityDiagnostics(AltoClef mod) {
        this.mod = mod;
    }

    public void onPathEvent(PathEvent event) {
        if (event == PathEvent.CALC_STARTED || event == PathEvent.NEXT_SEGMENT_CALC_STARTED) {
            pathCalculations.add(System.nanoTime());
        }
    }

    public void tick(long tickNanos) {
        lastTickNanos = tickNanos;
        tickTimings.add(tickNanos);
        closeScanTimings.add(mod.getBlockScanner().getLastCloseScanNanos());
        tick++;
        String chain = taskChain();
        if (!chain.equals(lastTaskChain)) {
            repeatedTransitions++;
            lastTaskChain = chain;
        }
        if (recordingEnabled && tick % SAMPLE_INTERVAL_TICKS == 0) {
            samples.add(captureSample());
        }
    }

    public void markProgress() {
        lastProgressNanos = System.nanoTime();
        repeatedTransitions = 0;
    }

    public void markStuckActivation() {
        stuckActivations++;
    }

    public void setRecoveryStage(String recoveryStage) {
        this.recoveryStage = valueOrNone(recoveryStage);
    }

    public void setSurvivalOverride(String survivalOverride) {
        this.survivalOverride = valueOrNone(survivalOverride);
    }

    public void setInventoryReservations(String inventoryReservations) {
        this.inventoryReservations = valueOrNone(inventoryReservations);
    }

    public void setRecentFailure(String recentFailure) {
        this.recentFailure = valueOrNone(recentFailure);
    }

    public String getRecentFailure() {
        return recentFailure;
    }

    public void resetWorldState() {
        samples.clear();
        pathCalculations.clear();
        tickTimings.clear();
        closeScanTimings.clear();
        lastTaskChain = "idle";
        recoveryStage = "none";
        survivalOverride = "none";
        recentFailure = "none";
        repeatedTransitions = 0;
        stuckActivations = 0;
        markProgress();
    }

    public Snapshot snapshot() {
        DiagnosticSample sample = captureSample();
        RollingTiming.Stats tickStats = tickTimings.stats();
        RollingTiming.Stats scanStats = closeScanTimings.stats();
        ItemStack cursor = StorageHelper.getItemStackInCursorSlot();
        String cursorState = cursor.isEmpty() ? "empty"
                : BuiltInRegistries.ITEM.getKey(cursor.getItem()) + "=" + cursor.getCount();
        var screen = Minecraft.getInstance().gui.screen();
        return new Snapshot(
                recordingEnabled,
                sample.taskChain(),
                sample.baritoneProcess(),
                sample.goal(),
                sample.pathStatus(),
                millisSince(lastProgressNanos),
                recoveryStage,
                inventoryReservations,
                survivalOverride,
                recentFailure,
                repeatedTransitions,
                stuckActivations,
                pathCalculationsPerSecond(),
                lastTickNanos / 1_000_000.0,
                mod.getBlockScanner().getLastCloseScanNanos() / 1_000_000.0,
                mod.getBlockScanner().getLastAsyncScanNanos() / 1_000_000.0,
                mod.getBlockScanner().getLastAsyncChunksVisited(),
                mod.getBlockScanner().getLastClosePositionsVisited(),
                cursorState,
                screen == null ? "none" : screen.getClass().getSimpleName(),
                tickStats.medianNanos() / 1_000_000.0,
                tickStats.p95Nanos() / 1_000_000.0,
                scanStats.medianNanos() / 1_000_000.0,
                scanStats.p95Nanos() / 1_000_000.0,
                samples.size());
    }

    private DiagnosticSample captureSample() {
        if (!AltoClef.inGame()) {
            return new DiagnosticSample(tick, "idle", "none", "none", "idle", "not in world", "none", "none");
        }
        IPathingBehavior pathing = mod.getClientBaritone().getPathingBehavior();
        String process = mod.getClientBaritone().getPathingControlManager().mostRecentInControl()
                .map(value -> value.getClass().getSimpleName() + ":" + value.displayName())
                .orElse("none");
        String goal = pathing.getGoal() == null ? "none" : pathing.getGoal().toString();
        String pathStatus = pathStatus(pathing);
        String player = "hp=" + mod.getPlayer().getHealth()
                + ",food=" + mod.getPlayer().getFoodData().getFoodLevel()
                + ",air=" + mod.getPlayer().getAirSupply()
                + ",fire=" + mod.getPlayer().isOnFire()
                + ",fall=" + mod.getPlayer().fallDistance
                + ",hostiles=" + mod.getEntityTracker().getHostiles().size();
        return new DiagnosticSample(tick, taskChain(), process, goal, pathStatus, player, inventory(), craftingPlan());
    }

    private String taskChain() {
        TaskChain chain = mod.getTaskRunner().getCurrentTaskChain();
        if (chain == null || chain.getTasks().isEmpty()) {
            return "idle";
        }
        StringJoiner result = new StringJoiner(" > ");
        for (Task task : chain.getTasks()) {
            result.add(task.getClass().getSimpleName() + "[" + task.getDebugState() + "]");
        }
        return chain.getName() + ": " + result;
    }

    private String craftingPlan() {
        TaskChain chain = mod.getTaskRunner().getCurrentTaskChain();
        if (chain == null) return "none";
        return chain.getTasks().stream()
                .filter(task -> task.getClass().getSimpleName().contains("Craft"))
                .map(Task::toString)
                .findFirst()
                .orElse("none");
    }

    private String inventory() {
        Map<String, Integer> counts = new LinkedHashMap<>();
        for (ItemStack stack : mod.getItemStorage().getItemStacksPlayerInventory(true)) {
            if (stack.isEmpty()) continue;
            String id = BuiltInRegistries.ITEM.getKey(stack.getItem()).toString();
            counts.merge(id, stack.getCount(), Integer::sum);
        }
        StringJoiner result = new StringJoiner(",");
        counts.forEach((item, count) -> result.add(item + "=" + count));
        return result.length() == 0 ? "empty" : result.toString();
    }

    private static String pathStatus(IPathingBehavior pathing) {
        IPathExecutor current = pathing.getCurrent();
        if (current == null) {
            return pathing.getInProgress().isPresent() ? "calculating" : "none";
        }
        return (pathing.isPathing() ? "pathing" : "paused") + " "
                + current.getPosition() + "/" + current.getPath().length();
    }

    private double pathCalculationsPerSecond() {
        long now = System.nanoTime();
        List<Long> recent = new ArrayList<>(pathCalculations.snapshot());
        recent.removeIf(timestamp -> now - timestamp > RATE_WINDOW_NANOS);
        return recent.size() / 30.0;
    }

    private static long millisSince(long timestamp) {
        return Math.max(0, (System.nanoTime() - timestamp) / 1_000_000);
    }

    private static String valueOrNone(String value) {
        return value == null || value.isBlank() ? "none" : value;
    }

    public record DiagnosticSample(long tick, String taskChain, String baritoneProcess, String goal,
                                   String pathStatus, String playerState, String inventory,
                                   String craftingPlan) {
    }

    public record Snapshot(boolean recordingEnabled, String taskChain, String baritoneProcess, String goal,
                           String pathStatus, long millisSinceProgress, String recoveryStage,
                           String inventoryReservations, String survivalOverride, String recentFailure,
                           int repeatedTransitions, int stuckActivations, double pathCalculationsPerSecond,
                           double tickMillis, double closeScanMillis, double asyncScanMillis,
                           int asyncChunks, int closePositions, String cursorState, String screenType,
                           double tickMedianMillis, double tickP95Millis,
                           double scanMedianMillis, double scanP95Millis, int recordedSamples) {
        public List<String> conciseLines() {
            return List.of(
                    "task=" + taskChain + " | process=" + baritoneProcess + " | goal=" + goal + " | path=" + pathStatus,
                    "progress=" + millisSinceProgress + "ms | recovery=" + recoveryStage + " | survival=" + survivalOverride
                            + " | cursor=" + cursorState + " | screen=" + screenType
                            + " | reservations=" + inventoryReservations + " | failure=" + recentFailure,
                    String.format("transitions=%d | stuck=%d | pathCalc=%.2f/s | tick=%.2fms med/p95=%.2f/%.2f | scan=%.2fms med/p95=%.2f/%.2f (%d cells, %.2fms/%d chunks) | recorder=%s(%d/%d)",
                            repeatedTransitions, stuckActivations, pathCalculationsPerSecond, tickMillis,
                            tickMedianMillis, tickP95Millis, closeScanMillis, scanMedianMillis, scanP95Millis,
                            closePositions, asyncScanMillis, asyncChunks, recordingEnabled ? "on" : "off",
                            recordedSamples, HISTORY_CAPACITY));
        }
    }
}
