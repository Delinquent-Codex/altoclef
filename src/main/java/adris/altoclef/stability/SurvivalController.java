package adris.altoclef.stability;

public final class SurvivalController {
    private static final int SAFE_CLEAR_TICKS = 20;

    private State state = State.NONE;
    private int safeTicks;

    public State tick(Signals signals) {
        State candidate = choose(signals);
        if (candidate.priority() > state.priority()) {
            state = candidate;
            safeTicks = 0;
            return state;
        }
        if (!isSafeFrom(state, signals)) {
            safeTicks = 0;
            return state;
        }
        if (candidate != State.NONE) {
            state = candidate;
            safeTicks = 0;
            return state;
        }
        if (++safeTicks >= SAFE_CLEAR_TICKS) {
            state = State.NONE;
            safeTicks = 0;
        }
        return state;
    }

    public State getState() {
        return state;
    }

    public void reset() {
        state = State.NONE;
        safeTicks = 0;
    }

    private static State choose(Signals signals) {
        if (signals.inLava()) return State.LAVA;
        if (signals.onFire()) return State.FIRE;
        if (signals.suffocating()) return State.SUFFOCATION;
        if (signals.underwater() && signals.air() < signals.maxAir() - 20) {
            return signals.solidOverhead() ? State.UNDERWATER_TRAPPED : State.DROWNING;
        }
        if (signals.dangerousFall()) return State.DANGEROUS_FALL;
        if (signals.nearbyHostiles() > 0
                && (signals.health() <= 12 || (!signals.hasShield() && !signals.hasWeapon()))) {
            return State.HOSTILE_RETREAT;
        }
        if (signals.hunger() <= 6) {
            return signals.hasFood() ? State.CRITICAL_HUNGER : State.CRITICAL_HUNGER_NO_FOOD;
        }
        if (signals.health() <= 8) return State.LOW_HEALTH;
        return State.NONE;
    }

    private static boolean isSafeFrom(State state, Signals signals) {
        return switch (state) {
            case NONE -> true;
            case LAVA -> !signals.inLava() && !signals.onFire();
            case FIRE -> !signals.onFire();
            case SUFFOCATION -> !signals.suffocating();
            case DROWNING, UNDERWATER_TRAPPED -> !signals.underwater() && signals.air() >= signals.maxAir();
            case DANGEROUS_FALL -> !signals.dangerousFall();
            case HOSTILE_RETREAT -> signals.nearbyHostiles() == 0;
            case CRITICAL_HUNGER, CRITICAL_HUNGER_NO_FOOD -> signals.hunger() >= 12;
            case LOW_HEALTH -> signals.health() >= 14;
        };
    }

    public enum State {
        NONE(0),
        LOW_HEALTH(10),
        CRITICAL_HUNGER(20),
        CRITICAL_HUNGER_NO_FOOD(21),
        HOSTILE_RETREAT(30),
        DANGEROUS_FALL(40),
        DROWNING(50),
        UNDERWATER_TRAPPED(51),
        SUFFOCATION(60),
        FIRE(70),
        LAVA(80);

        private final int priority;

        State(int priority) {
            this.priority = priority;
        }

        public int priority() {
            return priority;
        }

        public boolean isWaterEmergency() {
            return this == DROWNING || this == UNDERWATER_TRAPPED;
        }

        public boolean isWorldEmergency() {
            return isWaterEmergency() || this == LAVA || this == FIRE || this == SUFFOCATION;
        }
    }

    public record Signals(float health, int hunger, int air, int maxAir, boolean hasFood,
                          boolean underwater, boolean solidOverhead, boolean inLava, boolean onFire,
                          boolean suffocating, boolean dangerousFall, int nearbyHostiles,
                          boolean hasShield, boolean hasWeapon) {
    }
}
