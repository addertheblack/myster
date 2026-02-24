package com.myster.access;

import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Extensible, string-based operation type identifier for access list blocks.
 *
 * <p>Operations are identified by string names in the serialized format rather than numeric
 * codes, providing forward compatibility: a node running an older version can read an access
 * list containing operations it doesn't recognize. Unknown operations are preserved in the
 * chain and can be displayed in the UI as their raw string type.
 *
 * <p>Uses the extensible enum pattern:
 * <ul>
 *   <li><b>Canonical</b> types are ones this version of the code recognizes and can interpret.</li>
 *   <li><b>Non-canonical</b> types represent unknown operations from future versions — they are
 *       preserved in the chain but their effect on derived state is skipped.</li>
 * </ul>
 *
 * <p>Using strings instead of numbers avoids conflicts when two groups independently extend
 * the format (e.g. {@code "SET_POLICY"} vs {@code "SET_NAME"} are self-describing and
 * unlikely to collide).
 */
public final class OpType {

    // Known canonical types — access control operations
    public static final OpType SET_POLICY = new OpType("SET_POLICY");
    public static final OpType ADD_WRITER = new OpType("ADD_WRITER");
    public static final OpType REMOVE_WRITER = new OpType("REMOVE_WRITER");
    public static final OpType ADD_MEMBER = new OpType("ADD_MEMBER");
    public static final OpType REMOVE_MEMBER = new OpType("REMOVE_MEMBER");
    public static final OpType ADD_ONRAMP = new OpType("ADD_ONRAMP");
    public static final OpType REMOVE_ONRAMP = new OpType("REMOVE_ONRAMP");

    // Known canonical types — type metadata operations
    public static final OpType SET_TYPE_PUBLIC_KEY = new OpType("SET_TYPE_PUBLIC_KEY");
    public static final OpType SET_NAME = new OpType("SET_NAME");
    public static final OpType SET_DESCRIPTION = new OpType("SET_DESCRIPTION");
    public static final OpType SET_EXTENSIONS = new OpType("SET_EXTENSIONS");
    public static final OpType SET_SEARCH_IN_ARCHIVES = new OpType("SET_SEARCH_IN_ARCHIVES");

    private static final Map<String, OpType> KNOWN_TYPES = new ConcurrentHashMap<>();

    static {
        KNOWN_TYPES.put(SET_POLICY.identifier, SET_POLICY);
        KNOWN_TYPES.put(ADD_WRITER.identifier, ADD_WRITER);
        KNOWN_TYPES.put(REMOVE_WRITER.identifier, REMOVE_WRITER);
        KNOWN_TYPES.put(ADD_MEMBER.identifier, ADD_MEMBER);
        KNOWN_TYPES.put(REMOVE_MEMBER.identifier, REMOVE_MEMBER);
        KNOWN_TYPES.put(ADD_ONRAMP.identifier, ADD_ONRAMP);
        KNOWN_TYPES.put(REMOVE_ONRAMP.identifier, REMOVE_ONRAMP);
        KNOWN_TYPES.put(SET_TYPE_PUBLIC_KEY.identifier, SET_TYPE_PUBLIC_KEY);
        KNOWN_TYPES.put(SET_NAME.identifier, SET_NAME);
        KNOWN_TYPES.put(SET_DESCRIPTION.identifier, SET_DESCRIPTION);
        KNOWN_TYPES.put(SET_EXTENSIONS.identifier, SET_EXTENSIONS);
        KNOWN_TYPES.put(SET_SEARCH_IN_ARCHIVES.identifier, SET_SEARCH_IN_ARCHIVES);
    }

    private final String identifier;
    private final boolean canonical;

    private OpType(String identifier, boolean canonical) {
        this.identifier = Objects.requireNonNull(identifier);
        this.canonical = canonical;
    }

    private OpType(String identifier) {
        this(identifier, true);
    }

    /**
     * Returns the known canonical constant for the given identifier string, or creates a
     * non-canonical instance if the identifier is not recognized.
     *
     * @param identifier the serialized operation type string
     * @return the corresponding OpType (canonical if known, non-canonical otherwise)
     */
    public static OpType fromString(String identifier) {
        OpType known = KNOWN_TYPES.get(identifier);
        if (known != null) {
            return known;
        }
        return new OpType(identifier, false);
    }

    /**
     * Returns the string identifier used in the serialized format.
     *
     * @return the operation type identifier
     */
    public String getIdentifier() {
        return identifier;
    }

    /**
     * Whether this operation type is recognized by the current version.
     * Non-canonical types come from future versions and their effect on
     * derived state is skipped during chain replay.
     *
     * @return true if this is a known operation type
     */
    public boolean isCanonical() {
        return canonical;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof OpType other)) return false;
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


