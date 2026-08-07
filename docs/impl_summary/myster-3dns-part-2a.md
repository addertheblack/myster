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
- Added `MysterServerPool.validateCandidate(...)`. It contacts the advertised address using the
  expected key, checks the returned server-stats identity, onboards matching servers through the
  normal stats path, and converts failed validation into an empty result.
- Coalesced concurrent validation attempts for the same address/public-key pair while keeping
  validation work separate from the normal server-refresh in-flight map.

This part intentionally does not implement table maintenance/bootstrap or iterative CID lookup;
those remain Parts 2b and 3.

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
- `src/main/java/com/myster/tracker/MysterServerPool.java`
- `src/main/java/com/myster/tracker/MysterServerPoolImpl.java`
- `src/test/java/com/myster/net/datagram/client/TestMysterDatagramExpectedKey.java`
- `src/test/java/com/myster/threedns/TestThreeDnsServerList.java`
- `src/test/java/com/myster/tracker/TestMysterServerPoolImpl.java`
- `src/test/java/com/myster/transaction/TestFindClosestDatagramProtocol.java`
- `docs/design/Myster 3DNS.md`

## Key decisions

- Candidate validation uses the existing server-stats datagram transaction through encrypted MSD.
  Successfully processing the request proves possession of the private key corresponding to the
  expected public key, and the `/Identity` value provides an explicit identity consistency check.
- A cached server is accepted immediately only when the candidate address itself is already bound
  to the expected identity and the server is currently usable. Knowing the same public key at a
  different address does not validate a newly advertised address.
- The server snapshots usable addresses while encoding each response and omits candidates that are
  down or no longer have an up address.
- Malformed candidates invalidate the complete response instead of yielding a partial candidate
  set. This keeps callers from accidentally treating attacker-controlled partial data as complete.
- Wire addresses must be literal IPv4 or IPv6 text and ports must be in `1..65535`; decoding never
  performs a DNS lookup from untrusted response data.

## Deviations and clarifications

- The plan left the response budget configurable by implementation; this implementation fixes it at
  16 KiB for both encoding and decoding.
- The expected-key hook is implemented on encrypted UDP server stats rather than adding a separate
  TLS validation path. This uses the normal Myster request stack and supplies the required
  proof-of-key-possession property.
- Literal-IP validation is stricter than the minimum wire description. Existing `MysterAddress`
  construction can resolve hostnames, so this restriction prevents untrusted 3DNS responses from
  triggering local resolver lookups.

## Documentation

- Updated `docs/design/Myster 3DNS.md` with the final transaction code, wire schema, limits,
  candidate validation path, and expected-key behavior.
- Added or updated Javadoc for the public candidate models, datagram API, parameter option, and
  server-pool validation contract.
- The project-wide Javadoc goal reaches source analysis but currently fails on 27 pre-existing
  errors in unrelated classes, including broken `@see` references, malformed HTML, and invalid
  tags. No reported Javadoc error is in a Part 2a file.

## Tests run

- `mvn -q -DskipTests compile` passed.
- `mvn -q -Djava.awt.headless=true -Dtest=TestFindClosestDatagramProtocol,TestMysterDatagramExpectedKey,TestMysterServerPoolImpl,TestThreeDnsServerList test`
  passed: 28 tests, zero failures and zero errors.
- The full headless suite ran 428 tests with zero assertion failures and 10 environment-related
  errors: three UDP tests could not bind sockets in the sandbox, and seven multi-source download
  tests could not write under `~/Library/Application Support/Myster/Incoming`. All new and focused
  Part 2a tests passed during that run.
- `mvn -q -Djava.awt.headless=true javadoc:javadoc` was attempted and produced the unrelated
  project-wide errors described above.

## Follow-up

- [Part 2b](../plans/myster-3dns-part-2b.md) owns highly jittered hourly maintenance, immediate
  repair of damaged entries, and bootstrap behavior.
- [Part 3](../plans/myster-3dns-part-3.md) owns iterative CID resolution and direction-aware query
  selection.
- The legacy project-wide Javadoc errors and sandbox-dependent full-suite tests remain outside the
  scope of Part 2a.
