package com.flowpilot.projects;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.UUID;

import org.junit.jupiter.api.Test;

import com.flowpilot.shared.web.ApiException;

/**
 * Permission matrix and {@link ProjectScope} behavior, as specified in
 * {@code docs/01-architecture.md} section 6. Pure unit test: no Spring context, no database, so it
 * runs anywhere the JDK does (including CI without Docker).
 */
class ProjectRoleTest {

    @Test
    void manager_has_every_permission() {
        for (ProjectPermission permission : ProjectPermission.values()) {
            assertThat(ProjectRole.MANAGER.has(permission))
                    .as("MANAGER should hold %s", permission)
                    .isTrue();
        }
    }

    @Test
    void contributor_can_create_and_edit_tasks_upload_and_ask_ai_but_cannot_comment_or_manage_the_project() {
        ProjectRole contributor = ProjectRole.CONTRIBUTOR;

        assertThat(contributor.has(ProjectPermission.PROJECT_READ)).isTrue();
        assertThat(contributor.has(ProjectPermission.TASK_CREATE)).isTrue();
        assertThat(contributor.has(ProjectPermission.TASK_EDIT)).isTrue();
        assertThat(contributor.has(ProjectPermission.DOCUMENT_UPLOAD)).isTrue();
        assertThat(contributor.has(ProjectPermission.AI_ASK)).isTrue();
        assertThat(contributor.has(ProjectPermission.ANALYTICS_VIEW)).isTrue();

        // contributors do not comment (manager-only) and cannot manage project-wide concerns
        assertThat(contributor.has(ProjectPermission.COMMENT_WRITE)).isFalse();
        assertThat(contributor.has(ProjectPermission.MEMBER_MANAGE)).isFalse();
        assertThat(contributor.has(ProjectPermission.MILESTONE_MANAGE)).isFalse();
        assertThat(contributor.has(ProjectPermission.PROJECT_UPDATE)).isFalse();
        assertThat(contributor.has(ProjectPermission.PROJECT_DELETE)).isFalse();
        assertThat(contributor.has(ProjectPermission.TASK_MANAGE_ANY)).isFalse();
        assertThat(contributor.has(ProjectPermission.DOCUMENT_MANAGE_ANY)).isFalse();
    }

    @Test
    void viewer_is_read_only_plus_ai_and_analytics() {
        ProjectRole viewer = ProjectRole.VIEWER;

        assertThat(viewer.has(ProjectPermission.PROJECT_READ)).isTrue();
        assertThat(viewer.has(ProjectPermission.AI_ASK)).isTrue();
        assertThat(viewer.has(ProjectPermission.ANALYTICS_VIEW)).isTrue();

        assertThat(viewer.has(ProjectPermission.TASK_CREATE)).isFalse();
        assertThat(viewer.has(ProjectPermission.COMMENT_WRITE)).isFalse();
        assertThat(viewer.has(ProjectPermission.DOCUMENT_UPLOAD)).isFalse();
        assertThat(viewer.has(ProjectPermission.PROJECT_UPDATE)).isFalse();
    }

    @Test
    void roles_rank_manager_above_contributor_above_viewer() {
        assertThat(ProjectRole.MANAGER.outranks(ProjectRole.CONTRIBUTOR)).isTrue();
        assertThat(ProjectRole.CONTRIBUTOR.outranks(ProjectRole.VIEWER)).isTrue();
        assertThat(ProjectRole.VIEWER.outranks(ProjectRole.MANAGER)).isFalse();
        assertThat(ProjectRole.MANAGER.outranks(ProjectRole.MANAGER)).isFalse();
    }

    @Test
    void scope_require_throws_forbidden_when_permission_is_missing() {
        ProjectScope viewerScope = new ProjectScope(
                UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), ProjectRole.VIEWER);

        assertThatThrownBy(() -> viewerScope.require(ProjectPermission.TASK_CREATE))
                .isInstanceOf(ApiException.class);

        // a manager scope holds every permission, so require() never throws
        ProjectScope managerScope = new ProjectScope(
                viewerScope.orgId(), viewerScope.projectId(), viewerScope.userId(), ProjectRole.MANAGER);
        assertThatCode(() -> managerScope.require(ProjectPermission.TASK_CREATE))
                .doesNotThrowAnyException();
    }
}
