# Myster 3DNS - Part 3: Iterative CID Resolution

Prerequisites and follow-on plan:

- [Part 2a: FIND_CLOSEST Protocol and Expected-Key Hook](myster-3dns-part-2a.md)
- [Part 2b: Candidate Identity Verification](myster-3dns-part-2b.md)
- [Part 4: Routing-Table Maintenance and Bootstrap](myster-3dns-part-4.md)

> Preliminary plan: finalize routing limits and caller API after Part 2b establishes the verified-query result shape.

## 1. Summary

Implement the public lookup operation that resolves a target CID by iteratively querying identity-verified 3DNS peers, selecting direction-appropriate candidates, and returning either the verified exact peer or an explicit bounded failure/closest-known result without requiring active routing-table maintenance.

## 2. Non-goals

- Do not change the `FIND_CLOSEST` wire format or transaction code.
- Do not reimplement expected-key proof; use the Part 2b verified-query primitive.
- Do not own periodic routing-table maintenance or bootstrap scheduling; that is Part 4.
- Do not insert resolved peers into the server pool or retained finger table; Part 4 owns persistent onboarding when needed.
- Do not claim success from an unverified public-key/address hint.
- Do not integrate the resolver into private-type onramps or UI in this milestone.
- Do not add general DHT values, caching policy, reputation, or Byzantine consensus.

## 3. Assumptions & open questions

- The routing table uses positive power-of-two offsets. Resolution therefore follows a monotonic positive-ring route and normally approaches the target from its LEFT/predecessor side; exact is preferred first.
- Both wire sides remain available for resilience and future routing policies. The next-hop selector must explicitly score the appropriate side rather than depend on response grouping or ordering.
- An exact hint is queried through Part 2b before success. The successful expected-key response proves possession; final exact success additionally requires the verified responder's locally derived CID to equal the target.
- Initial seeds are ordinary candidate snapshots from currently usable retained 3DNS entries or other known pool servers. They are entry points, not implicitly verified for this lookup; querying them through Part 2b establishes the same proof as every later hop.
- Part 4-maintained fingers improve availability and route quality but are not a correctness prerequisite. With no seed, Part 3 returns `NO_ROUTE` until normal discovery or an explicit caller-supplied entry point becomes available.
- Open question for finalization: whether the public result should expose only exact/not-found or also the closest verified predecessor/successor. Preliminary recommendation: a structured result with status and closest verified peers because Part 4 needs the closest reachable peer for target maintenance.
- Open question for finalization: tune hop, frontier, parallelism, byte, and time budgets after Part 2b testing. Preliminary values are 16 hops, frontier 64, per-side limit 2, and low parallelism.

## 4. Proposed design

`ThreeDnsLookup.resolve(ServerCid)` starts from a bounded snapshot of untrusted address/key candidates supplied by the tracker/pool. It maintains a bounded frontier, queries the best unvisited candidate through `ThreeDnsPeerClient.findClosest(...)`, records the returned verified responder, derives and scores every returned candidate CID, rejects malformed or non-progressing hints, and repeats.

The selector is direction-aware. With positive-offset routing, an exact derived CID wins; otherwise candidates on the LEFT/predecessor side of the target are favored, ordered by predecessor distance. A candidate is eligible as a next hop only if it makes strict positive-ring progress from the current best position toward the target. RIGHT/successor results can be retained as bounded fallback/diagnostic information but cannot silently cause an overshoot or loop.

An exact hint does not complete lookup. It is placed at the head of the frontier and queried with its expected key. Lookup completes `EXACT_VERIFIED` only when that query succeeds and the resulting `VerifiedThreeDnsPeer.cid()` equals the target. Exhaustion, no strict progress, limits, timeout, cancellation, and malformed responses produce explicit non-success statuses with any closest verified diagnostics allowed by the final result contract.

## 5. Architecture connections

| New / changed thing | Owned / created by | Called / used by | Connects to (existing) |
|---|---|---|---|
| Resolver API | `ThreeDnsLookup` | Future CID-based features, Part 4 maintenance | `ThreeDnsPeerClient`, seed provider, `ServerCid` |
| Lookup result | `ThreeDnsLookupResult` | Resolver callers and Part 4 | `VerifiedThreeDnsPeer`, target CID, terminal status |
| Direction-aware frontier | Lookup-internal model | Iteration loop | `ServerCid` ring comparisons, untrusted Part 2a candidate groups |
| Seed candidate snapshot | `Tracker` or injected provider | Resolver startup | retained `ThreeDnsServerList`, known usable pool servers |
| Per-hop identity proof | Part 2b `ThreeDnsPeerClient` | Resolver | expected-key UDP `FIND_CLOSEST`, verified responder type |

No new wire or disk format is introduced. Part 3 consumes transaction `303`; it can use passively retained state from Part 1a but does not require or mutate the actively maintained state introduced in Part 4.

The data flow is: snapshot known entry-point candidates; query one using Part 2b; promote only that responding peer to verified; score its untrusted returned hints locally; query a strictly better hint; and stop only on a verified exact peer or an explicit bounded terminal condition.

## 6. Key decisions & edge cases

