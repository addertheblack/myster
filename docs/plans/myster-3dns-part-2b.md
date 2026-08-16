# Myster 3DNS - Part 2b: Candidate Identity Verification

Prerequisites and follow-on plans:

- [Part 2a: FIND_CLOSEST Protocol and Expected-Key Hook](myster-3dns-part-2a.md)
- [Part 3: Iterative CID Resolution](myster-3dns-part-3.md)
- [Part 4: Routing-Table Maintenance and Bootstrap](myster-3dns-part-4.md)

## 1. Summary

Turn Part 2a's expected-key UDP transport hook into the smallest reusable 3DNS query primitive: query an untrusted public-key/address candidate with `FIND_CLOSEST`, accept the result only after an authenticated encrypted response proves possession of the advertised private key, and return a transport-neutral verified-peer value alongside the still-untrusted candidates in the response.

## 2. Non-goals

- Do not implement iterative CID lookup; that is Part 3.
- Do not bootstrap or periodically maintain the retained finger table; that is Part 4.
- Do not fetch server stats, insert candidates into `MysterServerPool`, or persist verified peers.
- Do not add a second verification round trip before every `FIND_CLOSEST`; the useful query response is itself the proof.
- Do not add general in-flight deduplication or caching before concurrent production callers demonstrate a need.
- Do not expose expected-key TCP connections through `MysterStream` in this milestone.
- Do not treat a verified peer as honest, permanently reachable, or authorized to make trusted claims about other peers.

## 3. Assumptions & open questions

- 3DNS lookup is primarily a UDP operation. `ParamBuilder.withExpectedServerPublicKey(...)` already forces the UDP request through `EncryptingStandardDatagramClientImpl`, independently of the address-to-key cache.
- The expected private key is required to decrypt the request and recover its one-time symmetric response key. A response that passes ChaCha20-Poly1305 decryption therefore proves possession of that private key even though the current optional server-signature section is not checked separately.
- `ThreeDnsAddressCandidate` already derives its CID locally from the candidate's X.509 public key and rejects construction with an inconsistent explicit CID. No candidate CID is accepted from the wire.
- Verification binds one successful transaction to the candidate key and reachable address/path. It does not make subsequent plaintext traffic safe; later traffic that relies on the identity must continue to supply the expected key or use an equivalently authenticated connection.
- `TLSSocket.createClientSocket(...)` already accepts an expected server public key and checks the peer certificate, but `MysterSocketFactory` and `MysterStream` do not currently expose that argument. The verified-peer model should not depend on UDP so a future TCP adapter can produce the same type.
- No architecture-blocking questions remain. UDP is the only verification transport implemented in Part 2b; TCP exposure can be added when a real streaming caller requires it.

## 4. Proposed design

Add a narrow `ThreeDnsPeerClient` in `com.myster.threedns`. Its asynchronous `findClosest(...)` operation accepts an untrusted `ThreeDnsAddressCandidate`, the lookup target, and the per-side limit. It calls the existing datagram API with the candidate address and `withExpectedServerPublicKey(candidate.identity().getPublicKey())`.

There is no standalone ping or proof exchange. If the encrypted `FIND_CLOSEST` transaction succeeds and its response authenticates under the one-time response key, `ThreeDnsPeerClient` creates a `VerifiedThreeDnsPeer` for the responder and returns it with the decoded `ThreeDnsAddressCandidateSet`. The candidates returned by that peer remain untrusted hints; each becomes verified only when it is itself queried through the same expected-key path.

`VerifiedThreeDnsPeer` is a trust-typed value with read-only identity, derived CID, and address accessors. Construction is restricted to the verification package/path so ordinary wire decoding cannot accidentally promote a hint. The value means only that this key answered at this address/path during the completed operation.

The client codec also checks any response entry labelled `exact`: after deriving its CID from its public key, it must equal the target in the request. A mismatch rejects the response as malformed. Part 3 still independently scores all candidates and decides whether an exact verified responder or returned hint satisfies the caller's target.

## 5. Architecture connections

Part 2b is the trust boundary between wire hints and the iterative resolver. It composes existing encryption and codec behavior; it does not introduce another network protocol.

| New / changed thing | Owned / created by | Called / used by | Connects to (existing) |
|---|---|---|---|
| Expected-key 3DNS query | `ThreeDnsPeerClient` | Part 3 resolver, later Part 4 maintenance | `MysterDatagram.findClosest(...)`, `ParamBuilder.withExpectedServerPublicKey(...)` |
| Verified responder value | `VerifiedThreeDnsPeer` | Part 3 lookup result/frontier, later authenticated transports | `ThreeDnsAddressCandidate`, `PublicKeyIdentity`, `ServerCid`, `MysterAddress` |
| Verified query result | `ThreeDnsVerifiedQueryResult` | Part 3 iteration | verified responder plus untrusted `ThreeDnsAddressCandidateSet` |
| Exact-claim validation | `FindClosestDatagramClient` | Every decoded `FIND_CLOSEST` response | locally derived candidate CID and request target |

The data flow is: an earlier peer supplies an untrusted public-key/address hint; `ThreeDnsPeerClient` encrypts a useful `FIND_CLOSEST` request to that key; only its private-key holder can recover the response key; successful authenticated response decoding promotes the responder to `VerifiedThreeDnsPeer`; returned neighbors remain untrusted inputs for later iterations.

There is no new wire or disk format. Transaction `303` and the Part 2a MessagePak schema remain unchanged.

