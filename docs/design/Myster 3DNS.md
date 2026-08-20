# Myster 3DNS Design

## 1. Summary

3DNS is a distributed, dynamic, identity-based routing and discovery layer for Myster. Its purpose is to allow a Myster node to locate and route toward another node by CID without relying on a centralized naming system.

In Myster, each server already has a stable cryptographic identity represented by a CID. 3DNS treats these CIDs as positions in a circular 128-bit address space. Each server maintains knowledge of other servers positioned throughout that space and routes requests by repeatedly asking peers for nodes closer to a target CID.

The system is intentionally narrow in scope. Rather than acting as a general distributed key-value store, 3DNS focuses on a single primitive: given a target CID, return known public-key/address candidates that are closer to that target. Routing emerges from repeatedly applying that primitive.

The protocol is implemented using Myster’s UDP transaction model. In this context, a “transaction” is a request-response interaction where requests are idempotent and can be safely retried. This is important for robustness over UDP and aligns naturally with lookup operations.

All protocol payloads are encoded using MessagePak. For documentation purposes, structures may be represented in JSON form, but this is understood to be a JSON-equivalent representation of a MessagePak structure. MessagePak allows additional value types such as integers and byte arrays, while keys remain strings.

Implementation is staged across these plans:

- [Part 1a: Core Data Structures](../plans/myster-3dns-part-1a.md)
- [Part 1b: Tracker UI Integration](../plans/myster-3dns-part-1b.md)
- [Part 2a: FIND_CLOSEST Protocol and Expected-Key Hook](../plans/myster-3dns-part-2a.md)
- [Part 2b: Candidate Identity Verification](../plans/myster-3dns-part-2b.md)
- [Part 3: Iterative CID Resolution](../plans/myster-3dns-part-3.md)
- [Part 4: Routing-Table Maintenance and Bootstrap](../plans/myster-3dns-part-4.md)
- [Target-Slot Inspection UI](../plans/tracker-3dns-target-slots-ui.md)

## 2. Conceptual Model

3DNS treats the live network of servers as the routing structure itself. Each server occupies a position in a circular numeric space defined by its CID. Routing is performed by moving through known servers toward a target CID, rather than by querying a central authority or retrieving a stored record.

This differs from traditional DNS, which maps names to addresses, and from generic DHT systems, which map keys to stored values. In 3DNS, the objective is to reach the server corresponding to a CID directly. If that server is present and reachable, routing will converge to it. If it is not present, the lookup will terminate at the closest known node.

This model avoids pretending that the system “knows” where nodes are ahead of time. Instead, it discovers them dynamically by traversing the structure formed by currently connected peers.

## 3. Relationship to Prior Systems

3DNS belongs to the family of structured overlay networks. Mechanically, it is closest to systems that use a circular numeric keyspace and power-of-two offset routing strategies. Conceptually, it aligns with systems that treat the overlay as a routing fabric rather than a storage system.

The distinguishing aspect of 3DNS in Myster is that it is identity-driven. The CID is both the identity of the node and its position in the routing space. The system is therefore not resolving abstract keys but navigating directly to known entities.

## 4. CID Representation and Numeric Model

For routing to be efficient, server CIDs must support fast numeric operations. The public `ServerCid` composes a package-private 128-bit value that caches two unsigned 64-bit values for comparison and ring arithmetic. This prevents a Myster type CID from being passed to the server-identity ring while retaining the existing byte representation.

```java
public final class ServerCid implements Comparable<ServerCid> {}
```

This representation allows efficient unsigned comparison, natural wraparound arithmetic, and direct use as keys in ordered data structures such as TreeMap. It also avoids repeated allocation and copying of byte arrays in routing code.

Serialization remains fixed at 16 bytes. The public constructor accepts exactly 16 bytes and `bytes()` returns a defensive copy in the same fixed-width big-endian form used by existing access-list and datagram code.

