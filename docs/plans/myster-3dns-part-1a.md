# Myster 3DNS - Part 1a: Core Data Structures

Companion plans:

- [Myster 3DNS - Part 1b: Tracker UI Integration](myster-3dns-part-1b.md)
- [Myster 3DNS - Part 2: Protocol and Lookup](myster-3dns-part-2.md)

## 1. Summary

Implement the local 3DNS core: efficient `Cid128` ordering, an `IdentityTracker`-backed CID candidate iterator, a pool closest-by-CID API that returns currently up `PublicKeyIdentity` entries as exact/left/right groups, and a tracker-owned `ThreeDnsServerList` that persists and retains balanced server candidates around the local node's positive exponential offset targets.

## 2. Non-goals

- Do not add the UDP `FIND_CLOSEST` transaction in Part 1a.
- Do not add iterative network lookup in Part 1a.
- Do not add future-returning remote address-candidate validation in Part 1a.
- Do not add TrackerWindow or `TypeChoice` UI work in Part 1a; that is Part 1b.
- Do not replace existing tracker type lists, LAN discovery, bookmarks, mDNS, or onramp behavior.
- Do not reshape the pool's weak `Map<MysterIdentity, WeakReference<MysterServerImplementation>>` cache into the ordered CID index.
- Do not implement a distributed key-value store, reputation, proof-of-work, or Sybil resistance.

## 3. Assumptions & open questions

- Assumption: closest-by-CID is a pool-level capability for callers, but identity indexes belong in `IdentityTracker` in this codebase.
- Assumption: the existing `IdentityTracker.cid128ToIdentity` map becomes a `NavigableMap<Cid128, MysterIdentity>` rather than adding a second CID index elsewhere.
- Assumption: identities without public keys, especially `MysterAddressIdentity`, remain excluded from the CID index because they have no stable CID position.
- Assumption: only `PublicKeyIdentity` entries are relevant for 3DNS routing results. Address identities can become eligible only after normal stats refresh exposes a public key.
- Assumption: "left/negative" means predecessor side of the target in unsigned CID order; "right/positive" means successor side.
- Assumption: current maintenance targets are positive exponential offsets, `localCid + 2^bitIndex`.
- Assumption: "down" means operationally not responsive/offline. A 3DNS candidate is eligible only when the server is currently considered up and has a usable up address.
- Decision: `IdentityTracker` exposes a package-private directional candidate producer. The pool consumes candidates until it has enough currently up identities or the tracker runs out.
- Decision: the pool-facing API returns an `IdentityNeighborSet` of `PublicKeyIdentity` entries. Address-bearing wire candidates are a Part 2 protocol concern.
- Decision: persist the 3DNS retained finger list like the normal file/type server lists: store external names in preferences and restore whatever retained entries were there at startup. Restored entries are retained as list references first, then runtime APIs decide whether they are currently usable/up.

## 4. Proposed design

Part 1a adds only local data structures and tracker/pool behavior.

`Cid128` gains unsigned ordering and ring-distance helpers while preserving the existing public surface (`new Cid128(byte[])`, `bytes()`, `asHex()`, equality, and hashing). These operations are the foundation for ordered map lookup and target-relative predecessor/successor comparisons.

`IdentityTracker` owns the ordered CID index. The existing `cid128ToIdentity` index is already maintained when identities are added or removed, so it should be upgraded to a `NavigableMap` and given a directional candidate producer. Address-only identities are never inserted into this map.

`MysterServerPool` exposes closest-by-CID as `findClosestByCid(Cid128 target, int perSideLimit)`. The implementation delegates candidate production to `IdentityTracker`, resolves identities through the existing weak cache, filters out GCed/down/nonresponsive servers, and returns an `IdentityNeighborSet` with optional exact, left, and right `PublicKeyIdentity` groups.

`Tracker` owns a `ThreeDnsServerList`. This is a finger/retention list, not the authoritative routing table and not a normal type-shaped `ServerList`. It retains strong references to small balanced sets around each local positive exponential target. When a retained server becomes nonresponsive, the list removes it and asks the pool for nearest currently up replacement candidates for the affected target.

`ThreeDnsServerList` should persist its retained entries under the tracker preferences tree using the same external-name style as `MysterTypeServerList` and `BookmarkMysterServerList`. It should restore the retained entries that were present on shutdown/startup load before normal unreferenced server cleanup can delete them. Loading resolves each saved `ExternalName` through `MysterServerPool` and rebuilds the retained slots; current reachability filtering happens when returning closest results, seeds, or protocol/display snapshots that require up servers.

## 5. Architecture connections

Part 1a plugs into the existing tracker and pool layer only. It prepares local state that Part 2 can expose over UDP and that Part 1b can display in the tracker UI.

