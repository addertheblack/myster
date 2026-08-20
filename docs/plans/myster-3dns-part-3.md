# Myster 3DNS - Part 3: Iterative CID Resolution

Prerequisites and follow-on plan:

- [Part 2a: FIND_CLOSEST Protocol and Expected-Key Hook](myster-3dns-part-2a.md)
- [Part 2b: Candidate Identity Verification](myster-3dns-part-2b.md)
- [Part 4: Routing-Table Maintenance and Bootstrap](myster-3dns-part-4.md)

## 1. Summary

Implement the public asynchronous CID resolver that automatically obtains target-specific starting candidates from `Tracker`, iteratively queries them through the Part 2b identity-proof operation, and returns either the verified target peer or an explicit bounded closest/no-route result.

## 2. Non-goals

- Do not change the `FIND_CLOSEST` wire format, transaction code, encryption, or identity-proof mechanism.
- Do not require callers to select or pass seed candidates to the production resolver API.
- Do not schedule routing-table maintenance or bootstrap refresh; that is Part 4.
- Do not insert discoveries into the server pool or retained finger table.
- Do not fetch server stats or treat a verified peer as honest beyond possession of its key at the responding address.
- Do not integrate CID resolution into private-type onramps, UI, or other product features in this milestone.
- Do not add caching, reputation, Byzantine consensus, or a general DHT value layer.

## 3. Assumptions & open questions

- The production `resolve(ServerCid)` operation always auto-seeds from a target-specific immutable snapshot obtained through `Tracker`. Testability comes from injecting the seed provider into the resolver, not from requiring production callers to pass seeds.
- Tracker currently exposes identity-only target neighbors. Part 3 adds an address-bearing candidate accessor backed by `MysterServerPool.findClosestByCid(...)`, so it can use all currently usable public-key servers, including retained 3DNS entries, even when the optional local `ThreeDnsServerList` is unavailable.
- Tracker candidates are useful local hints, not proof for the current lookup. Every seed is queried through `ThreeDnsPeerClient` with its advertised key before it becomes a `VerifiedThreeDnsPeer`.
- Positive-ring progress is measured by `target.comparePredecessorDistance(...)`. Exact distance is zero. Wire `left`/`right` grouping is not trusted and does not itself decide eligibility; candidates from either group can make strict progress through wraparound.
- The public result remains structured because Part 4 needs the closest verified peer when an ideal finger target has no exact node. Exact-address callers use the verified exact peer's address and must not mistake a closest peer for the target.
- Cancellation is represented by cancellation of the returned `PromiseFuture`. Unexpected runtime implementation failures are programmer errors and propagate through the executing framework thread; they are not converted into lookup results or failed promise outcomes.
- Default resource policy is two candidates per response side, a 64-entry frontier, 32 total query attempts, two concurrent queries, at most two attempted addresses per identity, and a 60-second lookup deadline. These limits are injectable for deterministic tests and later tuning.
- Each accepted response is already limited to 16 KiB by Part 2a. The 32-query limit therefore bounds accepted response data to 512 KiB without adding a second raw-byte counter above the decoder.
- No architecture-blocking questions remain.

## 4. Proposed design

`ThreeDnsLookup.resolve(ServerCid)` first asks `Tracker` for exact, predecessor-side, and successor-side address/key candidates near the target. The resolver combines those immutable groups into one locally scored frontier; callers do not manage bootstrap candidates themselves.

The resolver prioritizes an exact derived CID, then orders all other candidates by positive-ring predecessor distance to the target with deterministic identity/address tie-breaking. Once at least one peer has been verified, newly launched non-exact queries must be strictly closer than the best verified peer. Candidates already in flight may finish out of order; their responders and returned hints are accepted only when they improve global lookup state.

At most two candidates are queried concurrently through `ThreeDnsPeerClient.findClosest(...)`. A successful query verifies only its responder. The resolver locally derives and scores every returned hint, deduplicates identity/address pairs, and adds bounded useful hints to the frontier. It completes exact only when the authenticated responder's own CID equals the requested target.

