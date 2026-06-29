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

    private static ProgressWatchdog.Fingerprint fingerprint(String task, String position, int inventory, int path) {
        return new ProgressWatchdog.Fingerprint(task, position, inventory, "overworld", path, 10, "none", "none");
    }
}
