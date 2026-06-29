package adris.altoclef.stability;

import java.util.List;
import java.util.Optional;
import java.util.function.ToIntFunction;

public final class MatchingMaterialPlanner {
    private MatchingMaterialPlanner() {
    }

    public static <T> Optional<Selection<T>> select(List<T> variants,
                                                    ToIntFunction<T> expectedCount,
                                                    ToIntFunction<T> actualCount,
                                                    int remainingOutputs,
                                                    int materialPerCraft,
                                                    int outputPerCraft) {
        if (variants.isEmpty() || remainingOutputs <= 0 || materialPerCraft <= 0 || outputPerCraft <= 0) {
            return Optional.empty();
        }
        int craftsRequired = (remainingOutputs + outputPerCraft - 1) / outputPerCraft;
        int materialRequired = craftsRequired * materialPerCraft;
        T selected = variants.get(0);
        int selectedExpected = expectedCount.applyAsInt(selected);
        for (int i = 1; i < variants.size(); i++) {
            T variant = variants.get(i);
            int count = expectedCount.applyAsInt(variant);
            if (count > selectedExpected) {
                selected = variant;
                selectedExpected = count;
            }
        }
        return Optional.of(new Selection<>(selected, selectedExpected, actualCount.applyAsInt(selected),
                materialRequired, craftsRequired));
    }

    public record Selection<T>(T material, int expectedCount, int actualCount,
                               int materialRequired, int craftsRequired) {
        public int missingExpected() {
            return Math.max(0, materialRequired - expectedCount);
        }

        public int missingActual() {
            return Math.max(0, materialRequired - actualCount);
        }

        public boolean canCommit() {
            return actualCount >= materialRequired;
        }
    }
}
