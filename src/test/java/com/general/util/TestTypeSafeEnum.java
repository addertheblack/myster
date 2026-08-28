package com.general.util;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Map;

import org.junit.jupiter.api.Test;

class TestTypeSafeEnum {
    @Test
    void resolvesCanonicalSingletonOrEquivalentUnknownValue() {
        assertSame(ExampleValue.FIRST, ExampleValue.fromString("first"));

        ExampleValue future = ExampleValue.fromString("future");
        ExampleValue repeated = ExampleValue.fromString("future");
        assertFalse(future.isCanonical());
        assertEquals("future", future.getIdentifier());
        assertEquals(future, repeated);
        assertEquals(future.hashCode(), repeated.hashCode());
        assertEquals("future (non-canonical)", future.toString());
    }

    @Test
    void equalityRequiresTheSameConcreteEnumType() {
        assertNotEquals(ExampleValue.FIRST, OtherValue.FIRST);
        assertNotEquals(ExampleValue.fromString("future"), OtherValue.fromString("future"));
        assertTrue(ExampleValue.FIRST.isCanonical());
        assertTrue(OtherValue.FIRST.isCanonical());
    }

    @Test
    void canonicalIndexRejectsUnknownAndDuplicateValues() {
        assertThrows(IllegalArgumentException.class,
                () -> ExampleValue.index(ExampleValue.FIRST, new ExampleValue("first")));
        assertThrows(IllegalArgumentException.class,
                () -> ExampleValue.index(new ExampleValue("future", false)));
    }

    private static final class ExampleValue extends TypeSafeEnum<ExampleValue> {
        private static final ExampleValue FIRST = new ExampleValue("first");
        private static final Map<String, ExampleValue> VALUES = canonicalValueMap(FIRST);

        private ExampleValue(String identifier) {
            super(identifier);
        }

        private ExampleValue(String identifier, boolean canonical) {
            super(identifier, canonical);
        }

        private static ExampleValue fromString(String identifier) {
            return from(identifier, VALUES, id -> new ExampleValue(id, false));
        }

        private static Map<String, ExampleValue> index(ExampleValue... values) {
            return canonicalValueMap(values);
        }
    }

    private static final class OtherValue extends TypeSafeEnum<OtherValue> {
        private static final OtherValue FIRST = new OtherValue("first");
        private static final Map<String, OtherValue> VALUES = canonicalValueMap(FIRST);

        private OtherValue(String identifier) {
            super(identifier);
        }

        private OtherValue(String identifier, boolean canonical) {
            super(identifier, canonical);
        }

        private static OtherValue fromString(String identifier) {
            return from(identifier, VALUES, id -> new OtherValue(id, false));
        }
    }
}