`ThreeDnsLookupResult` distinguishes verified exact success, verified closest exhaustion, no route, query-limit exhaustion, and deadline exhaustion. It exposes an exact peer only for exact success and may expose the closest verified peer for diagnostics and Part 4. Individual timeouts, wrong-key responses, malformed responses, and unreachable candidates are ordinary candidate failures; the resolver continues while another eligible candidate remains.

## 5. Architecture connections

The resolver sits above the tracker snapshot and Part 2b one-hop proof. Tracker supplies only the initial hints; after the call starts, network traversal owns its bounded frontier and does not mutate tracker state.

| New / changed thing | Owned / created by | Called / used by | Connects to (existing) |
|---|---|---|---|
| Auto-seeded resolver API | `ThreeDnsLookup` | CID-address callers, Part 4 | `Tracker`, `ThreeDnsPeerClient`, `PromiseFuture` |
| Address-bearing seed snapshot | `Tracker` | `ThreeDnsLookup.resolve(...)` | `MysterServerPool.findClosestByCid(...)`, cached up `MysterServer` addresses |
| Detailed terminal result | `ThreeDnsLookupResult` | Resolver callers and Part 4 | `VerifiedThreeDnsPeer`, target CID, terminal status |
| Bounded scored frontier | Lookup-internal model | Resolver coordinator | `ServerCid.comparePredecessorDistance(...)`, untrusted candidate sets |
| Per-hop identity proof | Part 2b `ThreeDnsPeerClient` | Resolver coordinator | expected-key UDP `FIND_CLOSEST` |

The data flow is: `resolve(target)` snapshots target-nearest candidates from Tracker; the resolver queries up to two through Part 2b; successful responders become operation-scoped verified peers; returned hints remain untrusted until separately queried; locally derived CID distance selects strict progress; the operation stops on the verified target or a bounded terminal condition.

There is no new wire or disk format. Part 3 consumes transaction `303` unchanged. The new Tracker snapshot is an in-process immutable value and performs no network I/O.

## 6. Key decisions & edge cases

- Auto-seeding is part of the resolver contract. A normal caller supplies only the target CID.
- The Tracker seed accessor uses the live pool-wide CID index rather than only the optional retained list. Retained entries are included through their pool references, while a client without a local 3DNS finger list can still resolve from other known usable servers.
- An exact local or remote hint never completes lookup. That candidate must answer an expected-key query, and the verified responder's locally derived CID must equal the target.
- Positive-ring predecessor distance is the sole progress metric. A blanket rejection of `right`/successor hints would incorrectly reject valid wraparound progress.
- Response group labels and ordering are advisory; all candidate CIDs and ordering are recomputed locally.
- The frontier deduplicates identity/address pairs. One identity is a single logical node, but one alternate address may be attempted after transport failure; successful verification suppresses all remaining addresses for that identity. The same endpoint remains eligible under a different identity/CID.
- A failed query does not become the progress baseline. Only verified responders update the closest-known position.
- A slower in-flight response may still contribute a better responder or hint, but it cannot regress the global closest peer or re-enqueue visited work.
- When exact success, deadline, query limit, or caller cancellation terminates the operation, all outstanding queries and the deadline task are cancelled. Late callbacks cannot replace the terminal result.
- Frontier exhaustion with a verified peer returns `CLOSEST_VERIFIED`; exhaustion without any verified peer returns `NO_ROUTE`.
- Reaching the query limit or deadline preserves any closest verified peer in the result. Cancellation remains cancellation of the future rather than a normal result.
- Individual network/protocol failures are recorded only as bounded diagnostics if useful for tests/logging; they do not retain arbitrary exception chains in the result.

## 7. Acceptance criteria

