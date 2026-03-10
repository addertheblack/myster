# Myster Private Types — Access Lists (Part 1 Implementation Spec)

This is a condensed, implementation-ready specification for Part 1 of Myster Private Types.

Part 1 defines:

- An append-only signed Access List log
- A TCP protocol section for retrieving it
- Minimal GUI support

No consensus, fork resolution, or privacy guarantees are included in Part 1.

> **Design Update (2026-02-18):** Major corrections applied. TypeID replaced with MysterType (the existing type identifier based on a public key). Every type has an access list — public vs private is a policy distinction. Access list carries type metadata (name, extensions, etc.) and the type's full public key. Operations use string-based identifiers for extensibility.

------

# 1. Core Model

## 1.1 Type Identity

- Types are identified by `MysterType` = `MD5(typePublicKey.getEncoded())` (16 bytes)
- This is the SAME identifier used everywhere in the Myster network (wire protocol, type listings, etc.)
- The type's full RSA public key is stored in the access list genesis block via `SET_TYPE_PUBLIC_KEY`
- The genesis block hash (`SHA256(genesis_block_bytes)`) is useful for chain integrity pinning but is NOT the type identifier

> **Correction:** The original spec defined `TypeID = SHA256(genesis_block_bytes)` as a separate 32-byte identifier. This was wrong. MysterType IS the type identifier.

## 1.2 Unified Type Model

Every type (built-in, custom public, private) has an access list. The distinction between "public" and "private" is purely a policy setting in the access list:
- **Public types**: `discoverable=true`, `listFilesPublic=true` — anyone can see and download
- **Private types**: `discoverable=false`, `listFilesPublic=false` — restricted to listed members

## 1.3 Roles

- **Writer/Admin key**: may append blocks (Ed25519)
- **Member key**: may access Type resources (RSA identity)

Only two roles are implemented in Part 1:

- `MEMBER`
- `ADMIN`

`ADMIN` implies MEMBER and block-writing ability.

Roles use the same extensible string-based enum pattern as `OpType`. Future versions may add new roles (e.g., `"MODERATOR"`). Old nodes preserve unknown roles as non-canonical values without crashing.

------

# 2. Access List Structure

## 2.1 File Layout

Binary format only.

```
[Header]
[Block 0 (genesis)]
[Block 1]
...
[Block N]
```

Streaming decode must be supported.

------

## 2.2 Header

Fields:

- `magic` (fixed constant, 0x4D595354 = "MYST")
- `version = 1`
- `myster_type` (16 bytes — MysterType shortBytes)
- `hash_alg = SHA256`
- `sig_alg = Ed25519`

Header is not signed. Blocks are self-verifying.

------

## 2.3 Block Format

Each block contains:

- `prev_hash` (32 bytes)
- `height` (u64, starts at 0 for genesis)
- `timestamp` (u64, unix millis)
- `writer_pubkey` (variable length, encoded Ed25519 public key)
- `payload_length` (u32)
- `payload_bytes`
- `payload_hash = SHA256(payload_bytes)`
- `signature` (Ed25519 over canonical block bytes)

### 2.3.1 Canonical Signed Bytes

The signature MUST cover exactly:

```
myster_type (16 bytes)
prev_hash
height
timestamp
writer_pubkey
payload_hash
```

All fields encoded in fixed-width big-endian form.

------

## 2.4 Payload Operations

Operations use **string-based type identifiers** for forward compatibility (not numeric codes).

### 2.4.1 Serialization Format

```
[UTF-8 string length (u16)] [UTF-8 operation type string] [operation-specific payload]
```

### 2.4.2 Extensible Operation Types

- Each operation type is identified by a string (e.g., `"SET_POLICY"`, `"ADD_MEMBER"`)
- Known operations are "canonical" — the node understands their semantics
- Unknown operations from future versions are "non-canonical" — preserved in chain, effect on derived state is skipped
- `OpType.isCanonical()` distinguishes the two
- Unknown operations are deserialized as `UnknownOp` preserving raw payload bytes

