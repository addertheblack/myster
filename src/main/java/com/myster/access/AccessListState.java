package com.myster.access;

import java.security.PublicKey;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.myster.identity.Cid128;
import com.myster.type.MysterType;

/**
 * Derived state from applying all operations in an access list chain.
 *
 * <p>Maintains the current set of writers, members, policy, onramps, and type metadata
 * by sequentially applying operations from blocks. Also provides query methods for
 * membership, writer status, and type metadata.
 *
 * <p>Unknown (non-canonical) operations are silently skipped — the chain stays valid
 * but the operation has no effect on derived state.
 */
public class AccessListState {
    private final Set<PublicKey> writers;
    private final Map<Cid128, Role> members;
    private Policy policy;
    private final List<String> onramps;
    private byte[] tipHash;
    private long height;

    // Type metadata
    private PublicKey typePublicKey;
    private String name;
    private String description;
    private String[] extensions;
    private boolean searchInArchives;

    /**
     * Creates an empty state with no writers, members, or onramps.
     * Policy defaults to restrictive.
     */
    public AccessListState() {
        this.writers = new HashSet<>();
        this.members = new HashMap<>();
        this.policy = Policy.defaultRestrictive();
        this.onramps = new ArrayList<>();
        this.tipHash = new byte[32];
        this.height = -1;
        this.extensions = new String[0];
    }

    /**
     * Applies an operation to this state. Non-canonical operations are silently skipped.
     *
     * @param operation the operation to apply
     * @param writerPubkey the public key of the writer who signed the block
     * @throws IllegalStateException if the writer is not authorized (for non-genesis blocks)
     */
    public void applyOperation(BlockOperation operation, PublicKey writerPubkey) {
        if (height >= 0 && !isWriter(writerPubkey)) {
            throw new IllegalStateException("Unauthorized writer: " + writerPubkey.getAlgorithm());
        }

        if (!operation.getType().isCanonical()) {
            return;
        }

        OpType opType = operation.getType();

        if (opType.equals(OpType.SET_POLICY)) {
            this.policy = ((SetPolicyOp) operation).getPolicy();
        } else if (opType.equals(OpType.ADD_WRITER)) {
            writers.add(((AddWriterOp) operation).getWriterPubkey());
        } else if (opType.equals(OpType.REMOVE_WRITER)) {
            writers.remove(((RemoveWriterOp) operation).getWriterPubkey());
        } else if (opType.equals(OpType.ADD_MEMBER)) {
            AddMemberOp op = (AddMemberOp) operation;
            members.put(op.getMemberIdentity(), op.getRole());
        } else if (opType.equals(OpType.REMOVE_MEMBER)) {
            members.remove(((RemoveMemberOp) operation).getMemberIdentity());
        } else if (opType.equals(OpType.ADD_ONRAMP)) {
            String ep = ((AddOnrampOp) operation).getEndpoint();
            if (!onramps.contains(ep)) {
                onramps.add(ep);
            }
        } else if (opType.equals(OpType.REMOVE_ONRAMP)) {
            onramps.remove(((RemoveOnrampOp) operation).getEndpoint());
        } else if (opType.equals(OpType.SET_TYPE_PUBLIC_KEY)) {
            this.typePublicKey = ((SetTypePublicKeyOp) operation).getTypePublicKey();
        } else if (opType.equals(OpType.SET_NAME)) {
            this.name = ((SetNameOp) operation).getName();
        } else if (opType.equals(OpType.SET_DESCRIPTION)) {
            this.description = ((SetDescriptionOp) operation).getDescription();
        } else if (opType.equals(OpType.SET_EXTENSIONS)) {
            this.extensions = ((SetExtensionsOp) operation).getExtensions();
        } else if (opType.equals(OpType.SET_SEARCH_IN_ARCHIVES)) {
            this.searchInArchives = ((SetSearchInArchivesOp) operation).isSearchInArchives();
        }
    }

    /**
     * Checks if a public key is authorized to write blocks.
     * Compares by encoded bytes since PublicKey.equals may not work across instances.
     *
     * @param pubkey the public key to check
     * @return true if this key is in the writers set
     */
    public boolean isWriter(PublicKey pubkey) {
        byte[] targetEncoded = pubkey.getEncoded();
        for (PublicKey writer : writers) {
            if (java.util.Arrays.equals(writer.getEncoded(), targetEncoded)) {
                return true;
            }
        }
        return false;
    }

    public boolean isMember(Cid128 identity) {
        return members.containsKey(identity);
    }

    public Role getRole(Cid128 identity) {
        return members.get(identity);
    }

    public boolean isAdmin(Cid128 identity) {
        return Role.ADMIN.equals(getRole(identity));
    }

    public Set<PublicKey> getWriters() {
        return Collections.unmodifiableSet(writers);
    }

    public Map<Cid128, Role> getMembers() {
        return Collections.unmodifiableMap(members);
    }

    public Policy getPolicy() {
        return policy;
    }

    public List<String> getOnramps() {
        return Collections.unmodifiableList(onramps);
    }

    public byte[] getTipHash() {
        return tipHash.clone();
    }

    public long getHeight() {
        return height;
    }

    /** Returns the type's full RSA public key, or null if not yet set. */
    public PublicKey getTypePublicKey() {
        return typePublicKey;
    }

    public String getName() {
        return name;
    }

    public String getDescription() {
        return description;
    }

    public String[] getExtensions() {
        return extensions != null ? extensions.clone() : new String[0];
    }

    public boolean isSearchInArchives() {
        return searchInArchives;
    }

    /**
     * Derives a {@link MysterType} from the type's public key, or null if the
     * public key has not been set via a {@code SET_TYPE_PUBLIC_KEY} operation.
     */
    public MysterType toMysterType() {
        if (typePublicKey == null) {
            return null;
        }
        return new MysterType(typePublicKey);
    }

    void setTipHashAndHeight(byte[] tipHash, long height) {
        this.tipHash = tipHash.clone();
        this.height = height;
    }

    @Override
    public String toString() {
        return "AccessListState{" +
               "writers=" + writers.size() +
               ", members=" + members.size() +
               ", policy=" + policy +
               ", onramps=" + onramps.size() +
               ", height=" + height +
               ", name=" + name +
               "}";
    }
}
