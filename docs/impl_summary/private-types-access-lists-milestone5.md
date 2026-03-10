# Implementation Summary — Private Types Access Lists: Milestone 5

**Feature slug**: `private-types-access-lists-milestone5`
**Plan file**: `docs/plans/private-types-access-lists-milestone5.md`
**Date**: 2026-03-08

---

## What was implemented

All four phases of M5: access enforcement is now live on every TCP file-serving section handler
and the UDP type-lister. A private type whose `listFilesPublic` is `false` will silently hide
itself (or return empty results) for any caller whose verified `Cid128` is not in the members
map.

---

## Files changed

### New files
| File | Purpose |
|---|---|
| `com/myster/access/AccessListReader.java` | Narrow `@FunctionalInterface`: `Optional<AccessList> loadAccessList(MysterType)` |
| `com/myster/access/AccessEnforcementUtils.java` | Five-rule allow/deny logic used by all handlers |
| `src/test/…/TestAccessEnforcementUtils.java` | Five unit tests, one per allow/deny case |

### Modified files
| File | Change |
|---|---|
| `com/myster/access/AccessListManager.java` | `implements AccessListReader` |
| `com/myster/transaction/Transaction.java` | `callerCid` field; `callerCid()` accessor; `withCallerCid()` copy method |
| `com/myster/net/server/datagram/EncryptedDatagramServer.java` | Stamps `callerCid` from `decryptResult.keyHash` after decryption |
| `com/myster/net/server/ConnectionContext.java` | Added `Optional<Cid128> callerCid` record component; `withSectionObject` propagates it |
| `com/myster/net/server/ConnectionRunnable.java` | Derives `callerCid` from `TLSSocket.getPeerPublicKey()` at STLS upgrade; plaintext keeps `empty` |
| `com/myster/net/stream/server/FileTypeLister.java` | Injected `AccessListReader`; filters type array (no short-circuit) |
| `com/myster/net/stream/server/RequestDirThread.java` | Injected `AccessListReader`; `writeInt(0)` on deny |
| `com/myster/net/stream/server/FileStatsStreamServer.java` | Injected `AccessListReader`; empty `MessagePak` on deny |
| `com/myster/net/stream/server/FileStatsBatchStreamServer.java` | Injected `AccessListReader`; zero-entry response on deny |
| `com/myster/net/stream/server/FileByHash.java` | Injected `AccessListReader`; "not found" sentinel on deny |
| `com/myster/net/stream/server/MultiSourceSender.java` | Injected `AccessListReader`; "file not found" response on deny |
| `com/myster/net/stream/server/RequestSearchThread.java` | Injected `AccessListReader`; `writeUTF("")` terminator on deny |
| `com/myster/net/stream/server/MysterServerLister.java` | Injected `AccessListReader`; empty-string sentinel on deny |
| `com/myster/net/server/datagram/TypeDatagramServer.java` | Injected `AccessListReader`; filters type list with `Util.filter` |
| `com/myster/Myster.java` | Passes `AccessListManager` (as `AccessListReader`) into all injected constructors |

### Design docs updated
| File | Change |
|---|---|
| `docs/design/Myster Private Types — Access Lists (Part 1 Implementation Spec).md` | Rewrote §4.1 to describe the actual five-rule policy, both TCP and UDP identity paths, `AccessListReader`, fail-open behaviour, and `ConnectionContext.callerCid` |

---

## Key design decisions

- **`AccessListReader` interface** keeps section handlers from seeing the full `AccessListManager`.
  All they need is one read — the interface makes the dependency explicit and testable with a lambda.
- **Fail-open** (`IOException` → allow): a temporarily unreadable access list must not take a
  section handler offline.
- **`callerCid` in `ConnectionContext`** is set once at STLS upgrade time and then carried through
  the entire section dispatch chain without any per-handler TLS coupling.
- **`Transaction.callerCid()`** follows the existing `withDifferentPayload` immutable-copy pattern.
- **`FileTypeLister` filters, not short-circuits**: the connection stays alive; only unwanted type
  entries are removed from the response.

---

## Deviations from the plan

None. All four phases were implemented exactly as specified.

---

## Javadoc / design docs updated

- `AccessListReader` — full class Javadoc.
- `AccessEnforcementUtils` — class + method Javadoc documents all five rules.
- `ConnectionContext` — record Javadoc updated for `callerCid` semantics.
- `Transaction.callerCid()` and `Transaction.withCallerCid()` — Javadoc added.
- `EncryptedDatagramServer.transactionReceived` — inline comment explains stamp point.
- `ConnectionRunnable.run()` — inline comment at STLS site.
- All eight TCP section handler classes — class Javadoc updated to document deny response.
- `TypeDatagramServer` — class Javadoc updated.
- Design doc §4.1 — rewritten (see above).

---

## Tests

`TestAccessEnforcementUtils` (5 cases, all passing):

1. `noAccessList_allowsEveryone` — no list → allow for both anonymous and identified callers.
2. `publicPolicy_allowsEveryone` — `listFilesPublic=true` → allow regardless of identity.
3. `privateType_noCaller_denies` — empty `callerCid` → deny.
4. `privateType_memberCaller_allows` — known member → allow.
5. `privateType_unknownCaller_denies` — identified but not a member → deny.

Total tests: **288** (was 283 before M5). All pass.

---

## Known issues / follow-up work

- Enforcement covers the eight standard TCP sections and the UDP type-lister. Other UDP
  transaction types (download, search via UDP) are not yet enforced — noted as a non-goal in M5.
- Automatic access list distribution to members is deferred to a future milestone.
- `FileStatsBatchStreamServer` deny path writes `writeInt(0)` (zero entries) rather than the
  full `protocol_check_byte + 0-entry` sequence — matches what the client-side parser tolerates.
  Verify against the client parser if this section is ever exercised for private types in
  integration testing.

