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
    private final BoundedHistory<UiState> uiStates = new BoundedHistory<>(128);

    private Fingerprint previous;
    private RecoveryStage stage = RecoveryStage.NONE;
    private int stagnantTicks;
    private int stageTicks;
    private int childTransitions;
    private boolean actionPerformed;
    private boolean progressObserved;
    private RecoveryDomain domain = RecoveryDomain.MOVEMENT;
    private int uiRecoveryCooldownTicks;
    private int uiTransactionTicks;
    private int uiRecoveryCompletions;
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
        progressObserved = false;
        if (uiRecoveryCooldownTicks > 0) uiRecoveryCooldownTicks--;
        if (!eligible || current == null) {
            resetObservation();
            return stage;
        }
        if (current.passiveUiWait() && "empty".equals(current.cursorStack())) {
            resetRecoveryState();
            uiTransactionTicks = 0;
            uiStates.clear();
            return stage;
        }
        history.add(current);
        boolean repeatedUiState = false;
        if (current.uiTransaction()) {
            UiState currentUiState = UiState.from(current);
            repeatedUiState = uiStates.snapshot().contains(currentUiState);
            uiStates.add(currentUiState);
        } else {
            uiTransactionTicks = 0;
            uiRecoveryCompletions = 0;
            uiStates.clear();
        }
        if (previous == null) {
            previous = current;
            progressObserved = true;
            return stage;
        }

        boolean meaningfulProgress = meaningfulProgress(previous, current);
        boolean forwardProgress = meaningfulProgress && (!current.uiTransaction() || !repeatedUiState);
        if (current.uiTransaction()) {
            if (repeatedUiState || !meaningfulProgress) {
                uiTransactionTicks++;
            } else {
                uiTransactionTicks = 0;
                uiRecoveryCompletions = 0;
            }
        }
        boolean repeatedUiCycle = uiRecoveryCooldownTicks == 0 && uiTransactionTicks >= 20 * 10
                && current.uiTransaction();
        if (!repeatedUiCycle && forwardProgress) {
            resetRecoveryState();
            previous = current;
            progressObserved = true;
            return stage;
        }
        if (!repeatedUiCycle && meaningfulProgress) {
            stagnantTicks = 0;
            childTransitions = 0;
            previous = current;
            return stage;
        }

        if (!Objects.equals(previous.taskSignature(), current.taskSignature())) {
            childTransitions++;
        }
        stagnantTicks++;
        previous = current;

        if (stage == RecoveryStage.NONE && repeatedUiCycle) {
            domain = RecoveryDomain.UI;
            enter(uiRecoveryCompletions == 0 ? RecoveryStage.RECOVER_UI : RecoveryStage.RETURN_TO_PARENT,
                    uiRecoveryCompletions == 0 ? "prolonged inventory/UI transaction"
                            : "inventory/UI transaction repeated after recovery");
        } else if (stage == RecoveryStage.NONE
                && (stagnantTicks >= stuckTicksThreshold || childTransitions >= transitionLimit)) {
            domain = current.uiTransaction() ? RecoveryDomain.UI : RecoveryDomain.MOVEMENT;
            enter(domain == RecoveryDomain.UI ? RecoveryStage.RECOVER_UI : RecoveryStage.RETRY_INTERACTION,
                    domain == RecoveryDomain.UI ? "unresolved inventory/UI transaction" : "no meaningful progress");
        } else if (stage != RecoveryStage.NONE && ++stageTicks >= stageTicksThreshold) {
            enter(nextStage(stage, domain), failureReason);
        }
        return stage;
    }

    public RecoveryStage getStage() {
        return stage;
    }

    public boolean shouldPerformAction() {
        return stage != RecoveryStage.NONE && !actionPerformed;
    }

    public boolean progressObserved() {
        return progressObserved;
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
        resetRecoveryState();
        uiRecoveryCooldownTicks = 0;
        uiTransactionTicks = 0;
        uiRecoveryCompletions = 0;
        uiStates.clear();
    }

    public void markUiRecoveryCompleted() {
        int completedRecoveries = uiRecoveryCompletions + 1;
        reset();
        uiRecoveryCooldownTicks = 20 * 10;
        uiRecoveryCompletions = completedRecoveries;
    }

    private void resetRecoveryState() {
        previous = null;
        stage = RecoveryStage.NONE;
        stagnantTicks = 0;
        stageTicks = 0;
        childTransitions = 0;
        actionPerformed = false;
        progressObserved = false;
        failureReason = "none";
        domain = RecoveryDomain.MOVEMENT;
        history.clear();
    }

    private void resetObservation() {
        previous = null;
        uiTransactionTicks = 0;
        uiStates.clear();
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
        if (!Objects.equals(previous.cursorStack(), current.cursorStack())) return true;
        if (!"empty".equals(current.cursorStack())) return false;
        if (!Objects.equals(previous.blockPosition(), current.blockPosition())) return true;
        if (previous.inventoryHash() != current.inventoryHash()
                && "empty".equals(previous.cursorStack()) && "empty".equals(current.cursorStack())) return true;
        return current.pathPosition() > previous.pathPosition()
                && current.pathLength() == previous.pathLength();
    }

    private static RecoveryStage nextStage(RecoveryStage current, RecoveryDomain domain) {
        if (domain == RecoveryDomain.UI) {
            return switch (current) {
                case RECOVER_UI -> RecoveryStage.RESTART_CHILD_TASK;
                case RESTART_CHILD_TASK -> RecoveryStage.RETURN_TO_PARENT;
                case RETURN_TO_PARENT -> RecoveryStage.NONE;
                default -> RecoveryStage.RECOVER_UI;
            };
        }
        return current.next();
    }

    public enum RecoveryStage {
        NONE,
        RECOVER_UI,
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

    private enum RecoveryDomain {
        MOVEMENT,
        UI
    }

    public record Fingerprint(String taskSignature, String blockPosition, int inventoryHash,
                              String dimension, int pathPosition, int pathLength,
                              String interactionTarget, String recentFailure,
                              String cursorStack, String screenType, String uiOperation, boolean uiTransaction,
                              boolean passiveUiWait) {
    }

    public static boolean isPassiveUiWait(String lowerTaskDescription) {
        return lowerTaskDescription.contains("waiting")
                && (lowerTaskDescription.contains("smelt") || lowerTaskDescription.contains("furnace")
                || lowerTaskDescription.contains("smoker") || lowerTaskDescription.contains("blast"));
    }

    private record UiState(int inventoryHash, String cursorStack, String screenType, String uiOperation) {
        private static UiState from(Fingerprint fingerprint) {
            return new UiState(fingerprint.inventoryHash(), fingerprint.cursorStack(), fingerprint.screenType(),
                    fingerprint.uiOperation());
        }
    }
}
