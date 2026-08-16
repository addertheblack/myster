# Bidirectional Server Stats Exchange

Implementation record: [Bidirectional Server Stats Exchange](../impl_summary/bidirectional-server-stats.md).

## 1. Summary

Implement UDP transaction `102`, a bidirectional server-stats exchange in which the requester sends its own server stats and receives the responder's stats in the same round trip, allowing the responder to learn the requester's advertised Myster port instead of relying on a possibly NAT-scrambled UDP source port.

## 2. Non-goals

- Do not remove or modify legacy UDP stats transaction `101`.
- Do not add a TCP/stream bidirectional-stats section.
- Do not persist separate bidirectional-stats state or bypass normal pool validation.
- Do not add automatic client fallback from transaction `102` to `101`. Transaction `102` has been deployed long enough that supporting pre-102 servers is no longer a rollout requirement.

## 3. Assumptions & open questions

- The payload in each direction reuses `ServerStats.getServerStatsMessagePack(...)` and its existing MessagePak paths.
- The responder combines the UDP source IP with the requester's advertised `/Port`; it must not use the UDP source port when a valid advertised port is present.
- Learning the requester means passing the corrected address and advertised public-key identity to the normal `MysterServerPool` as an untrusted pair. The pool performs an expected-key stats refresh and installs the server only after the response identity matches; the request payload is not directly installed as trusted server state.
- Before all locally shared file lists are initialized, the client selects legacy transaction `101` instead of attempting to construct a partial transaction `102` card. A transaction `102` serialization that still encounters an uninitialized list is an invariant violation.
- Datagram request serializers declare checked `IOException`. The datagram wrapper carries expected serialization failures through `PromiseFuture`; it does not use `UncheckedIOException` or translate unexpected runtime failures.
- Anonymous request cards remain valid for protocol compatibility. A responder whose file lists are unavailable may return a minimal response containing `/Port` and available identity/name fields.
- Transaction code `102` is reserved for this protocol; `101` remains legacy server stats.
- Repository baseline note: the constant, client, server, API method, registration, and direct pool integration are implemented. Any follow-up implementation should audit them against the contract and add missing validation/tests rather than duplicate classes.
- No architecture-blocking questions remain.

## 4. Proposed design

`BidirectionalServerStatsDatagramClient` serializes the local node's complete stats as its request payload and parses the response with the same MessagePak representation used by legacy server stats. It is exposed as `MysterDatagram.getBidirectionalServerStats(...)`, and `MysterServerPoolImpl.refreshMysterServer()` uses that operation as the current discovery path. `MysterDatagramImpl` selects transaction `101` before all local shared file lists initialize and transaction `102` afterward.

`BidirectionalServerStatsDatagramServer` parses the request, validates the advertised port and identity, constructs the requester's server address from the observed source IP plus advertised Myster port, and calls the address/identity overload of `pool.suggestAddress(...)`. The pool verifies that pair with an expected-key transaction `102` refresh before onboarding it. If the request arrived over an authenticated encrypted channel, its verified caller CID must also match the advertised identity. The handler then returns its own stats. Both legacy `101` and new `102` servers remain registered.

There is intentionally no remote-failure `102 -> 101` negotiation. A peer that does not support `102` returns the normal transaction-unknown error and that refresh attempt fails through the existing error/dead-address path. Choosing `101` before local initialization happens before a request is sent and is not a compatibility retry. Transaction `101` remains registered so older clients can still request stats from a current server.

## 5. Architecture connections

| New / changed thing | Owned / created by | Called / used by | Connects to (existing) |
|---|---|---|---|
| Transaction code `102` | `DatagramConstants` | Client/server codecs | Existing UDP transaction manager |
| Stats-selection wrapper | `MysterDatagramImpl` | `MysterServerPoolImpl.refreshMysterServer()` | local file-list initialization, legacy `101`, bidirectional stats `102` |
| Bidirectional-stats client | `BidirectionalServerStatsDatagramClient` | `MysterDatagram` API after local initialization | `StandardDatagramClientImpl`, `ServerStats` MessagePak |
| Bidirectional-stats server | `BidirectionalServerStatsDatagramServer` | Datagram protocol manager | `TransactionProtocol`, address/identity `MysterServerPool.suggestAddress(...)` |
| Pair verification | `MysterServerPoolImpl` | Bidirectional-stats server | expected-key transaction `102`, response `/Identity` comparison |
| Local stats dependencies | `MysterDatagramImpl` construction | Bidirectional-stats client | name/port suppliers, `Identity`, `FileTypeListManager` |

Wire contract:

- Request transaction code: `102`.
- Request payload: one serialized server-stats MessagePak, including `/Port` and, when available, `/Identity`, `/ServerName`, uptime, speed, and file counts.
- Successful response error code: `NO_ERROR`.
- Response payload: the responder's serialized server-stats MessagePak with the same field contract.
- Unsupported servers return the transaction layer's `TRANSACTION_TYPE_UNKNOWN` error; current clients do not downgrade to `101`.

## 6. Key decisions & edge cases

