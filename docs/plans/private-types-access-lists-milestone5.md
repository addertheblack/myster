# Private Types Access Lists — Milestone 5: Access Enforcement (TCP & UDP)

## Summary

Turn on the locks. After Milestone 4 the access list is fully manageable. This milestone makes
it meaningful by enforcing membership in all TCP and UDP file-serving handlers.

1. **TCP enforcement** — all file-serving connection section handlers check the caller's
   `Cid128` (derived from the TLS peer certificate, already available after the handshake)
   against the access list before serving any data.
2. **UDP enforcement** — `TypeDatagramServer` checks the caller's `Cid128` (from the MSD
   Section 2 `/cid` field, plumbed through `Transaction.callerCid()`) before including a
   private type in the type-listing response.

A single shared `AccessEnforcementUtils` class (static methods only) is the one place where
the allow/deny logic lives, used by both TCP and UDP paths.

> **Note on M6 dependency**: Phase 4 (UDP enforcement) requires `Transaction.callerCid()`
> which is also needed by Milestone 6 (join requests). Implement it here as **Phase 0** so
> that M5 does not depend on the deferred M6. Phases 1–3 (TCP enforcement) have no dependency
> on M6 at all.

---

## Goals

1. **`ConnectionContext.callerCid()`** — add `Optional<Cid128>` to the `ConnectionContext`
   record, derived once from the TLS peer certificate when the connection is accepted. All
   TCP section handlers read it from context rather than re-deriving it.
2. **`AccessEnforcementUtils`** — a new `com.myster.access.AccessEnforcementUtils` (static
   methods only per the "Utils" convention) with a single `isAllowed(type, callerCid, alm)`
   method shared by all enforcement points.
3. **TCP enforcement** in all file-serving handlers:
   - `FileTypeLister` (74) — filter the returned type list.
   - `RequestDirThread` (78) — deny if caller is not a member.
   - `FileStatsStreamServer` (77) — deny if caller is not a member.
   - `FileStatsBatchStreamServer` (177) — deny if caller is not a member.
   - `FileByHash` (150) — deny if caller is not a member.
   - `MultiSourceSender` — deny if caller is not a member.
   - `RequestSearchThread` (35) — return empty results if caller is not a member.
   - `MysterServerLister` (10) — deny if caller is not a member of the requested type.
4. **UDP enforcement** in `TypeDatagramServer` — filter out private types the caller cannot
   access, using `Transaction.callerCid()`.

---

## Non-Goals (Milestone 5)

- Any new GUI changes.
- Enforcement in other UDP transaction types (only the type-lister is in scope).
- Multi-writer or multi-admin changes.
- Policy fields beyond `listFilesPublic`.
- Automatic access list distribution to members.

---

## Background

### How TCP caller identity is established

Every Myster TCP connection uses `TLSSocket`.  After the TLS handshake, the server side can
call `TLSSocket.getPeerPublicKey()` to get the caller's RSA public key.
`com.myster.identity.Util.generateCid(PublicKey)` produces the corresponding `Cid128`.

This derivation happens once at connection-accept time and is stored in
`ConnectionContext.callerCid()`.  Legacy plaintext connections (no TLS) produce
`Optional.empty()`, which causes enforcement to deny access to private types — correct
behaviour since identity cannot be verified over plaintext.

### How UDP caller identity is established

`DatagramEncryptUtil.decrypt()` returns a `DecryptResult` containing `Optional<byte[]> keyHash`
— the MSD Section 2 `/cid` field, already 16 bytes matching `Cid128`.  Adding
`Transaction.callerCid()` (Phase 0 of this milestone) plumbs this into the transaction layer.
`TypeDatagramServer` reads it from there.

### `AccessEnforcementUtils.isAllowed` logic

```
isAllowed(type, callerCid, alm):
  accessList = alm.loadAccessList(type)
  if accessList is absent  →  true   (no list = public)
  if accessList.state.policy.listFilesPublic  →  true
  if callerCid is empty  →  false   (no identity on a private type)
  return accessList.state.isMember(callerCid.get())
```

`isMember` returns `true` for any entry in the members map regardless of role (ADMIN implies
MEMBER).

### Why `ConnectionContext` must change

`ConnectionContext` is a Java record:

```java
public record ConnectionContext(MysterSocket socket,
                                MysterAddress serverAddress,
                                Object sectionObject,
                                TransferQueue transferQueue,
                                FileTypeListManager fileManager)
```

Adding `Optional<Cid128> callerCid` to the record is a compile-breaking change — every
construction site must be updated simultaneously.  The implementation agent must find all
`new ConnectionContext(...)` call sites (compiler will flag them) before making the edit.

### Deny responses per section

Each section has its own wire protocol.  On deny, use the minimal "nothing found" response the
existing client already handles:

| Section | Deny response |
|---|---|
| `FileTypeLister` (74) | Write `0` (count) then nothing — filtered array of length 0 |
| `RequestDirThread` (78) | Write `0` (count) |
| `FileStatsStreamServer` (77) | Write empty `MessagePak` |
| `FileStatsBatchStreamServer` (177) | Write protocol check byte + empty responses for each file |
| `FileByHash` (150) | Write the "not found" sentinel the protocol already defines |
| `MultiSourceSender` | Write the "file not found" response the protocol already defines |
| `RequestSearchThread` (35) | Write `0` (count) |
| `MysterServerLister` (10) | Write empty string sentinel immediately (no addresses) |

> **Implementation note**: verify the exact wire format for each by reading the corresponding
> client-side parser before implementing the deny path.

---

## Proposed Design (High-Level)

### 1. `ConnectionContext` — add `callerCid`

```java
public record ConnectionContext(MysterSocket socket,
                                MysterAddress serverAddress,
                                Object sectionObject,
                                TransferQueue transferQueue,
                                FileTypeListManager fileManager,
                                Optional<Cid128> callerCid) {

    public ConnectionContext withSectionObject(Object newSectionObject) {
        return new ConnectionContext(socket, serverAddress, newSectionObject,
                                     transferQueue, fileManager, callerCid);
    }
}
```

The accept loop derives `callerCid` after TLS completes:

```java
Optional<Cid128> callerCid = Optional.empty();
if (socket instanceof TLSSocket tls) {
    try { callerCid = Optional.of(Util.generateCid(tls.getPeerPublicKey())); }
    catch (IOException ignored) {}
}
```

### 2. `AccessEnforcementUtils` — single enforcement method

```java
public final class AccessEnforcementUtils {
    private AccessEnforcementUtils() {}

    public static boolean isAllowed(MysterType type,
                                    Optional<Cid128> callerCid,
                                    AccessListManager alm) { ... }
}
```

### 3. TCP enforcement pattern (per section)

```java
// At the top of section(), after reading the type:
if (!AccessEnforcementUtils.isAllowed(type, context.callerCid(), accessListManager)) {
    writeDenyResponse(context.socket().out);
    return;
}
// ... normal serving logic ...
```

`FileTypeLister` is the exception: it filters the array rather than short-circuiting, since
a single TCP connection can ask about multiple types.

### 4. UDP enforcement in `TypeDatagramServer`

```java
// After getting the full type list:
MysterType[] visible = Arrays.stream(allTypes)
    .filter(t -> AccessEnforcementUtils.isAllowed(t, transaction.callerCid(), alm))
    .toArray(MysterType[]::new);
// use visible instead of allTypes in the response
```

---

## Affected Modules / Packages

| Package | Component | Change |
|---|---|---|
| `com.myster.net.server` | `ConnectionContext` | Add `Optional<Cid128> callerCid` field |
| `com.myster.net.server` | TCP accept loop | Derive `callerCid` from TLS peer key |
| `com.myster.transaction` | `Transaction` (or equivalent) | Add `Optional<Cid128> callerCid()` (Phase 0) |
| `com.myster.transaction` | Transaction accept loop | Populate from `DecryptResult.keyHash` (Phase 0) |
| `com.myster.access` | `AccessEnforcementUtils` (new) | `isAllowed(type, callerCid, alm)` |
| `com.myster.access` | `AccessListState` | Add `isMember(Cid128)` if not already present |
| `com.myster.net.stream.server` | `FileTypeLister` (74) | Inject `AccessListManager`; filter array |
| `com.myster.net.stream.server` | `RequestDirThread` (78) | Inject `AccessListManager`; deny |
| `com.myster.net.stream.server` | `FileStatsStreamServer` (77) | Inject `AccessListManager`; deny |
| `com.myster.net.stream.server` | `FileStatsBatchStreamServer` (177) | Inject `AccessListManager`; deny |
| `com.myster.net.stream.server` | `FileByHash` (150) | Inject `AccessListManager`; deny |
| `com.myster.net.stream.server` | `MultiSourceSender` | Inject `AccessListManager`; deny |
| `com.myster.net.stream.server` | `RequestSearchThread` (35) | Inject `AccessListManager`; deny |
| `com.myster.net.stream.server` | `MysterServerLister` (10) | Inject `AccessListManager`; deny |
| `com.myster.net.server.datagram` | `TypeDatagramServer` | Inject `AccessListManager`; filter |
| `com.myster.Myster` | main wiring | Pass `AccessListManager` to all newly-injected classes |

---

## Files / Classes to Create or Change

### Create

#### 1. `com/myster/access/AccessEnforcementUtils.java`

