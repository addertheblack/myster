package com.myster.access;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.Objects;

/**
 * Operation to set the user-readable name for a type.
 */
public class SetNameOp implements BlockOperation {
    private final String name;

    public SetNameOp(String name) {
        this.name = Objects.requireNonNull(name, "Name cannot be null");
    }

    public String getName() {
        return name;
    }

    @Override
    public OpType getType() {
        return OpType.SET_NAME;
    }

    @Override
    public void serializePayload(DataOutputStream out) throws IOException {
        out.writeUTF(name);
    }

    static SetNameOp deserializePayload(DataInputStream in) throws IOException {
        return new SetNameOp(in.readUTF());
    }

    @Override
    public String toString() {
        return "SetNameOp{name='" + name + "'}";
    }
}

