package com.myster.access;

import java.io.IOException;

import com.myster.mml.MessagePak;

/**
 * Policy settings that control discoverability and access to a type.
 *
 * <p>Serialized using {@link MessagePak} so that future versions can add new policy
 * fields without breaking older nodes — unknown fields in the MessagePak blob are
 * silently ignored during deserialization.
 */
public class Policy {
    private static final String KEY_DISCOVERABLE = "/discoverable";
    private static final String KEY_LIST_FILES_PUBLIC = "/listFilesPublic";
    private static final String KEY_NODE_CAN_JOIN_PUBLIC = "/nodeCanJoinPublic";

    private final boolean discoverable;
    private final boolean listFilesPublic;
    private final boolean nodeCanJoinPublic;

    /**
     * Creates a policy with the specified settings.
     *
     * @param discoverable whether this type appears in type lister responses
     * @param listFilesPublic whether non-members can list files
     * @param nodeCanJoinPublic reserved for future use (auto-join without invitation)
     */
    public Policy(boolean discoverable, boolean listFilesPublic, boolean nodeCanJoinPublic) {
        this.discoverable = discoverable;
        this.listFilesPublic = listFilesPublic;
        this.nodeCanJoinPublic = nodeCanJoinPublic;
    }

    /**
     * Whether this type should be included in type lister responses.
     *
     * @return true if discoverable
     */
    public boolean isDiscoverable() {
        return discoverable;
    }

    /**
     * Whether non-members can list files of this type.
     *
     * @return true if file listing is public
     */
    public boolean isListFilesPublic() {
        return listFilesPublic;
    }

    /**
     * Reserved for future use. Would allow public joining without invitation.
     *
     * @return true if nodes can join publicly (not implemented in Part 1)
     */
    public boolean isNodeCanJoinPublic() {
        return nodeCanJoinPublic;
    }

    /**
     * Serializes this policy to a MessagePak blob for wire transmission.
     * New fields added by future versions are silently ignored by older nodes.
     *
     * @return the serialized bytes
     * @throws IOException if serialization fails
     */
    public byte[] toMessagePakBytes() throws IOException {
        MessagePak pak = MessagePak.newEmpty();
        pak.putBoolean(KEY_DISCOVERABLE, discoverable);
        pak.putBoolean(KEY_LIST_FILES_PUBLIC, listFilesPublic);
        pak.putBoolean(KEY_NODE_CAN_JOIN_PUBLIC, nodeCanJoinPublic);
        return pak.toBytes();
    }

    /**
     * Deserializes a policy from a MessagePak blob. Unknown fields are silently ignored.
     *
     * @param bytes the MessagePak bytes
     * @return the deserialized Policy
     * @throws IOException if deserialization fails
     */
    public static Policy fromMessagePakBytes(byte[] bytes) throws IOException {
        MessagePak pak = MessagePak.fromBytes(bytes);
        boolean discoverable = pak.getBoolean(KEY_DISCOVERABLE).orElse(false);
        boolean listFilesPublic = pak.getBoolean(KEY_LIST_FILES_PUBLIC).orElse(false);
        boolean nodeCanJoinPublic = pak.getBoolean(KEY_NODE_CAN_JOIN_PUBLIC).orElse(false);
        return new Policy(discoverable, listFilesPublic, nodeCanJoinPublic);
    }

    /**
     * Creates a default restrictive policy: not discoverable, files not public, no public join.
     *
     * @return a new Policy with all restrictions enabled
     */
    public static Policy defaultRestrictive() {
        return new Policy(false, false, false);
    }

    /**
     * Creates a permissive policy: discoverable, files public.
     *
     * @return a new Policy suitable for public types
     */
    public static Policy defaultPermissive() {
        return new Policy(true, true, false);
    }

    @Override
    public String toString() {
        return "Policy{discoverable=" + discoverable +
               ", listFilesPublic=" + listFilesPublic +
               ", nodeCanJoinPublic=" + nodeCanJoinPublic + "}";
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Policy other)) return false;
        return discoverable == other.discoverable &&
               listFilesPublic == other.listFilesPublic &&
               nodeCanJoinPublic == other.nodeCanJoinPublic;
    }

    @Override
    public int hashCode() {
        return Boolean.hashCode(discoverable) * 31 * 31 +
               Boolean.hashCode(listFilesPublic) * 31 +
               Boolean.hashCode(nodeCanJoinPublic);
    }
}
