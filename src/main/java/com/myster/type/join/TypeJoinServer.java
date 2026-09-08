package com.myster.type.join;

import java.io.IOException;
import java.util.Arrays;
import java.util.Optional;

import com.myster.cid.ServerCid;
import com.myster.mml.MessagePak;
import com.myster.net.server.ConnectionContext;
import com.myster.net.stream.server.ServerStreamHandler;
import com.myster.type.MysterType;

/**
 * Authenticated TCP stream section 126 for invitation redemption.
 *
 * <p>The caller identity comes only from the TLS connection context. Malformed and oversized
 * frames close the section without mutation, and responses never contain exception or invitation
 * state details.
 */
public final class TypeJoinServer extends ServerStreamHandler {
    public static final int NUMBER = 126;
    public static final int MAX_FRAME_BYTES = 4 * 1024;
    private static final int SCHEMA_VERSION = 1;

    private final TypeInvitationManager invitationManager;

    public TypeJoinServer(TypeInvitationManager invitationManager) {
        this.invitationManager = java.util.Objects.requireNonNull(invitationManager);
    }

    @Override
    public int getSectionNumber() {
        return NUMBER;
    }

    @Override
    public void section(ConnectionContext context) throws IOException {
        Optional<ServerCid> caller = context.callerCid();
        if (caller.isEmpty()) {
            throw new IOException("Invitation redemption requires authenticated TLS");
        }
        Request request = decodeRequest(context.socket().in.readMessagePack(MAX_FRAME_BYTES));
        char[] password = request.code().toCharArray();
        TypeJoinStatus status;
        try {
            status = invitationManager.redeem(request.type(), request.invitationId(), password,
                    caller.orElseThrow(), context.serverAddress().toString());
        } finally {
            Arrays.fill(password, '\0');
        }
        MessagePak response = MessagePak.newEmpty();
        response.putInt("/schemaVersion", SCHEMA_VERSION);
        response.putString("/status", status.getIdentifier());
        context.socket().out.writeMessagePack(response);
        context.socket().out.flush();
    }

    private static Request decodeRequest(MessagePak message) throws IOException {
        try {
            if (message.getInt("/schemaVersion").orElse(-1) != SCHEMA_VERSION) {
                throw new IOException("Unsupported type-join schema");
            }
            byte[] typeBytes = message.getByteArray("/type")
                    .orElseThrow(() -> new IOException("Missing type"));
            byte[] invitationId = message.getByteArray("/invitation")
                    .orElseThrow(() -> new IOException("Missing invitation"));
            String code = message.getString("/code")
                    .orElseThrow(() -> new IOException("Missing invitation code"));
            if (typeBytes.length != 16 || invitationId.length != TypeJoinUri.INVITATION_ID_BYTES
                    || code.codePointCount(0, code.length()) > TypeJoinUri.MAX_CODE_POINTS
                    || code.isEmpty()) {
                throw new IOException("Invalid type-join request bounds");
            }
            return new Request(new MysterType(typeBytes), invitationId, code);
        } catch (IllegalArgumentException exception) {
            throw new IOException("Malformed type-join request", exception);
        }
    }

    private record Request(MysterType type, byte[] invitationId, String code) {}
}
