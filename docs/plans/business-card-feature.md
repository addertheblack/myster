# Business Card Feature: Bidirectional Stats Protocol

Implementation record: [Bidirectional Server Stats Exchange](../impl_summary/bidirectional-server-stats.md).

## 1. Summary

Implement UDP transaction `102`, a bidirectional server-stats exchange in which the requester sends its own server stats and receives the responder's stats in the same round trip, allowing the responder to learn the requester's advertised Myster port instead of relying on a possibly NAT-scrambled UDP source port.

## 2. Non-goals

- Do not remove or modify legacy UDP stats transaction `101`.
- Do not add a TCP/stream business-card section.
- Do not persist separate business-card state or bypass normal pool validation.
- Do not add automatic client fallback from transaction `102` to `101`. Transaction `102` has been deployed long enough that supporting pre-102 servers is no longer a rollout requirement.

## 3. Assumptions & open questions

- The payload in each direction reuses `ServerStats.getServerStatsMessagePack(...)` and its existing MessagePak paths.
- The responder combines the UDP source IP with the requester's advertised `/Port`; it must not use the UDP source port when a valid advertised port is present.
- Learning the requester means suggesting the corrected address to the normal `MysterServerPool`, which performs ordinary refresh/onboarding; the request payload is not directly installed as trusted server state.
- Anonymous/minimal stats are allowed when identity or file listings are unavailable, but `/Port` is required for the NAT/non-default-port benefit.
- Transaction code `102` is reserved for this protocol; `101` remains legacy server stats.
- Repository baseline note: the constant, client, server, API method, registration, and direct pool integration are implemented. Any follow-up implementation should audit them against the contract and add missing validation/tests rather than duplicate classes.
- No architecture-blocking questions remain.

## 4. Proposed design

`BidirectionalServerStatsDatagramClient` serializes the local node's stats as its request payload and parses the response with the same MessagePak representation used by legacy server stats. It is exposed as `MysterDatagram.getBidirectionalServerStats(...)`, and `MysterServerPoolImpl.refreshMysterServer()` uses that operation as the current discovery path.

`BidirectionalServerStatsDatagramServer` parses the request, validates the advertised port, constructs the requester's server address from the observed source IP plus advertised Myster port, and calls `pool.suggestAddress(...)`. It then returns its own stats. Both legacy `101` and new `102` servers remain registered.

There is intentionally no `102 -> 101` negotiation. A peer that does not support `102` returns the normal transaction-unknown error and that refresh attempt fails through the existing error/dead-address path. Transaction `101` remains registered so older clients can still request stats from a current server.

## 5. Architecture connections

| New / changed thing | Owned / created by | Called / used by | Connects to (existing) |
|---|---|---|---|
| Transaction code `102` | `DatagramConstants` | Client/server codecs | Existing UDP transaction manager |
| Business-card client | `BidirectionalServerStatsDatagramClient` | `MysterDatagram` API and `MysterServerPoolImpl.refreshMysterServer()` | `StandardDatagramClientImpl`, `ServerStats` MessagePak |
| Business-card server | `BidirectionalServerStatsDatagramServer` | Datagram protocol manager | `TransactionProtocol`, `MysterServerPool.suggestAddress(...)` |
| Local stats dependencies | `MysterDatagramImpl` construction | Business-card client | name/port suppliers, `Identity`, `FileTypeListManager` |

Wire contract:

- Request transaction code: `102`.
- Request payload: one serialized server-stats MessagePak, including `/Port` and, when available, `/Identity`, `/ServerName`, uptime, speed, and file counts.
- Successful response error code: `NO_ERROR`.
- Response payload: the responder's serialized server-stats MessagePak with the same field contract.
- Unsupported servers return the transaction layer's `TRANSACTION_TYPE_UNKNOWN` error; current clients do not downgrade to `101`.

## 6. Key decisions & edge cases

