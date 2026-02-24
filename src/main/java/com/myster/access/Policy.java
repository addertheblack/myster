package com.myster.access;

import java.io.IOException;

import com.myster.mml.MessagePak;

/**
 * Policy settings that control access to a type.
 *
 * <p>Serialized using {@link MessagePak} so that future versions can add new policy
 * fields without breaking older nodes — unknown fields in the MessagePak blob are
 * silently ignored during deserialization.
 *
 * <p>Currently the only meaningful field is {@code listFilesPublic}: whether
 * non-members can list and download files of this type. Future fields (e.g.
 * discoverability rules) can be added as new MessagePak keys without breaking
 * existing nodes.
 */
public class Policy {
    private static final String KEY_LIST_FILES_PUBLIC = "/listFilesPublic";

    private final boolean listFilesPublic;

    /**
     * Creates a policy.
     *
     * @param listFilesPublic whether non-members can list and download files of this type
     */
    public Policy(boolean listFilesPublic) {
        this.listFilesPublic = listFilesPublic;
    }

    /**
     * Whether non-members can list and download files of this type.
     * {@code false} means only members (as defined by the access list) can access files.
     *
     * @return true if file listing and download is open to everyone
     */
    public boolean isListFilesPublic() {
        return listFilesPublic;
    }

    /**
     * Serializes this policy to a MessagePak blob.
     * Future fields added by later versions are silently ignored by older nodes.
     *
     * @return the serialized bytes
     * @throws IOException if serialization fails
     */
    public byte[] toMessagePakBytes() throws IOException {
        MessagePak pak = MessagePak.newEmpty();
        pak.putBoolean(KEY_LIST_FILES_PUBLIC, listFilesPublic);
        return pak.toBytes();
    }

    /**
     * Deserializes a policy from a MessagePak blob.
     * Unknown fields are silently ignored.
     *
     * @param bytes the MessagePak bytes
     * @return the deserialized Policy
     * @throws IOException if deserialization fails
     */
    public static Policy fromMessagePakBytes(byte[] bytes) throws IOException {
        MessagePak pak = MessagePak.fromBytes(bytes);
        boolean listFilesPublic = pak.getBoolean(KEY_LIST_FILES_PUBLIC).orElse(false);

        return new Policy(listFilesPublic);
    }

    /**
     * Restrictive policy: only members can access files.
     * Use for private types.
     *
     * @return a new restrictive Policy
     */
    public static Policy defaultRestrictive() {
        return new Policy(false);
    }

    /**
     * Permissive policy: anyone can list and download files.
     * Use for public types.
     *
     * @return a new permissive Policy
     */
    public static Policy defaultPermissive() {
        return new Policy(true);
    }

    @Override
    public String toString() {
        return "Policy{listFilesPublic=" + listFilesPublic + "}";
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Policy other)) return false;
        return listFilesPublic == other.listFilesPublic;
    }

    @Override
    public int hashCode() {
        return Boolean.hashCode(listFilesPublic);
    }
}
