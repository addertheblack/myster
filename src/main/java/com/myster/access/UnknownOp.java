package com.myster.access;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;

/**
 * Represents an operation type that this version of the code does not recognize.
 *
 * <p>Preserves the raw payload bytes so the block remains valid in the chain. The
 * operation's effect on derived state is skipped during chain replay, but the block
 * (including this unknown operation) is still stored and can be forwarded to other nodes.
 */
public class UnknownOp implements BlockOperation {
    private final OpType type;
    private final byte[] rawPayload;

    UnknownOp(OpType type, byte[] rawPayload) {
        this.type = type;
        this.rawPayload = rawPayload.clone();
    }

    /**
     * Returns the raw payload bytes of the unrecognized operation.
     *
     * @return a copy of the payload bytes
     */
    public byte[] getRawPayload() {
        return rawPayload.clone();
    }

    @Override
    public OpType getType() {
        return type;
    }

    @Override
    public void serializePayload(DataOutputStream out) throws IOException {
        out.writeInt(rawPayload.length);
        out.write(rawPayload);
    }

    /**
     * Deserializes the payload for an unknown operation type. Reads a length-prefixed
     * byte array so the data can be round-tripped without understanding its structure.
     */
    static UnknownOp deserializePayload(OpType type, DataInputStream in) throws IOException {
        int length = in.readInt();
        byte[] payload = new byte[length];
        in.readFully(payload);
        return new UnknownOp(type, payload);
    }

    @Override
    public String toString() {
        return "UnknownOp{type=" + type + ", payloadSize=" + rawPayload.length + "}";
    }
}

