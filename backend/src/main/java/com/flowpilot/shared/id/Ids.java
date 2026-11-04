package com.flowpilot.shared.id;

import java.security.SecureRandom;
import java.util.UUID;

/**
 * Generates time-ordered UUIDs (version 7, RFC 9562): 48-bit Unix milliseconds followed by
 * random bits. They are not guessable, and new rows land at the end of B-tree indexes,
 * which keeps inserts fast compared to fully random (v4) UUIDs.
 */
public final class Ids {

    private static final SecureRandom RANDOM = new SecureRandom();

    private Ids() {
    }

    public static UUID newId() {
        return newId(System.currentTimeMillis());
    }

    static UUID newId(long epochMillis) {
        long mostSignificant = ((epochMillis & 0xFFFF_FFFF_FFFFL) << 16)
                | 0x7000L                               // version 7
                | (RANDOM.nextInt() & 0x0FFFL);         // 12 random bits
        long leastSignificant = (RANDOM.nextLong() & 0x3FFF_FFFF_FFFF_FFFFL)
                | 0x8000_0000_0000_0000L;               // IETF variant
        return new UUID(mostSignificant, leastSignificant);
    }
}
