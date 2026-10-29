package com.myster.tracker.ui;

import java.util.HexFormat;
import java.util.Optional;
import java.util.regex.Pattern;

import com.myster.cid.ServerCid;

/** Unambiguous address or 128-bit server CID entered in the server picker. */
sealed interface ServerLookupCandidate {
    Pattern CID_HEX = Pattern.compile("[0-9a-fA-F]{32}");

    record Address(ServerAddressCandidate address) implements ServerLookupCandidate {}

    record Cid(ServerCid cid) implements ServerLookupCandidate {}

    /** Parses without DNS or network work; other text remains an ordinary server-name search. */
    static Optional<ServerLookupCandidate> parse(String text) {
        if (text == null) {
            return Optional.empty();
        }

        String trimmed = text.trim();
        if (CID_HEX.matcher(trimmed).matches()) {
            return Optional.of(new Cid(new ServerCid(HexFormat.of().parseHex(trimmed))));
        }
        return ServerAddressCandidate.parse(trimmed).map(Address::new);
    }

    default String displayText() {
        return switch (this) {
            case Address(var address) -> address.addressText();
            case Cid(var cid) -> cid.asHex();
        };
    }
}