The essential operations supported by this representation are unsigned comparison, addition of powers of two with wraparound, and comparison of closeness between candidate CIDs relative to a target CID on the circular space.

Rather than exposing distance as a first-class value, the system defines a comparison primitive that determines which of two candidates is closer to a target. This keeps the implementation efficient and aligned with the needs of routing.

## 5. Routing Table Structure

The ordered CID index is owned by `IdentityTracker`, not by `MysterServerPoolImpl`. The existing CID lookup map is upgraded to a navigable map:

```java
NavigableMap<ServerCid, MysterIdentity> serverCidToIdentity = new TreeMap<>();
```

This structure provides both exact lookup and ordered access to neighboring entries. Exact lookup remains logarithmic in complexity but is sufficiently fast given the expected size of the routing table. Avoiding a second CID index in the pool reduces memory usage and eliminates the need to maintain consistency between multiple data structures.

Only `PublicKeyIdentity` entries are inserted into the CID index. Address-only identities have no stable CID position and are excluded until normal stats refresh learns a public key.

The local 3DNS retention structure is `ThreeDnsServerList`, a tracker-owned finger list around the local node's positive exponential offset targets. It is not a normal type-shaped `ServerList`.

## 6. Core Lookup Operation

The central local operation is finding known nodes closest to a target CID.

This is implemented by walking the ordered map from closest outward on the requested side. `LEFT` means predecessor side and `RIGHT` means successor side in unsigned CID order, with wraparound at both ends of the 128-bit space.

The pool-facing result is split into optional exact, left, and right groups. Pool results are already filtered to currently responsive `PublicKeyIdentity` servers with at least one usable up address.

## 7. UDP Transaction Protocol

3DNS introduces a single conceptual operation implemented as a UDP transaction:

```text
FIND_CLOSEST(targetCid)
```

Its permanently reserved UDP transaction code is `303`.

The request carries schema version `1`, a fixed 16-byte target CID, and an optional per-side limit. The limit defaults to two and is clamped to four. The response returns exact, left, and right groups of public-key/address candidates that are closest to the requested target. The public key is sent as encoded X.509 bytes; the recipient derives the candidate CID locally from that public key.

Represented in JSON form (as a MessagePak structure), the response looks like:

```json
{
  "schemaVersion": 1,
  "exactCount": 1,
  "exact": { "publicKey": "X.509 bytes", "ip": "192.0.2.1", "port": 6669 },
  "leftCount": 1,
  "left": { "0": { "publicKey": "X.509 bytes", "ip": "192.0.2.2", "port": 6669 } },
  "rightCount": 1,
  "right": { "0": { "publicKey": "X.509 bytes", "ip": "192.0.2.3", "port": 6669 } }
}
```

The exact group contains zero or one candidate. Left and right each contain at most the clamped per-side limit. Responses are bounded to 16 KiB, addresses must be literal IP values with ports in `1..65535`, and malformed candidate groups reject the response. Candidate CIDs are intentionally absent from the wire format.

Returning multiple results is preferred over returning a single closest node. This improves robustness in the presence of node failure, stale information, or malicious responses. If one candidate fails, the caller can immediately try another without restarting the lookup process.

Because the protocol is transaction-based and idempotent, requests can be retried safely. This is important for UDP reliability and aligns with Myster’s existing networking model.

## 8. Lookup Flow

A lookup proceeds by repeatedly issuing expected-key `FIND_CLOSEST` requests. The wire operation remains side-neutral and returns explicit exact, left/predecessor, and right/successor groups; the resolver derives every CID locally, merges all three groups into one scored frontier, and chooses candidates by positive-ring predecessor distance to the target. A useful wraparound candidate may originate in either wire group, so the group label never determines eligibility.

