package com.myster.access;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.Objects;

import com.myster.identity.Cid128;

/**
 * Operation to add a member to the access list.
 * Members are identified by their {@link Cid128} (derived from their RSA identity).
 */
public class AddMemberOp implements BlockOperation {
    private final Cid128 memberIdentity;
    private final Role role;

    public AddMemberOp(Cid128 memberIdentity, Role role) {
        this.memberIdentity = Objects.requireNonNull(memberIdentity, "Member identity cannot be null");
        this.role = Objects.requireNonNull(role, "Role cannot be null");
    }

    public Cid128 getMemberIdentity() {
        return memberIdentity;
    }

    public Role getRole() {
        return role;
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
        byte[] cidBytes = new byte[16];
        in.readFully(cidBytes);
        Cid128 identity = new Cid128(cidBytes);
        String roleString = in.readUTF();
        Role role = Role.fromString(roleString);
        return new AddMemberOp(identity, role);
    }

    @Override
    public String toString() {
        return "AddMemberOp{identity=" + memberIdentity + ", role=" + role + "}";
    }
}
