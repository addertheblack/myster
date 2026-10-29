package com.myster.tracker.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Locale;

import org.junit.jupiter.api.Test;

class TestServerLookupCandidate {
    private static final String CID = "46e22ea215589a03f3d8751d241405fb";

    @Test
    void recognizes128BitCidWithCaseAndOuterWhitespace() {
        for (String input : List.of(CID, CID.toUpperCase(Locale.ROOT), "  " + CID + "\n")) {
            var candidate = assertInstanceOf(ServerLookupCandidate.Cid.class,
                    ServerLookupCandidate.parse(input).orElseThrow());

            assertEquals(CID, candidate.cid().asHex());
            assertEquals(CID, candidate.displayText());
        }
    }

    @Test
    void acceptsTheFull128BitRangeIncludingLeadingZeros() {
        for (String hex : List.of("0".repeat(32), "f".repeat(32), "0" + CID.substring(1))) {
            var candidate = assertInstanceOf(ServerLookupCandidate.Cid.class,
                    ServerLookupCandidate.parse(hex).orElseThrow());
            assertEquals(hex, candidate.cid().asHex());
        }
    }

    @Test
    void malformedOrPartialCidsRemainSearchText() {
        for (String text : List.of(CID.substring(1), CID + "0", "g" + CID.substring(1),
                                   "0x" + CID, CID.substring(0, 16) + " " + CID.substring(16),
                                   CID + ":6669", "", "server")) {
            assertTrue(ServerLookupCandidate.parse(text).isEmpty(), text);
        }
        assertTrue(ServerLookupCandidate.parse(null).isEmpty());
    }

    @Test
    void preservesAddressParsingAndNormalization() {
        for (String text : List.of("Example.COM:7000", "127.000.0.1")) {
            var candidate = assertInstanceOf(ServerLookupCandidate.Address.class,
                    ServerLookupCandidate.parse(text).orElseThrow());
            assertEquals(ServerAddressCandidate.parse(text).orElseThrow(), candidate.address());
            assertEquals(candidate.address().addressText(), candidate.displayText());
        }
    }
}
