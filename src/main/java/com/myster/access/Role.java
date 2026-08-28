package com.myster.access;

import java.util.Map;

import com.general.util.TypeSafeEnum;

/**
 * Extensible, string-based role identifier for access list members.
 *
 * <p>Extends {@link TypeSafeEnum}: known roles are canonical constants, while unknown roles from
 * future versions are preserved as non-canonical instances. This allows older nodes to read access
 * lists containing roles they don't recognize without crashing.
 *
 * <p>Known roles:
 * <ul>
 *   <li>{@link #MEMBER} — can access files of this type</li>
 *   <li>{@link #ADMIN} — can access files and implies writer status (can modify the access list)</li>
 * </ul>
 */
public final class Role extends TypeSafeEnum<Role> {
    public static final Role MEMBER = new Role("MEMBER");
    public static final Role ADMIN = new Role("ADMIN");

    private static final Map<String, Role> KNOWN_ROLES = canonicalValueMap(MEMBER, ADMIN);

    private Role(String identifier, boolean canonical) {
        super(identifier, canonical);
    }

    private Role(String identifier) {
        super(identifier);
    }

    /**
     * Returns the known canonical constant for the given identifier string, or creates a
     * non-canonical instance if the identifier is not recognized.
     *
     * @param identifier the serialized role string
     * @return the corresponding Role (canonical if known, non-canonical otherwise)
     */
    public static Role fromString(String identifier) {
        return from(identifier, KNOWN_ROLES, id -> new Role(id, false));
    }
}
