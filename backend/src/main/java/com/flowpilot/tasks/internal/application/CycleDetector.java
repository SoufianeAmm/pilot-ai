package com.flowpilot.tasks.internal.application;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Depth-first cycle detection over the task dependency graph. Pure logic — no Spring, no DB — so it
 * is unit-tested directly (see {@code DependencyCycleDetectionTest}).
 *
 * <p>An edge "A depends on B" means A is blocked by B. Adding A→B creates a cycle iff B already
 * (transitively) depends on A. This traverses the existing dependency graph starting from B; if A is
 * reached, the returned list is the cycle path (A → B → … → A), otherwise empty.
 */
final class CycleDetector {

    private CycleDetector() {
    }

    /**
     * @param dependsOn adjacency: taskId → the tasks it depends on (its blockers)
     * @param start     the task that will gain a new dependency (A)
     * @param target    the dependency about to be added (B)
     * @return the cycle path A → B → … → A if adding A→B would close a cycle, else empty
     */
    static List<UUID> findCycle(Map<UUID, List<UUID>> dependsOn, UUID start, UUID target) {
        List<UUID> path = new ArrayList<>();
        path.add(target);
        if (dfs(dependsOn, target, start, new HashSet<>(), path)) {
            List<UUID> cycle = new ArrayList<>();
            cycle.add(start);
            cycle.addAll(path);
            return cycle;
        }
        return List.of();
    }

    /** DFS from {@code current} following depends-on edges; returns true if {@code target} is reached. */
    private static boolean dfs(Map<UUID, List<UUID>> dependsOn, UUID current, UUID target,
                               Set<UUID> visited, List<UUID> path) {
        if (current.equals(target)) {
            return true;
        }
        if (!visited.add(current)) {
            return false;
        }
        for (UUID next : dependsOn.getOrDefault(current, List.of())) {
            path.add(next);
            if (dfs(dependsOn, next, target, visited, path)) {
                return true;
            }
            path.remove(path.size() - 1);
        }
        return false;
    }
}
