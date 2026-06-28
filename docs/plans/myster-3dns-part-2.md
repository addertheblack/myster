# Myster 3DNS - Part 2: Protocol and Lookup

Prerequisite plan: [Myster 3DNS - Part 1a: Core Data Structures](myster-3dns-part-1a.md).

Related UI plan: [Myster 3DNS - Part 1b: Tracker UI Integration](myster-3dns-part-1b.md).

## 1. Summary

Implement the 3DNS network layer on top of Part 1a: a UDP `FIND_CLOSEST` transaction with explicit exact/left/right public-key candidate groups, future-returning address-candidate validation, client-side iterative lookup with side-aware progress, and maintenance that refreshes local 3DNS finger targets over time.

## 2. Non-goals

- Do not rework Part 1a core data structures except where Part 2 needs validation or protocol hooks.
- Do not integrate 3DNS into private type import/onramp flows in this milestone.
- Do not add a UI for 3DNS routing tables or lookup debugging.
- Do not treat remote public-key/address candidates as trusted identity evidence before pool validation.
- Do not implement a general storage DHT, Byzantine-resistant routing, reputation, proof-of-work, or Sybil resistance.

## 3. Assumptions & open questions

- Assumption: Part 1a has landed and provides `IdentityNeighborSet`, `MysterServerPool.findClosestByCid(...)`, and local `ThreeDnsServerList` seeds.
- Assumption: the left/right split is part of the wire contract, not merely response ordering.
- Assumption: sparse networks are normal. Any exact, left, or right group can be empty without making a response malformed.
- Assumption: `FIND_CLOSEST` returns encoded public keys despite larger payloads to avoid a separate key-fetch round trip.
- Assumption: server identity keys are currently X.509-encoded RSA public keys, matching `Util.publicKeyFromBytes(...)`.
- Assumption: validation means contacting the candidate address through normal Myster stats/TLS paths and confirming that the server at that address presents the candidate public key.
- Assumption: exact target success requires `Util.generateCid(validatedPublicKey).equals(targetCid)`.
- Assumption: unencrypted `FIND_CLOSEST` is acceptable for bootstrap. When a public-key candidate is available, the client may use it as an expected key for the next datagram attempt, but the candidate is still not trusted until the peer proves it.
- Open question: what transaction code should be reserved permanently for 3DNS? The plan suggests `303`, but the final value only needs to avoid existing constants.
- Open question: should maintenance run exactly hourly or use a jittered interval? The plan recommends jitter.

## 4. Proposed design

Part 2 exposes the local 3DNS model over UDP and uses returned address candidates for iterative lookup.

The `FIND_CLOSEST` request carries a target CID and a per-side limit. The response has separate exact, left, and right groups. Each wire candidate contains an encoded public key and an address. The recipient decodes the public key into a `PublicKeyIdentity` and derives the candidate CID locally from that identity. The response intentionally omits a serialized candidate CID.

The server side answers only from local pool/tracker knowledge and must not return servers that the local pool currently considers down/nonresponsive. Because Part 1a filters closest-by-CID results, the transaction server should use that API and also avoid serializing entries that become down between lookup and response encoding.

The client side uses `ThreeDnsLookup` to iterate from local seeds and remote address candidates. It tracks visited CIDs and addresses, preserves side-aware progress, caps hops/queue sizes/response bytes, and validates returned candidates through the pool before reporting exact success or inserting trusted retained state.

`ThreeDnsMaintenance` periodically refreshes the local positive exponential targets by running lookups for a bounded set of targets and letting normal pool/listener behavior update the retained finger list.

## 5. Architecture connections

Part 2 uses the Part 1a local structures as the source of truth and adds network exposure plus lookup orchestration.

| New / changed thing | Owned / created by | Called / used by | Connects to (existing) |
|---|---|---|---|
| Pool address-candidate validation API | `MysterServerPool` / `MysterServerPoolImpl` | `ThreeDnsLookup` | Existing stats refresh, TLS/public-key validation, identity extraction |
| `FIND_CLOSEST` UDP transaction | `FindClosestDatagramServer` and `FindClosestDatagramClient` | Remote peers and `MysterDatagram` | `TransactionProtocol`, `StandardDatagramClientImpl`, `MessagePak` |
| `ThreeDnsAddressCandidate` / set | `com.myster.threedns` | Datagram client, lookup, validation | `PublicKeyIdentity`, `MysterAddress`, derived `Cid128` |
| Client lookup orchestration | `ThreeDnsLookup` | Future CID-based discovery callers | `MysterProtocol`, `MysterDatagram`, `MysterServerPool`, `ThreeDnsServerList` |
| Maintenance task | `ThreeDnsMaintenance` | Myster startup/shutdown | `ThreeDnsLookup`, local identity CID, `ThreeDnsServerList` |
| Expected-key datagram path | `MysterDatagramImpl` / `PublicKeyLookup` | `ThreeDnsLookup` | Existing MSD encryption/decryption path |

