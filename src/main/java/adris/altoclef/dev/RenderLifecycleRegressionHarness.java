package adris.altoclef.dev;

import adris.altoclef.AltoClef;
import adris.altoclef.multiversion.entity.PlayerVer;
import baritone.Baritone;
import baritone.utils.RenderDiagnostics;
import net.minecraft.client.Minecraft;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.LevelSettings;
import net.minecraft.world.level.WorldDataConfiguration;
import net.minecraft.world.level.levelgen.WorldOptions;
import net.minecraft.world.level.levelgen.presets.WorldPresets;
import java.nio.file.Files;

/** Development-only repeated-world renderer regression harness. */
public final class RenderLifecycleRegressionHarness {

    private static final String ENABLE_PROPERTY = "altoclef.renderRegression";

    private final AltoClef mod;
    private final Minecraft minecraft = Minecraft.getInstance();
    private final String worldName = System.getProperty("altoclef.renderRegression.world", "New World");
    private final long baseSeed = Long.getLong("altoclef.renderRegression.seed", 2602001L);
    private final boolean createWorldIfMissing = Boolean.parseBoolean(
            System.getProperty("altoclef.renderRegression.createWorldIfMissing", "false"));
    private final boolean freshWorldEachCycle = Boolean.parseBoolean(
            System.getProperty("altoclef.renderRegression.freshWorldEachCycle", "false"));
    private final String command = decodeCommand(System.getProperty("altoclef.renderRegression.command", "gamer"));
    private final String[] setupCommands = System.getProperty("altoclef.renderRegression.setupCommands", "")
            .split("\\|", -1);
    private final int targetCycles = Integer.getInteger("altoclef.renderRegression.cycles", 10);
    private final long activeNanos = Integer.getInteger("altoclef.renderRegression.activeSeconds", 90) * 1_000_000_000L;
    private final boolean renderVisuals = Boolean.parseBoolean(System.getProperty("altoclef.renderRegression.visuals", "true"));
    private final boolean renderPath = Boolean.parseBoolean(System.getProperty("altoclef.renderRegression.renderPath", "true"));

    private Phase phase = Phase.OPENING;
    private Object activeWorld;
    private long phaseStartedNanos;
    private int completedCycles;
    private boolean commandStarted;
    private boolean setupApplied;
    private boolean openingRequested;
    private long lastDiagnosticNanos;
    private String currentWorldName;

    private RenderLifecycleRegressionHarness(AltoClef mod) {
        this.mod = mod;
        this.phaseStartedNanos = System.nanoTime();
        Baritone.settings().renderBaritoneVisuals.value = renderVisuals;
        Baritone.settings().renderPath.value = renderPath;
        Baritone.settings().renderDiagnostics.value = true;
        log("started world=" + worldName + ", seed=" + baseSeed + ", cycles=" + targetCycles
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
                if (phase != Phase.ACTIVE) {
                    phase = Phase.ACTIVE;
                    openingRequested = false;
                    phaseStartedNanos = now;
                    commandStarted = false;
                    setupApplied = false;
                    log("cycle=" + (completedCycles + 1) + " entered handlers="
                            + mod.getClientBaritone().getGameEventHandler().getListenerCount());
                } else {
                    log("cycle=" + (completedCycles + 1) + " changed dimension");
                }
            }

            if (phase == Phase.ACTIVE && !setupApplied && now - phaseStartedNanos >= 2_000_000_000L) {
                setupApplied = true;
                int applied = 0;
                for (String setupCommand : setupCommands) {
                    if (!setupCommand.isBlank()) {
                        PlayerVer.sendChatCommand(minecraft.player, decodeCommand(setupCommand.trim()));
                        applied++;
                    }
                }
                if (applied > 0) log("cycle=" + (completedCycles + 1) + " setupCommands=" + applied);
            }

            if (phase == Phase.ACTIVE && !commandStarted && now - phaseStartedNanos >= 6_000_000_000L) {
                commandStarted = true;
                AltoClef.getCommandExecutor().executeWithPrefix(command);
                log("cycle=" + (completedCycles + 1) + " command=" + command);
            }

            if (phase == Phase.ACTIVE && now - lastDiagnosticNanos >= 30_000_000_000L) {
                lastDiagnosticNanos = now;
                for (String line : mod.getStabilityDiagnostics().snapshot().conciseLines()) {
                    log("diagnostic " + line);
                }
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
        currentWorldName = freshWorldEachCycle ? worldName + "-" + (completedCycles + 1) : worldName;
        if (createWorldIfMissing && !Files.isDirectory(minecraft.gameDirectory.toPath()
                .resolve("saves").resolve(currentWorldName))) {
            createWorld(currentWorldName, baseSeed + completedCycles);
            return;
        }
        minecraft.createWorldOpenFlows().openWorld(currentWorldName, () -> {
            if (createWorldIfMissing) {
                createWorld(currentWorldName, baseSeed + completedCycles);
            } else {
                log("failed to reopen world=" + currentWorldName);
                minecraft.stop();
            }
        });
        log("opening cycle=" + (completedCycles + 1) + " world=" + currentWorldName);
    }

    private void createWorld(String name, long seed) {
        log("creating world=" + name + " seed=" + seed);
        LevelSettings settings = new LevelSettings(name, GameType.SURVIVAL,
                LevelSettings.DifficultySettings.DEFAULT, true, WorldDataConfiguration.DEFAULT);
        minecraft.createWorldOpenFlows().createFreshLevel(name, settings,
                new WorldOptions(seed, true, false), WorldPresets::createNormalWorldDimensions,
                minecraft.gui.screen());
    }

    private static void log(String message) {
        System.out.println("[AltoClef render regression] " + message);
    }

    private static String decodeCommand(String encoded) {
        return encoded.replace('^', ' ');
    }

    private enum Phase {
        OPENING,
        ACTIVE,
        LEAVING,
        WAITING,
        COMPLETE
    }
}
