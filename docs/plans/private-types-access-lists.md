# Myster Private Types — Access Lists (Part 1)

## Summary

Implement access lists for Myster types via signed, append-only logs. Every Myster type (public or private) is conceptually the same — identified by a public key (`MysterType`). The only difference between "public" and "private" is the access policy: public types allow anyone to discover, list, and download files, while private types restrict those actions to members listed in the access list.

Each type's access list is a blockchain-like chain of signed blocks that carries:
- **Type metadata** (name, description, file extensions, etc.) — changeable by admins via signed operations
- **Membership** (who can access files of this type)
- **Policy** (who can discover, list, search)
- **The type's full public key** — so remote nodes can resolve shortBytes → PublicKey

This unifies the type system: built-in types, custom public types, and private types all use the same mechanism. The distinction is purely in the access policy settings.

Part 1 is intentionally minimal: it provides the core infrastructure (block format, validation, TCP protocol, basic GUI) without consensus, fork resolution, or privacy guarantees.

> **Design correction (2026-02-18):** The original plan introduced `TypeID` as `SHA256(genesis_block_bytes)` — a 32-byte identifier separate from `MysterType`. This was wrong. `MysterType` (the MD5 shortBytes of the type's public key) IS the type identifier everywhere. The genesis block hash is useful for chain integrity verification but is NOT the type's identity. The `TypeID` class has been replaced with `MysterType` throughout. Additionally, the access list now carries type metadata (name, extensions, etc.) and the type's full public key, making it the canonical source of type information for remote nodes.

## Goals

1. **Implement access list blockchain structure**
   - Binary file format with header + signed blocks
   - Ed25519 signatures over canonical block bytes
   - SHA256 for block hashing
   - Support for genesis block creation
   - **Type's full public key stored in genesis block** so remote nodes can resolve shortBytes → PublicKey

2. **Implement validation engine**
   - Sequential block validation (prev_hash, height, signatures)
   - Writer authorization checks
   - Derived state reconstruction (members, writers, policy, onramps, metadata)

3. **Add TCP protocol for access list distribution**
   - New `ACCESS_LIST_GET` protocol section (number TBD)
   - Support full chain retrieval and incremental updates
   - Streaming transfer for large access lists
   - Solves the "get type metadata from shortBytes" problem

4. **Create GUI for type management**
   - Create new type (public or private, generate genesis block)
   - Edit type membership and metadata (admin operations)
   - Share type (export bootstrap bundle)
   - View/import shared types

5. **Integrate with existing type system**
   - Store access lists alongside type data
   - **Every type has an access list** — public types just have permissive policy
   - Built-in types (StandardTypes) also have access lists with open policies
   - Unify type metadata: access list is the canonical source for name, description, extensions
   - Filter search/browse based on membership and policy

6. **Extensible operation system**
   - String-based operation type identifiers (not numeric codes) for forward compatibility
   - Non-canonical enum support: unknown operation types from future versions are preserved and displayable
   - Individual metadata operations: SET_NAME, SET_DESCRIPTION, SET_EXTENSIONS, SET_SEARCH_IN_ARCHIVES

## Non-Goals (Part 1)

- **Fork resolution**: If multiple branches exist, behavior is undefined
- **Consensus mechanisms**: No voting or multi-sig
- **Chain compaction**: No snapshots or pruning
- **Privacy of membership list**: Access lists are readable by anyone with the chain
- **Automatic discovery**: No DHT or gossip protocol for finding private types
- **Member revocation privacy**: Removed members can still read the access list
- **Encryption of file data**: Files are shared unencrypted (use OS-level encryption)

## Assumptions

1. **Ed25519 library available**: Java Cryptography Extension (JCE) or Bouncy Castle
2. **Existing Identity system usable**: Can generate/store Ed25519 keys alongside RSA keys
3. **CustomTypeDefinition already exists**: Has `isPublic` flag ready to use
4. **Access lists stored locally**: Each node stores access lists for types it cares about
5. **No automatic propagation**: Users manually share bootstrap bundles
6. **Single writer in Part 1**: Genesis creator is typically the only admin (multi-admin supported but not exercised)
7. **No cryptographic verification of file ownership**: Access control is membership-based, not file-level
8. **MysterType is a public key**: `MysterType` stores MD5 shortBytes of an RSA public key. The full public key is needed for verification and is carried in the access list genesis block.
9. **Every type has an access list**: Public vs private is a policy distinction, not a structural one. Built-in types have implicit open-policy access lists.

## Proposed Design (High-level)

### Architecture Overview

```
┌─────────────────────────────────────────────────────────┐
│                    Type System Layer                    │
│  - CustomTypeDefinition (isPublic flag)                 │
│  - TypeDescriptionList integration                      │
└────────────────────┬────────────────────────────────────┘
                     │
                     ▼
┌─────────────────────────────────────────────────────────┐
│              Access List Manager                        │
│  - Store/load access lists                              │
│  - Validate chains                                      │
│  - Derive membership state                              │
│  - Create/append blocks                                 │
└────────────────────┬────────────────────────────────────┘
                     │
        ┌────────────┼────────────┐
        ▼            ▼            ▼
┌──────────┐  ┌──────────┐  ┌──────────┐
│ Storage  │  │ Protocol │  │   GUI    │
│  Layer   │  │  Layer   │  │  Layer   │
│          │  │ TCP GET  │  │ Create/  │
│ Binary   │  │ Section  │  │ Edit/    │
│ files in │  │          │  │ Share    │
│ private  │  │          │  │ dialogs  │
└──────────┘  └──────────┘  └──────────┘
```

### Data Model

#### Type Identity

> **Corrected (2026-02-18):** The original design introduced `TypeID = SHA256(genesis_block_bytes)` as a separate 32-byte identifier. This is wrong. The type identifier is `MysterType`, which is the MD5 shortBytes of the type's RSA public key. The genesis block hash is useful for chain integrity verification (pinning) but is NOT the type's identity.

The type's **RSA public key** is the canonical identity. `MysterType` shortBytes are derived from it via MD5 and transmitted on the wire for compactness. The full public key is stored in the access list genesis block via `SET_TYPE_PUBLIC_KEY` so any node can:
1. Download the access list for a type (using MysterType shortBytes)
2. Extract the full RSA public key from the genesis block
3. Verify `MD5(publicKey) == mysterType shortBytes`
4. Use the full public key for any further verification

The genesis block hash (`SHA256(genesis_block_bytes)`) remains useful as a **chain integrity pin** — you can verify you have the same genesis that was originally shared — but it is NOT the type's identifier.

- **Type identifier**: `MysterType` (MD5 shortBytes of the type's RSA public key, 16 bytes)
- **Full public key**: Stored in the access list genesis block so remote nodes can resolve shortBytes → PublicKey
- **Genesis hash**: SHA256 of the genesis block bytes, used for chain integrity pinning (optional verification)
- **Relationship**: `MysterType` shortBytes are transmitted on the wire (e.g., type listings). To get the full public key, a node downloads the access list.

#### Access List File Structure

```
[Header]
  - magic: 0x4D595354 ("MYST")
  - version: 1
  - myster_type: [16 bytes] (MysterType shortBytes)
  - hash_alg: 0x01 (SHA256)
  - sig_alg: 0x01 (Ed25519)
  
[Block 0: Genesis]
  - prev_hash: [32 bytes of 0x00]
  - height: 0
  - timestamp: unix_millis
  - writer_pubkey: [32 bytes Ed25519]
  - payload_hash: SHA256(payload)
  - payload: [operations — must include SET_TYPE_PUBLIC_KEY with the type's RSA public key]
  - signature: Ed25519(canonical_bytes)

[Block 1]
  - prev_hash: SHA256(block_0_bytes)
  - height: 1
  - ...

[Block N]
  - ...
```

#### Block Operations (Payload)

Each block contains **one operation** (genesis blocks can contain multiple).

Operations use **string-based type identifiers** for forward compatibility (see Extensible Operation System below).

**Access control operations:**

1. **SET_POLICY**
   - Payload is a `MessagePak` blob (not raw booleans) for forward extensibility
   - Known fields:
     - `discoverable: bool` - Whether type appears in type lister responses
     - `list_files_public: bool` - Non-members can list files
     - `node_can_join_public: bool` - Open membership (future)
   - Old nodes silently ignore unknown fields added by future versions

2. **ADD_WRITER** / **REMOVE_WRITER**
   - `pubkey: [variable bytes]` - Ed25519 public key (encoded)
   - Writers can append blocks (admins)

3. **ADD_MEMBER** / **REMOVE_MEMBER**
   - `pubkey: [variable bytes]` - Identity public key (RSA from Identity system)
   - `role: String` - Extensible string-based role identifier (same pattern as OpType)
   - Known roles: `"MEMBER"` (can access files), `"ADMIN"` (can access files + write blocks)
   - Unknown roles from future versions are preserved as non-canonical

4. **ADD_ONRAMP** / **REMOVE_ONRAMP**
   - `endpoint: String` - "ip:port" or "hostname:port"
   - Suggested servers to query for the access list

**Type metadata operations:**

5. **SET_TYPE_PUBLIC_KEY** (required in genesis)
   - `pubkey: [variable bytes]` - The type's RSA public key
   - This is the key whose MD5 hash produces the MysterType shortBytes
   - Must be present in genesis block; allows any node to verify `shortBytes == MD5(pubkey)`

6. **SET_NAME**
   - `name: String` - User-readable name for this type

7. **SET_DESCRIPTION**
   - `description: String` - User-readable description

8. **SET_EXTENSIONS**
   - `extensions: String[]` - File extensions to filter by

9. **SET_SEARCH_IN_ARCHIVES**
   - `searchInArchives: bool` - Whether to look inside .zip files

#### Extensible Operation System

> **Design pattern (2026-02-18):** Uses the extensible enum pattern.

Operations are identified by **string names** (not numeric codes) in the serialized format. This provides:

- **Forward compatibility**: A node running an older version can read an access list containing operations it doesn't recognize. Unknown operations are preserved in the chain and can be displayed in the UI as their raw string type.
- **No conflicts**: Two groups extending the format independently won't collide on a numeric code. String names like `"SET_POLICY"` vs `"SET_NAME"` are self-describing and unlikely to conflict.
- **Debuggability**: When inspecting a binary access list file, string operation types are immediately meaningful.

**Implementation:**

```java
// OpType uses strings for serialization, supports non-canonical values
public class OpType {
    // Known canonical types
    public static final OpType SET_POLICY = new OpType("SET_POLICY", true);
    public static final OpType ADD_WRITER = new OpType("ADD_WRITER", true);
    public static final OpType REMOVE_WRITER = new OpType("REMOVE_WRITER", true);
    public static final OpType ADD_MEMBER = new OpType("ADD_MEMBER", true);
    public static final OpType REMOVE_MEMBER = new OpType("REMOVE_MEMBER", true);
    public static final OpType ADD_ONRAMP = new OpType("ADD_ONRAMP", true);
    public static final OpType REMOVE_ONRAMP = new OpType("REMOVE_ONRAMP", true);
    public static final OpType SET_TYPE_PUBLIC_KEY = new OpType("SET_TYPE_PUBLIC_KEY", true);
    public static final OpType SET_NAME = new OpType("SET_NAME", true);
    public static final OpType SET_DESCRIPTION = new OpType("SET_DESCRIPTION", true);
    public static final OpType SET_EXTENSIONS = new OpType("SET_EXTENSIONS", true);
    public static final OpType SET_SEARCH_IN_ARCHIVES = new OpType("SET_SEARCH_IN_ARCHIVES", true);
    
    private final String identifier;
    private final boolean canonical;
    
    // For known types
    private OpType(String identifier, boolean canonical) { ... }
    
    // For unknown types read from future data
    public static OpType fromString(String identifier) {
        // Returns the known constant if it matches, otherwise creates
        // a non-canonical instance
    }
    
    public String getIdentifier() { return identifier; }
    public boolean isCanonical() { return canonical; }
}
```

Unknown operations are deserialized as a generic `UnknownOp` that preserves the raw payload bytes. The chain remains valid — the node just can't interpret the operation's effect on derived state.

#### Derived State

After validating the chain, nodes maintain:

```java
class AccessListState {
    Set<PublicKey> writers;           // Ed25519 keys that can append blocks
    Map<Cid128, Role> members;       // Identity -> role mapping
    Policy policy;                   // Current policy settings
    List<String> onramps;            // Bootstrap servers
    byte[] tipHash;                  // Hash of latest block
    long height;                     // Current chain height
    
    // Type metadata (from access list operations)
    PublicKey typePublicKey;          // The type's RSA public key (from SET_TYPE_PUBLIC_KEY)
    String name;                     // From SET_NAME
    String description;              // From SET_DESCRIPTION
    String[] extensions;             // From SET_EXTENSIONS
    boolean searchInArchives;        // From SET_SEARCH_IN_ARCHIVES
}
```

**Role** uses the same extensible string-based enum pattern as `OpType`:

```java
// Type-safe extensible enum — same pattern as OpType
class Role {
    public static final Role MEMBER = new Role("MEMBER", true);  // Can access files
    public static final Role ADMIN = new Role("ADMIN", true);    // Can access files + implies writer status
    
    private final String identifier;
    private final boolean canonical;
    
    public static Role fromString(String identifier) {
        // Returns MEMBER or ADMIN constant if matches, otherwise
        // creates a non-canonical Role for unknown values from future versions
    }
    
    public String getIdentifier() { return identifier; }
    public boolean isCanonical() { return canonical; }
}
```

This way, if a future version introduces a new role (e.g., `"MODERATOR"`), old nodes can
still read the access list and preserve the unknown role in the chain without crashing.

**Policy** is serialized using `MessagePak` (the project's extensible key-value format) so
new policy fields can be added in future versions without breaking old nodes:

```java
class Policy {
    boolean discoverable;
    boolean listFilesPublic;
    boolean nodeCanJoinPublic;
    // Future fields are silently ignored by old nodes when reading MessagePak
    
    // Serialize to MessagePak for extensibility
    MessagePak toMessagePak() {
        MessagePak pak = MessagePak.newEmpty();
        pak.putBoolean("/discoverable", discoverable);
        pak.putBoolean("/listFilesPublic", listFilesPublic);
        pak.putBoolean("/nodeCanJoinPublic", nodeCanJoinPublic);
        return pak;
    }
    
    static Policy fromMessagePak(MessagePak pak) {
        boolean discoverable = pak.getBoolean("/discoverable").orElse(false);
        boolean listFilesPublic = pak.getBoolean("/listFilesPublic").orElse(false);
        boolean nodeCanJoinPublic = pak.getBoolean("/nodeCanJoinPublic").orElse(false);
        return new Policy(discoverable, listFilesPublic, nodeCanJoinPublic);
    }
}
```

This means `SET_POLICY` operation payload is a MessagePak blob, not raw booleans. Old nodes
encountering new policy fields in the MessagePak simply ignore them.

### Protocol Design

#### TCP Section: ACCESS_LIST_GET

**Section Number**: 125 (TBD - check for conflicts)

**Request Format**:
```
[4 bytes] section_number = 125
[16 bytes] myster_type (MysterType shortBytes)
[32 bytes] known_tip_hash (all zeros if client has no chain — requests full chain)
```

- `known_tip_hash`: The hash of the client's latest block. The server uses this to find where the client's chain ends and sends only the blocks after that point. All zeros means the client has nothing and wants the full chain from genesis.

**Response Format**:
```
[4 bytes] status_code
  - 0: OK
  - 1: NOT_FOUND (server doesn't have this type)
  - 2: FORK_DETECTED (known_tip_hash not found in server's chain)
  - 3: ERROR

If status == OK:
  [8 bytes] total_bytes_remaining (total size of all blocks that will follow)
  [blocks streamed, each prefixed with its byte size]:
    [4 bytes] block_byte_size (0 = end of stream)
    [block_byte_size bytes] block data
    ...repeat...
    [4 bytes] 0 (sentinel — end of stream)
```

- `total_bytes_remaining`: The total byte count of all block data (including size prefixes and sentinel) that follows. Allows the client to reject the transfer immediately if the payload is too large, without waiting for individual blocks.
- Each block is also prefixed with its individual byte size so the client can make per-block decisions as well.

**Behavior**:
- If `known_tip_hash == all zeros`: Send full chain (header + all blocks from genesis)
- If `known_tip_hash` matches a block in the chain: Send blocks after that point
- If `known_tip_hash` matches the tip: Return OK with `total_bytes_remaining = 4` (just the sentinel) — already up-to-date
- If `known_tip_hash` not found in chain: Return `FORK_DETECTED` — the client's chain has diverged

### File Storage

**Location**: `{PrivateDataPath}/AccessLists/{myster_type_hex}.accesslist`

**Format**: Binary (see structure above)

**Index**: Keep in-memory map of `MysterType -> AccessListState` for loaded types

### Integration with Type System

> **Corrected (2026-02-18):** Every type has an access list. Public vs private is just a policy setting. The access list is the canonical source of type metadata (name, description, extensions) and carries the type's full public key.

#### Unified Type Model

All types — built-in, custom public, and private — are `MysterType` instances (public key shortBytes). The access list determines:
- **Who can access**: Policy + membership
- **What the type is**: Metadata (name, extensions, etc.)
- **How to find it**: Onramps

For **built-in types** (StandardTypes like Music, Applications), the system generates implicit open-policy access lists at startup if none exist on disk.

For **custom public types**, the access list has `discoverable=true, listFilesPublic=true` — anyone can see and download.

For **private types**, the access list restricts access to listed members only.

#### CustomTypeDefinition Relationship

`CustomTypeDefinition` already stores the full public key locally. With the access list carrying the public key, `CustomTypeDefinition` becomes the local-only creation tool, while the access list is the network-distributable, verifiable source of truth.

#### Search/Browse Filtering

When a node receives a search or browse request for a type:

1. Load access list for that type
2. Check policy settings
3. If `policy.listFilesPublic == true` → process normally (public type behavior)
4. If `policy.listFilesPublic == false`:
   - Extract the requester's RSA public key from the TLS connection (`TLSSocket.getPeerPublicKey()`)
   - Derive `Cid128` from that public key (`Cid128 = Trunc128(SHA-256(publicKey.getEncoded()))`)
   - Check if that `Cid128` is in the access list's members map
   - If not a member → return empty results
   - If member → process normally

> **Note on identity verification:** Every Myster TCP connection is TLS-encrypted. Both sides present their RSA public key via TLS certificates during the handshake. The server already has the caller's full public key — it just needs to derive the `Cid128` from it and check the members map. No additional authentication step is needed beyond what TLS already provides.

#### Discovery Filtering

When responding to a UDP Myster Type Lister transaction (`TypeDatagramServer`):
- If `policy.discoverable == false` → omit from type list response
- Otherwise include

The type lister transaction can also be encrypted via MSD (Myster Secure Datagram), which carries the client's identity (public key / CID) in Section 2 of the MSD packet. This means the server could potentially return non-discoverable types to known members, though Part 1 simply omits non-discoverable types for all callers.

### GUI Design

#### 1. Create Type Dialog

> **Corrected (2026-02-18):** This is now "Create Type" not "Create Private Type" since all types have access lists. The dialog creates both public and private types — the difference is just the policy settings.

**Trigger**: "Create Type" button in Type Manager

**Fields**:
- Name (text field)
- Description (text area)
- Extensions (list widget)
- Search in archives (checkbox)
- **Privacy setting** (radio buttons):
  - Public (discoverable, files public) — sets permissive policy
  - Private (not discoverable, files not public) — sets restrictive policy
- **Initial members** (list widget, relevant for private types):
  - Add by server address (looks up identity)
  - Add by public key (paste)
  - Role selector (MEMBER | ADMIN)
- **Initial onramps** (list widget):
  - Add server addresses
- **Policy settings** (advanced, checkboxes):
  - Discoverable
  - List files public
  - Node can join public (disabled in Part 1)

**Action**:
1. Generate RSA keypair for the type (this IS the type's identity)
2. Generate Ed25519 keypair for access list signing (the admin key)
3. Create genesis block with operations:
   - SET_TYPE_PUBLIC_KEY (the RSA public key — required)
   - SET_NAME, SET_DESCRIPTION, SET_EXTENSIONS, SET_SEARCH_IN_ARCHIVES
   - ADD_WRITER (the Ed25519 admin key)
   - ADD_MEMBER for each initial member
   - ADD_ONRAMP for each onramp
   - SET_POLICY with selected settings
4. Compute MysterType = MD5(RSA public key)
5. Save access list file
6. Save admin keypair
7. Create CustomTypeDefinition
8. Add to TypeDescriptionList

#### 2. Edit Type Dialog

**Trigger**: "Edit" button in Type Manager (only for types you admin)

**Tabs**:

**Metadata Tab**:
- Name (text field) — change appends SET_NAME block
- Description (text area) — change appends SET_DESCRIPTION block
- Extensions (list widget) — change appends SET_EXTENSIONS block
- Search in archives (checkbox) — change appends SET_SEARCH_IN_ARCHIVES block

**Members Tab**:
- Table: Identity | Role | Added Date
- Buttons: Add Member, Remove Member, Change Role

**Writers Tab**:
- Table: Public Key (hex) | Added Date
- Buttons: Add Writer, Remove Writer

**Onramps Tab**:
- List: Server addresses
- Buttons: Add, Remove

**Policy Tab**:
- Checkboxes for policy settings

**Actions**:
- Each action creates and appends a new block
- Signs with stored admin keypair
- Updates access list file
- Broadcasts to onramps (optional, not in Part 1)

#### 3. Share Type Dialog

**Trigger**: "Share" button in Type Manager

**Shows**:
- MysterType shortBytes (hex, copiable)
- Genesis hash (hex, copiable) — for chain integrity verification
- Onramps (list, copiable)
- Expected writers (optional, for verification)

**Actions**:
- **Copy Bootstrap JSON**: Exports JSON bundle:
  ```json
  {
    "mysterType": "abc123...",
    "genesisHash": "def456...",
    "onramps": ["192.168.1.1:6346", "example.com:6346"],
    "writers": ["ed25519:abc..."]
  }
  ```
- **Save to File**: Writes JSON to file

#### 4. Import Type Dialog

**Trigger**: "Import Type" button in Type Manager

**Fields**:
- Paste bootstrap JSON (text area)
- OR select JSON file

**Actions**:
1. Parse bootstrap JSON
2. Connect to onramps and request access list via TCP (using MysterType shortBytes)
3. Validate full chain from genesis
4. Verify genesis contains SET_TYPE_PUBLIC_KEY operation
5. Verify `MD5(typePublicKey) == mysterType` from bootstrap JSON
6. Optionally verify genesis hash matches expected
7. Derive final state (including type metadata)
8. Check if user's identity is in members
9. If yes → save access list and add to TypeDescriptionList
10. If no → show warning but allow adding as "observer"

## Affected Modules/Packages

- **`com.myster.type`** - CustomTypeDefinition, TypeDescription, TypeDescriptionList
  - Access list integration for all types (not just private)
  - Type metadata sourced from access list derived state

- **`com.myster.access`** (NEW package) - Access list blockchain implementation
  - `AccessList` - Main class for managing a single access list
  - `AccessListManager` - Global manager for all loaded access lists
  - `AccessBlock` - Block data structure
  - `AccessListValidator` - Validation engine
  - `AccessListState` - Derived state (including type metadata)
  - `BlockOperation` - Operation types and serialization (string-based, extensible)
  - `OpType` - Extensible operation type with canonical/non-canonical support
  - `AccessListStorage` - Binary file I/O

- **`com.myster.net.stream.server`** - TCP protocol server
  - Add `AccessListGetServer` handler for section 125

- **`com.myster.net.stream.client`** - TCP protocol client
  - Add `AccessListGetClient` for retrieving access lists

- **`com.myster.identity`** - Identity system
  - Extend to support Ed25519 keys for access lists
  - Add `AccessListIdentity` for managing signing keys

- **`com.myster.access.ui`** (NEW package) - GUI components
  - `CreateTypeDialog`
  - `EditTypeDialog`
  - `ShareTypeDialog`
  - `ImportTypeDialog`

- **`com.myster.filemanager`** - File sharing
  - Add membership checks before serving files
  - Filter search results based on access lists

- **`com.myster.net.server.datagram`** - UDP protocol server
  - Filter type lister responses based on discoverable policy

## Files/Classes to Change or Create

### Files to Create

#### 1. **`com/myster/access/AccessBlock.java`** - Block data structure
```java
public class AccessBlock {
    private final byte[] prevHash;
    private final long height;
    private final long timestamp;
    private final PublicKey writerPubkey;  // Ed25519
    private final byte[] payloadHash;
    private final byte[] payload;
    private final byte[] signature;
    
    // Methods:
    // - byte[] toCanonicalBytes(byte[] mysterTypeBytes)
    // - boolean verifySignature(byte[] mysterTypeBytes)
    // - byte[] computeHash()
    // - BlockOperation parsePayload()
}
```

#### 2. **`com/myster/access/BlockOperation.java`** - Operation types
```java
public interface BlockOperation {
    OpType getType();
    void serialize(DataOutputStream out) throws IOException;
    static BlockOperation deserialize(DataInputStream in) throws IOException;
}

// OpType: string-based, extensible, with canonical/non-canonical support
// Known types: SET_POLICY, ADD_WRITER, REMOVE_WRITER, ADD_MEMBER, REMOVE_MEMBER,
//              ADD_ONRAMP, REMOVE_ONRAMP, SET_TYPE_PUBLIC_KEY, SET_NAME, 
//              SET_DESCRIPTION, SET_EXTENSIONS, SET_SEARCH_IN_ARCHIVES
// Unknown types: preserved as UnknownOp with raw payload bytes

// Implementations: SetPolicyOp, AddWriterOp, RemoveWriterOp, 
//                  AddMemberOp, RemoveMemberOp, AddOnrampOp, RemoveOnrampOp,
//                  SetTypePublicKeyOp, SetNameOp, SetDescriptionOp,
//                  SetExtensionsOp, SetSearchInArchivesOp,
//                  UnknownOp (for forward compatibility)
```

#### 3. **`com/myster/access/AccessListState.java`** - Derived state
```java
public class AccessListState {
    private final Set<PublicKey> writers;
    private final Map<Cid128, Role> members;
    private final Policy policy;
    private final List<String> onramps;
    private final byte[] tipHash;
    private final long height;
    
    // Type metadata
    private PublicKey typePublicKey;
    private String name;
    private String description;
    private String[] extensions;
    private boolean searchInArchives;
    
    // Methods:
    // - void applyOperation(BlockOperation op, PublicKey writer)
    // - boolean isMember(Cid128 identity)
    // - boolean isWriter(PublicKey pubkey)
    // - Role getRole(Cid128 identity)
    // - MysterType toMysterType() — derives MysterType from typePublicKey
}
```

#### 4. **`com/myster/access/AccessList.java`** - Access list management
```java
public class AccessList {
    private final MysterType mysterType;  // Derived from the type's public key in genesis
    private final List<AccessBlock> blocks;
    private AccessListState state;
    
    // Methods:
    // - static AccessList createGenesis(...)
    //   Genesis MUST contain SET_TYPE_PUBLIC_KEY operation
    // - void appendBlock(BlockOperation op, PrivateKey signingKey)
    // - boolean validate()
    // - AccessListState deriveState()
    // - void save(File path)
    // - static AccessList load(File path)
}
```

#### 5. **`com/myster/access/AccessListManager.java`** - Global manager
```java
public class AccessListManager {
    private final Map<MysterType, AccessList> loadedLists;
    private final File storageDir;
    
    // Methods:
    // - AccessList getAccessList(MysterType type)
    // - void addAccessList(AccessList list)
    // - Optional<AccessList> loadFromDisk(MysterType type)
    // - void saveToDisk(MysterType type, AccessList list)
}
```

#### 6. **`com/myster/access/AccessListStorage.java`** - Binary I/O
```java
public class AccessListStorage {
    // Methods:
    // - static void write(AccessList list, OutputStream out)
    // - static AccessList read(InputStream in)
    // - static void writeHeader(...)
    // - static void writeBlock(AccessBlock block, OutputStream out)
    // - static AccessBlock readBlock(InputStream in)
}
```

#### 7. **`com/myster/access/AccessListValidator.java`** - Validation
```java
public class AccessListValidator {
    // Methods:
    // - static ValidationResult validate(AccessList list)
    // - static boolean validateBlock(AccessBlock block, 
    //                                 AccessBlock prev, 
    //                                 AccessListState state,
    //                                 byte[] mysterTypeBytes)
    // Must verify genesis contains SET_TYPE_PUBLIC_KEY
    // Must verify MD5(typePublicKey) == mysterType shortBytes
}
```

#### 8. **`com/myster/identity/AccessListIdentity.java`** - Ed25519 key management
```java
public class AccessListIdentity {
    private final File keystoreDir;
    
    // Methods:
    // - KeyPair generateAccessListKeys()
    // - void saveKeyPair(MysterType type, KeyPair keys)
    // - Optional<KeyPair> loadKeyPair(MysterType type)
    // - byte[] sign(byte[] data, PrivateKey key)
    // - boolean verify(byte[] data, byte[] signature, PublicKey key)
}
```

#### 9. **`com/myster/net/stream/server/AccessListGetServer.java`** - TCP server
```java
public class AccessListGetServer extends ServerStreamHandler {
    public static final int NUMBER = 125;
    
    private final AccessListManager manager;
    
    @Override
    public int getSectionNumber() { return NUMBER; }
    
    @Override
    public void section(ConnectionContext context) throws IOException {
        // Read mysterType shortBytes (16 bytes)
        // Read known_tip_hash (32 bytes)
        // Load access list from AccessListManager
        // If not found → return status 1 (NOT_FOUND)
        // If known_tip_hash is all zeros → stream all blocks from genesis
        // Find known_tip_hash in chain to determine offset
        // If known_tip_hash not found → return status 2 (FORK_DETECTED)
        // If known_tip_hash matches tip → return OK + total_bytes=4 + sentinel (0)
        // Otherwise write OK status, compute and write total_bytes_remaining,
        //   then stream blocks after offset, each prefixed with [4 bytes] block_byte_size
        // Write [4 bytes] 0 sentinel to end stream
    }
}
```

#### 10. **`com/myster/net/stream/client/AccessListGetClient.java`** - TCP client
```java
public class AccessListGetClient {
    // Methods:
    // - AccessList fetchAccessList(MysterAddress server, MysterType type)
    // - AccessList fetchIncrementalUpdate(MysterAddress server, 
    //                                      MysterType type, 
    //                                      byte[] knownTipHash)
}
```

#### 11. **`com/myster/access/ui/CreateTypeDialog.java`** - GUI
```java
public class CreateTypeDialog extends JDialog {
    // Fields for name, description, extensions, members, onramps, policy
    // Privacy setting (public vs private = policy choice)
    // Generate genesis on "Create" button (includes SET_TYPE_PUBLIC_KEY)
    // Add to TypeDescriptionList
}
```

#### 12. **`com/myster/access/ui/EditTypeDialog.java`** - GUI
```java
public class EditTypeDialog extends JDialog {
    // Tabs for metadata, members, writers, onramps, policy
    // Each action appends a new block
}
```

#### 13. **`com/myster/access/ui/ShareTypeDialog.java`** - GUI
```java
public class ShareTypeDialog extends JDialog {
    // Display MysterType hex, genesis hash, onramps
    // Export as JSON
}
```

#### 14. **`com/myster/access/ui/ImportTypeDialog.java`** - GUI
```java
public class ImportTypeDialog extends JDialog {
    // Parse bootstrap JSON
    // Fetch access list from onramps
    // Validate (including SET_TYPE_PUBLIC_KEY → MysterType verification)
    // Save
}
```

### Files to Modify

#### 15. **`com/myster/type/CustomTypeDefinition.java`**
- Access list provides the canonical type metadata over the network
- Local `CustomTypeDefinition` is used for type creation; access list is the distributed form
- Consider: should metadata changes via access list update the local CustomTypeDefinition?

#### 16. **`com/myster/type/DefaultTypeDescriptionList.java`**
- Integrate with AccessListManager for policy checks
- Source type metadata from access list state when available

#### 17. **`com/myster/type/ui/TypeManagerPreferences.java`**
- Add "Create Type" button (opens CreateTypeDialog for both public and private)
- Add "Import Type" button
- Enable "Edit" for types where user is admin
- Enable "Share" for all types with access lists

#### 18. **`com/myster/filemanager/FileTypeListManager.java`**
- Add policy/membership check before serving files
- Filter based on `AccessListManager` and `AccessListState.getPolicy()`

#### 19. **`com/myster/net/server/datagram/TypeDatagramServer.java`**
- Filter type lister responses based on `discoverable` policy
- Omit non-discoverable types from type lister transaction response
- Note: currently only sends MysterType shortBytes (16 bytes) — this is correct

#### 20. **`com/myster/net/stream/server/RequestDirThread.java`**
- Add policy/membership check
- If not member AND not `listFilesPublic` → return empty

#### 21. **`com/myster/net/stream/server/RequestSearchThread.java`**
- Add policy/membership check
- Filter results if requester not a member

#### 22. **`com/myster/Myster.java`**
- Initialize `AccessListManager` on startup
- Register `AccessListGetServer` handler
- Load access lists for enabled types

#### 23. **`com/myster/application/MysterGlobals.java`**
- Add `getAccessListPath()` method (returns `{PrivateDataPath}/AccessLists/`)

## Step-by-Step Implementation Plan

### Implementation Strategy for Large Feature

This is a large feature with 10 phases. To make implementation manageable and reviewable, break it into **3 major milestones** with separate implementation summaries:

**Milestone 1: Core Infrastructure (Phases 1-4)**
- Goal: Working blockchain that can be created, validated, saved, and transferred
- Creates: Block structure, validation, storage, TCP protocol
- No GUI yet - testable via unit/integration tests
- Deliverable: Can programmatically create access lists and fetch them via TCP
- Implementation summary: `docs/impl_summary/private-types-core-infrastructure.md`

**Milestone 2: GUI and Type Creation (Phases 5-7)**
- Goal: Users can create, edit, share, and import types (both public and private)
- Creates: All GUI dialogs, integration with TypeManager
- Depends on: Milestone 1 complete
- Deliverable: Full type lifecycle via UI
- Implementation summary: `docs/impl_summary/private-types-gui.md`

**Milestone 3: Access Control Integration (Phases 8-10)**
- Goal: Membership checks enforced across file sharing
- Creates: Access control hooks in file serving, search, discovery
- Depends on: Milestone 2 complete
- Deliverable: Private types fully functional with access control
- Implementation summary: `docs/impl_summary/private-types-access-control.md`

**Recommended Approach:**
1. Implement Milestone 1, create implementation summary
2. Test thoroughly with unit/integration tests
3. Implement Milestone 2, create implementation summary  
4. Test with manual GUI testing
5. Implement Milestone 3, create implementation summary
6. Full end-to-end testing

This allows for:
- **Incremental progress**: Each milestone is independently reviewable
- **Clear dependencies**: Can't do GUI without core, can't do access control without GUI
- **Testability**: Each milestone has clear test criteria
- **Rollback safety**: Can roll back to a stable milestone if issues found

### Phase 1: Core Block Structure (Standalone, No Integration)

**Goal**: Implement and test the blockchain structure independently.

#### Step 1.1: Create block data structures
1. **Create `com.myster.access` package**
2. **Implement `AccessBlock.java`**:
   - Constructor with all fields
   - `toCanonicalBytes(byte[] mysterTypeBytes)` - Serialize for signing (uses MysterType shortBytes, 16 bytes)
   - `computeHash()` - SHA256 of full block bytes
   - `verifySignature(byte[] mysterTypeBytes)` - Verify Ed25519 signature
3. **Implement `OpType.java`** (extensible, string-based):
   - String identifier field + boolean canonical field
   - Static constants for all known operations (SET_POLICY, ADD_WRITER, etc.)
   - Static constants for metadata operations (SET_TYPE_PUBLIC_KEY, SET_NAME, SET_DESCRIPTION, SET_EXTENSIONS, SET_SEARCH_IN_ARCHIVES)
   - `fromString(String)` factory: returns known constant or creates non-canonical instance
   - `isCanonical()` method
4. **Implement `BlockOperation.java` interface**:
   - Uses `OpType` (string-based, extensible)
   - Create concrete operation classes:
     - `SetPolicyOp`
     - `AddWriterOp`, `RemoveWriterOp`
     - `AddMemberOp`, `RemoveMemberOp`
     - `AddOnrampOp`, `RemoveOnrampOp`
     - `SetTypePublicKeyOp` (carries the type's full RSA public key)
     - `SetNameOp`, `SetDescriptionOp`, `SetExtensionsOp`, `SetSearchInArchivesOp`
     - `UnknownOp` (preserves raw payload for unrecognized operation types from future versions)
   - Serialization: write OpType string identifier + operation-specific payload
   - Deserialization: read OpType string, dispatch to known deserializer or create UnknownOp

#### Step 1.2: Implement state derivation
1. **Create `Policy.java`**: Data class with boolean fields, serialized via MessagePak for extensibility
   - `toMessagePak()` / `fromMessagePak()` for wire format
   - New policy fields added in future versions are silently ignored by old nodes
2. **Create `Role.java`**: Extensible string-based type-safe enum (same pattern as OpType)
   - Known values: `MEMBER`, `ADMIN`
   - `fromString(String)` factory: returns known constant or creates non-canonical instance
   - `isCanonical()` method
3. **Implement `AccessListState.java`**:
   - Constructor with empty collections
   - `applyOperation(BlockOperation op, PublicKey writer)`:
     - For each known operation type, update appropriate collection/field
     - For unknown (non-canonical) operations, skip silently (chain stays valid)
     - Validate writer authorization
   - Type metadata fields: `typePublicKey`, `name`, `description`, `extensions`, `searchInArchives`
   - `toMysterType()`: derives `MysterType` from `typePublicKey` (MD5 shortBytes)
   - Query methods: `isMember()`, `isWriter()`, `getRole()`
4. **Unit tests**: Test state transitions for each operation, including metadata ops and unknown ops

#### Step 1.3: Implement access list class
1. **Implement `AccessList.java`**:
   - `createGenesis(...)`: Static factory for genesis block
     - Take type RSA public key, initial members, writers, policy, onramps, metadata
     - Genesis MUST include `SetTypePublicKeyOp` with the type's RSA public key
     - Genesis SHOULD include `SetNameOp`, `SetExtensionsOp`, etc.
     - Create block with height=0, prev_hash=zeros
     - Sign with Ed25519 admin key
     - Compute `MysterType` = `MD5(RSA public key)` — this identifies the type
     - Return AccessList
   - `appendBlock(BlockOperation op, PrivateKey key)`:
     - Create new block with height++
     - Set prev_hash to tip hash
     - Sign with provided key
     - Add to blocks list
     - Re-derive state
   - `validate()`: Full chain validation
     - Check header
     - Validate each block in sequence
     - Verify genesis contains `SET_TYPE_PUBLIC_KEY`
     - Verify `MD5(typePublicKey) == mysterType` from header
   - `deriveState()`: Apply all operations
2. **Unit tests**: Create chain, append blocks, validate

### Phase 2: Binary Serialization

**Goal**: Save/load access lists to binary files.

#### Step 2.1: Implement storage layer
1. **Implement `AccessListStorage.java`**:
   - `writeHeader(OutputStream out, byte[] mysterTypeBytes)`:
     - Write magic, version, mysterTypeBytes (16 bytes), hash_alg, sig_alg
   - `writeBlock(AccessBlock block, OutputStream out)`:
     - Write all block fields in fixed-width big-endian
   - `write(AccessList list, OutputStream out)`:
     - Write header + all blocks
   - `readHeader(InputStream in)`: Parse and validate header, return MysterType shortBytes
   - `readBlock(InputStream in)`: Read single block
   - `read(InputStream in)`: Full access list
2. **Unit tests**: Round-trip serialization

#### Step 2.2: Implement file management
1. **Modify `MysterGlobals.java`**:
   - Add `getAccessListPath()` method
   - Create directory on first access
2. **Implement `AccessListManager.java`**:
   - `loadFromDisk(MysterType type)`:
     - Check `{AccessListPath}/{mysterType.toHexString()}.accesslist`
     - Use `AccessListStorage.read()`
   - `saveToDisk(MysterType type, AccessList list)`:
     - Write to file
   - In-memory cache of loaded lists
3. **Unit tests**: Save/load from temp directory

### Phase 3: Ed25519 Integration

**Goal**: Generate and manage Ed25519 keys for access lists.

#### Step 3.1: Choose crypto library
1. **Add Bouncy Castle dependency** to `pom.xml` (if not already present)
2. **Verify Ed25519 support** in current Java version

#### Step 3.2: Implement key management
1. **Create `AccessListIdentity.java`**:
   - `generateAccessListKeys()`: Generate Ed25519 KeyPair
   - `saveKeyPair(MysterType type, KeyPair keys)`:
     - Store in `{PrivateDataPath}/AccessListKeys/{type.toHexString()}.key`
     - Use PKCS8 for private key, X.509 for public key
   - `loadKeyPair(MysterType type)`: Load from disk
   - `sign(byte[] data, PrivateKey key)`: Ed25519 signature
   - `verify(byte[] data, byte[] signature, PublicKey key)`: Verification
2. **Unit tests**: Generate, save, load, sign, verify

#### Step 3.3: Integrate with AccessBlock
1. **Update `AccessBlock.verifySignature()`**: Use `AccessListIdentity.verify()`
2. **Update `AccessList.appendBlock()`**: Use `AccessListIdentity.sign()`
3. **Integration tests**: Full chain with real Ed25519 signatures

### Phase 4: TCP Protocol

**Goal**: Serve and fetch access lists over TCP.

#### Step 4.1: Choose section number
1. **Check existing section numbers** in codebase
2. **Assign section 125** (or next available)
3. **Document in protocol constants** (if such file exists)

#### Step 4.2: Implement server
1. **Create `AccessListGetServer.java`**:
   - Extend `ServerStreamHandler`
   - `getSectionNumber()`: Return 125
   - `section(ConnectionContext context) throws IOException`:
     - Read mysterType shortBytes (16 bytes)
     - Read known_tip_hash (32 bytes)
     - Load access list from `AccessListManager`
     - If not found → return status 1 (NOT_FOUND)
     - If known_tip_hash is all zeros → stream all blocks from genesis
     - Find known_tip_hash in chain to determine offset
     - If known_tip_hash not found → return status 2 (FORK_DETECTED)
     - If known_tip_hash matches tip → return OK + `total_bytes_remaining=4` + sentinel (0)
     - Otherwise write OK status, compute and write `total_bytes_remaining` (sum of all block sizes + size prefixes + sentinel)
     - Stream blocks after offset, each prefixed with `[4 bytes] block_byte_size`
     - Write `[4 bytes] 0` sentinel to end stream
2. **Unit tests**: Mock ConnectionContext, test various scenarios

#### Step 4.3: Implement client
1. **Create `AccessListGetClient.java`**:
   - `fetchAccessList(MysterAddress server, MysterType type)`:
     - Connect to server
     - Send request with known_tip_hash = all zeros (full fetch)
     - Read response status
     - If OK, read `total_bytes_remaining` — reject if too large
     - Loop: read block_byte_size, if 0 stop, else read block data
     - Reconstruct AccessList from blocks
     - Validate genesis contains SET_TYPE_PUBLIC_KEY
     - Verify MD5(typePublicKey) matches the requested MysterType
   - `fetchIncrementalUpdate(MysterAddress server, MysterType type, byte[] knownTipHash)`:
     - Send request with known_tip_hash
     - Read status — handle FORK_DETECTED
     - Read `total_bytes_remaining` — reject if too large
     - Read size-prefixed blocks, append to existing list
     - Client can also abort mid-stream if an individual block size exceeds a reasonable threshold
2. **Unit tests**: Mock socket, test fetch

#### Step 4.4: Register server handler
1. **Modify `Myster.java` (in `addServerConnectionSettings()`):**
   - Add `serverFacade.addConnectionSection(new AccessListGetServer(accessListManager))`

### Phase 5: GUI - Create Type

**Goal**: User can create a new type (public or private — the difference is just the policy setting).

#### Step 5.1: Create dialog
1. **Create `com.myster.access.ui` package**
2. **Implement `CreateTypeDialog.java`**:
   - Extends `JDialog`
   - Fields:
     - Name, description, extensions (reuse from CustomTypeDefinition UI)
     - Search in archives checkbox
     - Privacy setting (public vs private radio buttons)
     - Initial members table (identity, role) — for private types
     - Initial onramps list
     - Policy checkboxes (advanced)
   - "Add Member" button:
     - Prompts for server address
     - Uses tracker to lookup identity
     - Adds to table
   - "Create" button:
     - Validates inputs
     - Generates RSA keypair for the type (this IS the type's identity)
     - Generates Ed25519 keypair for access list signing (the admin key)
     - Computes MysterType = MD5(RSA public key)
     - Creates genesis block with operations:
       - SET_TYPE_PUBLIC_KEY (the RSA public key — required)
       - SET_NAME, SET_DESCRIPTION, SET_EXTENSIONS, SET_SEARCH_IN_ARCHIVES
       - ADD_WRITER (the Ed25519 admin key)
       - ADD_MEMBER for each initial member
       - ADD_ONRAMP for each onramp
       - SET_POLICY with selected settings
     - Saves access list to disk (keyed by MysterType hex)
     - Saves Ed25519 admin keypair
     - Creates CustomTypeDefinition
     - Adds to TypeDescriptionList

#### Step 5.2: Integrate with Type Manager
1. **Modify `TypeManagerPreferences.java`**:
   - Add "Create Type" button
   - On click → open `CreateTypeDialog`
2. **Test**: Create type, verify file saved, appears in type list

### Phase 6: GUI - Edit Type

**Goal**: Admin can modify type metadata, membership, and policy.

#### Step 6.1: Create dialog
1. **Implement `EditTypeDialog.java`**:
   - Extends `JDialog`
   - Constructor takes `MysterType` and `AccessListManager`
   - Load current `AccessList` and `AccessListState`
   - Load admin keypair (if user is admin)
   - Tabbed pane:
     - **Metadata tab**:
       - Name, description, extensions, search-in-archives
       - Each change appends appropriate operation block (SET_NAME, SET_DESCRIPTION, etc.)
     - **Members tab**:
       - Table showing current members (identity, role, date added)
       - Buttons: Add, Remove, Change Role
       - Each action appends a block:
         - `list.appendBlock(new AddMemberOp(identity, role), adminKey)`
     - **Writers tab**: Similar for writers
     - **Onramps tab**: Add/remove onramps
     - **Policy tab**: Checkboxes for policy, save appends SET_POLICY block
2. **Integrate with Type Manager**:
   - "Edit" button enabled for types if user is admin
   - Check `state.isWriter(userIdentity)`

#### Step 6.2: Test
3. Verify block appended
4. Verify state updated
5. Verify file saved

### Phase 7: GUI - Share and Import

**Goal**: Users can share and import private types.

#### Step 7.1: Share dialog
1. **Implement `SharePrivateTypeDialog.java`**:
   - Display TypeID (hex, with copy button)
   - Display genesis hash (hex, with copy button)
   - Display onramps (list)
   - "Export JSON" button:
     - Creates bootstrap JSON:
       ```json
       {
         "typeId": "abc...",
         "genesisHash": "def...",
         "onramps": ["192.168.1.1:6346"],
         "writers": ["ed25519:..."]
       }
       ```
     - Save to file or copy to clipboard

#### Step 7.2: Import dialog
1. **Implement `ImportPrivateTypeDialog.java`**:
   - Text area to paste JSON or file picker
   - "Import" button:
     - Parse JSON
     - Extract typeId, genesisHash, onramps
     - Connect to onramps (try each in order)
     - Call `AccessListGetClient.fetchAccessList()`
     - Validate chain
     - Verify genesis hash matches
     - Derive state
     - Check if user's identity is member
     - If member → add to type list with full access
     - If not → show warning, optionally add as "observer"
2. **Integrate with Type Manager**:
   - "Import Private Type" button
   - Opens dialog

#### Step 7.3: Test
1. Share type from one instance
2. Import to another instance
3. Verify access list fetched
4. Verify type appears in list

### Phase 8: Access Control Integration

**Goal**: Enforce membership checks in file serving.

#### Step 8.1: File listing filtering
1. **Modify `RequestDirThread.java`**:
   - After reading type, check if private
   - Get `TypeDescription`, check `source == CUSTOM && !isPublic`
   - If private:
     - Load access list
     - Get requester identity from connection context
     - Check `state.isMember(requesterIdentity)`
     - If not member:
       - Check `state.policy.listFilesPublic`
       - If false → return empty list
   - Otherwise proceed normally

#### Step 8.2: Search filtering
1. **Modify `RequestSearchThread.java`**:
   - Similar logic to RequestDirThread
   - Filter results if requester not a member

#### Step 8.3: Type broadcast filtering
1. **Modify `TypeDatagramServer.java`**:
   - When building type list to broadcast:
     - For each enabled type:
       - If private, load access list
       - Check `state.policy.discoverable`
       - If false, omit from broadcast

#### Step 8.4: Test
1. Create private type with discoverable=false
2. Search from non-member → empty results
3. Add member
4. Search from member → see files

### Phase 9: Testing and Documentation

**Goal**: Comprehensive tests and documentation.

#### Step 9.1: Unit tests
- Test each class independently
- Test operation serialization
- Test state transitions
- Test validation rules

#### Step 9.2: Integration tests
- Test full create → edit → share → import flow
- Test access control enforcement
- Test incremental updates

#### Step 9.3: Documentation
- Javadoc for all public classes
- Update codebase-structure.md
- Update this plan with "Implementation Summary"
- Create user guide for private types

### Phase 10: Migration and Rollout

**Goal**: Deploy without breaking existing types.

#### Step 10.1: Migration
- All existing custom types have `isPublic = true`
- No migration needed

#### Step 10.2: Rollout
1. **Deploy Phase 1-4** (backend) without GUI
2. **Test** access list creation/loading programmatically
3. **Deploy Phase 5-7** (GUI) behind feature flag
4. **Test** with small group
5. **Enable** for all users

## Tests/Verification

### Unit Tests

1. **`AccessBlockTest.java`**
   - Test block creation
   - Test canonical bytes serialization
   - Test signature verification
   - Test hash computation

2. **`BlockOperationTest.java`**
   - Test each operation type serialization
   - Test deserialization edge cases

3. **`AccessListStateTest.java`**
   - Test applying each operation type
   - Test writer authorization
   - Test member queries

4. **`AccessListTest.java`**
   - Test genesis creation
   - Test block appending
   - Test validation failures (bad signature, wrong height, etc.)

5. **`AccessListStorageTest.java`**
   - Test header write/read
   - Test block write/read
   - Test full access list round-trip
   - Test reading corrupted files

6. **`AccessListIdentityTest.java`**
   - Test key generation
   - Test key save/load
   - Test signing/verification

### Integration Tests

1. **`AccessListManagerTest.java`**
   - Test loading from disk
   - Test saving to disk
   - Test caching

2. **`AccessListProtocolTest.java`**
   - Test TCP server/client interaction
   - Test incremental updates
   - Test error cases (not found, etc.)

3. **`PrivateTypeE2ETest.java`**
   - Create private type
   - Share to another node
   - Import on another node
   - Verify access control works

### Manual Testing Checklist

- [ ] Create private type with initial members
- [ ] Edit type to add/remove members
- [ ] Edit type to change policy
- [ ] Share type as JSON
- [ ] Import type from JSON
- [ ] Verify non-members cannot see files
- [ ] Verify members can see files
- [ ] Verify non-discoverable types don't appear in broadcasts
- [ ] Verify incremental updates work
- [ ] Test with multiple admins
- [ ] Test chain validation rejects invalid blocks

## Docs/Comments to Update

### Code Documentation

1. **`com.myster.access` package Javadoc**
   - Package overview explaining access list system
   - Link to design document

2. **`AccessList.java`**
   - Comprehensive class Javadoc
   - Explain genesis creation
   - Explain validation rules

3. **`AccessBlock.java`**
   - Document canonical bytes format
   - Document signature coverage

4. **`BlockOperation.java`**
   - Document each operation type
   - Document serialization format

5. **`AccessListManager.java`**
   - Document lifecycle (load, cache, save)
   - Document thread safety

### Architecture Documentation

1. **`docs/codebase-structure.md`**
   - Add section on Access List subsystem
   - Describe blockchain structure
   - Describe TCP protocol

2. **`docs/design/Myster Private Types — Access Lists (Part 1 Implementation Spec).md`**
   - Already exists, reference it

3. **Create `docs/user-guide/private-types.md`**
   - User-facing guide on creating/using private types
   - Screenshots of dialogs
   - Example use cases

### Protocol Documentation

1. **Create `docs/protocol/access-list-protocol.md`**
   - Document ACCESS_LIST_GET section
   - Request/response format
   - Error codes

### README Updates

1. **`README.md`**
   - Add "Private Types" to features list
   - Link to user guide

## Acceptance Criteria

### Core Functionality

- [ ] Can create private type with genesis block
- [ ] TypeID correctly computed as SHA256(genesis)
- [ ] Can append blocks with valid signatures
- [ ] Chain validation rejects:
  - [ ] Invalid signatures
  - [ ] Wrong prev_hash
  - [ ] Non-sequential heights
  - [ ] Unauthorized writers
- [ ] Derived state correctly reflects all operations
- [ ] Access lists persist to disk and reload correctly

### Protocol

- [ ] TCP ACCESS_LIST_GET serves full chains
- [ ] TCP ACCESS_LIST_GET serves incremental updates
- [ ] Client can fetch access lists from servers
- [ ] Streaming transfer works for large chains

### GUI

- [ ] Create Private Type dialog creates valid genesis
- [ ] Edit dialog appends valid blocks
- [ ] Share dialog exports valid bootstrap JSON
- [ ] Import dialog fetches and validates access lists
- [ ] Type Manager shows private types with correct icons/labels

### Access Control

- [ ] Non-members cannot list files (if listFilesPublic=false)
- [ ] Non-members cannot search files (if listFilesPublic=false)
- [ ] Non-discoverable types don't appear in broadcasts
- [ ] Members can access files
- [ ] Admins can modify access lists

### Security

- [ ] Ed25519 signatures verified correctly
- [ ] Unauthorized writers cannot append blocks
- [ ] Chain tampering detected and rejected
- [ ] Private keys stored securely in private data path

## Risks/Edge Cases/Rollout Notes

### Risks

1. **Fork handling undefined**
   - *Mitigation*: Part 1 explicitly doesn't handle forks. Document this limitation.
   - *Future*: Part 2 will add fork resolution via consensus.

2. **Access list size unbounded**
   - *Mitigation*: Add max_blocks limit in protocol. Warn if chain > 1MB.
   - *Future*: Chain compaction via snapshots.

3. **No revocation privacy**
   - *Mitigation*: Document that removed members can still read the access list.
   - *Future*: Private channels or encrypted membership lists.

4. **Onramp availability**
   - *Mitigation*: Allow multiple onramps. Bootstrap JSON can be shared out-of-band.
   - *Future*: DHT or gossip protocol for discovery.

5. **Key loss**
   - *Mitigation*: Warn users to backup private keys. Consider multiple admins.
   - *Future*: Key recovery mechanisms.

### Edge Cases

1. **Empty access list (no members)**
   - Behavior: Type exists but no one can access it except admins
   - Handling: Allow, warn in GUI

2. **Self-revocation (admin removes self)**
   - Behavior: Type becomes unmodifiable if only admin
   - Handling: Warn before allowing, require confirmation

3. **Circular onramp references**
   - Behavior: Import might fail if onramps unreachable
   - Handling: Try all onramps, fallback to manual file import

4. **Genesis hash mismatch**
   - Behavior: Chain validation fails
   - Handling: Reject import, show error message

5. **Concurrent edits**
   - Behavior: Two admins append blocks simultaneously
   - Handling: Part 1 creates fork (undefined). Document limitation.

6. **Very old knownHeight in incremental update**
   - Behavior: Large response
   - Handling: Respect maxBytes/maxBlocks limits, return TOO_LARGE if needed

### Rollout Notes

1. **Backward compatibility**
   - Protocol section 125 is new, old clients ignore it
   - New clients gracefully handle missing ACCESS_LIST_GET

2. **Feature flag**
   - Consider hiding "Create Private Type" button behind preference flag initially
   - Enable after sufficient testing

3. **Performance**
   - Access list validation can be expensive for long chains
   - Cache derived state, don't re-validate on every file request

4. **Storage**
   - Access lists stored in `{PrivateDataPath}/AccessLists/`
   - Keys stored in `{PrivateDataPath}/AccessListKeys/`
   - Ensure these directories are in .gitignore

5. **Monitoring**
   - Log access list loading/validation
   - Log protocol requests/responses
   - Track access list chain length (warn if > 10,000 blocks)

6. **User education**
   - Private types require understanding of membership model
   - Provide clear documentation and examples
   - Consider in-app tutorials

## Open Questions

1. **Should we allow public key import for writers/admins?**
   - *Current*: Auto-generate only
   - *Alternative*: Allow paste/import
   - *Decision*: Auto-generate for MVP, add import in future

2. **Should we implement fork detection (but not resolution)?**
   - *Current*: Undefined behavior
   - *Alternative*: Detect and warn user
   - *Decision*: Detect and log warning, don't use forked chain

3. **Should we cache access list state in memory or re-derive on each request?**
   - *Current*: Re-derive
   - *Alternative*: Cache with invalidation
   - *Decision*: Cache in AccessListManager, invalidate on append

4. **Should we allow observers (non-members who can still see the access list)?**
   - *Current*: Import allowed for non-members
   - *Alternative*: Reject import
   - *Decision*: Allow, mark as "observer" in UI

5. **What's the policy for genesis blocks with no initial members?**
   - *Current*: Allowed
   - *Alternative*: Require at least one
   - *Decision*: Require creator as first ADMIN

## Implementation Notes

### Why This Design

1. **Blockchain structure**: Provides verifiable, append-only log with clear history
2. **Ed25519**: Fast, small signatures, widely supported
3. **SHA256**: Standard, secure hashing
4. **Binary format**: Compact, efficient to parse
5. **TCP protocol**: Reuses existing infrastructure, reliable streaming
6. **Separate keys for access lists**: Isolates risk, allows per-type admin rotation

### TypeID Derivation

TypeID = SHA256(genesis_block_bytes) ensures:
- Global uniqueness (collision-resistant)
- Immutability (changing genesis changes TypeID)
- Self-certifying (genesis is verifiable)

### Canonical Signing

Signing only the essential fields (not the full block) allows:
- Smaller signature payload
- Clearer signature coverage
- Easier verification

### State Derivation

Maintaining derived state separate from block log allows:
- Fast membership queries
- Efficient access control checks
- Clear separation of concerns

### Part 1 Limitations Acknowledged

This is intentionally a minimal implementation:
- No consensus → Single source of truth assumed
- No fork resolution → Undefined behavior
- No privacy → Access lists readable by all
- Manual distribution → No automatic discovery

These will be addressed in future parts.

---

**Plan Version**: 1.0
**Created**: 2026-02-15
**Status**: Ready for implementation

