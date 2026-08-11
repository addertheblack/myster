# Myster 3DNS - Part 2a: FIND_CLOSEST Protocol and Expected-Key Hook

Prerequisites and follow-on plans:

- [Part 1a: Core Data Structures](myster-3dns-part-1a.md)
- [Part 1b: Tracker UI Integration](myster-3dns-part-1b.md)
- [Part 2b: Routing-Table Maintenance and Bootstrap](myster-3dns-part-2b.md)
- [Part 3: Iterative CID Resolution](myster-3dns-part-3.md)

## 1. Summary

Add the 3DNS `FIND_CLOSEST` UDP transaction, reserve transaction code `303`, and add the protocol-stack hook that lets a future caller contact a returned address using its advertised public key as the expected peer identity. This milestone exposes local closest-node knowledge and the primitive needed to prove candidate identity, but does not orchestrate validation, maintain the local table, or perform multi-hop CID resolution.

## 2. Non-goals

- Do not implement hourly routing-table maintenance or bootstrap orchestration; that is Part 2b.
- Do not implement iterative CID-to-server resolution; that is Part 3.
- Do not add a pool-level candidate-validation/onboarding operation before a production consumer needs it; Part 2b will determine the narrowest required shape.
- Do not change Part 1a target retention or persistence except for small accessors needed by the protocol.
- Do not trust a public-key/address pair merely because another peer returned it.
- Do not add a 3DNS UI, general DHT storage, reputation, proof-of-work, or Sybil resistance.

## 3. Assumptions & open questions

- Part 1a is implemented and provides `IdentityNeighborSet`, `MysterServerPool.findClosestByCid(...)`, and tracker-held public-key servers.
- `LEFT` means predecessor/negative side and `RIGHT` means successor/positive side in the unsigned 128-bit ring.
- The wire protocol returns both sides without choosing routing policy. Part 2b and Part 3 decide which group to prefer.
- Returned public keys are X.509 encoded and CIDs are always derived locally with `Util.generateCid(publicKey)`.
- Future candidate validation should reuse normal Myster identity mechanisms. A request encrypted to the advertised public key can only be understood by the holder of its private key; server stats also carry `/Identity`, and TLS already supports an expected server public key.
- Transaction code `303` is permanently reserved for `FIND_CLOSEST`.
- No architecture-blocking questions remain for Part 2a.

## 4. Proposed design

`FIND_CLOSEST` is a small MessagePak request/response transaction over the existing UDP transaction manager. A request contains a target CID and a per-side result limit. The server asks the pool for live closest identities and replies with separate exact, left, and right groups. Each candidate contains its encoded public key and currently usable address; no candidate CID is serialized.

The datagram API gains an explicit expected-peer-key option associated with an address. This must not mutate the normal address-to-identity cache before proof. When present, `MysterDatagramImpl` encrypts the request to that key even if the key is not cached. A successfully decrypted response proves that the remote endpoint could decrypt a request sealed to the advertised public key. Part 2a deliberately stops at this transport primitive; candidate selection, stats comparison, onboarding, and in-flight sharing are deferred until Part 2b has a production call path.

## 5. Architecture connections

Part 2a is the boundary between untrusted routing hints and the existing trusted tracker state.

| New / changed thing | Owned / created by | Called / used by | Connects to (existing) |
|---|---|---|---|
| `FIND_CLOSEST` transaction `303` | Datagram client/server | Peers, later Parts 2b/3 | `TransactionProtocol`, `StandardDatagramClientImpl`, `MessagePak` |
| Wire candidate models | `com.myster.threedns` | Datagram codec, later Parts 2b/3 | `PublicKeyIdentity`, `Cid128`, `MysterAddress` |
| Expected-peer-key request hook | `ParamBuilder` and `MysterDatagramImpl` | Later candidate proof and 3DNS queries | `EncryptingStandardDatagramClientImpl`, `PublicKeyLookup`, MSD encryption |

The Part 2a data flow ends after a peer returns a public key/address and the receiver derives the CID. The expected-key hook lets a later consumer contact that address without first trusting the association. Part 2b decides whether and where to compare returned identity data and invoke normal onboarding.

Wire contract, represented as MessagePak paths:

- Request:
  - `/schemaVersion`: int, required, value `1`
  - `/targetCid`: byte array, required, exactly 16 bytes
  - `/perSideLimit`: int, optional, default `2`, clamped to `1..4`
- Response:
  - `/schemaVersion`: int, value `1`
  - `/exactCount`: int, `0` or `1`
  - `/exact/publicKey`, `/exact/ip`, `/exact/port` when exact is present
  - `/leftCount`: int, `0..perSideLimit`
  - `/left/<index>/publicKey`, `/left/<index>/ip`, `/left/<index>/port`
  - `/rightCount`: int, `0..perSideLimit`
  - `/right/<index>/publicKey`, `/right/<index>/ip`, `/right/<index>/port`

Public keys are X.509 bytes. IP is the textual address from `MysterAddress`; port is the advertised Myster server port. The response omits candidate CIDs deliberately. Receivers reject or skip malformed keys/addresses according to one documented codec policy and enforce a bounded response size.

## 6. Key decisions & edge cases

