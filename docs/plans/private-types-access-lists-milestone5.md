# Private Types Access Lists — Milestone 5: Access Enforcement (TCP & UDP)

---
## Design (for the owner/reviewer)
---

### 1. Summary

Turn on the locks. After M4 the access list is fully manageable. This milestone makes it
meaningful by enforcing membership at every TCP file-serving handler and in the UDP type-lister.
A single `AccessEnforcementUtils.isAllowed()` method is the one place the allow/deny logic lives.

### 2. Non-Goals

- Any new GUI changes.
- Enforcement in UDP transaction types other than the type-lister.
- Multi-writer or multi-admin changes.
- Policy fields beyond `listFilesPublic`.
- Automatic access list distribution to members.

### 3. Assumptions & Open Questions

All open questions from the original draft are now resolved:

- **OQ-1 (Transaction mutability) RESOLVED** — `Transaction` is a regular `final class`, not
  a record. Adding `Optional<Cid128> callerCid` as a field with `Optional.empty()` default is
  not compile-breaking. A `withCallerCid(Optional<Cid128>)` method follows the existing
  `withDifferentPayload` pattern.
- **`AccessListState.isMember` RESOLVED** — already exists and is tested. No change needed.
- **TCP accept loop location RESOLVED** — `ConnectionRunnable.run()` at the
  `STLS_CONNECTION_SECTION` case is where the `TLSSocket` is available and `ConnectionContext`
  is rebuilt. That is the injection point.
- **UDP callerCid injection site RESOLVED** — `EncryptedDatagramServer.transactionReceived`,
  after `DatagramEncryptUtil.decryptRequestPacket`, has `decryptResult.keyHash`. Call
  `withCallerCid` on the decrypted transaction before `manager.resendTransaction`.

### 4. Proposed Design

**Phase 0** — add `callerCid` to `Transaction` via a new `withCallerCid()` method; populate it
in `EncryptedDatagramServer` from `decryptResult.keyHash`.

**Phase 1** — add `Optional<Cid128> callerCid` to the `ConnectionContext` record; derive it at
the STLS upgrade site in `ConnectionRunnable` from `TLSSocket.getPeerPublicKey()`. Plaintext
connections keep `Optional.empty()`.

**Phase 2** — create `AccessListReader` (a single-method interface in `com.myster.access`) and
`AccessEnforcementUtils` in the same package. `AccessListReader` exposes only
`Optional<AccessList> loadAccessList(MysterType)`. `AccessListManager` implements it.
`isAllowed(MysterType, Optional<Cid128>, AccessListReader)` contains the sole allow/deny logic.

**Phase 3** — inject `AccessListReader` into each of the eight TCP file-serving handlers and
call `isAllowed` at the top of `section()`. `FileTypeLister` filters the array; the others
short-circuit with a deny response.

**Phase 4** — inject `AccessListReader` into `TypeDatagramServer`; filter the type array
with `isAllowed(type, transaction.callerCid(), accessListReader)`.

### 5. Architecture Connections

Identity flows in from two directions into a single enforcement point:

- **TCP path**: `TLSSocket.getPeerPublicKey()` → `Util.generateCid()` → `ConnectionContext.callerCid()` → section handler → `AccessEnforcementUtils`
- **UDP path**: `DatagramEncryptUtil.decryptRequestPacket()` → `DecryptResult.keyHash` → `Transaction.callerCid()` → `TypeDatagramServer` → `AccessEnforcementUtils`

Both paths converge on `AccessEnforcementUtils.isAllowed()`, which reads from an `AccessListReader`
and delegates membership checks to `AccessListState.isMember()`. The handlers never see the full
`AccessListManager`; they only know how to ask "does a list exist for this type?"

