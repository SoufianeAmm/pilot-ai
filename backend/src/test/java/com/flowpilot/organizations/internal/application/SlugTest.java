package com.flowpilot.organizations.internal.application;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class SlugTest {

    @ParameterizedTest
    @CsvSource(delimiter = '|', value = {
            "Acme Corp|acme-corp",
            "Équipe Données & IA|equipe-donnees-ia",
            "  --Hello__World--  |hello-world",
            "!!!|org",
            "FlowPilot 2026|flowpilot-2026"
    })
    void slugify_producesUrlSafeNames(String name, String expected) {
        String slug = OrganizationService.slugify(name);

        assertThat(slug).isEqualTo(expected);
        assertThat(OrganizationService.SLUG.matcher(slug).matches()).isTrue();
    }

    @ParameterizedTest
    @CsvSource({"a", "abcdefghijklmnopqrstuvwxyz abcdefghijklmnopqrstuvwxyz abcdefghijklmnopqrstuvwxyz"})
    void slugify_leavesRoomForASuffix(String name) {
        assertThat(OrganizationService.slugify(name).length()).isLessThanOrEqualTo(55);
    }
}
