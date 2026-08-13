package com.myster.access;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.Objects;

import com.drew.lang.annotations.NotNull;
import com.myster.cid.ServerCid;

/**
 * Operation to remove a member from the access list.
 * After removal, the member can no longer access files of this type.
 */
public record RemoveMemberOp(ServerCid memberIdentity) implements BlockOperation {
    public RemoveMemberOp(ServerCid memberIdentity) {
        this.memberIdentity = Objects.requireNonNull(memberIdentity, "Member identity cannot be null");
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
        byte[] cidBytes = new byte[ServerCid.LENGTH];
        in.readFully(cidBytes);
        return new RemoveMemberOp(new ServerCid(cidBytes));
    }

    @Override
    @NotNull
    public String toString() {
        return "RemoveMemberOp{identity=" + memberIdentity + "}";
    }
}
