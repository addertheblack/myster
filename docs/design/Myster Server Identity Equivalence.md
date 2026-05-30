# Myster Identity Alias Design

## Goal

Myster nodes are identified by CIDs derived from public keys. A single person/operator may control multiple Myster nodes, each with its own keypair and CID. The system needs a decentralized way to prove that several CIDs should be treated as equivalent identities.

The core rule is:

> Servers publish the alias pairs they know. Peers ingest valid signed pairs. Everyone computes equivalence locally.

This allows features such as authorization, messaging, UI grouping, history, and trust decisions to treat `CID_A`, `CID_B`, and `CID_C` as the same operator when valid alias evidence exists.

## Identity Semantics

Aliases are not a new identity and do not create a group CID.

Instead, aliases form an equivalence relation over existing CIDs.

For example:

```text
A <-> B
B <-> C
```

means:

```text
A, B, and C may be treated as the same identity.
```

A message or permission addressed to any CID in the alias component may be treated as applying to all CIDs in that component.

## Alias Evidence

The durable proof unit is a signed alias pair.

```text
AliasPair:
  cid_a
  cid_b
  created_time
  optional_expiry
  optional_sequence
  signature_by_a
  signature_by_b
```

Both sides must sign the same canonical representation of the pair. This prevents one node from unilaterally claiming another node as an alias.

## Alias Bundle

A server should publish a bundle containing the aliases it currently knows for itself.

```text
AliasBundle:
  subject_cid
  equivalent_cids: [A, B, C]
  proof_pairs:
    - A <-> B
    - B <-> C
  known_servers:
    - server/contact info for A
    - server/contact info for B
    - server/contact info for C
```

The bundle is a convenience object. The proof pairs are authoritative. A receiver verifies the proof pairs and computes the equivalent set locally.

## ServerStats Integration

The Myster ServerStats response should contain an optional alias/equivalence block.

```text
ServerStats:
  normal server stats...
  alias_block:
    equivalent_cids
    proof_pairs
    known_alias_servers
```

A remote server does not need to crawl the network to discover aliases. It can verify the supplied proof pairs and cache the result.

## Tracker Architecture

`Tracker` owns the `ServerList`s.

A new server list should be added for alias maintenance:

```java
AliasServerList implements ServerList
```

The purpose of `AliasServerList` is to keep alive and refresh servers that are relevant to the local node’s identity aliases.

Internally it may contain a helper object:

```java
AliasIndex
```

The split is:

```text
AliasServerList:
  maintains the list of MysterServers involved in local alias relationships

AliasIndex:
  stores signed alias pairs
  computes equivalent CIDs for the local node
  builds the alias block for ServerStats
```

## Observer-Side Alias Store

Separately, servers that want to understand identity equivalence for arbitrary remote nodes need an observed alias store.

Possible name:

```java
ObservedAliasStore
```

Responsibilities:

```java
void ingest(AliasBundle bundle, MysterServer source);

boolean areAliases(CID a, CID b);

Set<CID> findAliases(CID cid);

List<AliasPair> findProofPairs(CID cid);
```

This object is not about the local node’s own aliases. It is about understanding alias relationships learned from other servers.

## Local Node Alias Maintenance

For local node `A`, the alias system maintains:

```text
Direct alias pairs:
  A <-> B

Learned relevant alias pairs:
  B <-> C

Computed equivalents:
  A, B, C

Servers to refresh:
  server for B
  server for C
```

The local node should publish enough evidence that outsiders can verify the equivalent set cheaply.

## Main Operations

Useful API operations:

```java
Set<CID> findEquivalentCids(CID cid);

boolean areEquivalent(CID a, CID b);

List<AliasPair> findAliasProofPairs(CID cid);

Set<MysterServer> findAliasServers(CID cid);

void ingestAliasBundle(AliasBundle bundle, MysterServer source);

ServerStatsAliasBlock buildServerStatsAliasBlock(CID localCid);
```

## GUI Setup Flow

The GUI should support linking two nodes as equivalent identities.

Possible action name:

```text
Link as My Node...
```

or:

```text
Link Identity...
```

Example flow:

```text
1. User sees CID_B somewhere in the UI.
2. User chooses "Link as My Node..."
3. Local node CID_A sends a link request to CID_B.
4. CID_B shows a dialog:
   "CID_A wants to link with this node as the same identity."
5. User accepts.
6. A signs A <-> B.
7. B signs A <-> B.
8. Both nodes store the signed AliasPair.
9. AliasServerList begins tracking the relevant servers.
10. ServerStats begins advertising the updated alias block.
```

A second setup method should support copy/paste or email links:

```text
myster://link-identity?cid=...&contact=...&token=...
```

This is useful when the same person owns both nodes but they are not currently visible to each other in the UI.

## Messaging Behavior

If a hosted identity has aliases, the server may treat messages to any CID in the alias component as messages to the same identity.

Example:

```text
A, B, C are aliases.

Message to A
Message to B
Message to C
```

All may be delivered to the same hosted identity/inbox/session set.

There is no separate group address and no normal way to target only one CID once it has declared itself equivalent to the others.

## Authorization Behavior

Authorization checks may use alias equivalence.

Example:

```text
CID_B is allowed.
CID_A and CID_C are valid aliases of CID_B.
Therefore CID_A and CID_C may also be allowed.
```

The alias layer should not own authorization policy. It should only answer equivalence questions.

## Important Design Principle

The pairwise signed links are the proof layer.

The computed alias set is a materialized view.

```text
AliasPair = evidence
AliasBundle = portable evidence package
Alias component = computed result
```

## Open Questions

1. Should alias pairs expire?
2. How should revocation work?
3. Should an alias pair have a sequence number?
4. Should ServerStats publish only local alias bundles, or also observed third-party bundles?
5. How much alias information should be persisted versus rebuilt from refresh?
6. What exact GUI wording should be used: “Link Identity,” “Link as My Node,” “Combine Nodes,” etc.?
7. Should messaging automatically merge delivery for aliases, or should there be a local setting?
8. Should authorization automatically honor aliases, or should each feature opt in?
