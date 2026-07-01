package adris.altoclef.stability;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class WaterEscapePlannerTest {
    @Test
    void selectsReachableAirAboveWaterColumn() {
        WaterEscapePlanner.GridPos start = new WaterEscapePlanner.GridPos(0, 0, 0);
        Map<WaterEscapePlanner.GridPos, WaterEscapePlanner.Cell> cells = Map.of(
                start, WaterEscapePlanner.Cell.WATER,
                new WaterEscapePlanner.GridPos(0, 1, 0), WaterEscapePlanner.Cell.WATER,
                new WaterEscapePlanner.GridPos(0, 2, 0), WaterEscapePlanner.Cell.AIR);

        Optional<WaterEscapePlanner.GridPos> result = WaterEscapePlanner.nearestAir(start, 4,
                pos -> cells.getOrDefault(pos, WaterEscapePlanner.Cell.BLOCKED), pos -> false);

        assertEquals(Optional.of(new WaterEscapePlanner.GridPos(0, 2, 0)), result);
    }

    @Test
    void skipsTemporarilyFailedExit() {
        WaterEscapePlanner.GridPos start = new WaterEscapePlanner.GridPos(0, 0, 0);
        WaterEscapePlanner.GridPos failed = new WaterEscapePlanner.GridPos(0, 1, 0);
        WaterEscapePlanner.GridPos alternate = new WaterEscapePlanner.GridPos(1, 1, 0);
        Map<WaterEscapePlanner.GridPos, WaterEscapePlanner.Cell> cells = Map.of(
                start, WaterEscapePlanner.Cell.WATER,
                failed, WaterEscapePlanner.Cell.AIR,
                new WaterEscapePlanner.GridPos(1, 0, 0), WaterEscapePlanner.Cell.WATER,
                alternate, WaterEscapePlanner.Cell.AIR);

        Optional<WaterEscapePlanner.GridPos> result = WaterEscapePlanner.nearestAir(start, 4,
                pos -> cells.getOrDefault(pos, WaterEscapePlanner.Cell.BLOCKED), failed::equals);

        assertEquals(Optional.of(alternate), result);
    }

    @Test
    void returnsEmptyForFullyCappedTrap() {
        WaterEscapePlanner.GridPos start = new WaterEscapePlanner.GridPos(0, 0, 0);
        assertTrue(WaterEscapePlanner.nearestAir(start, 4,
                pos -> pos.equals(start) ? WaterEscapePlanner.Cell.WATER : WaterEscapePlanner.Cell.BLOCKED,
                pos -> false).isEmpty());
    }
}
