# Myster 3DNS - Part 3: Iterative CID Resolution

Prerequisites:

- [Part 2a: FIND_CLOSEST Protocol and Candidate Validation](myster-3dns-part-2a.md)
- [Part 2b: Routing-Table Maintenance and Bootstrap](myster-3dns-part-2b.md)

> Preliminary plan: finalize routing limits and caller API after Parts 2a and 2b establish real protocol behavior.

## 1. Summary

Implement the public lookup operation that resolves a target CID by iteratively querying validated 3DNS peers, selecting direction-appropriate candidates, and returning either the validated exact server or an explicit bounded failure/closest-known result.

## 2. Non-goals

- Do not change the `FIND_CLOSEST` wire format or transaction code.
- Do not own periodic routing-table maintenance.
- Do not claim success from an unvalidated public-key/address hint.
- Do not integrate the resolver into private-type onramps or UI in this milestone.
- Do not add general DHT values, caching policy, reputation, or Byzantine consensus.

## 3. Assumptions & open questions

- The routing table uses positive power-of-two offsets. Resolution therefore follows a monotonic positive-ring route and normally approaches the target from its LEFT/predecessor side; exact is preferred first.
- Both wire sides remain available for resilience and future routing policies. The next-hop selector must explicitly score the appropriate side rather than depend on response ordering.
- Every queried address is contacted with its candidate public key as the expected identity. A peer that can decrypt and answer the request proves possession; final exact success also requires `generateCid(validatedKey) == target`.
- Open question for finalization: whether the public result should return only exact/not-found or also expose closest validated predecessor/successor diagnostics. Preliminary recommendation: a structured result with status and closest-known candidates.
- Open question for finalization: tune hop, queue, parallelism, and byte budgets after Part 2a testing. Preliminary values are 16 hops, queue 64, per-side limit 2, and low parallelism.

## 4. Proposed design

`ThreeDnsLookup.resolve(Cid128)` starts from tracker/pool seeds and maintains a bounded frontier of validated or expected-key-address candidates. It queries the best unvisited peer for the target, derives CIDs from every returned key, rejects malformed/non-progressing hints, validates useful candidates, and repeats.

The selector is direction-aware. With positive-offset routing, exact candidates win; otherwise candidates on the LEFT/predecessor side of the target are favored, ordered by predecessor distance. A candidate is eligible as a next hop only if it makes strict positive-ring progress from the current best position toward the target. RIGHT/successor results are retained as fallback/diagnostic information but cannot silently cause an overshoot or a loop.

Visited identities and addresses are tracked separately. Exact resolution completes only after expected-key proof and pool validation. Exhaustion, no strict progress, limits, timeout, cancellation, and malformed/oversized responses produce explicit non-success statuses.

## 5. Architecture connections

| New / changed thing | Owned / created by | Called / used by | Connects to (existing) |
|---|---|---|---|
| Resolver API | `ThreeDnsLookup` | Future CID-based features | `MysterProtocol`, tracker seeds, pool |
| Lookup result | `ThreeDnsLookupResult` | Resolver callers | validated `MysterServer`, target/closest CIDs, status |
| Direction-aware frontier | Lookup-internal model | Iteration loop | `Cid128` ring distances, Part 2a candidate groups |
| Candidate proof | Part 2a validation API | Resolver | expected-key UDP/TLS and normal pool onboarding |

No new wire or disk format is introduced. Part 3 consumes transaction `303` and the local retained state produced by Parts 1/2b.

## 6. Key decisions & edge cases

- Exact wire match is only a hint until key possession and target CID are validated.
- For the current positive-offset topology, exact then LEFT/predecessor is the normal next-hop preference.
- Strict progress is measured with ring arithmetic, not ordinary signed/numeric subtraction.
- A node or address is never queried twice in one lookup.
- Multiple addresses for one identity do not create multiple logical hops, but an alternate address may be tried after a transport failure within a bounded policy.
- Malicious peers can return unrelated, duplicate, cyclic, or fake closer candidates; all are constrained by proof, visited sets, progress rules, and budgets.
- Cancellation must stop new work and ignore late completions.
- The resolver distinguishes `EXACT_VALIDATED`, `CLOSEST_KNOWN`, `NO_ROUTE`, `LIMIT_REACHED`, `CANCELLED`, and `FAILED` (names may be refined during finalization).

## 7. Acceptance criteria

- [ ] A caller can asynchronously resolve a CID from local 3DNS seeds.
- [ ] Exact success is returned only after the address proves the expected public key and that key derives the requested CID.
- [ ] Next-hop selection explicitly favors the direction appropriate to positive-offset routing and prevents target overshoot.
- [ ] Every hop makes strict ring progress or the lookup terminates.
- [ ] CID/address visited sets prevent loops and repeated queries.
- [ ] Hop, frontier, response-byte, timeout, and parallelism limits bound resource use.
- [ ] Sparse, stale, unreachable, malformed, and malicious candidate responses terminate predictably.
- [ ] Lookup does not own or schedule hourly maintenance.

---
## ✦ IMPLEMENTATION DETAILS (for the implementation agent)
---

## 8. Affected files / classes

- New `src/main/java/com/myster/threedns/ThreeDnsLookup.java` - asynchronous iterative resolver.
- New `src/main/java/com/myster/threedns/ThreeDnsLookupResult.java` - immutable result/status and validated closest information.
- Optional new package-private `ThreeDnsLookupFrontier` - deterministic candidate scoring/deduplication if it keeps orchestration testable.
- `src/main/java/com/myster/tracker/Tracker.java` - provide bounded seed snapshots if existing accessors are insufficient.
- Focused tests under `src/test/java/com/myster/threedns`.

## 9. Step-by-step implementation

1. Finalize the public async method/result shape and injectable limits after Parts 2a/2b.
2. Implement a pure candidate scorer using `Cid128` ring comparisons: exact first, then strict predecessor-side progress toward the target for positive routing, with deterministic tie-breaking.
3. Seed from retained usable 3DNS entries, local pool closest results, and an optional explicit bootstrap candidate.
4. Track visited CIDs, identities, and addresses; maintain a bounded priority frontier.
5. Query the best candidate with `findClosest(...)` using its expected public key. Validate useful returned candidates before promotion to trusted/validated state.
6. Complete exact only when the validated key derives the target CID. Otherwise continue only on strict progress.
7. Implement cancellation, timeouts, terminal statuses, limits, and safe handling of late async callbacks.
8. Write `docs/impl_summary/myster-3dns-part3.md` after implementation.

## 10. Tests to write

- Pure scoring tests across ordinary and wraparound targets, including predecessor preference and overshoot rejection.
- Exact candidate tests for matching key, mismatched key, and same CID claim with wrong key.
- Multi-hop convergence tests, sparse network termination, dead intermediate fallback, duplicate/cycle rejection, malicious non-progress responses, and address alternates.
- Limit tests for hops, frontier size, bytes, timeout, and cancellation.
- Integration smoke test with at least three local nodes where one node knows only a seed and resolves another node's CID.

## 11. Docs / Javadoc to update

- Update `docs/design/Myster 3DNS.md` with the finalized resolution algorithm and public result semantics.
- Javadoc the trust/progress guarantees on the resolver and result types.
- Add `docs/impl_summary/myster-3dns-part3.md` during implementation.