| New / changed thing | Owned / created by | Called / used by | Connects to (existing) |
|---|---|---|---|
| Ordered CID identity index and iterator | `IdentityTracker` | `MysterServerPoolImpl.findClosestByCid(...)`, existing CID lookup | `cid128ToIdentity`, `MysterIdentity`, `PublicKeyIdentity`, `Cid128` |
| Pool closest-node API | `MysterServerPool` / `MysterServerPoolImpl` | `ThreeDnsServerList`, Part 2 protocol server | `IdentityTracker`, weak server cache, `MysterServer`, `MysterAddress` |
| `IdentityNeighborSet` | `com.myster.tracker` | Pool, finger list, Part 2 protocol server | Optional exact identity, left/predecessor identities, right/successor identities |
| `ThreeDnsServerList` | `Tracker` | Part 1a tests, Part 1b UI accessor, Part 2 lookup seed selection | pool listener events, `MysterServerPool`, local server identity |
| 3DNS retained-list persistence | `ThreeDnsServerList` | Tracker startup/shutdown and retention updates | `Preferences`, `ExternalName`, `MysterServerPool` |
| CID ring operations | `Cid128` | IdentityTracker neighbor lookup and progress checks | Existing identity/access-list CID usage |

The local data flow is: tracker/pool events update known server state, `IdentityTracker` maintains ordered public-key identity positions, `MysterServerPoolImpl` turns ordered identity candidates into currently up `PublicKeyIdentity` neighbor groups, and `ThreeDnsServerList` retains balanced local fingers by target bit and side.

On disk, the 3DNS retained list stores external-name strings grouped by target bit and side. It does not serialize public keys as the primary persistence format and does not persist live/trusted state. Restored entries are list references that must still pass runtime up checks before they are returned as usable 3DNS candidates.

## 6. Key decisions & edge cases

- Use `IdentityTracker`'s CID index as the ordered map. Do not add a parallel CID map in `MysterServerPoolImpl`.
- Keep the pool cache keyed by `MysterIdentity`.
- Do not implement the current type-shaped `ServerList` interface for 3DNS unless that interface is later generalized.
- `findClosestByCid` must not return down/nonresponsive identities.
- Sparse sides are valid. If fewer than `perSideLimit` up servers exist on either side, return fewer.
- Exact target, when present and up, is returned once and not duplicated into left/right lists.
- Empty local CID index or empty retained finger list is valid.
- Saved 3DNS retained entries should mirror normal file-list persistence: store `ExternalName` strings, restore the retained list on startup, and let normal pool state determine current reachability.
- Persistence should group external names by target bit and side, rather than serializing public keys or addresses.
- Restore the retained 3DNS list early enough that saved servers remain referenced before normal unreferenced-server cleanup can discard them.
- Self entries should not dominate seeds or neighbor sets when better options exist.
- Address-only identities can become eligible only after normal stats refresh exposes a public key.

## 7. Acceptance criteria

- [ ] `Cid128` supports stable byte serialization, unsigned ordering, positive power-of-two ring offsets, and target-relative predecessor/successor distance comparison.
- [ ] `IdentityTracker` uses a `NavigableMap<Cid128, MysterIdentity>` for the existing CID index and excludes address-only identities.
- [ ] `MysterServerPool` exposes a closest-by-CID API returning a structured exact/left/right `IdentityNeighborSet`.
- [ ] Pool closest-by-CID results include only currently responsive/up `PublicKeyIdentity` entries whose cached server has usable up addresses.
- [ ] `Tracker` owns a 3DNS finger/retention collection that watches pool events and keeps balanced closest known-up servers around local `localCid + 2^bitIndex` targets.
- [ ] When a retained 3DNS server becomes nonresponsive, the retention list removes it and asks the pool for nearest up replacement candidates for that offset target.
- [ ] `ThreeDnsServerList` persists retained entries using the normal server-list external-name pattern and reloads the retained slots through the pool before unreferenced-server cleanup.
- [ ] Existing tracker, LAN, bookmark, type-list, search, and server-stats behavior remains unchanged.
- [ ] Unit tests cover CID ring math, ordered index lookup, up-server filtering, finger retention/replacement, and retained-list persistence.

---
## ✦ IMPLEMENTATION DETAILS (for the implementation agent)
---

## 8. Affected files / classes

