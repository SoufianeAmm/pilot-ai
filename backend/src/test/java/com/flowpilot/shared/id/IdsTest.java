package com.flowpilot.shared.id;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.Test;

class IdsTest {

    @Test
    void newId_isVersion7WithIetfVariant() {
        UUID id = Ids.newId();

        assertThat(id.version()).isEqualTo(7);
        assertThat(id.variant()).isEqualTo(2);
    }

    @Test
    void newId_laterTimestamp_sortsAfterEarlierOne() {
        UUID earlier = Ids.newId(1_700_000_000_000L);
        UUID later = Ids.newId(1_700_000_000_001L);

        assertThat(later.compareTo(earlier)).isPositive();
    }

    @Test
    void newId_sameMillisecond_isStillUnique() {
        Set<UUID> ids = new HashSet<>();
        for (int i = 0; i < 10_000; i++) {
            ids.add(Ids.newId(1_700_000_000_000L));
        }

        assertThat(ids).hasSize(10_000);
    }
}
