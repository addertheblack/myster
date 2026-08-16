# Myster Bidirectional Server Stats Protocol

## Purpose

The Myster bidirectional server-stats protocol is UDP transaction `102`. A requester sends
its local server stats and receives the responder's server stats in the same
transaction. The requester's advertised Myster port is used instead of its UDP
source port, which may be ephemeral or rewritten by NAT.

Legacy transaction `101` remains a one-way server-stats request. Normal pool
refresh uses `102` without retrying `101` when the remote server does not
support the newer operation.

## Wire contract

The request and successful response each contain one length-prefixed
MessagePak using the `ServerStats` field paths. `/Port` is required and must be
within `1..65535`. `/Identity` contains the encoded RSA public key when an
identity is available. Name, version, uptime, speed, and file counts are
included when available.

The requester sends transaction `102` only after all locally shared file lists
are initialized. Before that point, `MysterDatagramImpl` selects legacy
transaction `101`, which does not require a local card. If the transaction
`102` codec nevertheless encounters an uninitialized list, it reports an
illegal state rather than constructing a partial request.

A transaction `102` responder whose file lists are not initialized sends a
minimal response containing `/Port` plus available `/Identity` and
`/ServerName`. A malformed payload, invalid port, or malformed encoded identity
rejects the request without suggesting an address to the pool.

## Address and CID discovery

The responder combines the observed UDP source IP with the advertised `/Port`.
When `/Identity` is present, it derives the corresponding `ServerCid` and hands
the corrected address and `PublicKeyIdentity` to the address/identity overload
of `MysterServerPool.suggestAddress(...)`. An anonymous card uses the existing
address-only operation.

The pair is a discovery hint, not trusted pool state. `MysterServerPoolImpl`
performs a callback transaction `102` using
`ParamBuilder.withExpectedServerPublicKey(...)`. The request is encrypted to
the advertised key, and the returned `/Identity` must match that key byte for
byte before existing server creation/update and listener behavior runs.

`IdentityTracker` currently stores one identity per exact `MysterAddress`.
Adding a different CID at the same IP/port removes the previous association;
its `getIdentity(address)` API is singular and the pool's in-flight refreshes
are also keyed only by address. The bidirectional-stats path now carries an explicit
address/CID pair through verification, but simultaneous retention of multiple
CIDs at one exact socket address requires a separate tracker API/data-model
change.

If the original card arrived through the encrypted datagram wrapper with a
verified caller CID, the handler also requires that CID to equal the CID
derived from the advertised `/Identity`. A signed caller therefore cannot
advertise a different identity.

## Registration and lifecycle

`Myster` registers transaction handlers `101` and `102` on the main server
port. `ServerFacade` tracks both with the other main-port protocols so dynamic
port changes move them together. The transaction encryption wrapper forwards
the inner `102` code and payload and encrypts the response without changing the
bidirectional server-stats codec.

## Failure behavior

- Missing or out-of-range `/Port`: reject the request.
- Malformed MessagePak or encoded identity: reject the request.
- Local requester file lists unavailable: select transaction `101` before send.
- Local responder file lists unavailable: return a valid minimal `102` response.
- Request serialization failure: complete the datagram future with an
  `IOException` and send no packet.
- Datagram request codecs declare checked `IOException`; `UncheckedIOException`
  is not part of the codec or wrapper contract.
- Unexpected runtime failures while inspecting or building local stats propagate;
  they are not converted into failed futures or fallback cards.
- Unsupported transaction `102` after it is sent: fail through the existing pool refresh/dead
  address path; do not retry `101`.
- Expected-key or returned-identity mismatch: do not onboard the hinted pair.
