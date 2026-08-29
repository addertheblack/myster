package com.myster.tracker.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;

class TestServerAddressCandidate {
    @Test
    void acceptsAndNormalizesIpv4AndDnsCandidates() {
        assertCandidate(" 127.000.0.1 ", "127.0.0.1");
        assertCandidate("127.0.0.1:7000", "127.0.0.1:7000");
        assertCandidate("Example.COM", "example.com");
        assertCandidate("Example.COM:6669", "example.com");
        assertCandidate("sub-domain.example.com:1", "sub-domain.example.com:1");
    }

    @Test
    void rejectsSearchTextAndMalformedAddresses() {
        List<String> rejected = List.of(
                "", "server", "two words.com", "http://example.com", "example.com/path",
                ".example.com", "example..com", "example.com.", "-bad.example", "bad-.example",
                "bad_name.example", "999.1.1.1", "1.2.3", "1.2.3.4.5", "1..2.3",
                "example.com:", "example.com:nope", "example.com:0", "example.com:65536",
                "2001:db8::1", "[2001:db8::1]");

        rejected.forEach(value -> assertTrue(
                ServerAddressCandidate.parse(value).isEmpty(), value));
    }

    private static void assertCandidate(String input, String normalized) {
        ServerAddressCandidate candidate = ServerAddressCandidate.parse(input).orElseThrow();
        assertEquals(normalized, candidate.lookupKey());
        assertEquals(normalized, candidate.addressText());
    }
}
