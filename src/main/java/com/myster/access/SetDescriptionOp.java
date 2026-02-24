package com.myster.access;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.Objects;

/**
 * Operation to set the user-readable description for a type.
 */
public class SetDescriptionOp implements BlockOperation {
    private final String description;

    public SetDescriptionOp(String description) {
        this.description = Objects.requireNonNull(description, "Description cannot be null");
    }

    public String getDescription() {
        return description;
    }

    @Override
    public OpType getType() {
        return OpType.SET_DESCRIPTION;
    }

    @Override
    public void serializePayload(DataOutputStream out) throws IOException {
        out.writeUTF(description);
    }

    static SetDescriptionOp deserializePayload(DataInputStream in) throws IOException {
        return new SetDescriptionOp(in.readUTF());
    }

    @Override
    public String toString() {
        return "SetDescriptionOp{description='" + description + "'}";
    }
}

