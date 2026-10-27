package com.myster.link;

import static org.junit.jupiter.api.Assertions.*;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import com.myster.cid.ServerCid;
import com.myster.type.MysterType;

class TestMysterLinkUri {
    private static final String SERVER = "0123456789abcdef0123456789abcdef";
    private static final String TYPE = "fedcba9876543210fedcba9876543210";
    private static final String BASE = "myster://link/v1?server=" + SERVER + "&type=" + TYPE;

    @Test void exactlyThreeOfTheEightFieldCombinationsAreValid() throws Exception {
        String[] fields = {"server=" + SERVER, "type=" + TYPE, "name=file"};
        int accepted = 0;
        for (int mask = 0; mask < 8; mask++) {
            List<String> present = new ArrayList<>();
            for (int bit = 0; bit < 3; bit++) if ((mask & 1 << bit) != 0) present.add(fields[bit]);
            String text = "myster://link/v1?" + String.join("&", present);
            boolean server = (mask & 1) != 0;
            boolean type = (mask & 2) != 0;
            boolean name = (mask & 4) != 0;
            boolean valid = server && (type || !name);
            if (valid) {
                var uri = MysterLinkUri.parse(text);
                assertEquals(text, uri.toString());
                assertEquals(type, uri.type().isPresent());
                assertEquals(name, uri.name().isPresent());
                accepted++;
            } else assertThrows(IOException.class, () -> MysterLinkUri.parse(text), text);
        }
        assertEquals(3, accepted);
    }

    @Test void namesRoundTripWithoutFormOrPathNormalization() throws Exception {
        for (String name : List.of("a + % & = # ?", "folder/日本語 🐱", "a\\b", "%2F", "\u30ab\u3099")) {
            var uri = MysterLinkUri.create(new ServerCid(HexFormat.of().parseHex(SERVER)),
                    Optional.of(MysterType.fromHexString(TYPE)), Optional.of(name));
            assertEquals(name, MysterLinkUri.parse(uri.toString()).name().orElseThrow());
        }
        assertEquals("a+b", MysterLinkUri.parse(BASE + "&name=a+b").name().orElseThrow());
        var unordered = MysterLinkUri.parse("myster://link/v1?name=x&type=" + TYPE + "&server=" + SERVER);
        assertEquals(BASE + "&name=x", unordered.toString());
    }

    @ParameterizedTest
    @ValueSource(strings = {"&name=", "&type=", "&name=%00", "&name=%C0%AF", "&name=%ED%A0%80",
            "&name=日本語", "&name=x&name=y", "&unknown=x", "&hash_type=md5", "&hash=0",
            "&name=x&hash_type=sha-256&hash=00", "&hash_type=md5&hash=00", "&name=file&hash_type=md5&hash=0123456789abcdef0123456789abcdef"})
    void invalidFieldsCannotDegradeToABroaderLocation(String suffix) {
        assertThrows(IOException.class, () -> MysterLinkUri.parse(BASE + suffix));
    }

    @ParameterizedTest
    @ValueSource(strings = {"myster://file/v1?server=", "myster://link/v2?server=", "https://link/v1?server=",
            "myster://u@link/v1?server=", "myster://link:123/v1?server=", "myster://link/v1/other?server="})
    void rejectsOtherAuthoritiesVersionsAndSchemes(String prefix) {
        assertThrows(IOException.class, () -> MysterLinkUri.parse(prefix + SERVER));
    }

    @Test void boundsFragmentsAndMissingParentAreRejected() {
        assertThrows(IOException.class, () -> MysterLinkUri.parse(BASE + "#fragment"));
        assertThrows(IOException.class, () -> MysterLinkUri.parse(BASE + "&name=" + "x".repeat(16 * 1024)));
        assertThrows(IOException.class, () -> MysterLinkUri.create(new ServerCid(new byte[16]),
                Optional.empty(), Optional.of("file")));

    }
}