- Code `303` is final.
- Exact/left/right grouping is part of the wire contract; group order must not stand in for side information.
- The protocol returns both sides. It does not encode maintenance or lookup bias.
- Sparse groups, including all-empty, are valid.
- The server rechecks liveness/address usability while serializing so a newly down server is not emitted.
- Default `perSideLimit` is `2`; the server clamps it at `4` and enforces a total response-byte cap.
- A returned key/address is an untrusted hint until an expected-key request succeeds and stats identity matches byte-for-byte.
- An MSD request encrypted to the candidate key is useful proof even though current response-signature verification is incomplete: producing the symmetric-key response requires decrypting the request with the matching private key.
- Plain unencrypted success is insufficient proof by itself. TLS validation must receive the expected key, or UDP validation must use the expected-key encrypted path.
- CID equality alone is insufficient; compare encoded public keys and then derive the CID.
- Candidate validation/onboarding policy is intentionally not implemented until Part 2b supplies a production consumer.

## 7. Acceptance criteria

- [ ] Transaction code `303` is defined once and documented as 3DNS `FIND_CLOSEST`.
- [ ] A server answers valid requests with explicit exact/left/right public-key/address groups from currently up pool entries.
- [ ] Request limits default to two per side and are clamped to four.
- [ ] Recipients derive every candidate CID locally from its public key.
- [ ] An address and expected public key can be supplied together to the datagram stack without first inserting that key into trusted pool state.
- [ ] Existing datagram behavior is unchanged when no expected key is supplied.
- [ ] Focused tests cover codec round trips, sparse groups, malformed input, size/limit enforcement, and the expected-key transport proof.
- [ ] Part 2a adds no scheduler, bootstrap loop, or iterative lookup implementation.

---
## ✦ IMPLEMENTATION DETAILS (for the implementation agent)
---

## 8. Affected files / classes

- `src/main/java/com/myster/net/datagram/DatagramConstants.java` - reserve `THREE_DNS_FIND_CLOSEST_TRANSACTION_CODE = 303`.
- New `src/main/java/com/myster/threedns/ThreeDnsAddressCandidate.java` - expected public-key identity, derived CID, and address.
- New `src/main/java/com/myster/threedns/ThreeDnsAddressCandidateSet.java` - immutable exact/left/right response groups.
- New `src/main/java/com/myster/net/server/datagram/FindClosestDatagramServer.java` - parse, query pool, and encode response.
- New `src/main/java/com/myster/net/datagram/client/FindClosestDatagramClient.java` - encode request and decode/derive candidates.
- `src/main/java/com/myster/net/client/MysterDatagram.java` - expose one-hop `findClosest(...)`.
- `src/main/java/com/myster/net/client/ParamBuilder.java` - retain an address plus an expected public-key identity/key.
- `src/main/java/com/myster/net/datagram/client/MysterDatagramImpl.java` - prefer explicit expected key over cache lookup for a request.
- `src/main/java/com/myster/Myster.java` - register the transaction server only.
- Focused tests under `src/test/java/com/myster/net/datagram`, `.../tracker`, and `.../threedns`.

## 9. Step-by-step implementation

1. Add immutable wire candidate types. Decode X.509 keys with `Util.publicKeyFromBytes(...)`, construct `PublicKeyIdentity`, derive `Cid128`, and defensively copy response lists.
2. Add transaction `303` and implement the server codec. Validate schema, exact target length, requested limit, address port, candidate liveness, and final encoded size. Use `pool.findClosestByCid(target, limit)` and the best currently up address for each identity.
3. Implement the client codec and `MysterDatagram.findClosest(ParamBuilder, Cid128, int)`. Preserve exact/left/right groups and never accept a serialized CID as authoritative.
4. Extend `ParamBuilder` with a distinct expected-peer identity/key field that does not clear its address. Keep the existing address/identity targeting behavior compatible.
5. In `MysterDatagramImpl.doSection(...)`, use the explicit expected key ahead of `PublicKeyLookup`. Force the encrypted decorator for expected-key validation; fail if encryption cannot be constructed rather than silently sending plaintext.
6. Register `FindClosestDatagramServer` next to existing datagram transactions in `Myster.addServerConnectionSettings(...)`. Do not start maintenance or construct iterative lookup.
7. Run focused tests and write `docs/impl_summary/myster-3dns-part-2a.md` after implementation.

## 10. Tests to write

- Codec tests: full and sparse groups, ring-edge CIDs, invalid CID length, invalid key, invalid address/port, limit default/clamp, deterministic grouping, response-byte cap.
- Server tests: only currently up identities with usable addresses are serialized; liveness changes during encoding are excluded.
- Expected-key tests: address and key coexist in `ParamBuilder`; explicit key overrides an absent or conflicting cache entry; plaintext fallback is impossible in validation mode.
- Regression tests: ordinary cached-key encrypted and no-key plaintext datagram calls retain current behavior.

## 11. Docs / Javadoc to update

- Update `docs/design/Myster 3DNS.md` with code `303`, the exact wire fields, validation proof, and milestone links.
- Javadoc the trust boundary on `ThreeDnsAddressCandidate`, `ParamBuilder` expected-key state, and `MysterDatagram.findClosest(...)`.
- Add `docs/impl_summary/myster-3dns-part2a.md` during implementation.
