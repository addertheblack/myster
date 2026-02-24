package com.myster.access;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.security.KeyPair;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.Signature;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import com.myster.type.MysterType;

/**
 * Represents a complete access list chain for a type.
 *
 * <p>An AccessList is an append-only blockchain of signed blocks that define
 * membership, permissions, policies, and type metadata. The chain starts with a
 * genesis block (height 0) that must include a {@link SetTypePublicKeyOp} carrying
 * the type's RSA public key — the MD5 hash of which produces the {@link MysterType}
 * identifier.
 *
 * <p>Every Myster type (public or private) conceptually has an access list. Public
 * types simply have a permissive policy.
 */
public class AccessList {
    private final MysterType mysterType;
    private final List<AccessBlock> blocks;
    private AccessListState state;

    private AccessList(AccessBlock genesisBlock, MysterType mysterType) {
        if (genesisBlock.getHeight() != 0) {
            throw new IllegalArgumentException("Genesis block must have height 0");
        }

        this.mysterType = mysterType;
        this.blocks = new ArrayList<>();
        this.blocks.add(genesisBlock);
        this.state = new AccessListState();

        deriveState();
    }

    private AccessList(List<AccessBlock> blocks, MysterType mysterType) {
        if (blocks.isEmpty()) {
            throw new IllegalArgumentException("Block list cannot be empty");
        }
        if (blocks.get(0).getHeight() != 0) {
            throw new IllegalArgumentException("First block must be genesis (height 0)");
        }

        this.mysterType = mysterType;
        this.blocks = new ArrayList<>(blocks);
        this.state = new AccessListState();

        validate();
        deriveState();
    }

    /**
     * Creates a new access list with a genesis block.
     *
     * <p>The genesis block will contain (in order):
     * <ol>
     *   <li>{@code SET_TYPE_PUBLIC_KEY} — the type's RSA public key (required)</li>
     *   <li>{@code ADD_WRITER} — the admin's Ed25519 public key</li>
     *   <li>Any initial member operations</li>
     *   <li>Any initial onramp operations</li>
     *   <li>Any metadata operations (name, description, extensions, searchInArchives)</li>
     *   <li>{@code SET_POLICY} — initial policy settings</li>
     * </ol>
     *
     * @param typePublicKey the type's RSA public key (its MD5 hash becomes the MysterType)
     * @param adminKeyPair the Ed25519 keypair for the initial admin/writer
     * @param initialMembers initial members to add
     * @param initialOnramps initial onramp servers
     * @param policy initial policy settings
     * @param name initial type name (may be null)
     * @param description initial type description (may be null)
     * @param extensions initial file extensions (may be null)
     * @param searchInArchives initial search-in-archives setting
     * @return a new AccessList with genesis block
     * @throws IOException if block creation fails
     */
    public static AccessList createGenesis(
            PublicKey typePublicKey,
            KeyPair adminKeyPair,
            List<AddMemberOp> initialMembers,
            List<String> initialOnramps,
            Policy policy,
            String name,
            String description,
            String[] extensions,
            boolean searchInArchives) throws IOException {

        MysterType mysterType = new MysterType(typePublicKey);
        byte[] mysterTypeBytes = mysterType.toBytes();

        byte[] prevHash = new byte[32];
        long height = 0;
        long timestamp = System.currentTimeMillis();
        PublicKey writerPubkey = adminKeyPair.getPublic();

        // Build genesis payload with all initial operations
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        DataOutputStream dos = new DataOutputStream(baos);

        // Count operations
        int opCount = 2; // SET_TYPE_PUBLIC_KEY + ADD_WRITER + SET_POLICY = minimum
        opCount += initialMembers.size();
        opCount += initialOnramps.size();
        opCount += 1; // SET_POLICY
        if (name != null) opCount++;
        if (description != null) opCount++;
        if (extensions != null) opCount++;
        opCount++; // SET_SEARCH_IN_ARCHIVES
        dos.writeInt(opCount);

        // 1. SET_TYPE_PUBLIC_KEY (required, must be first)
        new SetTypePublicKeyOp(typePublicKey).serialize(dos);

        // 2. ADD_WRITER
        new AddWriterOp(writerPubkey).serialize(dos);

        // 3. Initial members
        for (AddMemberOp memberOp : initialMembers) {
            memberOp.serialize(dos);
        }

        // 4. Initial onramps
        for (String onramp : initialOnramps) {
            new AddOnrampOp(onramp).serialize(dos);
        }

        // 5. Metadata
        if (name != null) new SetNameOp(name).serialize(dos);
        if (description != null) new SetDescriptionOp(description).serialize(dos);
        if (extensions != null) new SetExtensionsOp(extensions).serialize(dos);
        new SetSearchInArchivesOp(searchInArchives).serialize(dos);

        // 6. SET_POLICY
        new SetPolicyOp(policy).serialize(dos);

        byte[] payload = baos.toByteArray();

        // Sign with the actual MysterType bytes
        AccessBlock tempBlock = new AccessBlock(prevHash, height, timestamp,
                                                writerPubkey, payload, new byte[64]);
        byte[] canonicalBytes = tempBlock.toCanonicalBytes(mysterTypeBytes);
        byte[] signature = signBytes(canonicalBytes, adminKeyPair.getPrivate(), writerPubkey);

        AccessBlock genesisBlock = new AccessBlock(prevHash, height, timestamp,
                                                   writerPubkey, payload, signature);

        return new AccessList(genesisBlock, mysterType);
    }

