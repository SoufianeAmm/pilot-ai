package com.flowpilot.projects;

/**
 * Project-level permissions. Which role grants which permission is defined in
 * {@link ProjectRole#permissions()}.
 *
 * <p>Task and document <em>ownership</em> rules ("a contributor may delete only their own tasks") are
 * enforced inside the {@code tasks} and {@code documents} modules by checking authorship, not by
 * adding finer-grained permissions here. This keeps the project-role matrix coarse and stable.
 */
public enum ProjectPermission {
    PROJECT_READ,
    PROJECT_UPDATE,
    PROJECT_DELETE,
    MEMBER_MANAGE,
    MILESTONE_MANAGE,
    TASK_CREATE,
    /** Edit task fields (title, description, status, priority, dates, assignee, milestone). */
    TASK_EDIT,
    /** Edit, reassign, close or delete <em>any</em> task in the project (manager only). */
    TASK_MANAGE_ANY,
    COMMENT_WRITE,
    DOCUMENT_UPLOAD,
    /** Delete <em>any</em> document in the project (manager only; contributors delete only their own). */
    DOCUMENT_MANAGE_ANY,
    AI_ASK,
    ANALYTICS_VIEW
}