- This is bidirectional, not a fire-and-forget reverse stats message: both peers exchange cards in one round trip.
- The server uses observed source IP plus advertised Myster port. Missing or invalid ports must follow a documented safe policy; never accept ports outside `1..65535`.
- Received stats are discovery hints. The address/identity `suggestAddress(...)` overload carries the derived address/CID association into the pool, but expected-key stats and an exact response-identity comparison keep normal validation/onboarding in control.
- Select transaction `101` while any locally shared file list is uninitialized. Transaction `102` always sends a complete card; an uninitialized-list exception after selection is an illegal state.
- A transaction `102` responder may return a valid minimal response containing port and available identity/name if its local file lists are unavailable.
- Serialization failures fail the transaction; they must not silently emit an empty packet.
- Checked serialization failures use the datagram API's `PromiseFuture` error channel. `UncheckedIOException` is not used.
- The protocol must work through the existing encrypted datagram wrapper as well as the normal unencrypted transaction path.
- Existing `101` behavior and registration remain untouched.
- No automatic fallback to `101` is planned. Keeping the old server handler supports old clients without carrying a permanent client downgrade path.

## 7. Acceptance criteria

- [x] Transaction code `102` is uniquely defined and documented.
- [x] The client sends local stats and returns remote stats in one UDP round trip.
- [x] The server returns local stats and suggests the requester using source IP plus advertised valid Myster port.
- [x] Pre-initialization refresh uses `101`; transaction `102` requests carry complete local stats, and minimal responses retain identity/port information.
- [x] Invalid/malformed payloads and ports do not add unsafe addresses to the pool.
- [x] Transactions `101` and `102` are both registered and independently callable.
- [x] Encrypted datagram wrapping preserves bidirectional-stats behavior.
- [x] Normal pool refresh selects `101` before local initialization and `102` afterward, without retrying `101` when a sent `102` is unsupported.
- [x] Focused client/server protocol tests pass.

---
## ✦ IMPLEMENTATION DETAILS (for the implementation agent)
---

## 8. Affected files / classes

- `src/main/java/com/myster/net/datagram/DatagramConstants.java` - transaction `102` constant/Javadoc.
- `src/main/java/com/myster/net/datagram/client/BidirectionalServerStatsDatagramClient.java` - request/response codec.
- `src/main/java/com/myster/net/datagram/client/StandardDatagramClientImpl.java` and request codec implementations - checked request-serialization contract.
- `src/main/java/com/myster/net/datagram/client/EncryptingStandardDatagramClientImpl.java` - preserve checked serialization/decryption failures through encryption.
- `src/main/java/com/myster/net/server/datagram/BidirectionalServerStatsDatagramServer.java` - corrected-address discovery and response codec.
- `src/main/java/com/myster/net/client/MysterDatagram.java` - bidirectional stats operation.
- `src/main/java/com/myster/net/datagram/client/MysterDatagramImpl.java` - operation and local stats dependencies.
- `src/main/java/com/myster/net/stream/server/ServerStats.java` - shared minimal-card builder.
- `src/main/java/com/myster/tracker/MysterServerPool.java` and `MysterServerPoolImpl.java` - address/identity hint verification and transaction `102` refresh without downgrade.
- `src/main/java/com/myster/Myster.java` - register server `102` alongside `101`.
- New focused tests under `src/test/java/com/myster/net/datagram` and `.../server/datagram`.

## 9. Step-by-step implementation

1. Audit the existing transaction `102` implementation against this plan before changing it; preserve working code and user changes.
2. Have `MysterDatagramImpl` select legacy `101` while any shared local file list is uninitialized and `102` afterward. The `102` client emits one complete stats MessagePak and treats `NotInitializedException` as an illegal state. Let request serializers declare `IOException` and carry that checked failure through `PromiseFuture`; do not use `UncheckedIOException` or catch unexpected runtime failures.
3. Ensure the server parses one MessagePak, validates `/Port` and `/Identity`, builds `MysterAddress(sourceInetAddress, advertisedPort)`, and only then calls the appropriate `pool.suggestAddress(...)` overload. Missing or invalid ports reject the request. Address/identity hints use expected-key transaction `102` verification and exact response-identity comparison before onboarding.
4. Generate the response through the same shared server-stats builder, with a valid minimal response if file lists are unavailable.
5. Keep `getBidirectionalServerStats(...)` as the operation used by `MysterServerPoolImpl.refreshMysterServer()`. Its pre-send local-initialization selection may use `101`; never retry `101` after a remote `102` failure.
6. Register both stats servers in `Myster.addServerConnectionSettings(...)` and verify encrypted forwarding preserves inner error/data behavior.
7. Add any missing focused unit/integration tests and update `docs/impl_summary/bidirectional-server-stats.md` if implementation behavior changes.

## 10. Tests to write

- Client full request serialization, illegal pre-initialization codec use, response parsing, transaction code, and serialization failure.
- Wrapper selection of `101` before local file-list initialization and `102` afterward.
- Server valid advertised port, missing port, invalid low/high port, malformed MessagePak, corrected source-IP address, pool suggestion, full/minimal response.
- Round-trip test proving both nodes receive the other's stats and the responder discovers the requester's advertised port.
- Encrypted-wrapper round trip for transaction `102`.
- Regression test that transaction `101` remains registered and unchanged.
- Regression test that an unsupported `102` does not cause an automatic `101` retry.

## 11. Docs / Javadoc to update

- Javadoc `DatagramConstants`, both codec classes, and `MysterDatagram.getBidirectionalServerStats(...)`.
- Document the request/response MessagePak and its discovery-hint trust boundary in the relevant protocol design documentation.
- Keep `docs/impl_summary/bidirectional-server-stats.md` aligned with the no-fallback and address/CID verification decisions.
