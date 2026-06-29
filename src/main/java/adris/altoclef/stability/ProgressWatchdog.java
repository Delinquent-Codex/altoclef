package adris.altoclef.stability;

import java.util.Objects;

public final class ProgressWatchdog {
    private static final int DEFAULT_STUCK_TICKS = 20 * 20;
    private static final int DEFAULT_STAGE_TICKS = 6 * 20;
    private static final int DEFAULT_TRANSITION_LIMIT = 30;

    private final int stuckTicksThreshold;
    private final int stageTicksThreshold;
    private final int transitionLimit;
    private final BoundedHistory<Fingerprint> history = new BoundedHistory<>(128);

    private Fingerprint previous;
    private RecoveryStage stage = RecoveryStage.NONE;
    private int stagnantTicks;
    private int stageTicks;
    private int childTransitions;
    private boolean actionPerformed;
    private String failureReason = "none";

    public ProgressWatchdog() {
        this(DEFAULT_STUCK_TICKS, DEFAULT_STAGE_TICKS, DEFAULT_TRANSITION_LIMIT);
    }

    ProgressWatchdog(int stuckTicksThreshold, int stageTicksThreshold, int transitionLimit) {
        this.stuckTicksThreshold = stuckTicksThreshold;
        this.stageTicksThreshold = stageTicksThreshold;
        this.transitionLimit = transitionLimit;
    }

    public RecoveryStage observe(Fingerprint current, boolean eligible) {
        if (!eligible || current == null) {
            resetObservation();
            return stage;
        }
        history.add(current);
        if (previous == null) {
            previous = current;
            return stage;
        }

        if (meaningfulProgress(previous, current)) {
            reset();
            previous = current;
            return stage;
        }

        if (!Objects.equals(previous.taskSignature(), current.taskSignature())) {
            childTransitions++;
        }
        stagnantTicks++;
        previous = current;

        if (stage == RecoveryStage.NONE
                && (stagnantTicks >= stuckTicksThreshold || childTransitions >= transitionLimit)) {
            enter(RecoveryStage.RETRY_INTERACTION, "no meaningful progress");
        } else if (stage != RecoveryStage.NONE && ++stageTicks >= stageTicksThreshold) {
            enter(stage.next(), failureReason);
        }
        return stage;
    }

    public RecoveryStage getStage() {
        return stage;
    }

    public boolean shouldPerformAction() {
        return stage != RecoveryStage.NONE && !actionPerformed;
    }

    public void markActionPerformed() {
        actionPerformed = true;
    }

    public String getFailureReason() {
        return failureReason;
    }

    public int getChildTransitions() {
        return childTransitions;
    }

    public void reset() {
        previous = null;
        stage = RecoveryStage.NONE;
        stagnantTicks = 0;
        stageTicks = 0;
        childTransitions = 0;
        actionPerformed = false;
        failureReason = "none";
        history.clear();
    }

    private void resetObservation() {
        previous = null;
        if (stage == RecoveryStage.NONE) {
            stagnantTicks = 0;
            childTransitions = 0;
        }
    }

    private void enter(RecoveryStage next, String reason) {
        if (next == RecoveryStage.NONE) {
            reset();
            return;
        }
        stage = next;
        stageTicks = 0;
        actionPerformed = false;
        failureReason = reason;
    }

    private static boolean meaningfulProgress(Fingerprint previous, Fingerprint current) {
        if (!Objects.equals(previous.dimension(), current.dimension())) return true;
        if (!Objects.equals(previous.blockPosition(), current.blockPosition())) return true;
        if (previous.inventoryHash() != current.inventoryHash()) return true;
        return current.pathPosition() > previous.pathPosition()
                && current.pathLength() == previous.pathLength();
    }

    public enum RecoveryStage {
        NONE,
        RETRY_INTERACTION,
        RECALCULATE_PATH,
        CLEAR_UNREACHABLE,
        MOVE_TO_SAFE_POSITION,
        REEVALUATE_RESOURCE,
        RESTART_CHILD_TASK,
        RETURN_TO_PARENT;

        RecoveryStage next() {
            if (this == RETURN_TO_PARENT) return NONE;
            return values()[ordinal() + 1];
        }
    }

    public record Fingerprint(String taskSignature, String blockPosition, int inventoryHash,
                              String dimension, int pathPosition, int pathLength,
                              String interactionTarget, String recentFailure) {
    }
}
