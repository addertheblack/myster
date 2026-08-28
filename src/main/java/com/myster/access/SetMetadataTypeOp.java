package com.myster.access;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.Objects;

import com.myster.type.MetadataTypeId;

/**
 * Sets the single metadata profile associated with a custom Myster Type.
 *
 * <p>The operation payload is an integer byte length followed by an opaque frame containing one
 * modified-UTF {@link MetadataTypeId} identifier. The frame lets an older reader preserve the
 * unrecognized operation through {@link UnknownOp}. An explicit
 * {@link MetadataTypeId#GENERIC} value supersedes an earlier specialized or unknown association.
 */
public final class SetMetadataTypeOp implements BlockOperation {
    private static final int MAX_PAYLOAD_LENGTH = 128;

    private final MetadataTypeId metadataTypeId;

    public SetMetadataTypeOp(MetadataTypeId metadataTypeId) {
        this.metadataTypeId = Objects.requireNonNull(metadataTypeId);
    }

    public MetadataTypeId getMetadataTypeId() {
        return metadataTypeId;
    }

    @Override
    public OpType getType() {
        return OpType.SET_METADATA_TYPE;
    }

    @Override
    public void serializePayload(DataOutputStream out) throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (DataOutputStream payload = new DataOutputStream(bytes)) {
            payload.writeUTF(metadataTypeId.getIdentifier());
        }
        byte[] framedPayload = bytes.toByteArray();
        out.writeInt(framedPayload.length);
        out.write(framedPayload);
    }

    static SetMetadataTypeOp deserializePayload(DataInputStream in) throws IOException {
        int length = in.readInt();
        if (length < 0 || length > MAX_PAYLOAD_LENGTH) {
            throw new IOException("Invalid SET_METADATA_TYPE payload length: " + length);
        }

        byte[] payloadBytes = in.readNBytes(length);
        if (payloadBytes.length != length) {
            throw new IOException("Truncated SET_METADATA_TYPE payload");
        }

        try (DataInputStream payload = new DataInputStream(
                new ByteArrayInputStream(payloadBytes))) {
            MetadataTypeId id = MetadataTypeId.fromString(payload.readUTF());
            if (payload.available() != 0) {
                throw new IOException("Trailing bytes in SET_METADATA_TYPE payload");
            }
            return new SetMetadataTypeOp(id);
        } catch (IllegalArgumentException e) {
            throw new IOException("Invalid metadata type identifier", e);
        }
    }

    @Override
    public String toString() {
        return "SetMetadataTypeOp{metadataTypeId=" + metadataTypeId + "}";
    }
}
