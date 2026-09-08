package com.myster.type.join;

import java.io.IOException;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.Callable;
import java.util.concurrent.atomic.AtomicReference;
import java.util.prefs.BackingStoreException;
import java.util.prefs.Preferences;

import com.general.thread.Cancellable;
import com.general.thread.PromiseFuture;
import com.general.thread.PromiseFutures;
import com.myster.access.AccessList;
import com.myster.cid.ServerCid;
import com.myster.net.MysterSocket;
import com.myster.net.client.DnsLookupProtocol;
import com.myster.net.client.MysterStream;
import com.myster.net.client.ParamBuilder;
import com.myster.threedns.ThreeDnsLookupResult;
import com.myster.threedns.VerifiedThreeDnsPeer;
import com.myster.type.TypeDescriptionList;

/**
 * Coordinates exact 3DNS discovery, expected-key TLS preview, invitation redemption, signed-chain
 * proof, and final type import. Expected-key TLS uses an ordinary reusable {@link MysterSocket};
 * connection establishment additionally verifies that the peer certificate contains the public
 * key returned by 3DNS.
 *
 * <p>No persistent type state is changed during preview. A successful wire status is only a
 * candidate result: the freshly fetched full chain must validate and contain the local server CID
 * before import. Returned futures are cancellable; cancellation makes late discovery/network
 * results and imports moot, but cannot promise that a server-side redemption already committed is
 * rolled back.
 */
public final class TypeJoinCoordinator {
    private static final String KEY_LAST_ENABLE = "enableJoinedType";

    public enum Failure {
        BOOTSTRAP_NOT_FOUND,
        INVITATION_NOT_ACCEPTED,
        UNSUPPORTED_VERSION,
        INVALID_NETWORK,
        TYPE_NOT_FOUND,
        NOT_AUTHORIZER,
        CONNECTION_FAILED
    }

    /** Checked failure category translated to friendly text by the Swing boundary. */
    public static final class JoinException extends IOException {
        private final Failure failure;

        JoinException(Failure failure, String message) {
            super(message);
            this.failure = failure;
        }

        JoinException(Failure failure, String message, IOException cause) {
            super(message, cause);
            this.failure = failure;
        }

        public Failure failure() {
            return failure;
        }
    }

    /** Validated metadata preview tied to one exact, authenticated bootstrap peer. */
    public record Preview(TypeJoinUri uri, VerifiedThreeDnsPeer peer, AccessList accessList,
                          String typeName, boolean invitationRequired) {}

    private final DnsLookupProtocol lookup;
    private final TypeDescriptionList typeDescriptions;
    private final ServerCid localServerCid;
    private final Preferences preferences;
    private final MysterStream stream;

    public TypeJoinCoordinator(DnsLookupProtocol lookup, TypeDescriptionList typeDescriptions,
            ServerCid localServerCid, Preferences preferences, MysterStream stream) {
        this.lookup = Objects.requireNonNull(lookup, "lookup");
        this.typeDescriptions = Objects.requireNonNull(typeDescriptions, "typeDescriptions");
        this.localServerCid = Objects.requireNonNull(localServerCid, "localServerCid");
        this.preferences = Objects.requireNonNull(preferences, "preferences");
        this.stream = Objects.requireNonNull(stream, "stream");
    }

    /** Resolves only an exact verified peer and fetches a validated, non-persistent preview. */
    public PromiseFuture<Preview> prepare(TypeJoinUri uri) {
        Objects.requireNonNull(uri, "uri");
        return lookup.resolve(uri.bootstrap()).mapAsyncInline(result -> {
            if (result.status() != ThreeDnsLookupResult.Status.EXACT_VERIFIED
                    || result.exactPeer().isEmpty()) {
                return PromiseFuture.newPromiseFutureException(new JoinException(
                        Failure.BOOTSTRAP_NOT_FOUND,
                        "The invitation bootstrap could not be found and may be offline."));
            }
            VerifiedThreeDnsPeer peer = result.exactPeer().orElseThrow();
            return executeExpectedKey(peer,
                    socket -> prepareOnSocket(uri, peer, socket),
                    Failure.BOOTSTRAP_NOT_FOUND,
                    "The invitation bootstrap could not be contacted.");
        });
    }

    private Preview prepareOnSocket(TypeJoinUri uri, VerifiedThreeDnsPeer peer, MysterSocket socket)
            throws IOException {
        AccessList accessList = stream.getAccessList(socket, uri.type())
                .orElseThrow(() -> new JoinException(Failure.INVALID_NETWORK,
                        "The bootstrap returned no access list."));

        validateChain(uri, accessList);

        String typeName = Optional.ofNullable(accessList.getState().getName())
                .filter(name -> !name.isBlank())
                .orElse("Private type");

        return new Preview(uri,
                           peer,
                           accessList,
                           typeName,
                           !accessList.getState().getPolicy().isListFilesPublic());
    }

    /**
     * Redeems a prepared invitation on a virtual thread and imports only after signed membership
     * proof. Public types bypass section 126 because no invitation is needed.
     */
    public PromiseFuture<AccessList> redeem(Preview preview, String code, boolean enabled) {
        Objects.requireNonNull(preview, "preview");

        if (!preview.invitationRequired()) {
            return PromiseFutures.execute(() -> finishImport(preview, preview.accessList(), enabled));
        }

        VerifiedThreeDnsPeer peer = preview.peer();
        PromiseFuture<AccessList> networkResult = executeExpectedKey(peer,
                socket -> redeemOnSocket(preview, code, socket),
                Failure.CONNECTION_FAILED,
                "The bootstrap connection failed.");

        return networkResult.mapAsyncInline(proven ->
                PromiseFutures.execute(() -> finishImport(preview, proven, enabled)));
    }

