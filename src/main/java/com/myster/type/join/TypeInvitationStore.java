package com.myster.type.join;

import java.time.Clock;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.prefs.BackingStoreException;
import java.util.prefs.Preferences;

import com.myster.cid.ServerCid;
import com.myster.type.MysterType;

/**
 * Persists bounded, local-only invitation records beneath an injected Preferences root.
 *
 * <p>Known malformed and expired version 1 nodes are removed lazily. Nodes carrying a future schema
 * version are ignored and retained. Every mutation is flushed before this class reports success.
 * Methods are synchronized because a create, claim, or completion may span several Preferences
 * operations that must appear as one local store action.
 */
public final class TypeInvitationStore {
    public static final int SCHEMA_VERSION = 1;
    public static final int MAX_UNEXPIRED_PER_TYPE = 64;

    static final String KEY_SCHEMA_VERSION = "schemaVersion";
    static final String KEY_KDF_ALGORITHM = "kdfAlgorithm";
    static final String KEY_KDF_ITERATIONS = "kdfIterations";
    static final String KEY_SALT = "salt";
    static final String KEY_VERIFIER = "verifier";
    static final String KEY_CREATED_AT = "createdAt";
    static final String KEY_EXPIRES_AT = "expiresAt";
    static final String KEY_CLAIMED_BY = "claimedBy";
    static final String KEY_REDEMPTION_COMPLETE = "redemptionComplete";

    private static final HexFormat HEX = HexFormat.of();
    private static final Set<String> REQUIRED_KEYS = Set.of(
            KEY_SCHEMA_VERSION, KEY_KDF_ALGORITHM, KEY_KDF_ITERATIONS, KEY_SALT, KEY_VERIFIER,
            KEY_CREATED_AT, KEY_EXPIRES_AT, KEY_REDEMPTION_COMPLETE);
    private static final Set<String> ALLOWED_KEYS = Set.of(
            KEY_SCHEMA_VERSION, KEY_KDF_ALGORITHM, KEY_KDF_ITERATIONS, KEY_SALT, KEY_VERIFIER,
            KEY_CREATED_AT, KEY_EXPIRES_AT, KEY_CLAIMED_BY, KEY_REDEMPTION_COMPLETE);

    private final Preferences root;
    private final Clock clock;

