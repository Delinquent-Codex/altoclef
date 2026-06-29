package adris.altoclef.stability;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class SurvivalControllerTest {
    @Test
    void drowningPreemptsHostilesAndHunger() {
        SurvivalController controller = new SurvivalController();
        SurvivalController.State state = controller.tick(signals(8, 4, 80, true, true, false, 2));

        assertEquals(SurvivalController.State.DROWNING, state);
    }

    @Test
    void trappedWaterHasDistinctOwnership() {
        SurvivalController controller = new SurvivalController();

        assertEquals(SurvivalController.State.UNDERWATER_TRAPPED,
                controller.tick(signals(20, 20, 100, true, true, true, 0)));
    }

    @Test
    void overrideClearsOnlyAfterStableSafeWindow() {
        SurvivalController controller = new SurvivalController();
        controller.tick(signals(20, 20, 100, true, true, false, 0));
        SurvivalController.Signals safe = signals(20, 20, 300, true, false, false, 0);

        for (int i = 0; i < 19; i++) controller.tick(safe);
        assertEquals(SurvivalController.State.DROWNING, controller.getState());
        controller.tick(safe);
        assertEquals(SurvivalController.State.NONE, controller.getState());
    }

    private static SurvivalController.Signals signals(float health, int hunger, int air, boolean hasFood,
                                                       boolean underwater, boolean solidOverhead, int hostiles) {
        return new SurvivalController.Signals(health, hunger, air, 300, hasFood, underwater, solidOverhead,
                false, false, false, false, hostiles, false, false);
    }
}