    /**
     * Appends a new block with the given operation, signed by the provided keypair.
     *
     * @param operation the operation to append
     * @param signingKeyPair the keypair to sign with (must be an authorized writer)
     * @return the new block
     * @throws IOException if serialization fails
     * @throws IllegalStateException if signer is not authorized
     */
    public AccessBlock appendBlock(BlockOperation operation, KeyPair signingKeyPair) throws IOException {
        PublicKey writerPubkey = signingKeyPair.getPublic();

        if (!state.isWriter(writerPubkey)) {
            throw new IllegalStateException("Writer not authorized to append blocks");
        }

        AccessBlock tip = blocks.get(blocks.size() - 1);
        byte[] prevHash = tip.computeHash();
        long height = tip.getHeight() + 1;
        long timestamp = System.currentTimeMillis();

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        DataOutputStream dos = new DataOutputStream(baos);
        operation.serialize(dos);
        byte[] payload = baos.toByteArray();

        byte[] mysterTypeBytes = mysterType.toBytes();

        AccessBlock tempBlock = new AccessBlock(prevHash, height, timestamp,
                                                writerPubkey, payload, new byte[64]);
        byte[] canonicalBytes = tempBlock.toCanonicalBytes(mysterTypeBytes);
        byte[] signature = signBytes(canonicalBytes, signingKeyPair.getPrivate(), writerPubkey);

        AccessBlock newBlock = new AccessBlock(prevHash, height, timestamp,
                                              writerPubkey, payload, signature);

        if (!newBlock.verifySignature(mysterTypeBytes)) {
            throw new IOException("Signature verification failed on new block");
        }

        blocks.add(newBlock);

        state.applyOperation(operation, writerPubkey);
        state.setTipHashAndHeight(newBlock.computeHash(), height);

        return newBlock;
    }

    /**
     * Validates the entire chain.
     *
     * @throws IllegalStateException if validation fails
     */
    public void validate() {
        if (blocks.isEmpty()) {
            throw new IllegalStateException("No blocks in chain");
        }

        AccessBlock genesis = blocks.get(0);
        if (genesis.getHeight() != 0) {
            throw new IllegalStateException("Genesis block must have height 0");
        }

        byte[] expectedZeros = new byte[32];
        if (!Arrays.equals(genesis.getPrevHash(), expectedZeros)) {
            throw new IllegalStateException("Genesis prev_hash must be all zeros");
        }

        byte[] mysterTypeBytes = mysterType.toBytes();

        AccessListState tempState = new AccessListState();

        for (int i = 0; i < blocks.size(); i++) {
            AccessBlock block = blocks.get(i);

            if (block.getHeight() != i) {
                throw new IllegalStateException(
                    "Block height mismatch at index " + i + ": expected " + i + ", got " + block.getHeight());
            }

            if (i > 0) {
                AccessBlock prevBlock = blocks.get(i - 1);
                byte[] expectedPrevHash = prevBlock.computeHash();
                if (!Arrays.equals(block.getPrevHash(), expectedPrevHash)) {
                    throw new IllegalStateException("Block " + i + " prev_hash does not match");
                }
            }

            if (!block.verifySignature(mysterTypeBytes)) {
                throw new IllegalStateException("Block " + i + " signature verification failed");
            }

            if (i > 0 && !tempState.isWriter(block.getWriterPubkey())) {
                throw new IllegalStateException("Block " + i + " writer was not authorized");
            }

            try {
                List<BlockOperation> operations = parseBlockOperations(block, i == 0);
                for (BlockOperation operation : operations) {
                    tempState.applyOperation(operation, block.getWriterPubkey());
                }
                tempState.setTipHashAndHeight(block.computeHash(), block.getHeight());
            } catch (IOException e) {
                throw new IllegalStateException("Failed to parse operation in block " + i, e);
            }
        }

        // Verify genesis contains SET_TYPE_PUBLIC_KEY
        if (tempState.getTypePublicKey() == null) {
            throw new IllegalStateException("Genesis block must contain SET_TYPE_PUBLIC_KEY operation");
        }

        // Verify MD5(typePublicKey) matches mysterType
        MysterType derived = tempState.toMysterType();
        if (!mysterType.equals(derived)) {
            throw new IllegalStateException("MysterType mismatch: header=" + mysterType +
                                            " but derived from public key=" + derived);
        }
    }