- [ ] A caller can asynchronously resolve a CID by passing only the target; the resolver automatically requests initial candidates from Tracker.
- [ ] Tracker returns an immutable target-specific seed snapshot containing public key, derived CID, and currently usable address for each candidate.
- [ ] Auto-seeding works from known usable pool servers even when the optional retained local 3DNS list is absent or empty.
- [ ] Every seed and returned hint remains untrusted until it completes a Part 2b expected-key query.
- [ ] Exact success is returned only when the authenticated responder's derived CID equals the requested target.
- [ ] Candidate selection recomputes positive-ring distance locally, accepts valid wraparound progress from either wire group, and never regresses the closest verified position.
- [ ] Duplicate identity/address pairs, cycles, non-progressing hints, and excessive alternate addresses are bounded and cannot loop; one address may remain eligible under distinct identities/CIDs.
- [ ] Individual dead, wrong-key, malformed, or malicious candidates do not prevent fallback to another eligible candidate.
- [ ] Query count, frontier size, concurrency, addresses per identity, accepted response data, and elapsed time are bounded.
- [ ] Caller cancellation and terminal completion stop outstanding queries and prevent late result changes.
- [ ] Results distinguish exact, closest, no-route, query-limit, and deadline outcomes without representing cancellation twice.
- [ ] Lookup does not fetch stats, mutate the pool/finger list, or schedule maintenance.

---
## ✦ IMPLEMENTATION DETAILS (for the implementation agent)
---

## 8. Affected files / classes

- New `src/main/java/com/myster/threedns/ThreeDnsLookup.java` - public auto-seeded asynchronous resolver and per-call coordinator.
- New `src/main/java/com/myster/threedns/ThreeDnsLookupResult.java` - immutable status, target, verified exact peer, and closest verified peer.
- New package-private `src/main/java/com/myster/threedns/ThreeDnsLookupFrontier.java` if extraction keeps scoring, deduplication, and bounds deterministic.
- New package-private `src/main/java/com/myster/threedns/ThreeDnsSeedProvider.java` - injectable target-to-candidate snapshot boundary used by tests and the Tracker-backed constructor.
- `src/main/java/com/myster/tracker/Tracker.java` - add an immutable address-bearing, target-specific seed accessor backed by the live pool CID index.
- New focused tests under `src/test/java/com/myster/threedns` plus Tracker seed-accessor coverage under `src/test/java/com/myster/tracker`.

## 9. Step-by-step implementation

1. Add `Tracker.getThreeDnsCandidatesForTarget(ServerCid target, int perSideLimit)` returning `ThreeDnsAddressCandidateSet`.
   - Call `pool.findClosestByCid(target, perSideLimit)` so resolution does not depend on the optional local `ThreeDnsServerList`.
   - Convert each identity to its cached up server and choose a currently up best address, falling back to the first up address as the existing datagram server does.
   - Preserve exact/left/right grouping, skip candidates that lose usability during snapshot construction, and return immutable groups.
   - Perform no refresh, stats query, or other I/O.
2. Add a small `ThreeDnsSeedProvider` abstraction whose production adapter calls the new Tracker method. The public `ThreeDnsLookup` constructor accepts `Tracker` and `ThreeDnsPeerClient`; a package-private constructor accepts the provider, limits, deadline scheduler, and any callback executor needed for deterministic tests.
3. Define `ThreeDnsLookupResult` with terminal statuses `EXACT_VERIFIED`, `CLOSEST_VERIFIED`, `NO_ROUTE`, `QUERY_LIMIT_REACHED`, and `DEADLINE_REACHED`.
   - Enforce invariants between status and optional peers.
   - Expose the exact peer/address only for verified exact success.
   - Allow limit/deadline results to retain the closest verified peer.
   - Do not add `CANCELLED` or general `FAILED` statuses. Use `PromiseFuture` cancellation for caller cancellation, treat expected remote failures as candidate failures, and let unexpected runtime implementation errors propagate.
4. Define injectable default limits: per-side seed/query limit `2`, frontier `64`, total query attempts `32`, in-flight queries `2`, addresses per identity `2`, and deadline `60` seconds. Validate custom limits at construction.
5. Implement a pure frontier/scoring component.
   - Merge exact, left, and right seed/response groups and ignore their labels after ingestion.
   - Derive every CID from its public key through `ThreeDnsAddressCandidate`.
   - Sort exact first, then by `target.comparePredecessorDistance(...)`, then deterministic public-key/address ordering.
   - Track queued, in-flight, failed-address, and verified identities separately; cap alternate addresses and retain only the closest 64 eligible candidates.
