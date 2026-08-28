package com.myster.access;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.Map;

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
        return BlockOperationDeserializers.deserialize(type, in);
    }
}

final class BlockOperationDeserializers {
    private static final Map<OpType, PayloadDeserializer> DESERIALIZERS = Map.ofEntries(
            Map.entry(OpType.SET_POLICY, SetPolicyOp::deserializePayload),
            Map.entry(OpType.ADD_WRITER, AddWriterOp::deserializePayload),
            Map.entry(OpType.REMOVE_WRITER, RemoveWriterOp::deserializePayload),
            Map.entry(OpType.ADD_MEMBER, AddMemberOp::deserializePayload),
            Map.entry(OpType.REMOVE_MEMBER, RemoveMemberOp::deserializePayload),
            Map.entry(OpType.ADD_ONRAMP, AddOnrampOp::deserializePayload),
            Map.entry(OpType.REMOVE_ONRAMP, RemoveOnrampOp::deserializePayload),
            Map.entry(OpType.SET_TYPE_PUBLIC_KEY, SetTypePublicKeyOp::deserializePayload),
            Map.entry(OpType.SET_NAME, SetNameOp::deserializePayload),
            Map.entry(OpType.SET_DESCRIPTION, SetDescriptionOp::deserializePayload),
            Map.entry(OpType.SET_EXTENSIONS, SetExtensionsOp::deserializePayload),
            Map.entry(OpType.SET_SEARCH_IN_ARCHIVES,
                    SetSearchInArchivesOp::deserializePayload),
            Map.entry(OpType.SET_METADATA_TYPE, SetMetadataTypeOp::deserializePayload));

    private BlockOperationDeserializers() {}

    static BlockOperation deserialize(OpType type, DataInputStream in) throws IOException {
        PayloadDeserializer deserializer = DESERIALIZERS.get(type);
        return deserializer == null
                ? UnknownOp.deserializePayload(type, in)
                : deserializer.deserialize(in);
    }

    @FunctionalInterface
    private interface PayloadDeserializer {
        BlockOperation deserialize(DataInputStream in) throws IOException;
    }
}
