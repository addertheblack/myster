package com.myster.access;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.Objects;

import com.myster.cid.ServerCid;

/**
 * Operation to add a member to the access list.
 * Members are identified by their {@link ServerCid} (derived from their RSA identity).
 */
public record AddMemberOp(ServerCid memberIdentity, Role role) implements BlockOperation {
    public AddMemberOp(ServerCid memberIdentity, Role role) {
        this.memberIdentity = Objects.requireNonNull(memberIdentity, "Member identity cannot be null");
        this.role = Objects.requireNonNull(role, "Role cannot be null");
    }

    @Override
    public OpType getType() {
        return OpType.ADD_MEMBER;
    }

    @Override
    public void serializePayload(DataOutputStream out) throws IOException {
        out.write(memberIdentity.bytes());
        out.writeUTF(role.getIdentifier());
    }

    static AddMemberOp deserializePayload(DataInputStream in) throws IOException {
        byte[] cidBytes = new byte[ServerCid.LENGTH];
        in.readFully(cidBytes);
        ServerCid identity = new ServerCid(cidBytes);
        String roleString = in.readUTF();
        Role role = Role.fromString(roleString);
        return new AddMemberOp(identity, role);
    }

    @Override
    public String toString() {
        return "AddMemberOp{identity=" + memberIdentity + ", role=" + role + "}";
    }
}
