package adris.altoclef.stability;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.List;
import org.junit.jupiter.api.Test;

class BoundedHistoryTest {
    @Test
    void evictsOldestValueAtCapacity() {
        BoundedHistory<Integer> history = new BoundedHistory<>(3);
        history.add(1);
        history.add(2);
        history.add(3);
        history.add(4);

        assertEquals(List.of(2, 3, 4), history.snapshot());
    }

    @Test
    void rejectsNonPositiveCapacity() {
        assertThrows(IllegalArgumentException.class, () -> new BoundedHistory<>(0));
    }
}
