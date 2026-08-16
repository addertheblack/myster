# Implementation Summary: Bidirectional Server Stats Exchange

Authoritative plan: [Bidirectional Server Stats Exchange](../plans/bidirectional-server-stats.md).

## What was implemented

UDP transaction `102` provides a bidirectional server-stats exchange. Normal
server-pool refresh uses the stats-selection wrapper, both `101` and `102`
remain registered, complete cards advertise identity and port, and the
responder passes the requester's corrected address/CID association into the
pool for expected-key verification before onboarding.

## Files changed

- `BidirectionalServerStatsDatagramClient` builds one complete request-card
  form. Unexpected pre-initialization use is an illegal state, and serialization
  failures never produce empty payloads.
- `StandardDatagramClientImpl` allows request codecs to declare `IOException`.
  Datagram serializers and the encryption decorator now preserve that checked
  contract instead of translating I/O failures into runtime exceptions.
- `MysterDatagramImpl` selects legacy `101` while any shared local file list is
  uninitialized and `102` afterward. Expected request-serialization I/O failures
  become failed `PromiseFuture` results before the transaction manager sends a
  packet; unexpected runtime failures propagate. No `UncheckedIOException` is
  used.
- `BidirectionalServerStatsDatagramServer` validates ports and identities,
  checks authenticated caller-CID consistency, suggests corrected
  address/identity pairs, and returns full or identity-bearing minimal stats.
- `ServerStats` owns the shared minimal-response builder.
- `MysterServerPool` and `MysterServerPoolImpl` carry untrusted address/identity
  hints through an expected-key stats refresh and require an exact returned
  `/Identity` match before normal onboarding.
- `TestBidirectionalServerStatsDatagramClient` and
  `TestBidirectionalServerStatsDatagramProtocol` provide focused codec,
  initialization-selection, validation, round-trip, response, and encrypted
  wrapper coverage.
- `TestMysterServerPoolImpl` verifies expected-key propagation, identity
  mismatch rejection, and the absence of a remote-failure `102` to `101` retry.
- `TestIdentityTracker` documents the current replacement behavior when two
  identities are added for the same exact address.
- [Myster Bidirectional Server Stats Protocol](../design/Myster%20Bidirectional%20Server%20Stats%20Protocol.md)
  documents the current wire, initialization, and trust contracts.

## Key design decisions

The public key in an incoming card is an untrusted hint. The server derives its
CID but does not install the request payload directly into trusted pool state.
Instead, the pool encrypts a callback stats request to that exact key and
compares the response identity byte for byte. This carries the address/CID pair
through discovery without letting a plaintext sender create a trusted
association by assertion alone.

The client does not construct partial transaction `102` cards while file lists
are indexing. The wrapper selects transaction `101` before all shared lists are
initialized and `102` afterward. If the `102` codec still receives
`NotInitializedException`, its precondition changed unexpectedly and the call
fails as an illegal state. This startup selection is not a compatibility
fallback: a sent `102` is never retried as `101` after remote failure.

Cards without `/Identity` remain valid for anonymous address discovery and use
the address-only pool suggestion path. `/Port` is mandatory because the UDP
source port may be ephemeral or NAT-rewritten. A responder that is still
indexing may return a minimal `102` response with its port and available name
and identity.

## Deviations from the original baseline

The pre-existing implementation called only `suggestAddress(address)` and
ignored `/Identity`. Completion added an address/identity overload and
expected-key verification. It also rejected unsafe ports and identities,
stopped returning empty request payloads, and added focused tests.

An initial completion used partial transaction `102` request cards while the
file manager was uninitialized. The final implementation instead selects `101`
before initialization and gives the `102` codec a single complete-card path.

Focused server protocol tests live in `com.myster.transaction` because
`Transaction` intentionally exposes its packet-construction test seam to that
package.

## Javadoc and design documentation

Javadoc describes the complete-card precondition, pre-initialization `101`
selection, address/identity suggestion contract, expected-key operation, and
serialization failure behavior. The design document records the MessagePak
wire fields, NAT address correction, trust boundary, encrypted caller-CID
check, initialization policy, registration, and failure policy.

## Tests

The full Maven suite passes: 472 tests, 0 failures, 0 errors, and 0 skipped.

Focused verification covers:

- Complete client cards, response decoding, and transaction code `102`.
- Selection of `101` before local file-list initialization and `102` afterward.
- Illegal direct use of the `102` codec before initialization.
- Unexpected initialization/build runtime failures propagating without network
  sends.
- Corrected source-IP/advertised-port pairing with the advertised identity.
- Missing, negative, zero, oversized, malformed, and invalid-identity cards.
- Authenticated caller-CID versus advertised-identity mismatch.
- Anonymous address-only cards and full/minimal server responses.
- Plaintext and encrypted transaction `102` round trips.
- Independent legacy `101` and bidirectional `102` codes.
- Pool expected-key propagation, mismatch rejection, and no remote-failure
  fallback to `101`.
- Current same-address/different-CID replacement behavior in `IdentityTracker`.

## Known issues and follow-up work

`IdentityTracker` remains one-to-one in the address-to-identity direction:
adding another CID for the same exact IP/port replaces the first association,
and `getIdentity(address)` cannot distinguish multiple identities. The pool's
in-flight refresh map is also keyed only by address. The bidirectional-stats flow now
plumbs and verifies the explicit address/CID pair, but retaining multiple CIDs
on one exact socket address requires a separate tracker/API redesign.

Metrics or a user-facing feature flag may be added later, but neither is
required by the protocol plan.
