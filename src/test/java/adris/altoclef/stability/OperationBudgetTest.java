package adris.altoclef.stability;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class OperationBudgetTest {
    @Test
    void enforcesExactOperationLimit() {
        OperationBudget budget = new OperationBudget(3);
        assertTrue(budget.tryAcquire());
        assertTrue(budget.tryAcquire());
        assertTrue(budget.tryAcquire());
        assertFalse(budget.tryAcquire());
        assertEquals(3, budget.used());
    }
}
