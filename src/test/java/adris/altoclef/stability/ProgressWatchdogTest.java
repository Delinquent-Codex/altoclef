package adris.altoclef.stability;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class ProgressWatchdogTest {
    @Test
    void escalatesInRequiredOrder() {
        ProgressWatchdog watchdog = new ProgressWatchdog(2, 2, 10);
        ProgressWatchdog.Fingerprint stuck = fingerprint("task", "0,0,0", 1, 0);

        watchdog.observe(stuck, true);
        assertEquals(ProgressWatchdog.RecoveryStage.NONE, watchdog.observe(stuck, true));
        assertEquals(ProgressWatchdog.RecoveryStage.RETRY_INTERACTION, watchdog.observe(stuck, true));
        watchdog.markActionPerformed();
        watchdog.observe(stuck, true);
        assertEquals(ProgressWatchdog.RecoveryStage.RECALCULATE_PATH, watchdog.observe(stuck, true));
        watchdog.markActionPerformed();
        watchdog.observe(stuck, true);
        assertEquals(ProgressWatchdog.RecoveryStage.CLEAR_UNREACHABLE, watchdog.observe(stuck, true));
    }

    @Test
    void positionProgressResetsRecovery() {
        ProgressWatchdog watchdog = new ProgressWatchdog(1, 2, 10);
        ProgressWatchdog.Fingerprint stuck = fingerprint("task", "0,0,0", 1, 0);
        watchdog.observe(stuck, true);
        watchdog.observe(stuck, true);
        assertTrue(watchdog.getStage() != ProgressWatchdog.RecoveryStage.NONE);

        watchdog.observe(fingerprint("task", "1,0,0", 1, 0), true);

        assertEquals(ProgressWatchdog.RecoveryStage.NONE, watchdog.getStage());
    }

    @Test
    void repeatedChildTransitionsTriggerEarly() {
        ProgressWatchdog watchdog = new ProgressWatchdog(100, 4, 2);
        watchdog.observe(fingerprint("a", "0,0,0", 1, 0), true);
        watchdog.observe(fingerprint("b", "0,0,0", 1, 0), true);
        watchdog.observe(fingerprint("a", "0,0,0", 1, 0), true);

        assertEquals(ProgressWatchdog.RecoveryStage.RETRY_INTERACTION, watchdog.getStage());
    }

    @Test
    void reportsOnlyMeaningfulProgress() {
        ProgressWatchdog watchdog = new ProgressWatchdog(10, 4, 10);
        ProgressWatchdog.Fingerprint first = fingerprint("task", "0,0,0", 1, 0);

        watchdog.observe(first, true);
        assertTrue(watchdog.progressObserved());
        watchdog.observe(first, true);
        assertFalse(watchdog.progressObserved());
        watchdog.observe(fingerprint("task", "1,0,0", 1, 0), true);
        assertTrue(watchdog.progressObserved());
    }

    @Test
    void routesCursorStallsToUiRecoveryWithoutMovementStages() {
        ProgressWatchdog watchdog = new ProgressWatchdog(2, 2, 10);
        ProgressWatchdog.Fingerprint stuck = uiFingerprint("craft", "minecraft:iron_pickaxe=1");

        watchdog.observe(stuck, true);
        watchdog.observe(stuck, true);
        assertEquals(ProgressWatchdog.RecoveryStage.RECOVER_UI, watchdog.observe(stuck, true));
        watchdog.markActionPerformed();
        watchdog.observe(stuck, true);
        assertEquals(ProgressWatchdog.RecoveryStage.RESTART_CHILD_TASK, watchdog.observe(stuck, true));
    }

    @Test
    void ignoresUnrelatedInventoryChurnWhileCursorIsUnresolved() {
        ProgressWatchdog watchdog = new ProgressWatchdog(2, 2, 10);
        ProgressWatchdog.Fingerprint first = uiFingerprint("craft", 1, "minecraft:iron_pickaxe=1");
        ProgressWatchdog.Fingerprint second = uiFingerprint("craft", 2, "minecraft:iron_pickaxe=1");

        watchdog.observe(first, true);
        watchdog.observe(second, true);
        assertEquals(ProgressWatchdog.RecoveryStage.RECOVER_UI, watchdog.observe(first, true));
    }

    @Test
    void detectsRepeatedUiStateCycleDespiteInventoryHashChanges() {
        ProgressWatchdog watchdog = new ProgressWatchdog(100, 2, 100);
        ProgressWatchdog.Fingerprint first = uiFingerprint("clear-grid", 1, "minecraft:planks=3");
        ProgressWatchdog.Fingerprint second = uiFingerprint("clear-grid", 2, "empty");

        for (int i = 0; i <= 100; i++) {
            watchdog.observe(first, true);
            watchdog.observe(second, true);
        }
        assertEquals(ProgressWatchdog.RecoveryStage.RECOVER_UI, watchdog.observe(first, true));
    }

    @Test
    void allowsLongUiTransactionWhenEachInventoryStateAdvances() {
        ProgressWatchdog watchdog = new ProgressWatchdog(500, 2, 500);

        for (int tick = 0; tick < 250; tick++) {
            watchdog.observe(uiFingerprint("move-slot", tick, "empty"), true);
        }

        assertEquals(ProgressWatchdog.RecoveryStage.NONE, watchdog.getStage());
    }

    @Test
    void suppressesImmediateUiRecoveryRetriggerAfterCompletion() {
        ProgressWatchdog watchdog = new ProgressWatchdog(100, 2, 100);
        ProgressWatchdog.Fingerprint first = uiFingerprint("clear-grid", 1, "minecraft:planks=3");
        ProgressWatchdog.Fingerprint second = uiFingerprint("clear-grid", 2, "empty");
        for (int i = 0; i <= 100; i++) {
            watchdog.observe(first, true);
            watchdog.observe(second, true);
        }
        assertEquals(ProgressWatchdog.RecoveryStage.RECOVER_UI, watchdog.getStage());

        watchdog.markUiRecoveryCompleted();
        for (int i = 0; i < 3; i++) {
            watchdog.observe(first, true);
            watchdog.observe(second, true);
        }
        assertEquals(ProgressWatchdog.RecoveryStage.NONE, watchdog.getStage());
    }

    @Test
    void returnsToParentWhenUiStallRepeatsAfterRecovery() {
        ProgressWatchdog watchdog = new ProgressWatchdog(500, 2, 500);
        ProgressWatchdog.Fingerprint stuck = uiFingerprint("move-slot", 1, "empty");
        for (int i = 0; i <= 200; i++) watchdog.observe(stuck, true);
        assertEquals(ProgressWatchdog.RecoveryStage.RECOVER_UI, watchdog.getStage());
        watchdog.markUiRecoveryCompleted();

        for (int i = 0; i <= 200; i++) watchdog.observe(stuck, true);
        assertEquals(ProgressWatchdog.RecoveryStage.RETURN_TO_PARENT, watchdog.getStage());
    }

    @Test
    void doesNotRecoverLegitimateSmeltingWait() {
        ProgressWatchdog watchdog = new ProgressWatchdog(2, 2, 2);
        ProgressWatchdog.Fingerprint waiting = new ProgressWatchdog.Fingerprint(
                "DoSmeltInFurnaceTask[Waiting...]", "0,0,0", 1, "overworld", -1, -1,
                "none", "none", "empty", "FurnaceScreen", "DoSmeltInFurnaceTask", true, true);

        for (int i = 0; i < 500; i++) watchdog.observe(waiting, true);

        assertEquals(ProgressWatchdog.RecoveryStage.NONE, watchdog.getStage());
    }

    @Test
    void identifiesOnlyPassiveContainerWaits() {
        assertTrue(ProgressWatchdog.isPassiveUiWait("dosmeltinfurnacetask[waiting...]"));
        assertFalse(ProgressWatchdog.isPassiveUiWait("craftgenericmanuallytask[moving item to slot]"));
        assertFalse(ProgressWatchdog.isPassiveUiWait("timeoutwandertask[waiting for path]"));
    }

    private static ProgressWatchdog.Fingerprint fingerprint(String task, String position, int inventory, int path) {
        return new ProgressWatchdog.Fingerprint(task, position, inventory, "overworld", path, 10,
                "none", "none", "empty", "none", task, false, false);
    }

    private static ProgressWatchdog.Fingerprint uiFingerprint(String task, String cursor) {
        return uiFingerprint(task, 1, cursor);
    }

    private static ProgressWatchdog.Fingerprint uiFingerprint(String task, int inventory, String cursor) {
        return new ProgressWatchdog.Fingerprint(task, "0,0,0", inventory, "overworld", -1, -1,
                "none", "none", cursor, "InventoryScreen", task, true, false);
    }
}
