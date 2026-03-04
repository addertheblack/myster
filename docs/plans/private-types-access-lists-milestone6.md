# Private Types Access Lists — Milestone 6: Client-Only Node Join Requests

> **⚠ Status: Design Deferred — see "Design Status" section below.**

## Summary

A node that never acts as a server (no open TCP port, never pingable, never in the tracker
pool) cannot be found via `ServerPickerDialog` and therefore cannot be granted access to a
private type through the normal Member Management GUI introduced in Milestone 4.

The natural fix is a lightweight *join request* mechanism: the client-only node proactively
sends a signed, authenticated message to the admin. The admin sees a notification and can
approve or deny.

However, this is fundamentally a **messaging problem** — and Myster will eventually need a
general peer-to-peer messaging / chat layer anyway. Building a one-off bespoke join-request
transport now that gets replaced when chat arrives is wasteful. This milestone is therefore
**deferred** until the messaging design is settled.

---

## Design Status

**Blocked pending messaging/chat design.**

The join-request flow as described below can be implemented in two substantially different ways:

### Option A — Bespoke MSD transaction (current plan)
Define a new `JOIN_REQUEST` MSD datagram transaction type. Simple to build now; becomes
dead code when a general messaging layer arrives.

### Option B — Built on a future chat/messaging layer
Myster will eventually have a chat server. A join request is just a structured message:
```
"I am <Cid128>, I would like to join type <MysterType>. My display name is <name>."
```
The admin's chat client receives it, and the approval/denial flows through the same messaging
infrastructure used for everything else. No bespoke transport needed.

**Recommendation**: defer M6 until at least a minimal messaging design exists. The `JOIN_REQUEST`
MSD transaction approach (Option A) should only be used as a fallback if the messaging layer
is too far out on the roadmap.

**In the meantime**, Milestone 5 (enforcement) does **not** depend on M6 and can proceed
independently. The only M6 artifact that M5 also needs — `Transaction.callerCid()` plumbing —
is implemented as Phase 0 of M5, removing any dependency entirely.

### Open design questions to resolve before implementing M6
1. Will Myster have a general messaging/chat layer? If yes, on what rough timeline?
2. If yes: should join requests be a specific message type within that layer, or a higher-level
   protocol built on top of it?
3. If a bespoke transport is chosen anyway (Option A): is the `JOIN_REQUEST` MSD datagram the
   right wire format, or should it be TCP-based so that delivery is reliable?

---

## Goals

1. **`JoinRequestClient`**: sends a signed MSD datagram to an admin address carrying the
   requested `MysterType` and an optional display name.
2. **`JoinRequestServer`** (`TransactionProtocol`): receives the datagram, reads the
   authenticated `Cid128` from `Transaction.callerCid()` (already in place from M5), and
   fires `JoinRequestListener`.
3. **`JoinRequestListener`** interface: single callback used by the admin UI.
4. **`PendingJoinRequestsPanel`**: in-memory list of pending requests; Approve / Deny buttons.
   Embedded in `TypeManagerPreferences`.
5. **"Request Access…" button** in `TypeEditorPanel` read-only mode: sends to onramp addresses
   from the access list, or prompts for a manual address if onramps are empty.

---

## Non-Goals (Milestone 6)

- Any enforcement changes — all enforcement is complete after M5.
- Approval persistence across restarts (requester simply sends again).
- Rate limiting / spam protection — future hardening.
- Multi-step approval workflows.
- Automatic access list sync to existing members.

---

## Background

### Why client-only nodes can't use the picker

`ServerPickerDialog` (M4) sources its list from `MysterServerPool`, which only contains servers
that have been pinged and responded.  A client-only node never pings and never responds, so it
never enters the pool.

The join-request flow inverts this: the client-only node proactively reaches out.  Because MSD
packets carry an authenticated identity in Section 2 (the `/cid` hash), the admin can trust
who is asking without any prior knowledge of that node.

### `Transaction.callerCid()` is already available

`Transaction.callerCid()` is introduced as Phase 0 of Milestone 5.  By the time M6 is
implemented it will already be in place.  `JoinRequestServer` simply reads it.

### Where the admin's address comes from

The access list's `onramps` list (populated via `AddOnrampOp`) is the canonical set of
known-good server addresses for a type.  A client-only node that has imported the type's access
list can read `state.getOnramps()` and send join requests to all listed addresses.  If the
onramps list is empty, the UI falls back to a manual address entry field.

---

## Proposed Design (High-Level)

### `JOIN_REQUEST` transaction

**Transaction code**: a new constant in `DatagramConstants` — verify no conflict with existing
codes before assigning. Tentative: `12`.

**Payload** (MessagePak inside MSD Section 3 ciphertext):

