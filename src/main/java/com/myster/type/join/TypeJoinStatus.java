package com.myster.type.join;

import java.util.Map;
import java.util.regex.Pattern;

import com.general.util.TypeSafeEnum;

/** Forward-compatible string status returned by TCP stream section 126. */
public final class TypeJoinStatus extends TypeSafeEnum<TypeJoinStatus> {
    public static final TypeJoinStatus APPROVED = new TypeJoinStatus("APPROVED");
    public static final TypeJoinStatus ALREADY_MEMBER = new TypeJoinStatus("ALREADY_MEMBER");
    public static final TypeJoinStatus INVITATION_NOT_ACCEPTED =
            new TypeJoinStatus("INVITATION_NOT_ACCEPTED");
    public static final TypeJoinStatus TYPE_NOT_FOUND = new TypeJoinStatus("TYPE_NOT_FOUND");
    public static final TypeJoinStatus NOT_AUTHORIZER = new TypeJoinStatus("NOT_AUTHORIZER");
    public static final TypeJoinStatus ERROR = new TypeJoinStatus("ERROR");

    private static final Pattern VALID = Pattern.compile("[A-Z][A-Z0-9_]{0,63}");
    private static final Map<String, TypeJoinStatus> KNOWN = canonicalValueMap(
            APPROVED, ALREADY_MEMBER, INVITATION_NOT_ACCEPTED, TYPE_NOT_FOUND, NOT_AUTHORIZER,
            ERROR);

    private TypeJoinStatus(String identifier) {
        super(identifier);
    }

    private TypeJoinStatus(String identifier, boolean canonical) {
        super(identifier, canonical);
    }

    /** Returns a known singleton or a preserved non-canonical future status. */
    public static TypeJoinStatus fromString(String identifier) {
        if (identifier == null || !VALID.matcher(identifier).matches()) {
            throw new IllegalArgumentException("Invalid type-join status identifier");
        }
        return from(identifier, KNOWN, value -> new TypeJoinStatus(value, false));
    }
}
