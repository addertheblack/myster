package com.myster.access;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.Objects;

import com.myster.identity.Cid128;

/**
 * Operation to remove a member from the access list.
 * After removal, the member can no longer access files of this type.
 */
public class RemoveMemberOp implements BlockOperation {
    private final Cid128 memberIdentity;

    public RemoveMemberOp(Cid128 memberIdentity) {
        this.memberIdentity = Objects.requireNonNull(memberIdentity, "Member identity cannot be null");
    }

    public Cid128 getMemberIdentity() {
        return memberIdentity;
    }

    @Override
    public OpType getType() {
        return OpType.REMOVE_MEMBER;
    }

    @Override
    public void serializePayload(DataOutputStream out) throws IOException {
        out.write(memberIdentity.bytes());
    }

    static RemoveMemberOp deserializePayload(DataInputStream in) throws IOException {
        byte[] cidBytes = new byte[16];
        in.readFully(cidBytes);
        return new RemoveMemberOp(new Cid128(cidBytes));
    }

    @Override
    public String toString() {
        return "RemoveMemberOp{identity=" + memberIdentity + "}";
    }
}