    public TypeInvitationStore(Preferences root, Clock clock) {
        this.root = Objects.requireNonNull(root, "root");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    /**
     * Writes a new record and flushes it before returning.
     *
     * @throws IllegalStateException if the id already exists or the per-type bound is reached
     * @throws BackingStoreException if Preferences cannot enumerate or durably flush the record
     */
    public synchronized void create(TypeInvitation invitation) throws BackingStoreException {
        Objects.requireNonNull(invitation, "invitation");
        List<TypeInvitation> current = list(invitation.type());
        if (current.size() >= MAX_UNEXPIRED_PER_TYPE) {
            throw new IllegalStateException("Too many active invitations for this type");
        }

        Preferences typeNode = root.node(invitation.type().toHexString());
        String invitationHex = HEX.formatHex(invitation.invitationId());
        if (typeNode.nodeExists(invitationHex)) {
            throw new IllegalStateException("Invitation id already exists");
        }
        Preferences invitationNode = typeNode.node(invitationHex);
        write(invitationNode, invitation);
        try {
            invitationNode.flush();
        } catch (BackingStoreException exception) {
            removeAfterFailedCreate(invitationNode, typeNode);
            throw exception;
        }
    }

    /** Returns a current version 1 record, or empty for missing, expired, malformed, or future data. */
    public synchronized Optional<TypeInvitation> load(MysterType type, byte[] invitationId)
            throws BackingStoreException {
        Objects.requireNonNull(type, "type");
        requireInvitationId(invitationId);
        if (!root.nodeExists(type.toHexString())) {
            return Optional.empty();
        }
        Preferences typeNode = root.node(type.toHexString());
        String invitationHex = HEX.formatHex(invitationId);
        if (!typeNode.nodeExists(invitationHex)) {
            cleanup(typeNode);
            return Optional.empty();
        }
        Preferences node = typeNode.node(invitationHex);
        ParseResult parsed = parse(type, invitationId, node);
        if (parsed.remove()) {
            node.removeNode();
            typeNode.flush();
        }
        return parsed.invitation();
    }

    /** Lists unexpired version 1 invitations after opportunistic cleanup. */
    public synchronized List<TypeInvitation> list(MysterType type) throws BackingStoreException {
        Objects.requireNonNull(type, "type");
        if (!root.nodeExists(type.toHexString())) {
            return List.of();
        }
        Preferences typeNode = root.node(type.toHexString());
        List<TypeInvitation> result = new ArrayList<>();
        boolean changed = false;
        for (String childName : typeNode.childrenNames()) {
            byte[] invitationId;
            try {
                invitationId = parseInvitationNodeName(childName);
            } catch (IllegalArgumentException exception) {
                typeNode.node(childName).removeNode();
                changed = true;
                continue;
            }
            Preferences node = typeNode.node(childName);
            ParseResult parsed = parse(type, invitationId, node);
            if (parsed.remove()) {
                node.removeNode();
                changed = true;
            } else {
                parsed.invitation().ifPresent(result::add);
            }
        }
        if (changed) {
            typeNode.flush();
        }
        return List.copyOf(result);
    }

    /** Replaces and flushes an existing record after a claim or completion transition. */
    public synchronized void update(TypeInvitation invitation) throws BackingStoreException {
        Objects.requireNonNull(invitation, "invitation");
        String typeHex = invitation.type().toHexString();
        String invitationHex = HEX.formatHex(invitation.invitationId());
        if (!root.nodeExists(typeHex) || !root.node(typeHex).nodeExists(invitationHex)) {
            throw new IllegalStateException("Invitation no longer exists");
        }
        Preferences node = root.node(typeHex).node(invitationHex);
        write(node, invitation);
        node.flush();
    }

    private void cleanup(Preferences typeNode) throws BackingStoreException {
        boolean changed = false;
        for (String childName : typeNode.childrenNames()) {
            byte[] id;
            try {
                id = parseInvitationNodeName(childName);
            } catch (IllegalArgumentException exception) {
                typeNode.node(childName).removeNode();
                changed = true;
                continue;
            }
            MysterType type;
            try {
                type = MysterType.fromHexString(typeNode.name());
            } catch (java.io.IOException exception) {
                return;
            }
            Preferences child = typeNode.node(childName);
            if (parse(type, id, child).remove()) {
                child.removeNode();
                changed = true;
            }
        }
        if (changed) {
            typeNode.flush();
        }
    }

    private ParseResult parse(MysterType type, byte[] invitationId, Preferences node)
            throws BackingStoreException {
        Set<String> keys = new HashSet<>(Arrays.asList(node.keys()));
        if (!keys.contains(KEY_SCHEMA_VERSION)) {
            return ParseResult.malformed();
        }
        int version = node.getInt(KEY_SCHEMA_VERSION, -1);
        if (version != SCHEMA_VERSION) {
            return version > SCHEMA_VERSION ? ParseResult.future() : ParseResult.malformed();
        }
        if (!keys.containsAll(REQUIRED_KEYS) || !ALLOWED_KEYS.containsAll(keys)) {
            return ParseResult.malformed();
        }

        try {
            String algorithm = node.get(KEY_KDF_ALGORITHM, null);
            int iterations = node.getInt(KEY_KDF_ITERATIONS, -1);
            byte[] salt = node.getByteArray(KEY_SALT, null);
            byte[] verifier = node.getByteArray(KEY_VERIFIER, null);
            long createdAt = node.getLong(KEY_CREATED_AT, Long.MIN_VALUE);
            long expiresAt = node.getLong(KEY_EXPIRES_AT, Long.MIN_VALUE);
            boolean complete = node.getBoolean(KEY_REDEMPTION_COMPLETE, false);
            byte[] claimedBytes = node.getByteArray(KEY_CLAIMED_BY, null);
            Optional<ServerCid> claimedBy = claimedBytes == null
                    ? Optional.empty()
                    : Optional.of(new ServerCid(claimedBytes));
            TypeInvitation invitation = new TypeInvitation(type, invitationId, algorithm, iterations,
                    salt, verifier, createdAt, expiresAt, claimedBy, complete);
            return invitation.isExpired(clock.millis())
                    ? ParseResult.expired()
                    : ParseResult.valid(invitation);
        } catch (IllegalArgumentException | NullPointerException exception) {
            return ParseResult.malformed();
        }
    }

    private static void write(Preferences node, TypeInvitation invitation) {
        node.putInt(KEY_SCHEMA_VERSION, SCHEMA_VERSION);
        node.put(KEY_KDF_ALGORITHM, invitation.algorithm());
        node.putInt(KEY_KDF_ITERATIONS, invitation.iterations());
        node.putByteArray(KEY_SALT, invitation.salt());
        node.putByteArray(KEY_VERIFIER, invitation.verifier());
        node.putLong(KEY_CREATED_AT, invitation.createdAt());
        node.putLong(KEY_EXPIRES_AT, invitation.expiresAt());
        if (invitation.claimedBy().isPresent()) {
            node.putByteArray(KEY_CLAIMED_BY, invitation.claimedBy().orElseThrow().bytes());
        } else {
            node.remove(KEY_CLAIMED_BY);
        }
        node.putBoolean(KEY_REDEMPTION_COMPLETE, invitation.redemptionComplete());
    }

    private static byte[] parseInvitationNodeName(String childName) {
        if (childName.length() != TypeJoinUri.INVITATION_ID_BYTES * 2
                || !childName.equals(childName.toLowerCase(java.util.Locale.ROOT))) {
            throw new IllegalArgumentException("Invalid invitation node name");
        }
        byte[] bytes = HEX.parseHex(childName);
        requireInvitationId(bytes);
        return bytes;
    }

    private static void requireInvitationId(byte[] invitationId) {
        if (invitationId == null || invitationId.length != TypeJoinUri.INVITATION_ID_BYTES) {
            throw new IllegalArgumentException("Invitation id must be 16 bytes");
        }
    }

    private static void removeAfterFailedCreate(Preferences node, Preferences parent) {
        try {
            node.removeNode();
            parent.flush();
        } catch (BackingStoreException ignored) {
            // The original flush failure remains authoritative; partial version 1 state fails closed.
        }
    }

    private record ParseResult(Optional<TypeInvitation> invitation, boolean remove) {
        static ParseResult valid(TypeInvitation invitation) {
            return new ParseResult(Optional.of(invitation), false);
        }

        static ParseResult future() {
            return new ParseResult(Optional.empty(), false);
        }

        static ParseResult malformed() {
            return new ParseResult(Optional.empty(), true);
        }

        static ParseResult expired() {
            return new ParseResult(Optional.empty(), true);
        }
    }
}
