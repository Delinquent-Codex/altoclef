package adris.altoclef.stability;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class DropSuppressionTest {
    @Test
    void suppressesNearbyDropUntilExpiry() {
        DropSuppression<String> suppression = new DropSuppression<>(4, 20, 3);
        DropSuppression.Position origin = new DropSuppression.Position(1, 2, 3);
        suppression.record("gravel", origin, 10);

        assertTrue(suppression.isSuppressed("gravel", new DropSuppression.Position(2, 2, 3), 11, false));
        assertFalse(suppression.isSuppressed("gravel", origin, 30, false));
    }

    @Test
    void changedTaskRequirementOverridesSuppression() {
        DropSuppression<String> suppression = new DropSuppression<>(4, 20, 3);
        DropSuppression.Position origin = new DropSuppression.Position(0, 0, 0);
        suppression.record("gold_nugget", origin, 0);

        assertFalse(suppression.isSuppressed("gold_nugget", origin, 1, true));
    }

    @Test
    void capacityEvictsOldestDrop() {
        DropSuppression<String> suppression = new DropSuppression<>(2, 100, 3);
        DropSuppression.Position origin = new DropSuppression.Position(0, 0, 0);
        suppression.record("a", origin, 0);
        suppression.record("b", origin, 1);
        suppression.record("c", origin, 2);

        assertFalse(suppression.isSuppressed("a", origin, 3, false));
        assertTrue(suppression.isSuppressed("c", origin, 3, false));
    }
}
