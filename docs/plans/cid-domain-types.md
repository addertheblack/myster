# Domain-Specific CID Types

## 1. Summary

Replace the publicly shared `Cid128` value with two non-interchangeable domain types: `MysterTypeCid` for the 16-byte MD5-derived compact identifier of a `MysterType`, and `ServerCid` for the 16-byte truncated-SHA-256 identifier of a public-key-backed Myster server identity. Both types compose one package-private 128-bit value implementation, while production APIs expose only the domain type appropriate to their module.

## 2. Non-goals

- Do not change either CID derivation algorithm or byte ordering.
- Do not change any network, access-list, preferences, or 3DNS persistence format.
- Do not merge `MysterType` and `MysterTypeCid`; `MysterType` remains the type used by file, search, tracker, and protocol APIs.
- Do not change 3DNS routing, retention, lookup, or encryption behavior.
- Do not add compatibility overloads that retain `Cid128` in public production APIs.
- Do not refactor unrelated identity, access-control, or type-management behavior.

## 3. Assumptions & open questions

- Both domain CIDs are exactly 16 bytes. `MysterTypeCid` is MD5 of the encoded type public key; `ServerCid` is the first 16 bytes of SHA-256 of the encoded server public key.
- `MysterType` is now a public-key-derived compact type identifier and should compose `MysterTypeCid`. A few tests still construct four-byte synthetic `MysterType` values; those fixtures are treated as stale and will be changed to valid 16-byte values.
- Existing wire readers remain length-prefixed, but constructing a `MysterType` from any length other than 16 bytes will fail as invalid input. Current built-in and custom types are public-key-derived 16-byte values.
- The common raw value is an implementation detail, not an extension point. It will move to a small CID package and become package-private so only the two public wrappers can name it.
- `ServerCid` owns unsigned ordering and ring-distance operations because only server identities participate in the 3DNS ring. `MysterTypeCid` needs byte/hex conversion and value equality, but no ring API.
- No architecture-blocking questions remain. If implementation discovers a supported production source of non-16-byte `MysterType` values, stop and revise this plan rather than silently breaking it.

## 4. Proposed design

Create a dedicated `com.myster.cid` package containing public immutable `MysterTypeCid` and `ServerCid` classes plus a package-private immutable `Cid128` implementation. Constructors from bytes defensively copy and require exactly 16 bytes. Public-key factories preserve the current algorithms, and byte/hex accessors preserve existing serialized representations. Equality remains domain-specific: identical bytes in a `MysterTypeCid` and a `ServerCid` are never equal and cannot be passed to one another's APIs.

`MysterType` will hold a `MysterTypeCid` rather than its own byte array and delegate public-key derivation, parsing, serialization, display, equality, and hashing. Existing callers continue using `MysterType`; they gain domain validation without needing to manipulate the CID directly.

Every current production use of `Cid128` represents a server identity and will become `ServerCid`. This includes tracker indexes, 3DNS targets and candidates, private-type member identities, authenticated TCP/UDP caller identities, server-picker UI values, and application wiring. MSD internals will also stop passing raw `byte[]` CIDs through lookup and decryption results; those values will be parsed once into `ServerCid` at the incoming wire boundary.

The generic `com.myster.identity.Util.generateCid` and `generateNakedCid` APIs will be removed. Domain construction will be explicit through `ServerCid.fromPublicKey(...)`, `ServerCid.fromBytes(...)`, `MysterTypeCid.fromPublicKey(...)`, and `MysterTypeCid.fromHexString(...)` (exact names may follow established constructor/factory style, but must retain domain wording).

## 5. Architecture connections

The module-level classification is:

| Module / package | CID domain | Why |
|---|---|---|
| `com.myster.cid` | Both | Owns the two public domain values and their private common 128-bit representation. |
| `com.myster.type` and ordinary type consumers | `MysterTypeCid`, indirectly through `MysterType` | A Myster type is represented compactly by the MD5-derived CID of its public key. Search, file, tracker, preference, and type-list APIs should continue passing `MysterType`, not raw CID values. |
| `com.myster.identity` and `com.myster.tracker` | `ServerCid` | Public-key identities are indexed, resolved, and ordered by the compact server identity. |
| `com.myster.threedns` and tracker 3DNS UI | `ServerCid` | Local CID, target CIDs, candidate CIDs, retained server CIDs, and ring arithmetic all belong to the server-identity ring. |
| `com.myster.net.datagram`, `com.myster.transaction`, and `com.myster.net.server` | `ServerCid` | MSD client/server identity fields and verified caller identities identify servers/peers, never Myster types. |
| `com.myster.access` | Both, in distinct roles | The access list itself is keyed by a `MysterType` backed by `MysterTypeCid`; member/admin operation payloads and membership maps contain `ServerCid`. |
| `com.myster.type.ui` | Both, in distinct roles | The editor edits a `MysterType`, while local identity, member rows, server selection, and display-name lookup use `ServerCid`. |
| `com.myster.Myster` composition root | `ServerCid` plus ordinary `MysterType` values | Startup derives the local server CID and wires server identity lookup/UI callbacks; type values remain encapsulated by `MysterType`. |

| New / changed thing | Owned / created by | Called / used by | Connects to (existing) |
|---|---|---|---|
| `MysterTypeCid` | `com.myster.cid` | `MysterType` | Type public keys, type wire bytes, preference/file hex identifiers |
| `ServerCid` | `com.myster.cid` | Identity, tracker, 3DNS, access membership, authenticated transport, UI | `PublicKeyIdentity`, server pool, MSD, access-list members |
| Package-private raw 128-bit value | `com.myster.cid` | Only `MysterTypeCid` and `ServerCid` | Defensive byte storage, hex conversion, unsigned comparison/ring math |
| Domain-specific construction | The two CID wrappers | Public-key and wire boundaries | Replaces `com.myster.identity.Util.generateCid/generateNakedCid` |
| `MysterType` composition | `com.myster.type` | All existing type consumers | Preserves the existing high-level type API and serialized bytes |
| Typed MSD identity flow | `DatagramEncryptUtil` | `EncryptedDatagramServer`, application key lookup | Replaces raw `byte[] keyHash` with `ServerCid` after packet parsing |

There is no wire or disk migration. Type CIDs remain the same 16 MD5 bytes in type messages, access-list headers, filenames, and preferences. Server CIDs remain the same 16 truncated-SHA-256 bytes in MSD Section 2, access-list member operations, and 3DNS request fields. Only in-memory Java types and input validation change.

## 6. Key decisions & edge cases

- Domain equality is intentionally strict; the two wrappers do not compare equal even when their 16 bytes match.
- No public unwrap to raw `Cid128` is provided. Wrappers expose defensive `bytes()` and hex output only.
- `ServerCid` implements `Comparable<ServerCid>` and returns `ServerCid` from `plusPowerOfTwo`; predecessor/successor comparison parameters are also `ServerCid`.
- `MysterTypeCid` does not implement server ring operations.
- Incoming wrong-length server CIDs remain packet/decryption failures at their current boundaries. Incoming wrong-length type IDs become `IOException` at network/file parsing boundaries rather than leaking `IllegalArgumentException`.
- Access-list `ADD_MEMBER` and `REMOVE_MEMBER` payloads keep their exact 16-byte layout but deserialize directly to `ServerCid`.
- `DatagramEncryptUtil.Lookup` receives `ServerCid`; its decrypted result exposes an optional `ServerCid` with a semantic field/accessor name instead of `keyHash` raw bytes.
- Existing `MysterType` byte and hex methods remain for compatibility, but delegate to `MysterTypeCid` and always return defensive values.
- The implementation must finish with no production reference to raw `Cid128` outside `MysterTypeCid.java` and `ServerCid.java`; the package-private implementation file itself is the only additional definition-site occurrence.

## 7. Acceptance criteria

- [ ] Code cannot pass a type CID to an API expecting a server CID, or vice versa.
- [ ] `MysterType` is backed by `MysterTypeCid` and preserves the established MD5-derived bytes and hex representation.
- [ ] All identity, tracker, 3DNS, access-member, authenticated-transport, and server-selection APIs use `ServerCid`.
- [ ] The raw common CID implementation is package-private and referenced only by the two domain wrappers.
- [ ] Public-key derivation produces byte-for-byte identical CIDs to the pre-refactor implementation for both domains.
- [ ] Existing type, MSD, access-list, and 3DNS wire/disk bytes remain unchanged.
- [ ] Invalid CID lengths are rejected at construction and mapped to `IOException` when they originate in incoming network/file data.
- [ ] Focused and full tests pass with no remaining `Cid128`, `generateCid`, or `generateNakedCid` production references outside the new CID package.

