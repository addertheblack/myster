package com.myster.type.join;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

import com.myster.type.MetadataTypeId;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.myster.access.AccessList;
import com.myster.access.AccessListManager;
import com.myster.access.Policy;
import com.myster.cid.ServerCid;
import com.myster.type.MysterType;

class TestTypeMembershipService {
    private AccessListManager manager;
    private AtomicReference<AccessList> stored;
    private KeyPair writer;
    private MysterType type;

    @BeforeEach
    void setUp() throws Exception {
        writer = KeyPairGenerator.getInstance("Ed25519").generateKeyPair();
        KeyPair typeIdentity = KeyPairGenerator.getInstance("RSA").generateKeyPair();
        AccessList genesis = AccessList.createGenesis(typeIdentity.getPublic(), writer, List.of(), List.of(), Policy.defaultRestrictive(),
                "Private", "", new String[]{"dat"}, false, MetadataTypeId.GENERIC);
        type = genesis.getMysterType();
        stored = new AtomicReference<>(genesis);
        manager = mock(AccessListManager.class);
        when(manager.loadAccessList(type)).thenAnswer(ignored -> Optional.of(stored.get()));
        doAnswer(invocation -> {
            stored.set(invocation.getArgument(0));
            return null;
        }).when(manager).saveAccessList(any(AccessList.class));
    }

    @Test
    void addAndRemoveUseFreshSignedCopies() throws Exception {
        TypeMembershipService service = new TypeMembershipService(manager,
                ignored -> Optional.of(writer));
        ServerCid member = newServerCid();

        assertEquals(TypeMembershipService.AddResult.ADDED, service.addMember(type, member));
        assertTrue(stored.get().getState().isMember(member));
        assertEquals(2, stored.get().getBlocks().size());

        assertEquals(TypeMembershipService.AddResult.ALREADY_MEMBER,
                service.addMember(type, member));
        assertEquals(2, stored.get().getBlocks().size());

        assertEquals(TypeMembershipService.RemoveResult.REMOVED,
                service.removeMember(type, member));
        assertFalse(stored.get().getState().isMember(member));
        assertEquals(3, stored.get().getBlocks().size());
    }

    @Test
    void absentWriterFailsWithoutSaving() {
        TypeMembershipService service = new TypeMembershipService(manager,
                ignored -> Optional.empty());

        assertThrows(IOException.class, () -> service.addMember(type, newServerCid()));
        try {
            verify(manager, never()).saveAccessList(any());
        } catch (IOException exception) {
            throw new AssertionError(exception);
        }
    }

    private static ServerCid newServerCid() throws Exception {
        return ServerCid.fromPublicKey(
                KeyPairGenerator.getInstance("RSA").generateKeyPair().getPublic());
    }
}