6. Implement the per-call coordinator returned by `resolve(target)`.
   - Snapshot Tracker seeds exactly once at call start.
   - Start up to two best eligible queries and replenish slots after each completion.
   - Track every in-flight query for caller cancellation and separately cancel all outstanding work on early exact/limit/deadline completion.
   - Serialize state transitions through one injected callback executor/invoker or an equivalently explicit synchronized coordinator; never mutate frontier state concurrently from transaction callback threads.
7. On successful `ThreeDnsPeerClient` completion, update the closest verified responder only when its positive-ring distance improves. If its CID equals the target, cancel other work and complete exact. Otherwise ingest its still-untrusted returned candidates and launch more strict progress.
8. On candidate timeout, wrong key, malformed response, or other transport failure, mark only that attempted identity/address failed and continue. Do not turn one candidate failure into whole-lookup exceptional completion. Cancellation means the query is moot and needs no outcome handler; final accounting still runs at its registered listener position.
9. Arm the overall deadline through an injectable scheduler. Caller cancellation, exact completion, query-limit completion, and deadline completion must cancel the timer and all outstanding queries. Actor serialization and promise cancellation prevent late outcome handlers from changing lookup state; rely on `AsyncContext` rejecting a second terminal result as a final defense.
10. Complete `CLOSEST_VERIFIED` when the frontier and in-flight set are empty after at least one verified response; otherwise complete `NO_ROUTE`. Complete the corresponding bounded status when query count or deadline ends the search.
11. Update the live 3DNS design and write `docs/impl_summary/myster-3dns-part-3.md` during implementation.

## 10. Tests to write

- Tracker seed tests: target is passed to `findClosestByCid`, exact/left/right identities become candidates with current up addresses, unusable races are skipped, groups are immutable, and a missing local `ThreeDnsServerList` does not suppress pool-backed seeds.
- Auto-seed API tests: `resolve(target)` requests Tracker seeds exactly once, callers pass no candidate list, and an empty snapshot completes `NO_ROUTE` without issuing a query.
- Pure scoring tests across ordinary and wraparound targets, including exact priority, deterministic ties, strict positive-ring progress, and a useful candidate originating in the wire `right` group.
- Exact tests proving an unverified exact seed/hint does not complete, a verified matching responder does, and wrong-key or mismatched identity responses cannot produce exact success.
- Multi-hop convergence tests with out-of-order completions, two-query concurrency, dead-candidate fallback, duplicate/cycle rejection, malicious non-progress hints, and one bounded alternate address.
- Trust-boundary tests showing that a verified responder's returned candidates remain unverified until separately queried.
- Terminal-result tests for `EXACT_VERIFIED`, `CLOSEST_VERIFIED`, `NO_ROUTE`, `QUERY_LIMIT_REACHED`, and `DEADLINE_REACHED`, including result invariants and retained closest diagnostics.
- Limit tests for frontier size, 32 attempted queries, two in-flight operations, two addresses per identity, and the derived 512 KiB maximum accepted response data.
- Cancellation tests proving caller cancellation and exact/deadline/limit completion cancel outstanding peer queries and prevent late callbacks from changing the result.
- Integration smoke test with at least three local nodes where the resolver knows only Tracker seeds and reaches a target through one intermediate peer.

## 11. Docs / Javadoc to update

- Update `docs/design/Myster 3DNS.md` with auto-seeding, positive-ring progress across both response groups, bounded concurrent traversal, and finalized result semantics.
- Javadoc `Tracker.getThreeDnsCandidatesForTarget(...)` as a synchronous immutable snapshot with no network I/O.
- Javadoc `ThreeDnsLookup` with automatic seed acquisition, trust boundaries, limits, cancellation, and normal candidate-failure behavior.
- Javadoc `ThreeDnsLookupResult` status invariants and the distinction between exact and closest addresses.
- Add `docs/impl_summary/myster-3dns-part-3.md` during implementation.
