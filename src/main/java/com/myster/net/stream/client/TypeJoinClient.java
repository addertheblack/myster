package com.myster.net.stream.client;

import java.io.IOException;

import com.myster.mml.MessagePak;
import com.myster.net.MysterSocket;
import com.myster.type.MysterType;
import com.myster.type.join.TypeJoinServer;
import com.myster.type.join.TypeJoinStatus;
import com.myster.type.join.TypeJoinUri;

/** Blocking section 126 codec owned by the stream protocol implementation. */
final class TypeJoinClient {
    private TypeJoinClient() {
    }

    static TypeJoinStatus redeem(MysterSocket socket, MysterType type, byte[] invitationId,
            String code) throws IOException {
        java.util.Objects.requireNonNull(socket, "socket");
        java.util.Objects.requireNonNull(type, "type");
        if (invitationId == null || invitationId.length != TypeJoinUri.INVITATION_ID_BYTES) {
            throw new IllegalArgumentException("Invitation id must be 16 bytes");
        }
        if (code == null || code.isEmpty()
                || code.codePointCount(0, code.length()) > TypeJoinUri.MAX_CODE_POINTS) {
            throw new IllegalArgumentException("Invitation code must contain 1 to 256 characters");
        }

        socket.out.writeInt(TypeJoinServer.NUMBER);
        socket.out.flush();
        if (socket.in.read() != 1) {
            throw new IOException("Server rejected invitation section");
        }

        MessagePak request = MessagePak.newEmpty();
        request.putInt("/schemaVersion", 1);
        request.putByteArray("/type", type.toBytes());
        request.putByteArray("/invitation", invitationId);
        request.putString("/code", code);
        socket.out.writeMessagePack(request);
        socket.out.flush();

        MessagePak response = socket.in.readMessagePack(TypeJoinServer.MAX_FRAME_BYTES);
        if (response.getInt("/schemaVersion").orElse(-1) != 1) {
            throw new IOException("Unsupported type-join response schema");
        }
        String identifier = response.getString("/status")
                .orElseThrow(() -> new IOException("Missing type-join status"));
        try {
            return TypeJoinStatus.fromString(identifier);
        } catch (IllegalArgumentException exception) {
            throw new IOException("Malformed type-join status", exception);
        }
    }
}