- This is bidirectional, not a fire-and-forget reverse stats message: both peers exchange cards in one round trip.
- The server uses observed source IP plus advertised Myster port. Missing or invalid ports must follow a documented safe policy; never accept ports outside `1..65535`.
- Received stats are discovery hints. `suggestAddress(...)` keeps normal validation/onboarding in control.
- If full local stats cannot be generated because file lists are uninitialized, send a valid minimal card containing port and available identity/name rather than malformed or empty bytes.
- Serialization failures fail the transaction; they must not silently emit an empty packet.
- The protocol must work through the existing encrypted datagram wrapper as well as the normal unencrypted transaction path.
- Existing `101` behavior and registration remain untouched.
- No automatic fallback to `101` is planned. Keeping the old server handler supports old clients without carrying a permanent client downgrade path.

## 7. Acceptance criteria

- [ ] Transaction code `102` is uniquely defined and documented.
- [ ] The client sends local stats and returns remote stats in one UDP round trip.
- [ ] The server returns local stats and suggests the requester using source IP plus advertised valid Myster port.
- [ ] Full and minimal business cards carry enough identity/port information for their intended behavior.
- [ ] Invalid/malformed payloads and ports do not add unsafe addresses to the pool.
- [ ] Transactions `101` and `102` are both registered and independently callable.
- [ ] Encrypted datagram wrapping preserves business-card behavior.
- [ ] Normal pool refresh uses transaction `102` directly and does not retry `101` when `102` is unsupported.
- [ ] Focused client/server protocol tests pass.

---
## ✦ IMPLEMENTATION DETAILS (for the implementation agent)
---

## 8. Affected files / classes

- `src/main/java/com/myster/net/datagram/DatagramConstants.java` - transaction `102` constant/Javadoc.
- `src/main/java/com/myster/net/datagram/client/BidirectionalServerStatsDatagramClient.java` - request/response codec.
- `src/main/java/com/myster/net/server/datagram/BidirectionalServerStatsDatagramServer.java` - corrected-address discovery and response codec.
- `src/main/java/com/myster/net/client/MysterDatagram.java` - bidirectional stats operation.
- `src/main/java/com/myster/net/datagram/client/MysterDatagramImpl.java` - operation and local stats dependencies.
- `src/main/java/com/myster/tracker/MysterServerPoolImpl.java` - use transaction `102` for normal server refresh without downgrade.
- `src/main/java/com/myster/Myster.java` - register server `102` alongside `101`.
- New focused tests under `src/test/java/com/myster/net/datagram` and `.../server/datagram`.

## 9. Step-by-step implementation

1. Audit the existing transaction `102` implementation against this plan before changing it; preserve working code and user changes.
2. Ensure the client emits a valid full stats MessagePak and, on `NotInitializedException`, a minimal card with `/Port`, available `/Identity`, and `/ServerName`. Convert serialization failures into failed futures/transactions instead of empty payloads.
3. Ensure the server parses one MessagePak, validates `/Port`, builds `MysterAddress(sourceInetAddress, advertisedPort)`, and only then calls `pool.suggestAddress(...)`. Define missing/invalid-port behavior explicitly and test it.
4. Generate the response through the same shared server-stats builder, with a valid minimal response if file lists are unavailable.
5. Keep `getBidirectionalServerStats(...)` as the operation used by `MysterServerPoolImpl.refreshMysterServer()` with no internal or caller-level fallback.
6. Register both stats servers in `Myster.addServerConnectionSettings(...)` and verify encrypted forwarding preserves inner error/data behavior.
7. Add any missing focused unit/integration tests and update `docs/impl_summary/bidirectional-server-stats.md` if implementation behavior changes.

## 10. Tests to write

- Client full/minimal request serialization, response parsing, transaction code, and serialization failure.
- Server valid advertised port, missing port, invalid low/high port, malformed MessagePak, corrected source-IP address, pool suggestion, full/minimal response.
- Round-trip test proving both nodes receive the other's stats and the responder discovers the requester's advertised port.
- Encrypted-wrapper round trip for transaction `102`.
- Regression test that transaction `101` remains registered and unchanged.
- Regression test that an unsupported `102` does not cause an automatic `101` retry.

## 11. Docs / Javadoc to update

- Javadoc `DatagramConstants`, both codec classes, and `MysterDatagram.getBidirectionalServerStats(...)`.
- Document the request/response MessagePak and its discovery-hint trust boundary in the relevant protocol design documentation.
- Keep `docs/impl_summary/bidirectional-server-stats.md` aligned with the no-fallback decision.