    private AccessList redeemOnSocket(Preview preview, String code, MysterSocket socket)
            throws IOException {
        if (code == null || code.isEmpty()) {
            throw new JoinException(Failure.INVITATION_NOT_ACCEPTED,
                    "The invitation code may be incorrect or expired.");
        }
        TypeJoinStatus status = stream.redeemTypeInvitation(socket,
                preview.uri().type(), preview.uri().invitationId(), code);
        if (!status.isCanonical()) {
            throw new JoinException(Failure.UNSUPPORTED_VERSION,
                    "This Myster version does not understand the server response.");
        }
        if (status.equals(TypeJoinStatus.INVITATION_NOT_ACCEPTED)) {
            throw new JoinException(Failure.INVITATION_NOT_ACCEPTED,
                    "The invitation code may be incorrect or expired.");
        }
        if (status.equals(TypeJoinStatus.TYPE_NOT_FOUND)) {
            throw new JoinException(Failure.TYPE_NOT_FOUND,
                    "The invited type is no longer available at that bootstrap.");
        }
        if (status.equals(TypeJoinStatus.NOT_AUTHORIZER)) {
            throw new JoinException(Failure.NOT_AUTHORIZER,
                    "The bootstrap can no longer authorize this type.");
        }
        if (!status.equals(TypeJoinStatus.APPROVED)
                && !status.equals(TypeJoinStatus.ALREADY_MEMBER)) {
            throw new JoinException(Failure.CONNECTION_FAILED,
                    "The bootstrap could not complete the invitation.");
        }
        return stream.getAccessList(socket, preview.uri().type())
                .orElseThrow(() -> new JoinException(Failure.INVALID_NETWORK,
                        "The bootstrap returned no membership proof."));
    }

    private AccessList finishImport(Preview preview, AccessList proven, boolean enabled)
            throws IOException {
        validateChain(preview.uri(), proven);
        if (preview.invitationRequired() && !proven.getState().isMember(localServerCid)) {
            throw new JoinException(Failure.INVALID_NETWORK,
                    "The signed access list does not prove this server's membership.");
        }
        rememberEnabled(enabled);
        typeDescriptions.importOrRefreshType(proven, enabled);
        return proven;
    }

    private <T> PromiseFuture<T> executeExpectedKey(VerifiedThreeDnsPeer peer,
            SocketOperation<T> operation, Failure connectionFailure, String connectionMessage) {
        return PromiseFutures.execute(new ExpectedKeySocketCall<>(
                peer, operation, connectionFailure, connectionMessage));
    }

    @FunctionalInterface
    private interface SocketOperation<T> {
        T run(MysterSocket socket) throws IOException;
    }

    private final class ExpectedKeySocketCall<T> implements Callable<T>, Cancellable {
        private final VerifiedThreeDnsPeer peer;
        private final SocketOperation<T> operation;
        private final Failure connectionFailure;
        private final String connectionMessage;
        private final AtomicReference<MysterSocket> activeSocket = new AtomicReference<>();
        private volatile boolean cancelled;

        private ExpectedKeySocketCall(VerifiedThreeDnsPeer peer, SocketOperation<T> operation,
                Failure connectionFailure, String connectionMessage) {
            this.peer = peer;
            this.operation = operation;
            this.connectionFailure = connectionFailure;
            this.connectionMessage = connectionMessage;
        }

        @Override
        public T call() throws IOException {
            ParamBuilder params = new ParamBuilder(peer.address())
                    .withExpectedServerPublicKey(peer.identity().getPublicKey());
            try (MysterSocket socket = stream.makeStreamConnection(params)) {
                activeSocket.set(socket);
                if (cancelled) {
                    closeQuietly(activeSocket.getAndSet(null));
                    throw new IOException("Invitation operation cancelled");
                }
                return operation.run(socket);
            } catch (JoinException exception) {
                throw exception;
            } catch (IOException exception) {
                throw new JoinException(connectionFailure, connectionMessage, exception);
            } finally {
                activeSocket.set(null);
            }
        }

        @Override
        public void cancel() {
            cancelled = true;
            closeQuietly(activeSocket.getAndSet(null));
        }

        private void closeQuietly(MysterSocket socket) {
            if (socket == null) {
                return;
            }
            try {
                socket.close();
            } catch (IOException ignored) {
                // Cancellation already owns the outcome.
            }
        }
    }

    private static void validateChain(TypeJoinUri uri, AccessList accessList) throws JoinException {
        try {
            accessList.validate();
        } catch (IllegalStateException exception) {
            throw new JoinException(Failure.INVALID_NETWORK,
                    "The bootstrap returned an invalid signed access list.");
        }
        if (!accessList.getMysterType().equals(uri.type())) {
            throw new JoinException(Failure.INVALID_NETWORK,
                    "The returned access list belongs to another type.");
        }
    }

    public boolean defaultEnabled() {
        return preferences.getBoolean(KEY_LAST_ENABLE, true);
    }

    private void rememberEnabled(boolean enabled) throws JoinException {
        preferences.putBoolean(KEY_LAST_ENABLE, enabled);
        try {
            preferences.flush();
        } catch (BackingStoreException exception) {
            throw new JoinException(Failure.CONNECTION_FAILED,
                    "The joined type preference could not be saved.");
        }
    }
}
