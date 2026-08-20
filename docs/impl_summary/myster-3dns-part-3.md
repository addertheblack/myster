# Myster 3DNS Part 3 Implementation Summary

## What was implemented

Implemented the bounded asynchronous CID resolver described by
`docs/plans/myster-3dns-part-3.md`:

- Added `ThreeDnsLookup.resolve(ServerCid)`, which automatically snapshots target-specific seeds
  from `Tracker`, queries up to two peers concurrently through the Part 2b expected-key boundary,
  and follows independently verified hints until it reaches the target or a bounded terminal state.
- Added `ThreeDnsLookupResult` with exact, closest, no-route, query-limit, and deadline outcomes.
  Cancellation remains cancellation of the returned `PromiseFuture`.
- Added a deterministic frontier that scores positive-ring predecessor distance, treats wire groups
  as advisory, prefers exact candidates, enforces strict progress after verification, deduplicates
  identity/address pairs while allowing one endpoint under distinct identities/CIDs, permits one
  alternate address, and retains only the closest 64 queued candidates. Its comparison method is
  passed directly at use sites rather than retained as pseudo-state.
- Added a pool-backed Tracker candidate snapshot that works without a local retained 3DNS list and
  performs no network I/O.
- Added deadline, query-count, concurrency, alternate-address, response-data, and cancellation
  bounds. Terminal completion cancels owned work and ignores late callbacks.
- Refactored lookup execution onto an invoker-confined actor state. `AsyncTaskTracker` now owns the
  changing set of peer-query promises, cancellation, and natural-exhaustion notification.
- Split peer-query completion into ordered result, exception, and final listeners. The final
  listener releases the in-flight slot and pumps replacement work after outcome handling.
- Hardened the concurrency library with any-thread `AsyncTaskTracker.cancel()`, natural-drain-only
  done listeners, and `PromiseFuture.withInvoker(...)` for safe actor-boundary adaptation.

## Files changed

- New `src/main/java/com/myster/threedns/ThreeDnsLookup.java`
- New `src/main/java/com/myster/threedns/ThreeDnsLookupFrontier.java`
- New `src/main/java/com/myster/threedns/ThreeDnsLookupResult.java`
- New `src/main/java/com/myster/threedns/ThreeDnsSeedProvider.java`
- `src/main/java/com/myster/tracker/Tracker.java`
- `src/main/java/com/general/thread/AsyncTaskTracker.java`
- `src/main/java/com/general/thread/PromiseFuture.java`
- `src/main/java/com/general/thread/PromiseFutureImpl.java`
- `src/main/java/com/general/thread/PromiseFutureList.java`
- `src/main/java/com/general/thread/PromiseFutureListImpl.java`
- `src/main/java/com/myster/search/AsyncNetworkCrawler.java`
- `src/main/java/com/myster/search/MultiSourceHashSearch.java`
- Existing 3DNS server, tracker model/pool, datagram-server, and tracker UI integration files
- New `src/test/java/com/myster/threedns/TestThreeDnsLookup.java`
- New `src/test/java/com/myster/threedns/TestThreeDnsLookupFrontier.java`
- New `src/test/java/com/myster/tracker/TestTrackerThreeDnsCandidates.java`
- New `src/test/java/com/general/thread/TestAsyncTaskTracker.java`
- `src/test/java/com/general/thread/TestCancellationTracking.java`
- New `src/test/java/com/general/thread/TestPromiseFuture.java`
- Existing 3DNS server-list and datagram-protocol tests
- `docs/design/Myster 3DNS.md`
- `docs/conventions/myster-coding-conventions.md`

## Key implementation decisions

- Each lookup uses a small actor-like `LookupState`. Initial seeding, ordinary query callbacks,
  frontier mutation, pumping, deadline decisions, and final result selection all run on one shared
  lookup invoker.
- `AsyncTaskTracker` replaces the manual future-to-candidate map and cancellation loop. Query
  callbacks capture their candidate, while a separate `activeQueries` count retains the explicit
  two-query concurrency bound.
