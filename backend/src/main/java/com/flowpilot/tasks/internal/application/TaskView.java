package com.flowpilot.tasks.internal.application;

import com.flowpilot.tasks.internal.domain.Task;

/**
 * A task paired with the derived fields the API exposes: the human key ("FP-42"), and the computed
 * {@code blocked} / {@code overdue} flags (never stored — see docs/03-data-model "Derived, not
 * stored"). Built by {@link TaskService}.
 */
public record TaskView(Task task, String key, boolean blocked, boolean overdue) {
}