### 2.4.3 Supported Operations (Part 1)

**Access control:**
- `SET_POLICY(MessagePak)` — policy fields serialized as MessagePak blob for extensibility. Known fields: `discoverable:bool`, `list_files_public:bool`, `node_can_join_public:bool`. Old nodes silently ignore unknown fields.
- `ADD_WRITER(pubkey)`
- `REMOVE_WRITER(pubkey)`
- `ADD_MEMBER(pubkey, role:string)` — role is a string identifier using the extensible enum pattern (known values: `"MEMBER"`, `"ADMIN"`; unknown roles preserved as non-canonical)
- `REMOVE_MEMBER(pubkey)`
- `ADD_ONRAMP(endpoint)`
- `REMOVE_ONRAMP(endpoint)`

**Type metadata:**
- `SET_TYPE_PUBLIC_KEY(pubkey)` — the type's RSA public key (REQUIRED in genesis)
- `SET_NAME(name:string)`
- `SET_DESCRIPTION(description:string)`
- `SET_EXTENSIONS(extensions:string[])`
- `SET_SEARCH_IN_ARCHIVES(flag:bool)`

One operation per block (except genesis which may contain multiple).

------

## 2.5 Genesis Block

- `height = 0`
- `prev_hash = 32 bytes of 0x00`
- MUST contain `SET_TYPE_PUBLIC_KEY` with the type's RSA public key
- SHOULD contain initial metadata (`SET_NAME`, `SET_EXTENSIONS`, etc.)
- Defines:
  - Initial writers (must include creator)
  - Initial policy
  - Optional initial onramps

After genesis creation, verify:
```
MD5(typePublicKey.getEncoded()) == myster_type from header
```

------

# 3. Validation Rules

To validate a chain:

1. Validate header version and algorithms
2. Validate genesis block contains `SET_TYPE_PUBLIC_KEY`
3. Validate `MD5(typePublicKey) == myster_type` from header
4. For each block in order:
   - `prev_hash` matches hash(previous_block_bytes)
   - `height` increments by 1
   - `payload_hash` matches payload
   - Signature verifies for `writer_pubkey`
   - `writer_pubkey` was authorized before this block
5. Apply operations sequentially to derive final state
   - Unknown (non-canonical) operations are skipped for state derivation but preserved in chain

If any rule fails → chain is invalid.

Fork handling is undefined in Part 1.

------

# 4. Derived State

Nodes MUST maintain derived state:

- Current writers set
- Current members map (Cid128 → Role, where Role uses extensible string-based enum)
- Current policy (Policy object with MessagePak-backed extensible fields)
- Current onramps
- Current tip hash
- Current height
- Type metadata: public key, name, description, extensions, searchInArchives

Members with non-canonical (unknown) roles are preserved in the members map.
Policy fields from future versions are preserved in the MessagePak but not interpreted.

Nodes MAY cache full chain bytes.

## 4.1 Membership Verification at Request Time

The sole allow/deny policy is encoded in `AccessEnforcementUtils.isAllowed(MysterType, Optional<Cid128>, AccessListReader)`:

1. No access list for the type → **allow** (type is effectively public).
2. `listFilesPublic == true` → **allow**.
3. Caller identity is unknown (empty `callerCid`) → **deny**. Identity cannot be verified without TLS.
4. `Cid128` present in the members map → **allow**. (ADMINs are also in the map — ADMIN implies MEMBER.)
5. Otherwise → **deny**.

**TCP path** — caller identity is derived at the STLS upgrade point in `ConnectionRunnable`:
1. Extract the peer's RSA public key from `TLSSocket.getPeerPublicKey()`.
2. Derive `Cid128 = Trunc128(SHA-256(publicKey.getEncoded()))` via `com.myster.identity.Util.generateCid()`.
3. Store in `ConnectionContext.callerCid()` (`Optional.empty()` for plaintext connections).
4. Each section handler passes it to `AccessEnforcementUtils.isAllowed()` via `AccessListReader`.

