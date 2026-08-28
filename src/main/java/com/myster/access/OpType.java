package com.myster.access;

import java.util.Map;

import com.general.util.TypeSafeEnum;

/**
 * Extensible, string-based operation type identifier for access list blocks.
 *
 * <p>Operations are identified by string names in the serialized format rather than numeric
 * codes, providing forward compatibility: a node running an older version can read an access
 * list containing operations it doesn't recognize. Unknown operations are preserved in the
 * chain and can be displayed in the UI as their raw string type.
 *
 * <p>Extends {@link TypeSafeEnum} using the extensible enum pattern:
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
public final class OpType extends TypeSafeEnum<OpType> {

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
    public static final OpType SET_METADATA_TYPE = new OpType("SET_METADATA_TYPE");

    private static final Map<String, OpType> KNOWN_TYPES = canonicalValueMap(
            SET_POLICY,
            ADD_WRITER,
            REMOVE_WRITER,
            ADD_MEMBER,
            REMOVE_MEMBER,
            ADD_ONRAMP,
            REMOVE_ONRAMP,
            SET_TYPE_PUBLIC_KEY,
            SET_NAME,
            SET_DESCRIPTION,
            SET_EXTENSIONS,
            SET_SEARCH_IN_ARCHIVES,
            SET_METADATA_TYPE);

    private OpType(String identifier, boolean canonical) {
        super(identifier, canonical);
    }

    private OpType(String identifier) {
        super(identifier);
    }

    /**
     * Returns the known canonical constant for the given identifier string, or creates a
     * non-canonical instance if the identifier is not recognized.
     *
     * @param identifier the serialized operation type string
     * @return the corresponding OpType (canonical if known, non-canonical otherwise)
     */
    public static OpType fromString(String identifier) {
        return from(identifier, KNOWN_TYPES, id -> new OpType(id, false));
    }
}
