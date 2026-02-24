package com.myster.access;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

/**
 * Tests for {@link OpType} extensible string-based operation type.
 * Verifies canonical/non-canonical behavior and wire format string stability.
 */
class TestOpType {

    @Test
    void knownTypesAreCanonical() {
        assertTrue(OpType.SET_POLICY.isCanonical());
        assertTrue(OpType.ADD_WRITER.isCanonical());
        assertTrue(OpType.REMOVE_WRITER.isCanonical());
        assertTrue(OpType.ADD_MEMBER.isCanonical());
        assertTrue(OpType.REMOVE_MEMBER.isCanonical());
        assertTrue(OpType.ADD_ONRAMP.isCanonical());
        assertTrue(OpType.REMOVE_ONRAMP.isCanonical());
        assertTrue(OpType.SET_TYPE_PUBLIC_KEY.isCanonical());
        assertTrue(OpType.SET_NAME.isCanonical());
        assertTrue(OpType.SET_DESCRIPTION.isCanonical());
        assertTrue(OpType.SET_EXTENSIONS.isCanonical());
        assertTrue(OpType.SET_SEARCH_IN_ARCHIVES.isCanonical());
    }

    @Test
    void unknownStringIsNonCanonical() {
        OpType future = OpType.fromString("FUTURE_OP_2030");
        assertFalse(future.isCanonical());
        assertEquals("FUTURE_OP_2030", future.getIdentifier());
    }

    @Test
    void knownStringReturnsCanonicalConstant() {
        assertSame(OpType.SET_POLICY, OpType.fromString("SET_POLICY"));
        assertSame(OpType.ADD_MEMBER, OpType.fromString("ADD_MEMBER"));
        assertSame(OpType.SET_NAME, OpType.fromString("SET_NAME"));
    }

    @Test
    void equalityIsByIdentifierString() {
        OpType a = OpType.fromString("FUTURE_OP");
        OpType b = OpType.fromString("FUTURE_OP");
        assertEquals(a, b);
        assertEquals(a.hashCode(), b.hashCode());
    }

    @Test
    void differentIdentifiersAreNotEqual() {
        assertNotEquals(OpType.SET_POLICY, OpType.ADD_WRITER);
        assertNotEquals(OpType.fromString("A"), OpType.fromString("B"));
    }

    /**
     * Guard against accidental renames — if any of these fail, the wire format has broken.
     */
    @Test
    void opTypeStringsNeverChange() {
        assertEquals("SET_POLICY", OpType.SET_POLICY.getIdentifier());
        assertEquals("ADD_WRITER", OpType.ADD_WRITER.getIdentifier());
        assertEquals("REMOVE_WRITER", OpType.REMOVE_WRITER.getIdentifier());
        assertEquals("ADD_MEMBER", OpType.ADD_MEMBER.getIdentifier());
        assertEquals("REMOVE_MEMBER", OpType.REMOVE_MEMBER.getIdentifier());
        assertEquals("ADD_ONRAMP", OpType.ADD_ONRAMP.getIdentifier());
        assertEquals("REMOVE_ONRAMP", OpType.REMOVE_ONRAMP.getIdentifier());
        assertEquals("SET_TYPE_PUBLIC_KEY", OpType.SET_TYPE_PUBLIC_KEY.getIdentifier());
        assertEquals("SET_NAME", OpType.SET_NAME.getIdentifier());
        assertEquals("SET_DESCRIPTION", OpType.SET_DESCRIPTION.getIdentifier());
        assertEquals("SET_EXTENSIONS", OpType.SET_EXTENSIONS.getIdentifier());
        assertEquals("SET_SEARCH_IN_ARCHIVES", OpType.SET_SEARCH_IN_ARCHIVES.getIdentifier());
    }
}

