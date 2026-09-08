package com.myster.type.join;

import java.io.IOException;
import java.security.KeyPair;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.prefs.BackingStoreException;

import com.myster.access.AccessList;
import com.myster.access.AccessListKeyUtils;
import com.myster.access.AccessListManager;
import com.myster.access.AddMemberOp;
import com.myster.access.RemoveMemberOp;
import com.myster.access.Role;
import com.myster.cid.ServerCid;
import com.myster.type.MysterType;

/**
 * Owns serialized access-list membership mutation.
 *
 * <p>Mutations for one type share a monitor while unrelated types remain independent. Invitation
 * redemption may execute its Preferences claim transitions inside the same critical section, which
 * prevents a UI member edit from racing the signed membership append. Access-list changes are made
 * on a validated copy and reach the manager cache only after the file save succeeds.
 */
public final class TypeMembershipService {
    public enum Authorization { AUTHORIZED, TYPE_NOT_FOUND, NOT_AUTHORIZER }
    public enum AddResult { ADDED, ALREADY_MEMBER }
    public enum RemoveResult { REMOVED, NOT_MEMBER }

    @FunctionalInterface
    public interface KeyPairLoader {
        Optional<KeyPair> load(MysterType type) throws IOException;
    }

    @FunctionalInterface
    public interface LockedMutation<T> {
        T run() throws IOException, BackingStoreException;
    }

    private final AccessListManager accessListManager;
    private final KeyPairLoader keyPairLoader;
    private final Map<MysterType, Object> typeLocks = new ConcurrentHashMap<>();

    public TypeMembershipService(AccessListManager accessListManager) {
        this(accessListManager, AccessListKeyUtils::loadKeyPair);
    }

    public TypeMembershipService(AccessListManager accessListManager, KeyPairLoader keyPairLoader) {
        this.accessListManager = Objects.requireNonNull(accessListManager, "accessListManager");
        this.keyPairLoader = Objects.requireNonNull(keyPairLoader, "keyPairLoader");
    }

    /** Executes a compound invitation/membership transition under the type's mutation lock. */
    public <T> T withTypeLock(MysterType type, LockedMutation<T> mutation)
            throws IOException, BackingStoreException {
        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(mutation, "mutation");
        synchronized (typeLocks.computeIfAbsent(type, ignored -> new Object())) {
            return mutation.run();
        }
    }

    /** Returns whether the type exists and the locally stored key remains an authorized writer. */
    public Authorization authorization(MysterType type) throws IOException {
        Optional<AccessList> accessList = accessListManager.loadAccessList(type);
        if (accessList.isEmpty()) {
            return Authorization.TYPE_NOT_FOUND;
        }
        Optional<KeyPair> keyPair = keyPairLoader.load(type);
        if (keyPair.isEmpty() || !accessList.get().getState().isWriter(keyPair.get().getPublic())) {
            return Authorization.NOT_AUTHORIZER;
        }
        return Authorization.AUTHORIZED;
    }

    public AddResult addMember(MysterType type, ServerCid member) throws IOException {
        synchronized (typeLocks.computeIfAbsent(type, ignored -> new Object())) {
            AuthorizedList authorized = requireAuthorized(type);
            if (authorized.accessList().getState().isMember(member)) {
                return AddResult.ALREADY_MEMBER;
            }
            AccessList updated = copy(authorized.accessList());
            updated.appendBlock(new AddMemberOp(member, Role.MEMBER), authorized.keyPair());
            accessListManager.saveAccessList(updated);
            return AddResult.ADDED;
        }
    }

    public RemoveResult removeMember(MysterType type, ServerCid member) throws IOException {
        synchronized (typeLocks.computeIfAbsent(type, ignored -> new Object())) {
            AuthorizedList authorized = requireAuthorized(type);
            if (!authorized.accessList().getState().isMember(member)) {
                return RemoveResult.NOT_MEMBER;
            }
            AccessList updated = copy(authorized.accessList());
            updated.appendBlock(new RemoveMemberOp(member), authorized.keyPair());
            accessListManager.saveAccessList(updated);
            return RemoveResult.REMOVED;
        }
    }

    /** Checks current membership under the same type lock used by mutation. */
    public boolean isMember(MysterType type, ServerCid member) {
        synchronized (typeLocks.computeIfAbsent(type, ignored -> new Object())) {
            return accessListManager.loadAccessList(type)
                    .map(list -> list.getState().isMember(member))
                    .orElse(false);
        }
    }

    private AuthorizedList requireAuthorized(MysterType type) throws IOException {
        AccessList list = accessListManager.loadAccessList(type)
                .orElseThrow(() -> new MembershipException(Authorization.TYPE_NOT_FOUND));
        KeyPair keyPair = keyPairLoader.load(type)
                .orElseThrow(() -> new MembershipException(Authorization.NOT_AUTHORIZER));
        if (!list.getState().isWriter(keyPair.getPublic())) {
            throw new MembershipException(Authorization.NOT_AUTHORIZER);
        }
        return new AuthorizedList(list, keyPair);
    }

    private static AccessList copy(AccessList accessList) {
        return AccessList.fromBlocks(accessList.getBlocks(), accessList.getMysterType());
    }

    private record AuthorizedList(AccessList accessList, KeyPair keyPair) {}

    /** Checked failure that preserves the safe public authorization category. */
    public static final class MembershipException extends IOException {
        private final Authorization authorization;

        MembershipException(Authorization authorization) {
            super(authorization == Authorization.TYPE_NOT_FOUND
                    ? "Type not found" : "Local key is not an authorized writer");
            this.authorization = authorization;
        }

        public Authorization authorization() {
            return authorization;
        }
    }
}