| New / changed thing | Owned by | Called / used by | Connects to (existing) |
|---|---|---|---|
| `AccessListReader` (new interface) | `com.myster.access` | `AccessEnforcementUtils`; 8 TCP handlers; `TypeDatagramServer` | `AccessListManager` (implements it) |
| `AccessEnforcementUtils` (new) | `com.myster.access` | 8 TCP handlers; `TypeDatagramServer` | `AccessListReader`, `AccessListState.isMember()` |
| `ConnectionContext.callerCid()` | `com.myster.net.server` | All TCP section handlers | `TLSSocket.getPeerPublicKey()` + `Util.generateCid()` |
| `Transaction.callerCid()` | `com.myster.transaction` | `TypeDatagramServer` | `EncryptedDatagramServer` populates via `withCallerCid()` |
| 8 TCP section handlers (modified) | `com.myster.net.stream.server` | `Myster.java` (wiring) | `AccessEnforcementUtils`, `ConnectionContext` |
| `TypeDatagramServer` (modified) | `com.myster.net.server.datagram` | `Myster.java` (wiring) | `AccessEnforcementUtils`, `Transaction` |

### 6. Key Decisions & Edge Cases

- **Fail-open on corrupt/missing access list**: catch `IOException` from `loadAccessList`,
  log WARNING, treat as public. Don't crash the serving thread.
- **Plaintext connections denied on private types**: `callerCid = empty` → deny. Correct —
  identity cannot be verified without TLS.
- **ADMIN implies MEMBER**: `isMember` checks `members.containsKey(cid)` — ADMINs are in
  the map and therefore pass.
- **`FileTypeLister` must write the filtered count first**: write `filtered.length` then
  the filtered entries — not the original count.
- **`ConnectionContext` record change is compile-breaking**: use the compiler to find every
  construction site. Do not rely on text search alone.

### 7. Acceptance Criteria

- [ ] `Transaction.callerCid()` returns the correct `Cid128` for encrypted packets; empty otherwise.
- [ ] `ConnectionContext.callerCid()` populated from TLS peer cert; empty for plaintext.
- [ ] `AccessEnforcementUtils.isAllowed` passes all five unit-test cases.
- [ ] All eight TCP section handlers deny non-members with the correct wire response.
- [ ] `FileTypeLister` filters the type array without short-circuiting the connection.
- [ ] `TypeDatagramServer` filters private types for non-members.
- [ ] Public types pass all enforcement checks unconditionally.
- [ ] All new unit tests pass; all existing M1-M4 tests still pass.

---
## Implementation Details (for the implementation agent)
---

### 8. Affected Files / Classes

New:
- `com.myster.access.AccessListReader` (interface: `Optional<AccessList> loadAccessList(MysterType)`)
- `com.myster.access.AccessEnforcementUtils`

Modified:
- `com.myster.access.AccessListManager` — implement `AccessListReader`
- `com.myster.transaction.Transaction` — add `callerCid` field + `withCallerCid()`
- `com.myster.net.datagram.server.EncryptedDatagramServer` — attach `callerCid` after decrypt
- `com.myster.net.server.ConnectionContext` — add `Optional<Cid128> callerCid` record component
- `com.myster.net.server.ConnectionRunnable` — derive `callerCid` at STLS upgrade site
- `com.myster.net.stream.server.FileTypeLister` — inject `AccessListReader`; filter array
- `com.myster.net.stream.server.RequestDirThread` — inject `AccessListReader` + deny
- `com.myster.net.stream.server.FileStatsStreamServer` — inject `AccessListReader` + deny
- `com.myster.net.stream.server.FileStatsBatchStreamServer` — inject `AccessListReader` + deny
- `com.myster.net.stream.server.FileByHash` — inject `AccessListReader` + deny
- `com.myster.net.stream.server.MultiSourceSender` — inject `AccessListReader` + deny
- `com.myster.net.stream.server.RequestSearchThread` — inject `AccessListReader` + deny
- `com.myster.net.stream.server.MysterServerLister` — inject `AccessListReader` + deny
- `com.myster.net.server.datagram.TypeDatagramServer` — inject `AccessListReader` + filter
- `com.myster.Myster` — pass `AccessListManager` (as `AccessListReader`) into all newly-injected classes