```msgpack
{
  "/type": <bin 16>,    // MysterType shortBytes
  "/name": <str>        // requester's self-reported display name (may be empty)
}
```

The requester's `Cid128` comes from the authenticated MSD Section 2 `/cid` field via
`Transaction.callerCid()` — not from the payload.

### `JoinRequestServer` logic

1. Read `transaction.callerCid()`. If empty: drop with `BadPacketException`.
2. Deserialize payload: read `/type` and `/name`.
3. Check `AccessListKeyUtils.hasKeyPair(type)`. If false: silently drop.
4. Fire `JoinRequestListener.joinRequestReceived(type, callerCid, displayName)`.

### `PendingJoinRequestsPanel`

- Implements `JoinRequestListener`; accumulates requests in an in-memory list.
- `MCList`: Type Name | Requester Name | Cid128 (abbreviated) | Time.
- De-duplicates by `(type, cid)`.
- **Approve**: load admin keypair, append `AddMemberOp`, save, remove row, confirm.
- **Deny**: remove row.
- Embedded in `TypeManagerPreferences` as a "Requests" tab.

### "Request Access…" button

In `TypeEditorPanel` read-only path (`editAdminKeyPair.isEmpty() && editAccessList.isPresent()`):

1. Read onramps from `editAccessList.get().getState().getOnramps()`.
2. If empty: show inline address field.
3. Resolve admin public key via `PublicKeyLookup.fetchPublicKey(addr)` (async).
4. Call `JoinRequestClient.sendJoinRequest(...)`.
5. Show "Request sent to N server(s)." via `AnswerDialog`.

---

## Affected Modules / Packages

| Package | Component | Change |
|---|---|---|
| `com.myster.access` | `JoinRequestServer` (new) | `TransactionProtocol`; fires listener |
| `com.myster.access` | `JoinRequestClient` (new) | Sends MSD join request datagram |
| `com.myster.access` | `JoinRequestListener` (new interface) | Admin UI callback |
| `com.myster.type.ui` | `PendingJoinRequestsPanel` (new) | Implements `JoinRequestListener`; admin approval UI |
| `com.myster.type.ui` | `TypeEditorPanel` | Add "Request Access…" in read-only edit mode |
| `com.myster.type.ui` | `TypeManagerPreferences` | Add "Requests" tab with `PendingJoinRequestsPanel` |
| `com.myster.net.server.datagram` | `DatagramConstants` | Add `JOIN_REQUEST_TRANSACTION_CODE` |
| `com.myster.Myster` | main wiring | Register `JoinRequestServer`; wire listener to panel |

---

## Files / Classes to Create or Change

### Create

#### 1. `com/myster/access/JoinRequestListener.java`

```java
/** Callback interface for incoming join requests to private types this node administers. */
public interface JoinRequestListener {
    void joinRequestReceived(MysterType type, Cid128 requesterCid, String displayName);
}
```

#### 2. `com/myster/access/JoinRequestServer.java`

```java
/**
 * Receives authenticated JOIN_REQUEST UDP datagrams.
 * Requires {@link Transaction#callerCid()} to be non-empty (guaranteed by M5 plumbing).
 * Requests for types this node does not administer are silently dropped.
 */
public class JoinRequestServer implements TransactionProtocol {
    public static final int TRANSACTION_CODE = 12; // verify no conflict
    // ...
}
```

#### 3. `com/myster/access/JoinRequestClient.java`

```java
/**
 * Sends a signed MSD JOIN_REQUEST datagram to an admin server.
 * Encrypted to the admin's RSA public key; signed with the local node's identity key.
 */
public class JoinRequestClient {
    public static PromiseFuture<Void> sendJoinRequest(MysterAddress adminAddress,
                                                      PublicKey adminPublicKey,
                                                      MysterType type,
                                                      Identity localIdentity,
                                                      String displayName) { ... }
}
```

#### 4. `com/myster/type/ui/PendingJoinRequestsPanel.java`

```java
/**
 * Displays pending join requests for types this node administers.
 * In-memory only — lost on restart. De-duplication by (type, cid).
 */
public class PendingJoinRequestsPanel extends JPanel implements JoinRequestListener { ... }
```

### Modify

#### 5. `com/myster/net/server/datagram/DatagramConstants.java`

Add `public static final int JOIN_REQUEST_TRANSACTION_CODE = 12;`
(verify no conflict — OQ-3).

#### 6. `com/myster/type/ui/TypeEditorPanel.java`

Add "Request Access…" button in the read-only edit path. Wire to `JoinRequestClient`.

#### 7. `com/myster/type/ui/TypeManagerPreferences.java`

Add "Requests" tab containing `PendingJoinRequestsPanel`.

#### 8. `com/myster/Myster.java`

