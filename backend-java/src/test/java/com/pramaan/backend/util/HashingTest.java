package com.pramaan.backend.util;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

class HashingTest {

    @Test
    void sha256MatchesKnownVector() {
        // SHA-256("abc")
        assertThat(Hashing.sha256Hex("abc".getBytes(StandardCharsets.UTF_8)))
                .isEqualTo("ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad");
    }

    @Test
    void matchesIsCaseInsensitiveAndRejectsNull() {
        byte[] c = "payload".getBytes(StandardCharsets.UTF_8);
        String hex = Hashing.sha256Hex(c);
        assertThat(Hashing.matches(c, hex.toUpperCase())).isTrue();
        assertThat(Hashing.matches(c, null)).isFalse();
        assertThat(Hashing.matches("other".getBytes(StandardCharsets.UTF_8), hex)).isFalse();
    }
}