- `src/main/java/com/myster/identity/Cid128.java` - add unsigned ordering, positive ring arithmetic, predecessor/successor distance comparison, and byte conversion while preserving existing callers.
- `src/main/java/com/myster/tracker/IdentityTracker.java` - change `cid128ToIdentity` to `NavigableMap<Cid128, MysterIdentity>` and add a directional candidate producer.
- `src/main/java/com/myster/tracker/IdentityProvider.java` - add only if closest lookup must be exposed outside `IdentityTracker`; otherwise keep new closest methods package-private.
- `src/main/java/com/myster/tracker/MysterServerPool.java` - add closest-by-CID API returning `IdentityNeighborSet`.
- `src/main/java/com/myster/tracker/MysterServerPoolImpl.java` - delegate candidate iteration to `IdentityTracker`, resolve identities through the weak cache, and filter to up servers.
- New `src/main/java/com/myster/tracker/IdentityNeighborSet.java` - immutable exact/left/right `PublicKeyIdentity` result used by pool, retention list, and Part 2 protocol server.
- New `src/main/java/com/myster/threedns/ThreeDnsFingerEntry.java` - retained local finger entry wrapping target CID, server, address, derived server CID, side, and update time.
- New `src/main/java/com/myster/threedns/ThreeDnsServerList.java` - tracker-owned 128-target finger/retention list fed by pool events.
- `src/main/java/com/myster/tracker/Tracker.java` - accept the local CID, own/expose the 3DNS finger list, and feed it from pool listener events.
- `src/test/java/com/myster/identity/...` - add CID math tests.
- `src/test/java/com/myster/tracker/...` - add IdentityTracker, pool closest lookup, up-filtering, and retention replacement tests.
- `src/test/java/com/myster/threedns/...` - add finger retention and persistence tests.

## 9. Step-by-step implementation

1. Add CID numeric operations.
   - Preserve `new Cid128(byte[])`, `bytes()`, `asHex()`, `equals()`, and `hashCode()`.
   - Preferred implementation: convert `Cid128` from a record to an immutable final class with defensive-copy byte storage and cached `hi`/`lo` fields.
   - Add unsigned `compareTo(Cid128 other)`.
   - Add `Cid128 plusPowerOfTwo(int bitIndex)`.
   - Add predecessor-side and successor-side distance comparison helpers.
   - Document bit numbering: prefer `0` for the least-significant bit and `127` for the most-significant bit.

2. Refactor `IdentityTracker`'s CID index.
   - Replace `private final Map<Cid128, MysterIdentity> cid128ToIdentity = new HashMap<>();` with `private final NavigableMap<Cid128, MysterIdentity> cid128ToIdentity = new TreeMap<>();`.
   - Keep existing `getIdentityFromCid(Cid128 cid128)` behavior.
   - Keep existing add/remove maintenance points that call `computerCidFromIdentity(...)`.
   - Add `enum Direction { LEFT, RIGHT }` or equivalent package-private direction type.
   - Add a package-private callback:
     - `@FunctionalInterface interface CandidateConsumer { boolean consume(PublicKeyIdentity candidate); }`
   - Add `void findClosest(Cid128 target, Direction direction, CandidateConsumer consumer)`.
   - Walk from closest outward on the requested side.
   - For `LEFT`, use predecessor traversal with `floorEntry(target)` / `lowerEntry(...)`, wrapping from first to last.
   - For `RIGHT`, use successor traversal with `ceilingEntry(target)` / `higherEntry(...)`, wrapping from last to first.
   - Stop when all candidates are exhausted or `consumer.consume(...)` returns `false`.
   - Do not include exact target in either directional walk if the pool handles exact separately with `getIdentityFromCid(target)`.

3. Add neighbor model classes.
   - `IdentityNeighborSet(Optional<PublicKeyIdentity> exact, List<PublicKeyIdentity> left, List<PublicKeyIdentity> right)` stores immutable defensive copies.
   - The pool builds this after filtering candidates for current up/down state.
   - The exact identity is never duplicated into left or right.
   - `ThreeDnsFingerEntry` stores target CID, server CID, retained server, chosen up address, side, and update time.

4. Add pool closest-by-CID.
   - In `MysterServerPool`, add `IdentityNeighborSet findClosestByCid(Cid128 target, int perSideLimit);`.
   - In `MysterServerPoolImpl`, normalize `perSideLimit` to a small sane range, defaulting to `2`.
   - Look up exact with `identityTracker.getIdentityFromCid(target)` and include it only if it is a currently up `PublicKeyIdentity`.
   - For left and right, call `identityTracker.findClosest(target, Direction.LEFT, consumer)` and `identityTracker.findClosest(target, Direction.RIGHT, consumer)`.
   - Require `PublicKeyIdentity`.
   - Resolve cached server with `getMysterIP(identity)`.
   - Require the cached server to be currently up: `server.getStatus()` is true and `server.getUpAddresses().length > 0`.
   - Continue consuming candidates until each side has `perSideLimit` up identities or the tracker runs out.
   - Preserve exact/left/right grouping while filtering GCed or down servers.
   - Preserve existing `lookupIdentityFromCid(...)` and `suggestAddress(...)` behavior.