Protocol format, MessagePak paths:

- Request:
  - `/schemaVersion` int, value `1`
  - `/targetCid` byte array, exactly 16 bytes
  - `/perSideLimit` int, optional; default `2`, server clamps to a small maximum such as `4`
- Response:
  - `/schemaVersion` int, value `1`
  - `/exactCount` int, `0` or `1`
  - `/exact/publicKey` byte array, X.509-encoded public key, present only when `exactCount == 1`
  - `/exact/ip` string, present only when `exactCount == 1`
  - `/exact/port` int, present only when `exactCount == 1`
  - `/leftCount` int, `0..perSideLimit`
  - `/left/0/publicKey` byte array, X.509-encoded public key
  - `/left/0/ip` string, textual IP from `MysterAddress.getIP()`
  - `/left/0/port` int
  - `/rightCount` int, `0..perSideLimit`
  - `/right/0/publicKey` byte array, X.509-encoded public key
  - `/right/0/ip` string, textual IP from `MysterAddress.getIP()`
  - `/right/0/port` int
  - repeated for each left/right result index

The recipient derives each candidate CID with `Util.generateCid(publicKey)` after decoding `/publicKey`. If a future diagnostic field includes a CID, it must be checked against the derived CID and ignored on mismatch.

## 6. Key decisions & edge cases

- `FIND_CLOSEST` uses explicit exact/left/right groups to preserve protocol flexibility.
- Empty exact, left, or right groups are valid.
- The server must not serialize a down/nonresponsive server.
- Public-key payload size is the main protocol cost. Keep per-side limits small and enforce an encoded-byte response budget.
- Returned address candidates are untrusted until validation proves the address presents the expected public key.
- A malicious node can return bogus closer candidates or unrelated public keys. The client mitigates this with visited sets, strict side-aware progress, hop caps, queue caps, byte caps, and validation before success.
- Exact CID lookup succeeds only after validation, not merely because a remote peer returned a matching public key.
- `suggestAddress` remains fire-and-forget for existing callers; `validateCandidate(...)` is the future-returning 3DNS path.

## 7. Acceptance criteria

- [ ] A server can answer `FIND_CLOSEST` over the existing UDP transaction system using MessagePak.
- [ ] `FIND_CLOSEST` returns explicit exact/left/right encoded public-key/address groups, with up to two candidates per side by default.
- [ ] `FIND_CLOSEST` can return fewer than two candidates on either side, including zero.
- [ ] `FIND_CLOSEST` never serializes a server that the local pool currently considers down/nonresponsive.
- [ ] Recipients derive CIDs locally from returned public keys.
- [ ] A client can perform iterative 3DNS lookup from known seeds with visited tracking, strict side-aware progress, and hop/result/byte caps.
- [ ] Returned public-key/address candidates are not inserted directly into trusted retained state without pool validation.
- [ ] Exact CID lookup succeeds only after pool validation confirms that the candidate address presents the expected public key and that the public key hashes to the target CID.
- [ ] Unit tests cover protocol serialization, sparse left/right response groups, lookup loop termination, and validation failure.
- [ ] A manual smoke path can start two or more local Myster nodes and route from one known node toward another node's CID.

---
## ✦ IMPLEMENTATION DETAILS (for the implementation agent)
---

## 8. Affected files / classes

