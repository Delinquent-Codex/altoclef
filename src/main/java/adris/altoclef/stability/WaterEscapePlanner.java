package adris.altoclef.stability;

import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Queue;
import java.util.Set;
import java.util.function.Function;
import java.util.function.Predicate;

public final class WaterEscapePlanner {
    private static final GridPos[] DIRECTIONS = {
            new GridPos(0, 1, 0), new GridPos(1, 0, 0), new GridPos(-1, 0, 0),
            new GridPos(0, 0, 1), new GridPos(0, 0, -1), new GridPos(0, -1, 0)
    };

    private WaterEscapePlanner() {
    }

    public static Optional<GridPos> nearestAir(GridPos start, int maxDepth,
                                                Function<GridPos, Cell> cells,
                                                Predicate<GridPos> excluded) {
        Queue<GridPos> queue = new ArrayDeque<>();
        Set<GridPos> visited = new HashSet<>();
        Map<GridPos, Integer> depths = new HashMap<>();
        queue.add(start);
        depths.put(start, 0);
        while (!queue.isEmpty()) {
            GridPos current = queue.remove();
            if (!visited.add(current)) continue;
            int depth = depths.get(current);
            if (depth > 0 && cells.apply(current) == Cell.AIR && !excluded.test(current)) {
                return Optional.of(current);
            }
            if (depth >= maxDepth) continue;
            for (GridPos direction : DIRECTIONS) {
                GridPos next = current.add(direction);
                if (visited.contains(next) || excluded.test(next) || cells.apply(next) == Cell.BLOCKED) continue;
                depths.putIfAbsent(next, depth + 1);
                queue.add(next);
            }
        }
        return Optional.empty();
    }

    public enum Cell {
        AIR,
        WATER,
        BLOCKED
    }

    public record GridPos(int x, int y, int z) {
        public GridPos add(GridPos other) {
            return new GridPos(x + other.x, y + other.y, z + other.z);
        }
    }
}