- The production deadline uses one shared daemon scheduled executor. Tests inject a manual
  scheduler, so deadline behavior requires no sleeping. Deadline callbacks enter the lookup
  invoker but are not counted as child search work.
- The query limit counts launched queries. Already-launched queries are allowed to finish; the
  resolver reports `QUERY_LIMIT_REACHED` only when eligible work remains after the final permitted
  attempts settle.
- Returned hints from slower in-flight queries are still considered, but only an improving verified
  responder changes the closest-known peer and only strict-progress hints can launch afterward.
- An address is queried under at most one advertised identity per lookup. This prevents repeated or
  conflicting address claims from consuming the bounded query budget.
- Promise cancellation means that a computation is moot for subsequent listeners; it is not an
  immutable terminal compare-and-set. `PromiseFuture.withInvoker(...)` may therefore return the
  same future, assign its first invoker, or return a cancellation-linked wrapper when preserving a
  different existing invoker.
- Expected peer/network failures remain candidate failures. Unexpected runtime exceptions inside
  the resolver indicate framework or implementation bugs and propagate as panics; the resolver
  does not convert them into failed outer promises.

## Deviations and clarifications

- The optional frontier extraction from the plan was used because it makes distance ordering,
  deduplication, alternate-address behavior, and the 64-entry bound independently testable.
- The initial implementation used explicit synchronization, but the final implementation uses the
  repository's existing `AsyncTaskTracker` and invoker-confinement pattern. This removes the manual
  in-flight future map and makes the intended actor boundary explicit.
- The multi-hop smoke test models three local peers deterministically through the query seam rather
  than opening real UDP sockets. Existing Part 2a/2b tests separately cover protocol round trips,
  expected-key encryption, responder verification, and cancellation. A live loopback multi-node
  test remains useful if a reusable node harness is added later.

## Documentation and Javadoc

- Updated `docs/design/Myster 3DNS.md` to describe automatic pool-backed seeding, direction-neutral
  positive-ring scoring, strict verified progress, resource bounds, cancellation, and result
  statuses.
- Added contract Javadoc to `ThreeDnsLookup`, `ThreeDnsLookupResult`, and
  `Tracker.getThreeDnsCandidatesForTarget(...)`.
- Expanded `PromiseFuture` and `AsyncTaskTracker` Javadoc with moot-cancellation semantics,
  any-thread tracker cancellation, natural exhaustion, zero-task behavior, synchronous callback
  scope, and the conditional-allocation `withInvoker(...)` contract.
- Corrected and renamed `PromiseFuture.addCancelListener(...)`; cancellation listeners now run for
  cancellation rather than exception outcomes.
- Documented and tested registration-order dispatch for ordinary `PromiseFuture` listeners,
  including the fact that a finally listener runs at its registration position.
- Added the invoker-confined asynchronous-state convention to
  `docs/conventions/myster-coding-conventions.md`.
- `mvn -q -DskipTests javadoc:javadoc` still fails on 14 existing errors in unrelated source files.
  None is caused by Javadoc added or edited for this implementation.

## Verification

- `mvn -q -Djava.awt.headless=true -Dtest=TestThreeDnsLookupFrontier,TestThreeDnsLookup test`
  passed.
- `mvn -q -Djava.awt.headless=true -Dtest=TestPromiseFuture,TestCancellationTracking,TestAsyncTaskTracker,TestThreeDnsLookup test`
  passed.
- `mvn -q -Djava.awt.headless=true test` passed: 460 tests, zero failures, zero errors, and zero
  skipped.
- `mvn -q -DskipTests compile` passed.
- `git diff --check` passed.

## Known follow-up

- Part 4 can construct `ThreeDnsLookup` from `Tracker` and `ThreeDnsPeerClient`, then securely
  onboard selected verified peers through its expected-key stats flow.
- Add a real loopback three-node resolver test when the project has a reusable lifecycle harness
  for multiple encrypted datagram nodes.
