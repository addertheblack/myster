# Myster 3DNS Design

## 1. Summary

3DNS is a distributed, dynamic, identity-based routing and discovery layer for Myster. Its purpose is to allow a Myster node to locate and route toward another node by CID without relying on a centralized naming system.

In Myster, each server already has a stable cryptographic identity represented by a CID. 3DNS treats these CIDs as positions in a circular 128-bit address space. Each server maintains knowledge of other servers positioned throughout that space and routes requests by repeatedly asking peers for nodes closer to a target CID.

The system is intentionally narrow in scope. Rather than acting as a general distributed key-value store, 3DNS focuses on a single primitive: given a target CID, return one or more known CID/IP pairs that are closer to that target. Routing emerges from repeatedly applying that primitive.

The protocol is implemented using Myster’s UDP transaction model. In this context, a “transaction” is a request-response interaction where requests are idempotent and can be safely retried. This is important for robustness over UDP and aligns naturally with lookup operations.

All protocol payloads are encoded using MessagePak. For documentation purposes, structures may be represented in JSON form, but this is understood to be a JSON-equivalent representation of a MessagePak structure. MessagePak allows additional value types such as integers and byte arrays, while keys remain strings.

## 2. Conceptual Model

3DNS treats the live network of servers as the routing structure itself. Each server occupies a position in a circular numeric space defined by its CID. Routing is performed by moving through known servers toward a target CID, rather than by querying a central authority or retrieving a stored record.

This differs from traditional DNS, which maps names to addresses, and from generic DHT systems, which map keys to stored values. In 3DNS, the objective is to reach the server corresponding to a CID directly. If that server is present and reachable, routing will converge to it. If it is not present, the lookup will terminate at the closest known node.

This model avoids pretending that the system “knows” where nodes are ahead of time. Instead, it discovers them dynamically by traversing the structure formed by currently connected peers.

## 3. Relationship to Prior Systems

3DNS belongs to the family of structured overlay networks. Mechanically, it is closest to systems that use a circular numeric keyspace and power-of-two offset routing strategies. Conceptually, it aligns with systems that treat the overlay as a routing fabric rather than a storage system.

The distinguishing aspect of 3DNS in Myster is that it is identity-driven. The CID is both the identity of the node and its position in the routing space. The system is therefore not resolving abstract keys but navigating directly to known entities.

## 4. CID Representation and Numeric Model

For routing to be efficient, CIDs must support fast numeric operations. The existing byte-array representation is not ideal for this purpose. The proposed approach is to represent CIDs internally as two unsigned 64-bit values.

```java
public record Cid128(long hi, long lo) implements Comparable<Cid128>
```

This representation allows efficient unsigned comparison, natural wraparound arithmetic, and direct use as keys in ordered data structures such as TreeMap. It also avoids repeated allocation and copying of byte arrays in routing code.

Serialization remains fixed at 16 bytes. Conversion methods such as `fromBytes` and `toBytes` are used at protocol boundaries.

The essential operations supported by this representation are unsigned comparison, addition of powers of two with wraparound, and comparison of closeness between candidate CIDs relative to a target CID on the circular space.

Rather than exposing distance as a first-class value, the system defines a comparison primitive that determines which of two candidates is closer to a target. This keeps the implementation efficient and aligned with the needs of routing.

## 5. Routing Table Structure

The routing table is implemented as a single ordered map:

```java
NavigableMap<Cid128, ServerInfo> routingTable = new TreeMap<>();
```

This structure provides both exact lookup and ordered access to neighboring entries. Exact lookup remains logarithmic in complexity but is sufficiently fast given the expected size of the routing table. Avoiding a second structure such as a HashMap reduces memory usage and eliminates the need to maintain consistency between multiple data structures.

The routing table is not intended to store the entire network. It contains a curated set of useful peers discovered through the tracker and through routing operations.

## 6. Core Lookup Operation

The central local operation is finding the known node closest to a target CID.

This is implemented by locating the nearest known entries on either side of the target in the ordered map and then comparing which one is closer on the circular space. Because the space wraps, the implementation must handle the transition between the maximum and minimum values correctly.

This operation relies on a comparison of ring closeness rather than linear distance. The closest node must always be one of the two adjacent nodes around the target position in the ordered set.

## 7. UDP Transaction Protocol

3DNS introduces a single conceptual operation implemented as a UDP transaction:

```text
FIND_CLOSEST(targetCid)
```