- `src/main/java/com/myster/tracker/MysterServerPool.java` - add future-returning public-key/address candidate validation API.
- `src/main/java/com/myster/tracker/MysterServerPoolImpl.java` - validate public-key/address candidates against stats/TLS public keys.
- New `src/main/java/com/myster/threedns/ThreeDnsAddressCandidate.java` - decoded wire candidate containing `PublicKeyIdentity`, `MysterAddress`, and derived `Cid128`.
- New `src/main/java/com/myster/threedns/ThreeDnsAddressCandidateSet.java` - exact/left/right decoded wire candidate groups returned by the datagram client.
- New `src/main/java/com/myster/threedns/ThreeDnsLookup.java` - client-side iterative lookup orchestration.
- New `src/main/java/com/myster/threedns/ThreeDnsLookupResult.java` - result object containing target, validated exact server if found, closest left/right candidates, and status.
- New `src/main/java/com/myster/threedns/ThreeDnsMaintenance.java` - periodic ideal-position refresh runner.
- `src/main/java/com/myster/net/datagram/DatagramConstants.java` - add `THREE_DNS_FIND_CLOSEST_TRANSACTION_CODE`.
- New `src/main/java/com/myster/net/server/datagram/FindClosestDatagramServer.java` - server-side transaction handler.
- New `src/main/java/com/myster/net/datagram/client/FindClosestDatagramClient.java` - client-side transaction serializer/parser.
- `src/main/java/com/myster/net/client/MysterDatagram.java` - add `findClosest(...)`.
- `src/main/java/com/myster/net/datagram/client/MysterDatagramImpl.java` - implement `findClosest(...)` through `doSection`.
- `src/main/java/com/myster/net/datagram/client/PublicKeyLookup.java` and/or `MysterDatagramImpl` - add a way for one lookup attempt to use a candidate expected public key without first inserting it as trusted pool state.
- `src/main/java/com/myster/Myster.java` - wire 3DNS server transaction and maintenance startup.
- `src/test/java/com/myster/tracker/...` - add address-candidate validation tests.
- `src/test/java/com/myster/threedns/...` - add lookup tests.
- `src/test/java/com/myster/net/datagram/...` - add protocol round-trip tests.

## 9. Step-by-step implementation

1. Add pool validation for expected public keys.
   - In `MysterServerPool`, add `PromiseFuture<Optional<MysterServer>> validateCandidate(ThreeDnsAddressCandidate candidate);`.
   - Contact `candidate.address()` through existing refresh/stats paths.
   - Complete with a server only if the resulting identity is `PublicKeyIdentity` whose encoded key equals `candidate.identity().getPublicKey().getEncoded()`.
   - If the address is already known and cached under the same public key and currently responsive/up, return the cached server immediately after triggering any needed up-notification.
   - If the address is known under a different public key, treat it as validation failure.
   - If an address is in `deadCache`, return `Optional.empty()`.
   - Share outstanding validation futures for the same address and expected public key.
   - Refactor `refreshMysterServer(MysterAddress)` and `serverStatsCallback(...)` as needed so validation can await the refreshed/created `MysterServer`.

2. Add the `FIND_CLOSEST` UDP protocol.
   - In `DatagramConstants`, add `public static final int THREE_DNS_FIND_CLOSEST_TRANSACTION_CODE = 303;` or another reserved unused value.
   - Add `FindClosestDatagramServer implements TransactionProtocol`.
   - Parse request MessagePak and validate `/targetCid` length is 16.
   - Read `/perSideLimit`, default `2`, maximum `4`.
   - Enforce an encoded-byte response budget.
   - Call `pool.findClosestByCid(target, perSideLimit)`.
   - Serialize `/exactCount`, `/leftCount`, and `/rightCount` groups.
   - Do not serialize any server that is no longer up by the time the response is written.
   - Add `FindClosestDatagramClient implements StandardDatagramClientImpl<ThreeDnsAddressCandidateSet>`.
   - Decode public keys with `Util.publicKeyFromBytes(...)`.
   - Derive candidate CIDs locally.
   - Skip malformed entries or fail the packet consistently with existing client behavior.

3. Expose client protocol API.
   - In `MysterDatagram`, add `PromiseFuture<ThreeDnsAddressCandidateSet> findClosest(ParamBuilder params, Cid128 target, int perSideLimit);`.
   - In `MysterDatagramImpl`, implement it with `doSection(params, new FindClosestDatagramClient(target, perSideLimit))`.
   - Add a path for a one-off expected server key when querying an address candidate.
   - Do not insert the candidate key into trusted pool identity state merely to make datagram encryption work.

