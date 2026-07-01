package adris.altoclef.stability;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class InventoryPolicyTest {
    @Test
    void blocksRootCleanupWhileChildOwnsTransaction() {
        assertFalse(InventoryPolicy.canRunRootInventoryCleanup(true, false));
    }

    @Test
    void blocksRootCleanupWithOpenScreenAndAllowsIdleCleanup() {
        assertFalse(InventoryPolicy.canRunRootInventoryCleanup(false, true));
        assertTrue(InventoryPolicy.canRunRootInventoryCleanup(false, false));
    }
}
