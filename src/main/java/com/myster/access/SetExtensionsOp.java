package com.myster.access;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.Objects;

/**
 * Operation to set the file extensions filter for a type.
 */
public class SetExtensionsOp implements BlockOperation {
    private final String[] extensions;

    public SetExtensionsOp(String[] extensions) {
        this.extensions = Objects.requireNonNull(extensions, "Extensions cannot be null").clone();
    }

    public String[] getExtensions() {
        return extensions.clone();
    }

    @Override
    public OpType getType() {
        return OpType.SET_EXTENSIONS;
    }

    @Override
    public void serializePayload(DataOutputStream out) throws IOException {
        out.writeInt(extensions.length);
        for (String ext : extensions) {
            out.writeUTF(ext);
        }
    }

    static SetExtensionsOp deserializePayload(DataInputStream in) throws IOException {
        int count = in.readInt();
        String[] extensions = new String[count];
        for (int i = 0; i < count; i++) {
            extensions[i] = in.readUTF();
        }
        return new SetExtensionsOp(extensions);
    }

    @Override
    public String toString() {
        return "SetExtensionsOp{extensions=" + java.util.Arrays.toString(extensions) + "}";
    }
}