    private void deriveState() {
        state = new AccessListState();

        for (int i = 0; i < blocks.size(); i++) {
            AccessBlock block = blocks.get(i);
            try {
                List<BlockOperation> operations = parseBlockOperations(block, i == 0);
                for (BlockOperation operation : operations) {
                    state.applyOperation(operation, block.getWriterPubkey());
                }
                state.setTipHashAndHeight(block.computeHash(), block.getHeight());
            } catch (IOException e) {
                throw new RuntimeException("Failed to derive state from block at height " + block.getHeight(), e);
            }
        }
    }

    private static List<BlockOperation> parseBlockOperations(AccessBlock block, boolean isGenesis) throws IOException {
        List<BlockOperation> operations = new ArrayList<>();
        java.io.ByteArrayInputStream bais = new java.io.ByteArrayInputStream(block.getPayload());
        java.io.DataInputStream dis = new java.io.DataInputStream(bais);

        if (isGenesis) {
            int opCount = dis.readInt();
            for (int i = 0; i < opCount; i++) {
                operations.add(BlockOperation.deserialize(dis));
            }
        } else {
            operations.add(BlockOperation.deserialize(dis));
        }

        return operations;
    }

    /** Returns the MysterType for this access list (MD5 shortBytes of the type's RSA public key). */
    public MysterType getMysterType() {
        return mysterType;
    }

    public AccessListState getState() {
        return state;
    }

    public List<AccessBlock> getBlocks() {
        return Collections.unmodifiableList(blocks);
    }

    public AccessBlock getGenesisBlock() {
        return blocks.get(0);
    }

    public AccessBlock getTipBlock() {
        return blocks.get(blocks.size() - 1);
    }

    public long getHeight() {
        return blocks.size() - 1;
    }

    /**
     * Creates an AccessList from a list of blocks and the MysterType from the file header.
     *
     * @param blocks the blocks to load
     * @param mysterType the MysterType from the file header
     * @return the AccessList
     * @throws IllegalArgumentException if blocks are invalid
     */
    public static AccessList fromBlocks(List<AccessBlock> blocks, MysterType mysterType) {
        return new AccessList(blocks, mysterType);
    }

    private static byte[] signBytes(byte[] data, PrivateKey privateKey, PublicKey publicKey) throws IOException {
        try {
            String algorithm = getSignatureAlgorithm(publicKey);
            Signature signature = Signature.getInstance(algorithm);
            signature.initSign(privateKey);
            signature.update(data);
            return signature.sign();
        } catch (Exception e) {
            throw new IOException("Failed to sign data", e);
        }
    }

    private static String getSignatureAlgorithm(PublicKey publicKey) {
        String algorithm = publicKey.getAlgorithm();
        return switch (algorithm) {
            case "Ed25519", "EdDSA" -> "Ed25519";
            case "EC" -> "SHA256withECDSA";
            case "RSA" -> "SHA256withRSA";
            default -> "Ed25519";
        };
    }

    @Override
    public String toString() {
        return "AccessList{mysterType=" + mysterType +
               ", blocks=" + blocks.size() +
               ", height=" + getHeight() +
               ", state=" + state +
               "}";
    }
}