The public `ThreeDnsLookup.resolve(target)` operation automatically takes one immutable target-specific seed snapshot from `Tracker`. The snapshot is backed by the live pool-wide CID index, includes currently usable address/key candidates, and remains available even when the optional local retained 3DNS list is absent. A successful expected-key encrypted response verifies the responding peer, while every candidate inside that response remains an untrusted hint. If a returned candidate's derived CID matches the target, the resolver queries that candidate next; lookup completes only when the target candidate itself returns an authenticated response. Otherwise, the resolver selects the closest eligible candidate not yet tried and repeats the process.

`ThreeDnsPeerClient` owns this one-hop trust transition. It performs one expected-key UDP `FIND_CLOSEST` operation and returns a `ThreeDnsVerifiedQueryResult`: its `VerifiedThreeDnsPeer` responder is verified for that completed operation, while its `ThreeDnsAddressCandidateSet` remains untrusted. Cancelling the wrapper cancels the underlying UDP transaction, and late completion cannot create a verified result. The decoder also rejects an entry labelled exact when the entry's locally derived CID differs from the request target.

The resolver tracks identities and addresses, permits at most one alternate address after a failed transport attempt, and requires every newly launched non-exact candidate to be strictly closer than the best verified responder. Exact candidates always take priority. Two queries may run concurrently, so a slower response can still improve the verified position or contribute a better untrusted hint without regressing global lookup state.

Traversal is bounded to a 64-entry frontier, 32 total query attempts, two concurrent queries, two attempted addresses per identity, and a 60-second overall deadline. The per-response 16 KiB decoder bound also caps accepted response data across 32 attempts at 512 KiB. Exact success, deadline, caller cancellation, and other terminal completion cancel outstanding queries and prevent late callbacks from changing the result.

Per-lookup state is actor-confined to the 3DNS lookup invoker. `AsyncTaskTracker` owns the dynamic set of peer-query promises, propagates cancellation, and signals natural exhaustion after response callbacks have had an opportunity to enqueue newly discovered work. The frontier, closest verified peer, query counters, and terminal policy are therefore mutated only on the lookup invoker and require no additional locking. The overall deadline is a separately tracked lifetime task because counting it as search work would prevent normal frontier exhaustion.

`ThreeDnsLookupResult` distinguishes verified exact success, verified closest exhaustion, no route, query-limit exhaustion, and deadline exhaustion. Limit and deadline results may preserve the closest verified peer. Cancellation remains cancellation of the returned `PromiseFuture`. Unexpected runtime failures inside the lookup implementation are programmer errors and propagate through the executing framework thread rather than being converted into failed lookup promises.

## 9. Routing Table Maintenance

Routing table maintenance is driven by usage and periodic refresh.

Each node maintains entries corresponding to its ideal positions in the space, defined by adding powers of two to its own CID. For a 128-bit space, this results in 128 target positions. The retained list keeps a small balanced set on both the left/predecessor and right/successor sides of each target.

Part 1 maintenance is driven by tracker/pool events: refreshed up public-key servers are considered for retained slots, and down/dead servers are removed and replaced from the pool's nearest-CID API. Part 4 active maintenance invokes the Part 3 resolver for these target positions, securely onboards selected verified peers, and lets normal pool/listener behavior update the retained list.

Healthy Part 4 maintenance runs roughly hourly with broad independent jitter across nodes and targets. It must not refresh all 128 targets in one synchronized burst. When a retained entry becomes damaged, down, or dead, the affected target is resolved immediately, subject to in-flight deduplication and failure backoff. Direction and progress policy remain owned by Part 3 instead of being duplicated in the scheduler.

## 10. Interaction with Existing Myster Tracker

3DNS augments, rather than replaces, the existing Myster tracker system.

The tracker already maintains a ServerPool containing identity-to-server mappings and multiple lists that organize servers according to different criteria, such as file type, local network presence, and bookmarks.

3DNS introduces a dedicated `ThreeDnsServerList` dedicated to routing retention. It is populated from tracker data and, later, routing discoveries. The tracker continues to serve its existing roles, including file discovery and server indexing.