---
## ✦ IMPLEMENTATION DETAILS (for the implementation agent)
---

## 8. Affected files / classes

- **New** `src/main/java/com/myster/cid/Cid128.java` — package-private immutable 16-byte storage, unsigned ordering, ring arithmetic, byte/hex conversion, equality, and hashing; moved/adapted from the current identity class.
- **New** `src/main/java/com/myster/cid/MysterTypeCid.java` — public type-domain wrapper, MD5 public-key derivation, strict 16-byte parsing, bytes/hex, equality, and hashing.
- **New** `src/main/java/com/myster/cid/ServerCid.java` — public server-domain wrapper, truncated-SHA-256 public-key derivation, strict parsing, bytes/hex, ordering, and typed ring operations.
- `src/main/java/com/myster/identity/Cid128.java` — remove after its implementation is moved behind the wrappers.
- `src/main/java/com/myster/identity/Util.java` — remove generic CID derivation APIs; retain unrelated public-key conversion utilities.
- `src/main/java/com/myster/type/MysterType.java` — replace `byte[] shortBytes` with `MysterTypeCid`; delegate construction, serialization, parsing, display, equality, and hashing.
- `src/main/java/com/myster/tracker/IdentityProvider.java` — change CID lookup contract to `ServerCid`.
- `src/main/java/com/myster/tracker/IdentityTracker.java` — change ordered CID map, exact lookup, neighbor traversal, and derivation to `ServerCid`.
- `src/main/java/com/myster/tracker/MysterServerPool.java` — expose `ServerCid` in lookup and closest-server contracts.
- `src/main/java/com/myster/tracker/MysterServerPoolImpl.java` — implement the typed pool contracts and closest-candidate traversal.
- `src/main/java/com/myster/tracker/Tracker.java` — accept local/target `ServerCid` values and expose typed 3DNS queries.
- `src/main/java/com/myster/threedns/ThreeDnsAddressCandidate.java` — make candidate CID a derived `ServerCid`.
- `src/main/java/com/myster/threedns/ThreeDnsFingerEntry.java` — make target and retained identity fields `ServerCid`.
- `src/main/java/com/myster/threedns/ThreeDnsServerList.java` — use `ServerCid` for local identity, targets, candidates, ordering, and ring math.
- `src/main/java/com/myster/threedns/ThreeDnsTargetSlotSnapshot.java` — expose `ServerCid` target values.
- `src/main/java/com/myster/net/client/MysterDatagram.java` — type the 3DNS target as `ServerCid`.
- `src/main/java/com/myster/net/datagram/client/FindClosestDatagramClient.java` — serialize a `ServerCid` target.
- `src/main/java/com/myster/net/datagram/client/MysterDatagramImpl.java` — implement the typed `findClosest` signature.
- `src/main/java/com/myster/net/server/datagram/FindClosestDatagramServer.java` — parse target bytes into `ServerCid` and use its length constant.
- `src/main/java/com/myster/net/datagram/DatagramEncryptUtil.java` — derive and serialize `ServerCid`; type decrypted peer identity and `Lookup.findPublicKey` instead of passing raw CID bytes.
- `src/main/java/com/myster/net/datagram/MSDConstants.java` — rename the MSD-specific CID length constant to make its `ServerCid` domain explicit without changing its value.
- `src/main/java/com/myster/net/server/datagram/EncryptedDatagramServer.java` — propagate the decrypted `ServerCid` directly as caller identity.
- `src/main/java/com/myster/transaction/Transaction.java` — replace optional caller `Cid128` with optional `ServerCid`.
- `src/main/java/com/myster/net/server/ConnectionContext.java` — replace optional authenticated caller `Cid128` with optional `ServerCid`.
- `src/main/java/com/myster/net/server/ConnectionRunnable.java` — derive the TLS peer's `ServerCid`.
- `src/main/java/com/myster/net/stream/server/MultiSourceSender.java` — retain typed optional `ServerCid` through deferred serving.
- `src/main/java/com/myster/net/stream/client/MysterDataInputStream.java` — map invalid `MysterTypeCid` byte lengths from incoming type fields to `IOException`.
- `src/main/java/com/myster/access/AddMemberOp.java` — serialize/deserialize member `ServerCid` without changing bytes.
- `src/main/java/com/myster/access/RemoveMemberOp.java` — serialize/deserialize member `ServerCid` without changing bytes.
- `src/main/java/com/myster/access/AccessListState.java` — key membership state and role queries by `ServerCid`.
- `src/main/java/com/myster/access/AccessEnforcementUtils.java` — accept verified optional `ServerCid`.
- `src/main/java/com/myster/type/ui/TypeEditorServerSource.java` — enumerate and resolve server display names by `ServerCid`.
- `src/main/java/com/myster/type/ui/ServerPickerDialog.java` — return and display selected `ServerCid`.
- `src/main/java/com/myster/type/ui/TypeEditorPanel.java` — use `ServerCid` for local server and member-table values while retaining `MysterType` for the edited type.
- `src/main/java/com/myster/type/ui/TypeManagerPreferences.java` — accept optional local `ServerCid`.
- `src/main/java/com/myster/tracker/ui/ThreeDnsTrackerRow.java` — type target and server columns as `ServerCid`.
- `src/main/java/com/myster/tracker/ui/TrackerThreeDnsPanel.java` — sort/display `ServerCid` values.
- `src/main/java/com/myster/Myster.java` — derive/wire local, lookup, and UI server CIDs with the typed API.
- `src/test/java/com/myster/identity/TestCid128RingMath.java` — move/rename to test `ServerCid` typed ring behavior and raw-value encapsulation.
- CID-using tests under `src/test/java/com/myster/access`, `.../net/datagram`, `.../threedns`, `.../tracker`, and `.../transaction` — update fixtures and signatures to `ServerCid`.
- MysterType-using tests under `src/test/java/com/myster/filemanager` and other affected packages — replace stale four-byte synthetic type fixtures with 16-byte values and verify `MysterTypeCid` delegation.
- **New** `src/test/java/com/myster/cid/TestMysterTypeCid.java` — verify MD5 derivation, strict length, defensive bytes, hex parsing, and domain equality.
- **New** `src/test/java/com/myster/cid/TestServerCid.java` — verify truncated-SHA-256 derivation, strict length, defensive bytes, ordering, ring math, and domain separation.
- `docs/codebase-structure.md` — document both domain CID types instead of public `Cid128`.
- `docs/design/Myster 3DNS.md` — replace generic CID terminology/types with `ServerCid` where discussing Java APIs while retaining protocol field names.
- `docs/design/Myster Private Types — Access Lists (Part 1 Implementation Spec).md` — distinguish `MysterTypeCid` list identity from `ServerCid` member identity.
- Active future plans `docs/plans/myster-3dns-part-2b.md`, `docs/plans/myster-3dns-part-3.md`, and `docs/plans/tracker-3dns-target-slots-ui.md` — update planned API type names so future implementation does not reintroduce `Cid128`.