The response returns a list of CID/IP pairs that are closest to the requested target.

Represented in JSON form (as a MessagePak structure), the response looks like:

```json
{
  "results": [
    { "cid": "...", "ip": "...", "port": 1234 },
    { "cid": "...", "ip": "...", "port": 1234 }
  ]
}
```

Returning multiple results is preferred over returning a single closest node. This improves robustness in the presence of node failure, stale information, or malicious responses. If one candidate fails, the caller can immediately try another without restarting the lookup process.

Because the protocol is transaction-based and idempotent, requests can be retried safely. This is important for UDP reliability and aligns with Myster’s existing networking model.

## 8. Lookup Flow

A lookup proceeds by repeatedly issuing FIND_CLOSEST requests.

The caller begins with any known server, typically obtained from the tracker or local state. It sends a request for the target CID and receives a list of candidates. If one of the returned CIDs matches the target, the lookup is complete. Otherwise, the caller selects the closest candidate not yet tried and repeats the process.

To prevent loops, the caller must track which CIDs have already been queried. Additionally, each step should ensure that progress is being made toward the target. If no closer node is found, the lookup terminates at the closest known position.

In practice, maintaining a visited set and always selecting a strictly closer node ensures forward progress and prevents cycles.

## 9. Routing Table Maintenance

Routing table maintenance is driven by usage and periodic refresh.

Each node maintains entries corresponding to its ideal positions in the space, defined by adding powers of two to its own CID. For a 128-bit space, this results in 128 target positions.

Maintenance consists of issuing FIND_CLOSEST requests for these target positions and updating the routing table with the results. Over time, this causes the node’s view of the network to converge toward a useful distribution of peers.

Maintenance does not require a complex interface. A periodic process, for example running roughly once per hour, is sufficient. In practice, normal usage of the routing system will also naturally keep entries fresh.

## 10. Interaction with Existing Myster Tracker

3DNS augments, rather than replaces, the existing Myster tracker system.

The tracker already maintains a ServerPool containing CID-to-server mappings and multiple ServerLists that organize servers according to different criteria, such as file type or local network presence.

3DNS introduces an additional ServerList dedicated to routing. This list is populated from both tracker data and routing discoveries. The tracker continues to serve its existing roles, including file discovery and server indexing.

The integration works as follows:

* The routing table is initially seeded from known servers in the ServerPool.
* Newly discovered servers from 3DNS can be added to the 3DNS ServerList.
* Existing liveness checks and onboarding logic ensure that only reachable and valid servers are retained.

This approach allows 3DNS to benefit from existing infrastructure without creating duplication or tight coupling.

## 11. Failure Handling and Robustness

Failures are handled pragmatically.

Returned CID/IP pairs may be stale or unreachable. This is addressed by returning multiple candidates and by relying on existing Myster mechanisms that validate and onboard servers into the tracker. Failed nodes naturally fall out of the routing table as they are no longer refreshed.

To avoid routing loops, each lookup tracks the set of visited CIDs and avoids revisiting them. Ensuring that each hop moves strictly closer to the target CID further guarantees progress.

A lookup only truly fails if there are no reachable nodes in the network. In all other cases, the system will return either the exact node or the closest known node.

## 12. Security Considerations

Security is largely handled by existing Myster identity mechanisms.

Each server’s CID corresponds to a cryptographic identity. When connecting to a server, its certificate must match its CID. If it does not, the server is rejected and not incorporated into the routing structures.

This ensures that nodes cannot impersonate arbitrary CIDs. While the system does not attempt to resist all Byzantine behavior, it maintains basic identity integrity through existing mechanisms.

## 13. Configuration Decisions

The system maintains one routing target for each bit position in the 128-bit space, resulting in 128 ideal positions. Links are not maintained symmetrically; the system relies on forward progress toward targets.

Maintenance runs periodically but is also driven by normal routing activity. No special scoring system for “stale entries” is required beyond existing liveness checks in the tracker and routing logic.

The protocol always returns a list of candidates rather than a single node, improving resilience without increasing conceptual complexity.

## 14. Summary

3DNS provides a lightweight, identity-based routing layer for Myster. By treating CIDs as positions in a circular space and using a single primitive to discover closer nodes, it enables decentralized discovery and routing without introducing unnecessary complexity. The system integrates cleanly with existing Myster infrastructure and can be implemented incrementally while remaining robust under real-world conditions.
