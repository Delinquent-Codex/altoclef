package adris.altoclef.dev;

import adris.altoclef.AltoClef;
import baritone.Baritone;
import baritone.utils.RenderDiagnostics;
import net.minecraft.client.Minecraft;

/** Development-only repeated-world renderer regression harness. */
public final class RenderLifecycleRegressionHarness {

    private static final String ENABLE_PROPERTY = "altoclef.renderRegression";

    private final AltoClef mod;
    private final Minecraft minecraft = Minecraft.getInstance();
    private final String worldName = System.getProperty("altoclef.renderRegression.world", "New World");
    private final String command = System.getProperty("altoclef.renderRegression.command", "gamer");
    private final int targetCycles = Integer.getInteger("altoclef.renderRegression.cycles", 10);
    private final long activeNanos = Integer.getInteger("altoclef.renderRegression.activeSeconds", 90) * 1_000_000_000L;
    private final boolean renderVisuals = Boolean.parseBoolean(System.getProperty("altoclef.renderRegression.visuals", "true"));
    private final boolean renderPath = Boolean.parseBoolean(System.getProperty("altoclef.renderRegression.renderPath", "true"));

    private Phase phase = Phase.OPENING;
    private Object activeWorld;
    private long phaseStartedNanos;
    private int completedCycles;
    private boolean commandStarted;
    private boolean openingRequested;

    private RenderLifecycleRegressionHarness(AltoClef mod) {
        this.mod = mod;
        this.phaseStartedNanos = System.nanoTime();
        Baritone.settings().renderBaritoneVisuals.value = renderVisuals;
        Baritone.settings().renderPath.value = renderPath;
        Baritone.settings().renderDiagnostics.value = true;
        log("started world=" + worldName + ", cycles=" + targetCycles
                + ", activeSeconds=" + activeNanos / 1_000_000_000L
                + ", visuals=" + renderVisuals + ", renderPath=" + renderPath);
    }

    public static RenderLifecycleRegressionHarness createIfEnabled(AltoClef mod) {
        return Boolean.getBoolean(ENABLE_PROPERTY) ? new RenderLifecycleRegressionHarness(mod) : null;
    }

    public void tick() {
        long now = System.nanoTime();

        if (minecraft.level != null && minecraft.player != null) {
            if (activeWorld != minecraft.level) {
                activeWorld = minecraft.level;
                phase = Phase.ACTIVE;
                openingRequested = false;
                phaseStartedNanos = now;
                commandStarted = false;
                log("cycle=" + (completedCycles + 1) + " entered handlers="
                        + mod.getClientBaritone().getGameEventHandler().getListenerCount());
            }

            if (phase == Phase.ACTIVE && !commandStarted && now - phaseStartedNanos >= 5_000_000_000L) {
                commandStarted = true;
                AltoClef.getCommandExecutor().executeWithPrefix(command);
                log("cycle=" + (completedCycles + 1) + " command=" + command);
            }

            if (phase == Phase.ACTIVE && now - phaseStartedNanos >= activeNanos) {
                phase = Phase.LEAVING;
                log("cycle=" + (completedCycles + 1) + " leaving liveBuffers=" + RenderDiagnostics.liveBuffers());
                mod.stopTasks();
                minecraft.disconnectWithSavingScreen();
            }
            return;
        }

        activeWorld = null;
        if (phase == Phase.OPENING && completedCycles == 0 && now - phaseStartedNanos >= 10_000_000_000L) {
            openWorld();
            return;
        }
        if (phase == Phase.LEAVING) {
            completedCycles++;
            phase = Phase.WAITING;
            phaseStartedNanos = now;
            log("cycle=" + completedCycles + " unloaded liveBuffers=" + RenderDiagnostics.liveBuffers());
        }

        if (phase == Phase.WAITING
                && !minecraft.hasSingleplayerServer()
                && now - phaseStartedNanos >= 5_000_000_000L) {
            if (completedCycles >= targetCycles) {
                log("complete cycles=" + completedCycles + ", liveBuffers=" + RenderDiagnostics.liveBuffers());
                phase = Phase.COMPLETE;
                minecraft.stop();
            } else {
                openWorld();
            }
        }
    }

    private void openWorld() {
        if (openingRequested) {
            return;
        }
        phase = Phase.OPENING;
        openingRequested = true;
        phaseStartedNanos = System.nanoTime();
        minecraft.createWorldOpenFlows().openWorld(worldName, () -> {
            log("failed to reopen world=" + worldName);
            minecraft.stop();
        });
        log("opening cycle=" + (completedCycles + 1));
    }

    private static void log(String message) {
        System.out.println("[AltoClef render regression] " + message);
    }

    private enum Phase {
        OPENING,
        ACTIVE,
        LEAVING,
        WAITING,
        COMPLETE
    }
}