```java
/**
 * Static utility methods for enforcing access list permissions at TCP and UDP request points.
 *
 * <p>All enforcement points call {@link #isAllowed} to decide whether to serve or deny.
 * Public types (absent access list, or {@code listFilesPublic == true}) always pass.
 * Private types require a non-empty {@code callerCid} that appears in the members map.
 */
public final class AccessEnforcementUtils {
    private AccessEnforcementUtils() {}

    /**
     * Returns true if the caller is permitted to access files of the given type.
     *
     * @param type      the type being accessed
     * @param callerCid the caller's identity hash; empty for unauthenticated connections
     * @param alm       the access list manager
     */
    public static boolean isAllowed(MysterType type,
                                    Optional<Cid128> callerCid,
                                    AccessListManager alm) { ... }
}
```

### Modify

#### 2. `com/myster/net/server/ConnectionContext.java`

Add `Optional<Cid128> callerCid` as the last component of the record.  Update
`withSectionObject` to propagate it.  Update all construction sites (compiler-guided).

#### 3. TCP accept loop (location TBD — search for `new ConnectionContext(`)

Derive `callerCid` from `TLSSocket.getPeerPublicKey()` after TLS handshake completes.

#### 4. `com/myster/transaction/Transaction.java` (or equivalent) — Phase 0

Add `Optional<Cid128> callerCid()`.  The transaction accept loop populates this from
`DecryptResult.keyHash` (wrap as `new Cid128(keyHash.get())`).

> **OQ-1**: If `Transaction` is a record, adding a field is a compile-breaking change. Inspect
> the class before editing. If mutability is limited, prefer a wrapper or companion object.
> Note: this same field will also be consumed by Milestone 6 (`JoinRequestServer`), so the
> change only needs to be made once.

#### 5. `com/myster/access/AccessListState.java`

Add `public boolean isMember(Cid128 cid)` if not already present:

```java
public boolean isMember(Cid128 cid) {
    return members.containsKey(cid);
}
```

#### 6–13. TCP section classes

Each receives `AccessListManager` via constructor injection.  Enforce at the top of `section()`.

