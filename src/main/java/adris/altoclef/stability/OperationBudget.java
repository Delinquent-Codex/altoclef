package adris.altoclef.stability;

public final class OperationBudget {
    private final int maximum;
    private int used;

    public OperationBudget(int maximum) {
        if (maximum < 1) throw new IllegalArgumentException("maximum must be positive");
        this.maximum = maximum;
    }

    public boolean tryAcquire() {
        if (used >= maximum) return false;
        used++;
        return true;
    }

    public int used() {
        return used;
    }
}