**UDP path** — `EncryptedDatagramServer` stamps `Transaction.callerCid()` from `DecryptResult.keyHash` after decryption. `TypeDatagramServer` reads it from the transaction and filters the type list with `AccessEnforcementUtils.isAllowed()`.

Section handlers receive an `AccessListReader` (narrow interface: `Optional<AccessList> loadAccessList(MysterType)`) injected at construction — they never hold a reference to the full `AccessListManager`.

On any `IOException` from the reader the decision **fails open** (allow) — a corrupt or temporarily unreadable access list must not take down the serving thread.

------

# 5. TCP Protocol: ACCESS_LIST_GET

## 5.1 Capability Name

```
ACCESS_LIST_GET
```

TCP-only.

------

## 5.2 Request

Fields:

- `myster_type` (16 bytes — MysterType shortBytes)
- `known_tip_hash` (32 bytes — hash of client's latest block, all zeros if client has no chain)

The server uses `known_tip_hash` to determine where the client's chain ends and sends only blocks after that point.

------

## 5.3 Response

Status codes:

- `OK` (0)
- `NOT_FOUND` (1) — server doesn't have this type
- `FORK_DETECTED` (2) — known_tip_hash not found in server's chain
- `ERROR` (3)

Payload (if OK):

First, the server sends the total size of the payload:

```
[8 bytes] total_bytes_remaining
```

Then blocks are streamed with size-prefixed framing:

```
[4 bytes] block_byte_size   (0 = end of stream)
[block_byte_size bytes] block data
...repeat...
[4 bytes] 0                 (sentinel — end of stream)
```

`total_bytes_remaining` includes all size prefixes, block data, and the sentinel. Client can reject immediately if too large.

Behavior:

- If `known_tip_hash == all zeros` → send full chain from genesis
- If `known_tip_hash` matches a block → send blocks after that point
- If `known_tip_hash` matches the tip → return OK + immediate sentinel (already up-to-date)
- If `known_tip_hash` not found → return `FORK_DETECTED`

Client may abort mid-stream if a block size exceeds a reasonable threshold.

------

# 6. Distribution Model (Part 1)

- Any node may serve a chain
- Clients SHOULD prefer onramps
- Clients MAY pin:
  - `genesis_hash` (for chain integrity verification)
  - `tip_hash`

Rollback protection is client policy only.

------

# 7. GUI Requirements

## 7.1 Create Type

Must:

- Generate RSA keypair for the type (this IS the type's identity)
- Generate Ed25519 admin keypair (for signing blocks)
- Create genesis block with `SET_TYPE_PUBLIC_KEY` and initial metadata
- Derive `MysterType = MD5(rsaPublicKey)`
- Allow entry of initial members, onramps, and policy
- Support both public and private presets (difference is just policy)

## 7.2 Edit Type (Admin Only)

Actions:

- Change metadata (name, description, extensions) — via SET_* operations
- Add/remove member
- Add/remove writer
- Modify policy
- Add/remove onramp

Each action creates and signs a new block.

## 7.3 Share Type

Export bootstrap bundle containing:

- `mysterType` (hex — MysterType shortBytes)
- `genesis_hash` (for integrity verification)
- `onramps`
- Optional expected writer pubkeys

------

# 8. Explicit Non-Goals (Part 1)

Not implemented:

- Fork resolution
- Consensus
- Multi-sig
- Chain compaction
- Privacy of membership list
- Snapshot blocks

------

# 9. Implementation Order

1. Implement block structure + canonical signing + extensible operations
2. Implement validation engine (including public key → MysterType verification)
3. Implement derived-state reconstruction (including type metadata)
4. Implement TCP ACCESS_LIST_GET
5. Implement GUI create/edit/share flow

This completes Part 1.


