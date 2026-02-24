package com.myster.access;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;

/**
 * Operation to remove an onramp server address from the access list.
 */
public class RemoveOnrampOp implements BlockOperation {
    private final String endpoint;

    public RemoveOnrampOp(String endpoint) {
        if (endpoint == null || endpoint.trim().isEmpty()) {
            throw new IllegalArgumentException("Endpoint cannot be null or empty");
        }
        this.endpoint = endpoint.trim();
    }

    public String getEndpoint() {
        return endpoint;
    }

    @Override
    public OpType getType() {
        return OpType.REMOVE_ONRAMP;
    }

    @Override
    public void serializePayload(DataOutputStream out) throws IOException {
        out.writeUTF(endpoint);
    }

    static RemoveOnrampOp deserializePayload(DataInputStream in) throws IOException {
        return new RemoveOnrampOp(in.readUTF());
    }

    @Override
    public String toString() {
        return "RemoveOnrampOp{endpoint='" + endpoint + "'}";
    }
}
