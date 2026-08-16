# Myster 3DNS Part 2b Implementation Summary

## What changed

Implemented the candidate identity-verification boundary from
`docs/plans/myster-3dns-part-2b.md`:

- Added `ThreeDnsPeerClient`, which performs one useful expected-key UDP `FIND_CLOSEST` query and
  promotes only the responding peer after the encrypted reply authenticates.
- Added immutable `VerifiedThreeDnsPeer` and `ThreeDnsVerifiedQueryResult` types. Only the responder
  is verified; candidates returned inside its response remain untrusted wire hints.
- Made wrapper cancellation cancel the underlying datagram transaction and reject late completion.
- Renamed the shared cancellation-tracking API from `registerDependentTask(...)` to
  `trackForCancellation(...)`, clarifying that it neither observes nor forwards task completion.
- Tightened `FindClosestDatagramClient` so an exact-group candidate is rejected unless its locally
  derived CID equals the request target.
- Kept pool mutation, stats fetch, persistence, caching, retry, iterative lookup, and maintenance out
  of Part 2b.

## Files changed

- New `src/main/java/com/myster/threedns/ThreeDnsPeerClient.java`
- New `src/main/java/com/myster/threedns/VerifiedThreeDnsPeer.java`
- New `src/main/java/com/myster/threedns/ThreeDnsVerifiedQueryResult.java`
- `src/main/java/com/myster/threedns/ThreeDnsAddressCandidate.java`
- `src/main/java/com/myster/net/client/MysterDatagram.java`
- `src/main/java/com/myster/net/datagram/client/FindClosestDatagramClient.java`
- `src/main/java/com/general/thread/TaskTracker.java`
- `src/main/java/com/general/thread/AsyncContext.java`
- `src/main/java/com/general/thread/AsyncContextList.java`
- `src/main/java/com/general/thread/AsyncTaskTracker.java`
- `src/main/java/com/general/thread/SimpleTaskTracker.java`
- `src/main/java/com/general/thread/PromiseFuture.java`
- `src/main/java/com/general/thread/PromiseFutureImpl.java`
- `src/main/java/com/general/thread/PromiseFutureListImpl.java`
- `src/main/java/com/general/thread/PromiseFutures.java`
- `src/main/java/com/myster/search/MultiSourceHashSearch.java`
- `src/main/java/com/myster/net/stream/client/msdownload/MSPartialFile.java`
- New `src/test/java/com/myster/threedns/TestThreeDnsPeerClient.java`
- New `src/test/java/com/general/thread/TestCancellationTracking.java`
- `src/test/java/com/myster/net/datagram/client/TestMysterDatagramExpectedKey.java`
- `src/test/java/com/myster/net/datagram/TestDatagramEncryptUtil.java`
- `src/test/java/com/myster/transaction/TestFindClosestDatagramProtocol.java`
- `docs/design/Myster 3DNS.md`

## Key design decisions

- Verification is fused with `FIND_CLOSEST`; there is no separate proof ping or duplicate network
  round trip.
- `VerifiedThreeDnsPeer` has no public constructor. Package code can read it, while normal wire
  decoding cannot directly promote `ThreeDnsAddressCandidate`.
- Verification is operation-scoped evidence of address/key possession. It does not assert honesty,
  permanent liveness, or safety of later unauthenticated traffic.
- `ThreeDnsVerifiedQueryResult` deliberately preserves the untrusted candidate-set type rather than
  implying that a verified responder transitively verified its claims.
- The wrapper uses `AsyncContext.trackForCancellation(...)` rather than `mapAsync(...)`, because
  cancelling a mapped future would otherwise leave the UDP request running.
- `trackForCancellation(...)` expresses one-way cancellation ownership only. Completion propagation
  remains explicit through callbacks such as `addSynchronousCallback(...)`.
- Exact-group consistency is enforced at the codec boundary because the request target is already
  available there; Part 3 must still query the exact hint before declaring success.

## Deviations and clarifications

- The immutable result types are final classes rather than records so their constructors can remain
  package-private. Public record canonical constructors could not enforce the promotion boundary.
- No TCP API was added. `TLSSocket` already has a low-level expected-key check, but the normal stream
  factory does not expose it; the verified-peer value itself remains transport-neutral.
- No general in-flight deduplication was added because Part 2b has only one concrete query caller and
  no demonstrated concurrent sharing requirement.
- The cancellation API rename was a post-implementation review improvement requested by the owner;
  it changes naming and documentation without changing cancellation behavior.

## Documentation

- Updated the live 3DNS design with the verified-responder/untrusted-hints distinction, exact-claim
  validation, and cancellation behavior.
- Updated Javadoc on the new public types, the candidate wire model, and `MysterDatagram.findClosest`.
- Documented `PromiseFuture.mapAsync(...)` cancellation semantics directly on the API and documented
  the cancellation-only contract of `TaskTracker.trackForCancellation(...)` in its Javadoc.

## Tests run

- `JAVA_HOME=/opt/local/Library/Java/JavaVirtualMachines/jdk-26-macports.jdk/Contents/Home mvn -q -DskipTests compile`
  passed.
- `JAVA_HOME=/opt/local/Library/Java/JavaVirtualMachines/jdk-26-macports.jdk/Contents/Home mvn -q -Djava.awt.headless=true test`
  passed: 413 tests, zero failures, zero errors, and zero skipped.
- `JAVA_HOME=/opt/local/Library/Java/JavaVirtualMachines/jdk-26-macports.jdk/Contents/Home mvn -q -DskipTests javadoc:javadoc`
  reached source analysis but failed on 14 existing errors in unrelated files, including malformed
  HTML, unknown tags, heading order, and invalid class-level `@throws`. No Javadoc error was reported
  in a Part 2b source file.
- After the cancellation API rename,
  `JAVA_HOME=/opt/local/Library/Java/JavaVirtualMachines/jdk-26-macports.jdk/Contents/Home mvn -q -Dtest=TestCancellationTracking,TestThreeDnsPeerClient test`
  passed.

## Follow-up

- [Part 3](../plans/myster-3dns-part-3.md) can build its frontier from untrusted candidates and use
  `ThreeDnsPeerClient` once per queried hop.
- [Part 4](../plans/myster-3dns-part-4.md) owns expected-key stats comparison and pool onboarding for
  discoveries that need persistent retention.