Register `JoinRequestServer`; wire `PendingJoinRequestsPanel` as the listener.

---

## Step-by-Step Implementation Plan

### Phase 1: `JoinRequestServer` and `JoinRequestClient`

1. Add `JOIN_REQUEST_TRANSACTION_CODE` to `DatagramConstants` (verify — OQ-3).
2. Implement `JoinRequestClient.sendJoinRequest(...)`:
   - MessagePak payload: `/type` (16 bytes) + `/name` (string).
   - Encrypt/sign via `DatagramEncryptUtil.encrypt(payload, adminPublicKey, localIdentity)`.
3. Implement `JoinRequestServer`: deserialize, check `callerCid`, check admin key, fire listener.
4. Register in `Myster.java`.
5. **Unit tests** `TestJoinRequestRoundTrip`:
   - Client → server → listener fires with correct type and `Cid128`.
   - Empty `callerCid` → `BadPacketException`.
   - Non-administered type → silent drop.
   - Malformed payload → `BadPacketException`.

### Phase 2: `PendingJoinRequestsPanel`

1. Implement panel; de-duplicate by `(type, cid)`.
2. Approve: load admin keypair, append `AddMemberOp`, save, remove row.
3. Deny: remove row.
4. Embed in `TypeManagerPreferences` ("Requests" tab).
5. **Manual test**: send join request → row appears → approve → access list updated.

### Phase 3: "Request Access…" in `TypeEditorPanel`

1. Add button in read-only edit path.
2. Resolve onramps; fall back to manual address field.
3. Resolve admin public key async; send; show result count via `AnswerDialog`.
4. **Manual test**: imported type → click button → admin sees request.

---

## Open Questions

> **OQ-2**: Admin public key resolution in `JoinRequestClient`. `PublicKeyLookup.fetchPublicKey`
> is async. Confirm this can be called from a virtual thread without blocking the EDT.

> **OQ-3**: `JOIN_REQUEST_TRANSACTION_CODE = 12`. Verify no conflict with existing
> `TransactionProtocol` implementations.

---

## Tests / Verification

### Unit Tests

| Test class | Coverage |
|---|---|
| `TestJoinRequestRoundTrip` | Client → server → listener fires with correct type and `Cid128` |
| `TestJoinRequestServerDropsUnauthenticated` | Empty `callerCid` → `BadPacketException` |
| `TestJoinRequestServerIgnoresNonAdminType` | Non-administered type → silent drop |

### Manual QA Checklist

- [ ] Client-only node imports a type with an onramp address.
- [ ] Clicks "Request Access…"; confirmation shows "Request sent to N server(s)."
- [ ] Admin "Requests" tab shows the row: type name, display name, Cid128.
- [ ] Duplicate request updates timestamp; no second row added.
- [ ] Admin approves → access list gains `AddMemberOp` → row removed.
- [ ] Admin denies → row removed, access list unchanged.
- [ ] Restart admin app → pending list is empty (expected).

---

## Docs / Comments to Update

1. `JoinRequestServer`, `JoinRequestClient`, `JoinRequestListener` — full Javadoc.
2. `PendingJoinRequestsPanel` — Javadoc on in-memory-only lifetime.
3. `docs/impl_summary/private-types-access-lists-milestone6.md` — create after implementation.

---

## Acceptance Criteria

- [ ] `JoinRequestClient` produces a valid MSD datagram parseable by `JoinRequestServer`.
- [ ] `JoinRequestServer` fires `JoinRequestListener` with correct type and `Cid128`.
- [ ] `JoinRequestServer` rejects unauthenticated packets; ignores non-administered types.
- [ ] `PendingJoinRequestsPanel`: Approve appends `AddMemberOp`; Deny discards.
- [ ] `TypeEditorPanel` shows "Request Access…" in read-only edit mode.
- [ ] All new unit tests pass.
- [ ] All M1–M5 tests still pass.

---

## Risks / Edge Cases / Rollout Notes

- **Admin key unavailable at approval time** — show error via `AnswerDialog`; remove the row.
- **Onramps list empty** — falls back to manual address field; bad address → `AnswerDialog` error.
- **Spam** — de-duplication by `(type, cid)` mitigates naive flooding from a single node.
- **Old MSD client without `/cid`** — `callerCid` empty → `BadPacketException`. Correct.

---

**Plan Version**: 1.0 (renumbered 2026-03-02; was Milestone 5)
**Created**: 2026-03-01
**Milestone**: 6 of 6
**Depends on**: Milestone 5 (`private-types-access-lists-milestone5.md`) complete
  (specifically: `Transaction.callerCid()` must be in place — it is, as M5 Phase 0)
**Status**: Design deferred — resolve Open Questions before implementation begins
