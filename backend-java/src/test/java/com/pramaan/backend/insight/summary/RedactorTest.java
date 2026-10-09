package com.pramaan.backend.insight.summary;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class RedactorTest {

    @Test
    void sensitiveKeysAreRecognisedCaseInsensitively() {
        for (String key : new String[]{"password", "DB_PASSWD", "clientSecret", "accessToken", "api_key", "API-KEY",
                "apikey", "private_key", "PrivateKey", "credentials", "Authorization", "bearer"}) {
            assertThat(Redactor.isSensitiveKey(key)).as(key).isTrue();
        }
        for (String key : new String[]{"status", "controlId", "observed", "command", "title"}) {
            assertThat(Redactor.isSensitiveKey(key)).as(key).isFalse();
        }
    }

    @Test
    void factUnderASensitiveLabelHidesItsValue() {
        FactSink sink = new FactSink();
        sink.fact("db_password", "hunter2", "/db_password");
        sink.fact("status", "ok", "/status");
        Extraction e = sink.build(ContentKind.TEXT, "");
        assertThat(e.facts()).extracting("value").containsExactly("[REDACTED]", "ok");
    }

    @Test
    void pemPrivateKeyBlocksAreHiddenWhereverTheyAppear() {
        String pem = "-----BEGIN RSA PRIVATE KEY-----\nMIIEowIBAAKCAQEA\nabc\n-----END RSA PRIVATE KEY-----";
        assertThat(Redactor.looksLikePrivateKey(pem)).isTrue();
        assertThat(FactSink.value("note", pem)).isEqualTo("[REDACTED]");
        assertThat(Redactor.redactText("before " + pem + " after")).isEqualTo("before [REDACTED] after");
    }

    @Test
    void redactionIsIdempotentSoTextScrubbedTwiceStaysClean() {
        for (String s : new String[]{"password=hunter2", "apiKey: abc123 exposed", "Authorization: Bearer abc.def",
                "{\"token\": \"t-1\"}", "-----BEGIN PRIVATE KEY-----\nx\n-----END PRIVATE KEY-----"}) {
            String once = Redactor.redactText(s);
            assertThat(Redactor.redactText(once)).isEqualTo(once);
        }
        assertThat(Redactor.redactText(Redactor.redactText("password=hunter2 ok"))).isEqualTo("password=[REDACTED] ok");
    }

    @Test
    void keyValuePairsAndBearerTokensInFreeTextAreScrubbed() {
        assertThat(Redactor.redactText("connect with password=hunter2 now")).isEqualTo("connect with password=[REDACTED] now");
        assertThat(Redactor.redactText("{\"api_key\": \"abc123\"}")).doesNotContain("abc123");
        assertThat(Redactor.redactText("Authorization: Bearer eyJhbGciOi.abc.def")).doesNotContain("eyJhbGciOi");
        assertThat(Redactor.redactText("server_tokens off")).isEqualTo("server_tokens off");
        assertThat(Redactor.redactText("Password Policy: scram-sha-256")).isEqualTo("Password Policy: scram-sha-256");
    }
}
