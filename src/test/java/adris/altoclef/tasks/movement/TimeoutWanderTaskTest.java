package adris.altoclef.tasks.movement;

import static org.junit.jupiter.api.Assertions.assertFalse;

import org.junit.jupiter.api.Test;

class TimeoutWanderTaskTest {
    @Test
    void isNotFinishedBeforeFirstTickInitializesOrigin() {
        assertFalse(TimeoutWanderTask.hasInitializedOrigin(null));
    }
}