- Exact wire grouping is only a hint. Exact success requires querying that candidate and comparing the verified responder's locally derived CID to the target.
- For the current positive-offset topology, exact then LEFT/predecessor is the normal next-hop preference.
- Strict progress is measured with ring arithmetic, not ordinary signed/numeric subtraction.
- Returned side labels and ordering never replace local CID derivation and scoring.
- A node identity or address is never queried twice in one lookup.
- Multiple addresses for one identity do not create multiple logical hops, but an alternate address may be tried after a transport failure within a bounded policy.
- A verified responder can still return unrelated, duplicate, cyclic, or malicious hints; proof authenticates identity, not honesty.
- Cancellation stops new work and prevents late completions from changing the result.
- The resolver distinguishes `EXACT_VERIFIED`, `CLOSEST_VERIFIED`, `NO_ROUTE`, `LIMIT_REACHED`, `CANCELLED`, and `FAILED` (names may be refined during finalization).
- Resolving without Part 4 is valid. A sparse or empty local candidate set affects reachability, not the resolver's trust guarantees.

## 7. Acceptance criteria

- [ ] A caller can asynchronously resolve a CID from local candidate seeds or an explicit bootstrap candidate.
- [ ] Exact success is returned only after the target candidate completes a Part 2b expected-key query and its derived CID equals the requested CID.
- [ ] Every queried hop is represented by a verified responder; returned neighbor hints are not transitively trusted.
- [ ] Next-hop selection explicitly favors the direction appropriate to positive-offset routing and prevents invalid overshoot.
- [ ] Every hop makes strict ring progress or the lookup terminates.
- [ ] CID/identity/address visited sets prevent loops and repeated queries.
- [ ] Hop, frontier, response-byte, timeout, and parallelism limits bound resource use.
- [ ] Sparse, stale, unreachable, malformed, and malicious candidate responses terminate predictably.
- [ ] With no usable seed, lookup returns `NO_ROUTE` without depending on Part 4.
- [ ] Lookup does not schedule maintenance, fetch stats, or mutate pool/retained state.

---
## ✦ IMPLEMENTATION DETAILS (for the implementation agent)
---

## 8. Affected files / classes

- New `src/main/java/com/myster/threedns/ThreeDnsLookup.java` - asynchronous iterative resolver.
- New `src/main/java/com/myster/threedns/ThreeDnsLookupResult.java` - immutable result/status and verified exact/closest information.
- Optional new package-private `ThreeDnsLookupFrontier` - deterministic candidate scoring/deduplication if it keeps orchestration testable.
- `src/main/java/com/myster/tracker/Tracker.java` - provide bounded immutable `ThreeDnsAddressCandidate` seed snapshots, including address and public key, if existing accessors are insufficient.
- Focused tests under `src/test/java/com/myster/threedns`.

## 9. Step-by-step implementation

1. Finalize the public async method/result shape against Part 2b's `VerifiedThreeDnsPeer`. Keep limits, seed provider, peer client, executor/invoker, and time source injectable.
2. Implement a pure candidate scorer using `ServerCid` ring comparisons: exact first, then strict predecessor-side progress toward the target for positive routing, with deterministic tie-breaking. Recompute placement from each candidate's derived CID rather than trusting response groups.
3. Add or adapt a tracker seed accessor that snapshots currently usable retained/pool servers as `ThreeDnsAddressCandidate` values with an address and public key. Allow an explicit caller-supplied candidate for bootstrap/testing.
4. Track visited CIDs, identities, and addresses; maintain a bounded priority frontier of untrusted candidates and bounded closest verified diagnostics.
5. Query the best candidate once through Part 2b `ThreeDnsPeerClient.findClosest(...)`. Record its verified responder and enqueue useful returned hints only after local derivation, deduplication, and progress checks.
6. Complete exact only when the verified responder's CID equals the target. Otherwise continue only on strict progress and preserve the best verified peer allowed by the result contract.
7. Implement cancellation, timeouts, terminal statuses, limits, and safe handling of late asynchronous callbacks.
8. Keep pool onboarding and retained-list mutation out of the resolver; expose enough verified result data for Part 4 to perform those operations.
9. Write `docs/impl_summary/myster-3dns-part-3.md` after implementation.

## 10. Tests to write

- Pure scoring tests across ordinary and wraparound targets, including predecessor preference, local recomputation instead of group trust, and overshoot rejection.
- Exact candidate tests proving an unverified exact hint does not complete, a verified matching responder does, and a wrong-key/mismatched CID does not.
- Multi-hop convergence tests, sparse network termination, dead intermediate fallback, duplicate/cycle rejection, malicious non-progress responses, and address alternates.
- Seed tests for retained candidates, known-pool fallback, explicit bootstrap candidate, and empty `NO_ROUTE` behavior without Part 4.
- Trust-boundary tests showing a verified responder's returned candidates remain unverified until queried.
- Limit tests for hops, frontier size, bytes, timeout, late completion, and cancellation.
- Integration smoke test with at least three local nodes where one node knows only a seed and resolves another node's CID.

## 11. Docs / Javadoc to update

- Update `docs/design/Myster 3DNS.md` with the finalized resolution algorithm, verified-peer boundary, and public result semantics.
- Javadoc the trust/progress guarantees on the resolver and result types.
- Document that active routing-table maintenance is a Part 4 availability optimization, not a lookup prerequisite.
- Add `docs/impl_summary/myster-3dns-part-3.md` during implementation.