The integration works as follows:

* The ordered CID index is maintained in `IdentityTracker`.
* The 3DNS retained list is initially seeded from known public-key servers in the ServerPool.
* Part 2a provides the expected-key UDP transport hook; Part 2b wraps a useful `FIND_CLOSEST` query around it and returns a verified responder without mutating tracker state.
* Part 3's auto-seeded `ThreeDnsLookup` returns verified exact or bounded closest peers without mutating tracker state. Part 4 performs expected-key stats identity comparison and normal pool onboarding when a discovery must become persistent retained state.
* Existing liveness checks continue to determine which onboarded servers remain usable and retained.

This approach allows 3DNS to benefit from existing infrastructure without creating duplication or tight coupling.

## 11. Failure Handling and Robustness

Failures are handled pragmatically.

Returned public-key/address candidates may be stale or unreachable. Multiple candidates provide fallback, but none is trusted transitively: each queried peer must complete an expected-key encrypted round trip. Part 4 separately onboards selected verified peers when persistent tracker state is needed. Failed nodes naturally fall out of the retained list as they are no longer refreshed or become nonresponsive.

To avoid routing loops, each lookup deduplicates identity/address pairs and does not revisit attempted work. The same endpoint may remain eligible under distinct identities/CIDs. Ensuring that each newly launched hop moves strictly closer to the target CID after the first verified response further guarantees progress.

A lookup terminates explicitly when it reaches the verified exact peer, exhausts strict progress, has no route/seed, reaches a resource limit, or is cancelled. Non-exact termination can include the closest verified peer for diagnostics and Part 4 maintenance. Unexpected implementation failures are panics, not lookup outcomes.

## 12. Security Considerations

Security is largely handled by existing Myster identity mechanisms.

Each server’s CID corresponds to a cryptographic identity. A remote 3DNS response is only a hint until the candidate address proves it owns the returned public key. `ParamBuilder.withExpectedServerPublicKey(...)` carries an address and expected key together without first trusting or caching that association. `MysterDatagramImpl` always encrypts such a request to the explicit key, even if the tracker has no key or a different cached key for the address. Only the matching private-key holder can decrypt the request and recover the one-time symmetric key required to create an authenticated response. Part 2b fuses that proof with the useful `FIND_CLOSEST` query and promotes only the responder; candidates returned inside the response remain untrusted. Exact target success still requires the verified responder's locally derived CID to equal the target.

TCP has the same underlying identity concept: `TLSSocket.createClientSocket(...)` can compare the peer certificate public key with an expected key. The ordinary `MysterStream`/`MysterSocketFactory` surface does not yet expose that argument, so current 3DNS verification is UDP-first while its verified-peer model remains transport-neutral.

This ensures that nodes cannot impersonate arbitrary CIDs. While the system does not attempt to resist all Byzantine behavior, it maintains basic identity integrity through existing mechanisms.

## 13. Configuration Decisions

The system maintains one routing target for each bit position in the 128-bit space, resulting in 128 ideal positions. Retention keeps an even left/right split when enough responsive peers exist, while current target generation still uses positive exponential offsets from the local CID.

Part 4 maintenance runs on a broadly jittered hourly schedule, is also driven immediately by damaged-node events, and reuses Part 3 lookup rather than implementing another routing loop. No separate stale-entry scoring system is required beyond existing liveness checks, immediate repair, and bounded retry backoff.

The protocol always returns a list of candidates rather than a single node, improving resilience without increasing conceptual complexity.

## 14. Summary

3DNS provides a lightweight, identity-based routing layer for Myster. By treating CIDs as positions in a circular space and using a single primitive to discover closer nodes, it enables decentralized discovery and routing without introducing unnecessary complexity. The system integrates cleanly with existing Myster infrastructure and can be implemented incrementally while remaining robust under real-world conditions.
