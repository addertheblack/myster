package com.myster.access;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;

/**
 * Operation to add an onramp server address to the access list.
 * Onramps are bootstrap servers that can provide copies of the access list.
 */
public class AddOnrampOp implements BlockOperation {
    private final String endpoint;

    public AddOnrampOp(String endpoint) {
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
        return OpType.ADD_ONRAMP;
    }

    @Override
    public void serializePayload(DataOutputStream out) throws IOException {
        out.writeUTF(endpoint);
    }

    static AddOnrampOp deserializePayload(DataInputStream in) throws IOException {
        return new AddOnrampOp(in.readUTF());
    }

    @Override
    public String toString() {
        return "AddOnrampOp{endpoint='" + endpoint + "'}";
    }
}
