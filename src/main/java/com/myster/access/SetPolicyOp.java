package com.myster.access;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.Objects;

/**
 * Operation to set or update policy settings for a type.
 * The payload is a MessagePak blob for forward extensibility.
 */
public class SetPolicyOp implements BlockOperation {
    private final Policy policy;

    public SetPolicyOp(Policy policy) {
        this.policy = Objects.requireNonNull(policy, "Policy cannot be null");
    }

    public Policy getPolicy() {
        return policy;
    }

    @Override
    public OpType getType() {
        return OpType.SET_POLICY;
    }

    @Override
    public void serializePayload(DataOutputStream out) throws IOException {
        byte[] policyBytes = policy.toMessagePakBytes();
        out.writeInt(policyBytes.length);
        out.write(policyBytes);
    }

    static SetPolicyOp deserializePayload(DataInputStream in) throws IOException {
        int length = in.readInt();
        byte[] policyBytes = new byte[length];
        in.readFully(policyBytes);
        Policy policy = Policy.fromMessagePakBytes(policyBytes);
        return new SetPolicyOp(policy);
    }

    @Override
    public String toString() {
        return "SetPolicyOp{" + policy + "}";
    }
}