## 9. Step-by-step implementation

1. Create `com.myster.cid.Cid128` from the current implementation and make it package-private. Keep fixed-width defensive storage, `bytes`, hex, unsigned comparison, modulo ring addition, and distance comparison behavior unchanged.
2. Add `MysterTypeCid` and `ServerCid` as final immutable wrappers. Both validate 16 bytes and delegate common value behavior. Implement MD5 derivation only on `MysterTypeCid`; implement truncated SHA-256 derivation and typed ring operations only on `ServerCid`. Do not expose the composed `Cid128`.
3. Refactor `MysterType` to compose `MysterTypeCid`. Preserve `MysterType(PublicKey)`, `MysterType(byte[])`, `toBytes()`, `toHexString()`, `fromHexString()`, `toString()`, equality, and hashing at the high-level API, but validate fixed length and map invalid incoming data to the caller's checked I/O contract where applicable.
4. Replace `Util.generateCid(publicKey)` with `ServerCid.fromPublicKey(publicKey)` and `Util.generateNakedCid(publicKey)` with `ServerCid.fromPublicKey(publicKey).bytes()`. Delete both ambiguous utilities after all callers move.
5. Convert tracker contracts and implementation from `Cid128` to `ServerCid`: `IdentityProvider`, `IdentityTracker`, `MysterServerPool`, `MysterServerPoolImpl`, and `Tracker`. Rename ambiguous fields such as `cid128ToIdentity` to `serverCidToIdentity` while preserving exact and neighbor behavior.
6. Convert every 3DNS model/API to `ServerCid`, including local CID, target CID, candidate CID, retained server CID, snapshots, UI rows, and datagram client/server target parameters. Keep `/targetCid` and all serialized bytes unchanged.
7. Convert authenticated transport identity end-to-end. In `DatagramEncryptUtil`, parse the Section 2 identifier into `ServerCid`, return `Optional<ServerCid>`, and make `Lookup.findPublicKey(ServerCid)` typed. Rename `keyHash` fields/parameters and the MSD CID-size constant to identify the `ServerCid` domain. Propagate the typed value through `EncryptedDatagramServer`, `Transaction`, `ConnectionContext`, TLS setup, and deferred multi-source serving.
8. Convert private-type membership from `Cid128` to `ServerCid` in block operations, `AccessListState`, enforcement, type-editor sources, server picker, member table, and application callbacks. Preserve the access list's separate `MysterType`/`MysterTypeCid` identity and the exact member payload bytes.
9. Update `Myster.java` composition-root wiring to use `ServerCid` factories for local identity, public-key lookup, server enumeration, and type-editor callbacks.
10. Remove `com.myster.identity.Cid128`. Run `rg` checks to ensure raw `Cid128` appears only in the three `com.myster.cid` implementation files and that no production references to `generateCid` or `generateNakedCid` remain.
11. Update affected tests, replacing only synthetic invalid-length `MysterType` fixtures. Add deterministic derivation vectors that compare new CID bytes with independently computed MD5/SHA-256 expected bytes, plus serialization round trips for MSD, access member operations, and 3DNS requests.
12. Update living design/codebase documentation and active future plans. Do not rewrite historical implementation summaries or completed milestone plans merely to rename types.
13. Run formatting/compile checks, the focused CID/type/tracker/3DNS/access/datagram suites, and then the full Maven test suite. Write `docs/impl_summary/cid-domain-types.md` with results and any compatibility findings.