| # | Class | Section | Deny response |
|---|---|---|---|
| 6 | `FileTypeLister` | 74 | Filter array (write 0-length if all denied) |
| 7 | `RequestDirThread` | 78 | Write `0` (count) |
| 8 | `FileStatsStreamServer` | 77 | Write empty `MessagePak` |
| 9 | `FileStatsBatchStreamServer` | 177 | Write check byte + empty responses |
| 10 | `FileByHash` | 150 | Write "not found" sentinel |
| 11 | `MultiSourceSender` | (verify #) | Write "file not found" |
| 12 | `RequestSearchThread` | 35 | Write `0` (count) |
| 13 | `MysterServerLister` | 10 | Write empty string sentinel |

#### 14. `com/myster/net/server/datagram/TypeDatagramServer.java`

Inject `AccessListManager` via constructor.  Filter the type array using
`AccessEnforcementUtils.isAllowed(type, transaction.callerCid(), alm)`.

#### 15. `com/myster/Myster.java`

Pass `AccessListManager` to each newly-injected class at construction time.

---

## Step-by-Step Implementation Plan

### Phase 0: `Transaction.callerCid()` (pulled forward from M6)

1. Locate where `DatagramEncryptUtil.decrypt()` is called in the transaction receive path.
2. After decryption, build `Optional<Cid128> callerCid` from `decryptResult.keyHash`.
3. Add `callerCid()` accessor to `Transaction` (see OQ-1).
4. **Unit test** `TestTransactionCallerCid`: known `keyHash` → correct `Cid128` returned;
   empty `keyHash` → `Optional.empty()`.

### Phase 1: `ConnectionContext` — add `callerCid`

1. Add `Optional<Cid128> callerCid` to the record.
2. Update `withSectionObject` to propagate it.
3. Find all `new ConnectionContext(...)` call sites; add `Optional.empty()` as the last
   argument everywhere except the TCP accept loop.
4. In the TCP accept loop: derive `callerCid` from the TLS socket after handshake; pass it.
5. **Unit test** `TestConnectionContext`: stub TLS socket → correct `Cid128`; stub plaintext
   socket → `Optional.empty()`.

### Phase 2: `AccessEnforcementUtils`

1. Create `AccessEnforcementUtils.java`.
2. Implement `isAllowed` as described.
3. Add `isMember(Cid128)` to `AccessListState` if absent.
4. **Unit tests** `TestAccessEnforcementUtils` (five cases):
   - No access list → `true`.
   - Public policy → `true` regardless of `callerCid`.
   - Private policy, caller is a member → `true`.
   - Private policy, caller not a member → `false`.
   - Private policy, `callerCid` is empty → `false`.

### Phase 3: TCP Section Enforcement

For each section in order (`FileTypeLister` → `RequestDirThread` → `FileStatsStreamServer` →
`FileStatsBatchStreamServer` → `FileByHash` → `MultiSourceSender` → `RequestSearchThread` →
`MysterServerLister`):

1. Add `AccessListManager` constructor parameter; store as field.
2. At the top of `section()`, after reading the type, call `isAllowed`.
3. On deny: write the appropriate minimal response and return.
4. Update `Myster.java`.

**Unit tests** per section: empty cid + private type → deny; member cid → allow; public type
→ always allow.

### Phase 4: UDP `TypeDatagramServer` Enforcement

1. Add `AccessListManager` constructor parameter to `TypeDatagramServer`.
2. Filter `allTypes` with `isAllowed(type, transaction.callerCid(), alm)`.
3. Update `Myster.java`.
4. **Unit tests** `TestTypeDatagramServerEnforcement`: no cid → private filtered; member cid
   → included; non-member cid → filtered; public type → always included.

---

## Tests / Verification

### Unit Tests

| Test class | Coverage |
|---|---|
| `TestTransactionCallerCid` | `Cid128` correctly populated; empty when `keyHash` absent |
| `TestConnectionContext` | `callerCid` from TLS; empty from plaintext |
| `TestAccessEnforcementUtils` | All five `isAllowed` cases |
| `TestFileTypeListerEnforcement` | Private type filtered; public type always visible |
| `TestRequestDirThreadEnforcement` | Member allowed; non-member gets 0-count response |
| `TestFileStatsStreamServerEnforcement` | Member allowed; non-member gets empty MessagePak |
| `TestFileStatsBatchStreamServerEnforcement` | Same pattern |
| `TestFileByHashEnforcement` | Same pattern |
| `TestMultiSourceSenderEnforcement` | Same pattern |
| `TestRequestSearchThreadEnforcement` | Same pattern |
| `TestMysterServerListerEnforcement` | Same pattern |
| `TestTypeDatagramServerEnforcement` | Private type filtered for non-members; public type passes |

### Manual QA Checklist

- [ ] Instance A has a private type. Instance B is not a member.
- [ ] B connects to A — A's private type does not appear in B's type list (TCP).
- [ ] B sends a UDP type-listing request — A's private type is absent from the response.
- [ ] A adds B as a member (via M4 Members tab). B reconnects.
- [ ] B now sees A's private type in both TCP type listing and UDP type listing.
- [ ] B can list files, download, and search under A's private type.
- [ ] A third instance C (not a member) still cannot see or access the private type.
- [ ] A removes B. B can no longer access the private type after reconnecting.
- [ ] Public types are unaffected — visible and accessible by all nodes.

---

## Docs / Comments to Update

1. `AccessEnforcementUtils` — full Javadoc including the five `isAllowed` cases.
2. `ConnectionContext` — update Javadoc to explain `callerCid` and when it is populated.
3. `Transaction` — document new `callerCid()` field.
4. Each modified TCP section class — note enforcement added in M5.
5. `TypeDatagramServer` — note UDP enforcement added in M5.
6. `docs/design/Encrypted UDP Packet.md` — note MSD Section 2 `/cid` used for enforcement.
7. `docs/impl_summary/private-types-access-lists-milestone5.md` — create after implementation.

---

## Acceptance Criteria

- [ ] `Transaction.callerCid()` populated for authenticated packets; empty otherwise.
- [ ] `ConnectionContext.callerCid()` populated from TLS peer cert; empty for plaintext.
- [ ] `AccessEnforcementUtils.isAllowed` passes all five unit-test cases.
- [ ] All eight TCP section handlers deny non-members with the correct wire response.
- [ ] `FileTypeLister` filters the type array (does not short-circuit the whole connection).
- [ ] `TypeDatagramServer` filters private types for non-members.
- [ ] Public types pass all enforcement checks unconditionally.
- [ ] All new unit tests pass.
- [ ] All existing M1–M4 tests still pass.

---

## Risks / Edge Cases / Rollout Notes

1. **`ConnectionContext` record change is compile-breaking.** Use the compiler to find all
   call sites; do not rely on text search.
2. **`Transaction` mutability** — see OQ-1. Potentially also compile-breaking.
3. **Non-TLS connections** produce `callerCid = empty` → denied on private types. Correct.
4. **`AccessListManager` cache** — verify it doesn't re-read disk on every request.
5. **Corrupt access list** — catch load errors, log, treat as public (fail open).
6. **`isMember` for ADMIN role** — must return `true`; ADMIN implies MEMBER.
7. **`FileTypeLister` filtered array** — write the filtered count, not the original count.
8. **UDP no `/cid`** (older client) — `callerCid` empty → private types filtered. Correct.

---

