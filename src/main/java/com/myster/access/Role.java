package com.myster.access;

import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Extensible, string-based role identifier for access list members.
 *
 * <p>Uses the same extensible enum pattern as {@link OpType}: known roles are canonical
 * constants, while unknown roles from future versions are preserved as non-canonical
 * instances. This allows older nodes to read access lists containing roles they don't
 * recognize without crashing.
 *
 * <p>Known roles:
 * <ul>
 *   <li>{@link #MEMBER} — can access files of this type</li>
 *   <li>{@link #ADMIN} — can access files and implies writer status (can modify the access list)</li>
 * </ul>
 */
public final class Role {
    public static final Role MEMBER = new Role("MEMBER", true);
    public static final Role ADMIN = new Role("ADMIN", true);

    private static final Map<String, Role> KNOWN_ROLES = new ConcurrentHashMap<>();

    static {
        KNOWN_ROLES.put(MEMBER.identifier, MEMBER);
        KNOWN_ROLES.put(ADMIN.identifier, ADMIN);
    }

    private final String identifier;
    private final boolean canonical;

    private Role(String identifier, boolean canonical) {
        this.identifier = Objects.requireNonNull(identifier);
        this.canonical = canonical;
    }

    /**
     * Returns the known canonical constant for the given identifier string, or creates a
     * non-canonical instance if the identifier is not recognized.
     *
     * @param identifier the serialized role string
     * @return the corresponding Role (canonical if known, non-canonical otherwise)
     */
    public static Role fromString(String identifier) {
        Role known = KNOWN_ROLES.get(identifier);
        if (known != null) {
            return known;
        }
        return new Role(identifier, false);
    }

    /**
     * Returns the string identifier used in the serialized format.
     *
     * @return the role identifier
     */
    public String getIdentifier() {
        return identifier;
    }

    /**
     * Whether this role is recognized by the current version.
     *
     * @return true if this is a known role
     */
    public boolean isCanonical() {
        return canonical;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Role other)) return false;
        return identifier.equals(other.identifier);
    }

    @Override
    public int hashCode() {
        return identifier.hashCode();
    }

    @Override
    public String toString() {
        return identifier + (canonical ? "" : " (non-canonical)");
    }
}