## 10. Tests to write

- `TestMysterTypeCid`: known public key produces the MD5 of `getEncoded()`, byte and hex round trips are stable, input/output arrays are defensive, null/wrong lengths fail, and equality/hash code are value-based.
- `TestServerCid`: known public key produces the first 16 SHA-256 bytes, byte/hex round trips are stable, input/output arrays are defensive, null/wrong lengths fail, unsigned ordering and wraparound ring arithmetic match existing tests.
- Domain-separation compile/API checks: no conversion or equality path accepts the other wrapper; use reflection/source `rg` only as a supplementary encapsulation check, not as the sole behavioral test.
- `MysterType` tests: public-key, bytes, and hex construction delegate to the same `MysterTypeCid`; serialization remains byte-identical; invalid-length network/file values surface as `IOException` at parsing boundaries.
- MSD tests: Section 2 bytes remain unchanged, lookup receives the expected `ServerCid`, authenticated request decryption yields that `ServerCid`, and anonymous requests remain empty.
- Access-list tests: `ADD_MEMBER`/`REMOVE_MEMBER` payload bytes remain unchanged, round-trip to `ServerCid`, and membership/enforcement uses `ServerCid`.
- Tracker tests: exact lookup, ordered index, predecessor/successor traversal, removal, and pool closest queries retain behavior with `ServerCid`.
- 3DNS tests: request/response bytes remain compatible; target/candidate/retained values are `ServerCid`; target generation and sorting retain ring behavior.
- Type-editor tests and fixtures: server selection/member rows use `ServerCid`; edited list identity remains `MysterType`.
- Full regression: all Maven tests pass after updating valid domain fixtures.

## 11. Docs / Javadoc to update

- Add class-level contracts to `MysterTypeCid` and `ServerCid`, including derivation algorithm, fixed length, byte order, immutability, and intended domain.
- Keep raw `Cid128` documentation implementation-focused and package-private; do not link it as public API.
- Update `MysterType` Javadoc to state that it composes a `MysterTypeCid` and is always a 16-byte public-key-derived identifier.
- Replace `Cid128` with `ServerCid` in tracker, 3DNS, transaction, connection-context, access-member, enforcement, and type-editor Javadocs.
- Document the mixed CID roles in access-list package/design docs: the list/type key is `MysterTypeCid`, while member identities are `ServerCid`.
- Update MSD lookup/result documentation to say `ServerCid`, not generic key hash or raw CID bytes.
- Update `docs/codebase-structure.md` and the living 3DNS/private-type design documents; update only active future plans that would otherwise prescribe obsolete `Cid128` APIs.
