package adris.altoclef.stability;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class MatchingMaterialPlannerTest {
    @Test
    void acceptsSameColorBedPlan() {
        Map<String, Integer> wool = Map.of("red", 3, "blue", 0, "white", 0);
        MatchingMaterialPlanner.Selection<String> selection = select(wool, 1);

        assertEquals("red", selection.material());
        assertTrue(selection.canCommit());
        assertEquals(3, selection.materialRequired());
    }

    @Test
    void rejectsMixedColorBedPlanAndChoosesOneVariant() {
        Map<String, Integer> wool = Map.of("red", 1, "blue", 1, "white", 1);
        MatchingMaterialPlanner.Selection<String> selection = select(wool, 1);

        assertFalse(selection.canCommit());
        assertEquals(2, selection.missingActual());
        assertEquals("red", selection.material());
    }

    @Test
    void reservesOneColorForMultipleBeds() {
        Map<String, Integer> wool = Map.of("red", 4, "blue", 3, "white", 0);
        MatchingMaterialPlanner.Selection<String> selection = select(wool, 2);

        assertEquals("red", selection.material());
        assertEquals(6, selection.materialRequired());
        assertEquals(2, selection.missingActual());
    }

    private static MatchingMaterialPlanner.Selection<String> select(Map<String, Integer> counts, int beds) {
        return MatchingMaterialPlanner.select(List.of("red", "blue", "white"),
                color -> counts.getOrDefault(color, 0), color -> counts.getOrDefault(color, 0), beds, 3, 1).orElseThrow();
    }
}
