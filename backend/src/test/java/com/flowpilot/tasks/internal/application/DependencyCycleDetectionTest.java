package com.flowpilot.tasks.internal.application;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.Test;

/**
 * Verifies {@link CycleDetector} — the DFS that rejects dependency cycles. Pure unit test: no
 * Spring, no DB. The graph is "taskId -> [tasks it depends on]" (its blockers).
 */
class DependencyCycleDetectionTest {

    private final UUID a = UUID.randomUUID();
    private final UUID b = UUID.randomUUID();
    private final UUID c = UUID.randomUUID();
    private final UUID d = UUID.randomUUID();

    @Test
    void noDependencies_addingEdge_isNotACycle() {
        // adding A -> B, B has no existing dependencies
        assertThat(CycleDetector.findCycle(Map.of(), a, b)).isEmpty();
    }

    @Test
    void targetDoesNotTransitivelyDependOnStart_isNotACycle() {
        // B depends on C, C depends on D; adding A -> B does not close a cycle (A is unreachable from B)
        Map<UUID, List<UUID>> graph = Map.of(b, List.of(c), c, List.of(d));
        assertThat(CycleDetector.findCycle(graph, a, b)).isEmpty();
    }

    @Test
    void directTwoCycle_isRejectedWithPath() {
        // B already depends on A; adding A -> B closes A -> B -> A
        Map<UUID, List<UUID>> graph = Map.of(b, List.of(a));
        List<UUID> cycle = CycleDetector.findCycle(graph, a, b);
        assertThat(cycle).containsExactly(a, b, a);
    }

    @Test
    void threeNodeCycle_isRejectedWithPath() {
        // B -> C -> A already; adding A -> B closes A -> B -> C -> A
        Map<UUID, List<UUID>> graph = Map.of(b, List.of(c), c, List.of(a));
        List<UUID> cycle = CycleDetector.findCycle(graph, a, b);
        assertThat(cycle).containsExactly(a, b, c, a);
    }

    @Test
    void cycleViaSharedDependency_isRejected() {
        // B -> C, C -> A (A is reachable from B); adding A -> B closes the cycle
        Map<UUID, List<UUID>> graph = Map.of(b, List.of(c, d), c, List.of(a));
        List<UUID> cycle = CycleDetector.findCycle(graph, a, b);
        // the path must start at A, go through B, and reach A again
        assertThat(cycle).first().isEqualTo(a);
        assertThat(cycle).last().isEqualTo(a);
        assertThat(cycle).contains(b, c);
    }
}
