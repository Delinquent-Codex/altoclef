package adris.altoclef.tasks;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class CraftGenericWithRecipeBooksTaskTest {
    @Test
    void fallsBackOnlyAfterBoundedRecipeBookWait() {
        assertFalse(CraftGenericWithRecipeBooksTask.shouldUseManualFallback(39));
        assertTrue(CraftGenericWithRecipeBooksTask.shouldUseManualFallback(40));
    }
}
