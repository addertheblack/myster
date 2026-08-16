# Myster 3DNS Part 2a Implementation Summary

## What changed

Implemented the authenticated 3DNS `FIND_CLOSEST` protocol foundation from
`docs/plans/myster-3dns-part-2a.md`:

- Reserved UDP transaction code `303` and registered the server transaction at startup.
- Added a versioned request/response codec with explicit exact, left, and right candidate groups,
  a default per-side limit of two, a server-side maximum of four, and a 16 KiB response limit.
- Added immutable address-candidate models. Candidate CIDs are always derived locally from their
  encoded X.509 public keys and are never accepted from the wire.
- Added `MysterDatagram.findClosest(...)` and the corresponding client and server transaction
  implementations.
- Added an expected-server-public-key option to `ParamBuilder`. When present, datagram requests
  always use encrypted MSD with that exact key, bypassing the learned-key cache and plaintext
  policy without inserting the supplied key into the cache.
- Candidate verification orchestration was deliberately deferred until Part 2b could wrap the
  expected-key transport around a useful production query.

This part intentionally does not implement candidate verification orchestration, iterative CID
lookup, or table maintenance/bootstrap; those remain Parts 2b, 3, and 4 respectively.

## Files changed

- `src/main/java/com/myster/Myster.java`
- `src/main/java/com/myster/net/client/MysterDatagram.java`
- `src/main/java/com/myster/net/client/ParamBuilder.java`
- `src/main/java/com/myster/net/datagram/DatagramConstants.java`
- `src/main/java/com/myster/net/datagram/client/FindClosestDatagramClient.java`
- `src/main/java/com/myster/net/datagram/client/MysterDatagramImpl.java`
- `src/main/java/com/myster/net/server/datagram/FindClosestDatagramServer.java`
- `src/main/java/com/myster/threedns/ThreeDnsAddressCandidate.java`
- `src/main/java/com/myster/threedns/ThreeDnsAddressCandidateSet.java`
- `src/test/java/com/myster/net/datagram/client/TestMysterDatagramExpectedKey.java`
- `src/test/java/com/myster/transaction/TestFindClosestDatagramProtocol.java`
- `docs/design/Myster 3DNS.md`

## Key decisions

- The expected-key hook supplies the proof-of-key-possession primitive without committing Part 2a
  to a pool-level validation API that has no production caller.
- The server snapshots usable addresses while encoding each response and omits candidates that are
  down or no longer have an up address.
- Malformed candidates invalidate the complete response instead of yielding a partial candidate
  set. This keeps callers from accidentally treating attacker-controlled partial data as complete.
- Wire addresses must be literal IPv4 or IPv6 text and ports must be in `1..65535`; decoding never
  performs a DNS lookup from untrusted response data.

## Deviations and clarifications

- The plan left the response budget configurable by implementation; this implementation fixes it at
  16 KiB for both encoding and decoding.
- The expected-key hook uses encrypted UDP rather than adding a separate TLS path. It remains a
  transport primitive; Part 2b will fuse it with a useful `FIND_CLOSEST` query, while persistent
  stats onboarding belongs to Part 4.
- Literal-IP validation is stricter than the minimum wire description. Existing `MysterAddress`
  construction can resolve hostnames, so this restriction prevents untrusted 3DNS responses from
  triggering local resolver lookups.

## Documentation

- Updated `docs/design/Myster 3DNS.md` with the final transaction code, wire schema, limits,
  deferred-validation boundary, and expected-key behavior.
- Added or updated Javadoc for the public candidate models, datagram API, and parameter option.
- The project-wide Javadoc goal reaches source analysis but currently fails on 14 pre-existing
  errors in unrelated classes, including malformed HTML, invalid tags, and a class-level
  `@throws`. No reported Javadoc error is in a Part 2a file.

## Tests run

- `mvn -q -DskipTests compile` passed.
- `mvn -q -Djava.awt.headless=true -Dtest=TestFindClosestDatagramProtocol,TestMysterDatagramExpectedKey,TestMysterServerPoolImpl,TestThreeDnsServerList test`
  passed: 25 tests, zero failures and zero errors.
- The current full headless suite ran 393 tests with zero assertion failures and 13
  environment-related errors: three UDP tests could not bind sockets, seven multi-source download
  tests could not write under `~/Library/Application Support/Myster/Incoming`, and three custom-type
  tests could not synchronize macOS Preferences in the sandbox. All new and focused Part 2a tests
  passed during that run.
- `mvn -q -Djava.awt.headless=true javadoc:javadoc` was attempted and produced the unrelated
  project-wide errors described above.

## Follow-up

- [Part 2b](../plans/myster-3dns-part-2b.md) owns the reusable expected-key verified-query boundary.
- [Part 3](../plans/myster-3dns-part-3.md) owns iterative CID resolution and direction-aware query
  selection.
- [Part 4](../plans/myster-3dns-part-4.md) owns highly jittered maintenance, repair, bootstrap
  scheduling, and secure pool onboarding for retained discoveries.
- The legacy project-wide Javadoc errors and sandbox-dependent full-suite tests remain outside the
  scope of Part 2a.