No change needed:
- `com.myster.access.AccessListState` — `isMember(Cid128)` already exists and is tested.

### 9. Step-by-Step Implementation

#### Phase 0 — Transaction.callerCid()

1. In `Transaction`, add `private final Optional<Cid128> callerCid = Optional.empty()` to all
   existing constructors (default to empty — not compile-breaking).
2. Add `public Optional<Cid128> callerCid()` accessor.
3. Add `public Transaction withCallerCid(Optional<Cid128> cid)` returning a new `Transaction`
   with all fields copied and `callerCid` replaced.
4. In `EncryptedDatagramServer.transactionReceived`, after building `decryptedTransaction` via
   `transaction.withDifferentPayload(...)`, add:
   `decryptedTransaction = decryptedTransaction.withCallerCid(decryptResult.keyHash.map(Cid128::new));`
   before `manager.resendTransaction(encrypterSender, decryptedTransaction)`.

#### Phase 1 — ConnectionContext.callerCid()

1. Add `Optional<Cid128> callerCid` as the last record component.
2. Update `withSectionObject` to propagate it.
3. Compile — let the compiler find every broken construction site.
4. Add `Optional.empty()` at every site except the STLS upgrade site.
5. At the STLS upgrade site in `ConnectionRunnable.run()` (where `tlsSocket` is built), derive:

```
Optional<Cid128> callerCid;
try {
    callerCid = Optional.of(Util.generateCid(tlsSocket.getPeerPublicKey()));
} catch (IOException ignored) {
    callerCid = Optional.empty();
}
context = new ConnectionContext(tlsSocket, context.serverAddress(), context.sectionObject(), transferQueue, fileManager, callerCid);
```

#### Phase 2 — AccessListReader + AccessEnforcementUtils

1. Create `AccessListReader` as a `@FunctionalInterface` in `com.myster.access`:
   ```
   Optional<AccessList> loadAccessList(MysterType type) throws IOException;
   ```
2. Add `implements AccessListReader` to `AccessListManager`.
3. Implement `isAllowed(MysterType type, Optional<Cid128> callerCid, AccessListReader reader)` in `AccessEnforcementUtils`:
   - Call `reader.loadAccessList(type)` — catch `IOException`, log WARNING, return `true` (fail-open).
   - `list.isEmpty()` → return `true`.
   - `state.getPolicy().isListFilesPublic()` → return `true`.
   - `callerCid.isEmpty()` → return `false`.
   - Return `state.isMember(callerCid.get())`.

#### Phase 3 — TCP Section Enforcement

For each class, add `private final AccessListReader accessListReader` + constructor param.
At top of `section()`, read type, then check `AccessEnforcementUtils.isAllowed(type, context.callerCid(), accessListReader)`. On deny write the minimal response
(verify exact format by reading the client-side parser for each section first):

| Class | Deny response |
|---|---|
| `FileTypeLister` (74) | Write filtered count + filtered entries (not a short-circuit) |
| `RequestDirThread` (78) | `out.writeInt(0)` |
| `FileStatsStreamServer` (77) | Write empty `MessagePak` |
| `FileStatsBatchStreamServer` (177) | Protocol check byte + 0-entry responses |
| `FileByHash` (150) | Existing "not found" sentinel |
| `MultiSourceSender` | Existing "file not found" response |
| `RequestSearchThread` (35) | `out.writeInt(0)` |
| `MysterServerLister` (10) | Empty string sentinel |

In `Myster.java`, pass the `AccessListManager` instance wherever `AccessListReader` is required.

#### Phase 4 — TypeDatagramServer UDP Enforcement

1. Add `AccessListReader accessListReader` constructor param.
2. Filter: `Util.filter(Arrays.asList(allTypes), t -> AccessEnforcementUtils.isAllowed(t, transaction.callerCid(), accessListReader))` then convert to array.
3. Use filtered list in response.
4. In `Myster.java`, pass the `AccessListManager` instance as `AccessListReader`.