4. Implement `ThreeDnsLookup`.
   - Dependencies: `MysterProtocol`, `MysterServerPool`, and a seed supplier from `Tracker`.
   - Suggested constants: max hops `16`, default `2` candidates per side, maximum `4` per side, queue cap `64`.
   - Seed from `Tracker.getThreeDnsSeeds(...)`, `pool.findClosestByCid(target, ...)`, and any explicit bootstrap candidate.
   - Track visited CIDs and visited addresses.
   - Query the closest unvisited candidate with `protocol.getDatagram().findClosest(...)`.
   - Use the candidate public key as expected key if the API supports it.
   - For each returned candidate, derive CID, classify side, validate asynchronously, and enqueue only if it improves that side and has not been visited.
   - Treat exact match as success only after `pool.validateCandidate(...)` confirms the expected public key and target CID.
   - Stop on validated exact success, no candidates, hop cap, oversized response, or no strict side-aware progress.
   - Result statuses: `EXACT_VALIDATED`, `CLOSEST_KNOWN`, `NO_ROUTE`, `FAILED`.

5. Implement `ThreeDnsMaintenance`.
   - Construct with local `Cid128`, `ThreeDnsLookup`, `ThreeDnsServerList`, and a scheduler/timer.
   - Generate 128 target CIDs using `localCid.plusPowerOfTwo(bitIndex)`.
   - Periodically refresh a bounded subset or all targets.
   - Use jitter so all nodes do not refresh at the same instant.
   - Stop on application shutdown through `MysterGlobals.addShutdownListener(...)`.

6. Wire startup in `Myster.java`.
   - Compute local CID before tracker construction with `identity.getMainIdentity().map(kp -> Util.generateCid(kp.getPublic()))`.
   - Pass local CID to the `Tracker` constructor, matching Part 1a.
   - Construct `ThreeDnsLookup` when `pool`, `protocol`, and `tracker` exist.
   - Register `new FindClosestDatagramServer(pool::findClosestByCid)` near the other datagram transactions.
   - Start `ThreeDnsMaintenance` only when local CID is present.
   - Register maintenance shutdown.

7. Keep private type/onramp integration out.
   - Do not change `AccessListState.getOnramps()` or join-request flows.
   - Future work can add CID-based onramp endpoints once lookup is proven.

## 10. Tests to write

- `TestMysterServerPoolImplThreeDnsValidation`
  - `validateCandidate` returns an existing cached server for the same address/public key
  - `validateCandidate` completes after stats refresh for a new address
  - `validateCandidate` rejects an address that resolves to a different public key
  - concurrent validation calls share outstanding refresh work
  - dead-cache addresses return empty

- `TestFindClosestDatagramProtocol`
  - request MessagePak serializes target CID and per-side limit
  - server defaults/clamps oversized per-side limits
  - server enforces encoded-byte response budget
  - response round-trips exact, left, and right public-key/address groups
  - response permits zero, one, or two candidates on either side without parse failure
  - server does not serialize down/nonresponsive servers
  - client derives CIDs from returned public keys
  - malformed CID length or malformed public key fails with `BadPacketException` or client `IOException`

- `TestThreeDnsLookup`
  - stops on validated exact candidate
  - rejects claimed exact candidate when pool validation resolves to a different public key/CID
  - terminates on no strict side-aware progress
  - avoids revisiting CIDs/addresses
  - respects hop, queue, per-side result, and byte caps
  - calls pool validation for returned candidates but does not directly mutate trusted retained state
  - can use a candidate public key for the next query without inserting it into the pool

- Integration/manual smoke
  - Start two local nodes with distinct identities/ports.
  - Seed node A with node B's address, verify B enters A's pool and 3DNS finger list.
  - Start node C with only node A known, lookup node B's CID through A, verify exact success only after validation.

## 11. Docs / Javadoc to update

- Update `docs/design/Myster 3DNS.md` after implementation to clarify explicit exact/left/right protocol groups and public-key/address candidates.
- Add Javadoc to `ThreeDnsAddressCandidate` explaining that it is a decoded wire candidate containing an official `PublicKeyIdentity` plus an address, and that the derived CID is local computation from the public key, not remote proof.
- Add Javadoc to `ThreeDnsLookup` documenting side-aware termination and validation semantics.
- Add Javadoc to `MysterServerPool.validateCandidate` explaining how it differs from fire-and-forget `suggestAddress`.
- Add `docs/impl_summary/myster-3dns-part2.md` after implementation.
