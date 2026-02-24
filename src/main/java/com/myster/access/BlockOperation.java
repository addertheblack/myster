package com.myster.access;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;

/**
 * Represents an operation that can be recorded in an access list block.
 *
 * <p>Operations modify the derived state of an access list by adding/removing
 * members, writers, onramps, changing policies, or setting type metadata. Each
 * non-genesis block contains exactly one operation; genesis blocks may contain multiple.
 *
 * <p>Operations are identified by string-based {@link OpType} identifiers for forward
 * compatibility. Unknown operations from future versions are deserialized as
 * {@link UnknownOp} and preserved in the chain without breaking validation.
 *
 * <p>Serialization format for each operation:
 * <pre>
 *   [UTF string] OpType identifier
 *   [variable]   operation-specific payload
 * </pre>
 */
public interface BlockOperation {

    /**
     * Returns the operation type.
     *
     * @return the operation type
     */
    OpType getType();

    /**
     * Serializes this operation's payload (not the OpType header) to binary format.
     *
     * @param out the output stream to write to
     * @throws IOException if an I/O error occurs
     */
    void serializePayload(DataOutputStream out) throws IOException;

    /**
     * Serializes this operation to binary format, including the OpType header.
     *
     * @param out the output stream to write to
     * @throws IOException if an I/O error occurs
     */
    default void serialize(DataOutputStream out) throws IOException {
        out.writeUTF(getType().getIdentifier());
        serializePayload(out);
    }

    /**
     * Deserializes an operation from binary format.
     * Reads the OpType string header, then dispatches to the appropriate
     * concrete deserializer. Unknown operation types produce an {@link UnknownOp}.
     *
     * @param in the input stream to read from
     * @return the deserialized operation
     * @throws IOException if an I/O error occurs
     */
    static BlockOperation deserialize(DataInputStream in) throws IOException {
        String typeString = in.readUTF();
        OpType type = OpType.fromString(typeString);

        if (!type.isCanonical()) {
            return UnknownOp.deserializePayload(type, in);
        }

        if (type.equals(OpType.SET_POLICY)) return SetPolicyOp.deserializePayload(in);
        if (type.equals(OpType.ADD_WRITER)) return AddWriterOp.deserializePayload(in);
        if (type.equals(OpType.REMOVE_WRITER)) return RemoveWriterOp.deserializePayload(in);
        if (type.equals(OpType.ADD_MEMBER)) return AddMemberOp.deserializePayload(in);
        if (type.equals(OpType.REMOVE_MEMBER)) return RemoveMemberOp.deserializePayload(in);
        if (type.equals(OpType.ADD_ONRAMP)) return AddOnrampOp.deserializePayload(in);
        if (type.equals(OpType.REMOVE_ONRAMP)) return RemoveOnrampOp.deserializePayload(in);
        if (type.equals(OpType.SET_TYPE_PUBLIC_KEY)) return SetTypePublicKeyOp.deserializePayload(in);
        if (type.equals(OpType.SET_NAME)) return SetNameOp.deserializePayload(in);
        if (type.equals(OpType.SET_DESCRIPTION)) return SetDescriptionOp.deserializePayload(in);
        if (type.equals(OpType.SET_EXTENSIONS)) return SetExtensionsOp.deserializePayload(in);
        if (type.equals(OpType.SET_SEARCH_IN_ARCHIVES)) return SetSearchInArchivesOp.deserializePayload(in);

        return UnknownOp.deserializePayload(type, in);
    }
}