5. Implement `ThreeDnsServerList`.
   - Keep two retained predecessor-side and two retained successor-side entries per target by default.
   - Generate 128 targets with `localCid.plusPowerOfTwo(bitIndex)`.
   - On server refresh, only consider public-key servers that are currently responsive/up and have at least one up address.
   - Update each target slot when the refreshed server improves that side's retained set.
   - On ping timeout, down status, or dead server, remove matching retained entries.
   - When removing a retained entry because it became down/nonresponsive, refill that target using `pool.findClosestByCid(target, 2)`.
   - Expose `seeds(int limit)`, `forTarget(Cid128 target, int perSideLimit)`, and `snapshot()` for tests and later lookup/UI accessors.
   - Persist retained entries like `MysterTypeServerList`:
     - store under the existing tracker preferences subtree, for example `ServerLists/ThreeDns`
     - group entries by target bit index and side (`left` / `right`)
     - store stable `ExternalName` strings, following `MysterTypeServerList` / `BookmarkMysterServerList` style
     - save after retained slots change
     - on startup, resolve external names through `pool.lookupIdentityFromName(...)` and `pool.getCachedMysterServer(...)`
     - restore the retained entries that resolve through the pool, even if they are not currently up
     - closest results, display filtering, and future protocol responses still require current up status where those APIs require up servers

6. Integrate with `Tracker`.
   - Change the `Tracker` constructor to receive `Optional<Cid128> localCid` from `Myster.java`.
   - Initialize `ThreeDnsServerList` when local CID is present; otherwise use an inert empty list.
   - Restore persisted retained entries before unreferenced-server cleanup can remove servers that are referenced only by 3DNS.
   - Seed it from `pool.forEach(threeDns::consider)`.
   - On `serverRefresh`, call `threeDns.consider(server)` in addition to existing list updates.
   - On `serverPing`, remove/refill retained entries when ping timed out; reconsider cached server when ping succeeds.
   - On `deadServer`, call `threeDns.removeIdentity(identity)`.
   - Add accessors for seeds and snapshots needed by Part 1b and Part 2. Keep UI-specific list-change methods in Part 1b unless a tiny hook is cheaper to add here.

7. Stop Part 1a here.
   - Do not add `TrackerWindow` or `TypeChoice` changes.
   - Do not add datagram constants or transaction handlers.
   - Run focused tests for `Cid128`, `IdentityTracker`, pool closest lookup, and `ThreeDnsServerList`.
   - Write `docs/impl_summary/myster-3dns-part1a.md` after implementation.

## 10. Tests to write

- `TestCid128RingMath`
  - constructor clones byte arrays
  - unsigned compare orders low/high values correctly
  - `plusPowerOfTwo` wraps correctly
  - predecessor-side and successor-side distance comparisons handle ordinary and wraparound targets
  - exact target is distance zero
  - `bytes()` remains stable 16-byte serialization

- `TestIdentityTrackerCidIndex`
  - CID index preserves exact `getIdentityFromCid(...)`
  - address-only identities are excluded
  - public-key identities are removed only after their last address is removed
  - balanced neighbor lookup returns exact target once
  - predecessor and successor walks wrap around zero
  - per-side limits return unique identities from both sides

- `TestMysterServerPoolImplThreeDns`
  - `findClosestByCid` delegates to tracker index and filters missing/GCed servers
  - returns balanced `PublicKeyIdentity` groups
  - excludes servers with `getStatus() == false`
  - excludes servers with no `getUpAddresses()`
  - tolerates sparse sides and returns fewer than requested per-side count
  - `suggestAddress` still behaves as before

- `TestThreeDnsServerList`
  - seeds from public-key servers only
  - ignores address-only identities
  - keeps balanced retained entries around local positive power-of-two targets
  - updates retained slots when a closer known-up server refreshes on either side
  - removes retained entries on ping timeout/down status and refills from pool closest lookup
  - removes retained entries on dead identity and refills from pool closest lookup
  - exposes bounded seed identities without duplicates
  - persists retained entries by external name and reloads the retained slots through the pool
  - restored entries are retained even if currently down, but closest-by-CID usable results still exclude down servers

## 11. Docs / Javadoc to update

- Add Javadoc to `Cid128` explaining unsigned ordering, bit numbering, positive offsets, and predecessor/successor ring distance.
- Add Javadoc to `IdentityTracker.findClosest(...)` explaining directional candidate production with wraparound and callback-controlled stopping.
- Add Javadoc to `IdentityNeighborSet` explaining exact/left/right semantics and that sides may contain fewer than the requested count.
- Add Javadoc to `ThreeDnsServerList` explaining that it is tracker-owned, not type-shaped, and retains server-list references while filtering usable/up candidates at runtime.
- Add Javadoc to `ThreeDnsServerList` persistence methods explaining that storage follows the normal server-list external-name pattern and restores retained entries through the pool.
- Add `docs/impl_summary/myster-3dns-part1a.md` after implementation.
