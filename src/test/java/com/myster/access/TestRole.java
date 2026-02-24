package com.myster.access;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for {@link Role} extensible string-based role identifier.
 * Verifies canonical/non-canonical behavior and wire format string stability.
 */
class TestRole {

    @Test
    void knownRolesAreCanonical() {
        assertTrue(Role.MEMBER.isCanonical());
        assertTrue(Role.ADMIN.isCanonical());
    }

    @Test
    void unknownRoleIsNonCanonical() {
        Role future = Role.fromString("MODERATOR");
        assertFalse(future.isCanonical());
        assertEquals("MODERATOR", future.getIdentifier());
    }

    @Test
    void knownStringReturnsCanonicalConstant() {
        assertSame(Role.MEMBER, Role.fromString("MEMBER"));
        assertSame(Role.ADMIN, Role.fromString("ADMIN"));
    }

    @Test
    void equalityIsByIdentifier() {
        assertEquals(Role.fromString("MODERATOR"), Role.fromString("MODERATOR"));
        assertNotEquals(Role.MEMBER, Role.ADMIN);
    }

    /**
     * Guard against accidental renames — if any of these fail, the wire format has broken.
     */
    @Test
    void roleStringsNeverChange() {
        assertEquals("MEMBER", Role.MEMBER.getIdentifier());
        assertEquals("ADMIN", Role.ADMIN.getIdentifier());
    }
}