## 6. Key decisions & edge cases

- Verification is fused with the useful UDP query, avoiding a duplicate proof request at each hop.
- CID derivation and address/key possession are different checks: local hashing establishes the key's CID, while the encrypted round trip establishes possession at the contacted endpoint/path.
- An `exact` wire-group entry whose derived CID differs from the requested target invalidates the response.
- A successfully verified responder need not have the target CID; intermediate peers are expected. Target equality is evaluated explicitly by Part 3.
- A verified responder's returned candidates are not transitively trusted.
- Timeout, malformed response, decryption/authentication failure, wrong-key response, and cancellation produce failure/cancellation, never a verified value.
- Verification is evidence from one completed operation, not a permanent liveness assertion or a cache mutation.
- The result types must make the trust distinction visible without claiming that a peer's routing answers are truthful.
- TCP expected-key support remains compatible but is not silently used as fallback; UDP failure stays an explicit failure.

## 7. Acceptance criteria

- [ ] A caller can issue `FIND_CLOSEST` to an untrusted candidate using that candidate's advertised public key without first caching the address/key association.
- [ ] A verified-peer value is returned only after the encrypted UDP response authenticates with the one-time key from the expected-key request.
- [ ] Candidate CIDs are derived locally from public keys; no remote CID claim is trusted.
- [ ] A candidate labelled exact is rejected unless its derived CID equals the request target.
- [ ] Candidates inside a verified peer's response remain explicitly untrusted until individually queried.
- [ ] Verification performs one useful `FIND_CLOSEST` round trip, not a proof round trip followed by a duplicate query.
- [ ] Failures and cancellation cannot produce or cache a verified-peer value.
- [ ] Part 2b does not mutate the server pool, retained finger table, preferences, or scheduler state.
- [ ] The verified-peer model is not tied to UDP internals, leaving room for a future expected-key TLS producer.

---
## ✦ IMPLEMENTATION DETAILS (for the implementation agent)
---

## 8. Affected files / classes

- New `src/main/java/com/myster/threedns/ThreeDnsPeerClient.java` - expected-key `FIND_CLOSEST` orchestration and verified-result creation.
- New `src/main/java/com/myster/threedns/VerifiedThreeDnsPeer.java` - trust-typed responder identity/CID/address with restricted construction.
- New `src/main/java/com/myster/threedns/ThreeDnsVerifiedQueryResult.java` - verified responder plus immutable untrusted response candidates.
- `src/main/java/com/myster/net/datagram/client/FindClosestDatagramClient.java` - reject a mismatched exact-group CID after deriving it from the key.
- Focused tests under `src/test/java/com/myster/threedns` and `src/test/java/com/myster/net/datagram/client`.

## 9. Step-by-step implementation

1. Add `VerifiedThreeDnsPeer` as an immutable public read type whose constructor or promotion factory is package-private. Preserve the candidate's `PublicKeyIdentity`, derived `ServerCid`, and `MysterAddress`; do not expose a public constructor that lets arbitrary callers claim verification.
2. Add immutable `ThreeDnsVerifiedQueryResult` containing the verified responder and decoded `ThreeDnsAddressCandidateSet`. Document that only the responder is verified.
3. Implement `ThreeDnsPeerClient.findClosest(ThreeDnsAddressCandidate peer, ServerCid target, int perSideLimit)` over an injected `MysterDatagram`.
   - Build `ParamBuilder` with `peer.address()` and `withExpectedServerPublicKey(peer.identity().getPublicKey())`.
   - Call the existing `MysterDatagram.findClosest(...)` once.
   - Promote the input peer only in the successful authenticated result callback.
   - Preserve exception and cancellation behavior and never retain a successful value after cancellation.
4. In `FindClosestDatagramClient`, compare a decoded exact candidate's locally derived CID to the request target and reject the entire malformed response on mismatch. Do not add candidate CIDs to the wire schema.
5. Keep pool lookup, stats fetch, persistence, retry, and deduplication out of this layer.
6. Write `docs/impl_summary/myster-3dns-part-2b.md` after implementation.

## 10. Tests to write

- `ThreeDnsPeerClient` passes the candidate address and exact expected key to `MysterDatagram.findClosest(...)`.
- A successful expected-key response produces a verified responder and leaves every returned candidate unverified.
- Timeout, malformed response, decryption failure, and cancellation do not produce a verified value.
- A conflicting cached address key cannot replace the explicit candidate key; retain the Part 2a lower-level regression test.
- `FindClosestDatagramClient` accepts an exact candidate derived to the requested CID and rejects a mismatched exact-group key.
- `VerifiedThreeDnsPeer` cannot be constructed through the public wire-model API and preserves the candidate identity/CID/address exactly.
- Focused crypto coverage demonstrates that a response encrypted with any key other than the request's one-time symmetric key fails authentication.

## 11. Docs / Javadoc to update

- Update `docs/design/Myster 3DNS.md` with the distinction between untrusted hints, verified responders, and pool-retained servers.
- Javadoc `ThreeDnsPeerClient`, `VerifiedThreeDnsPeer`, and `ThreeDnsVerifiedQueryResult`, especially the operation-scoped meaning of verification.
- Clarify in `ThreeDnsAddressCandidate` Javadoc that CID derivation is already enforced but does not prove address/key possession.
- Add `docs/impl_summary/myster-3dns-part-2b.md` during implementation.
