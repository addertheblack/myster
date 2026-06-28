# Myster 3DNS Part 1a Implementation Summary

## What changed

Implemented the local 3DNS core from `docs/plans/myster-3dns-part-1a.md`:

- `Cid128` now supports unsigned ordering, positive power-of-two ring offsets, and predecessor/successor distance comparison while preserving the byte-array constructor and `bytes()` serialization contract.
- `IdentityTracker` now stores its existing CID index as a `NavigableMap<Cid128, MysterIdentity>` and exposes a package-private left/right candidate walk for public-key identities.
- `MysterServerPool` now exposes `findClosestByCid(Cid128, int)` returning `IdentityNeighborSet` exact/left/right public-key identity groups filtered to currently up servers with up addresses.
- Added `ThreeDnsServerList` and `ThreeDnsFingerEntry` to retain two left and two right candidates around each local positive exponential offset target.
- `Tracker` now owns the optional 3DNS retained list, restores it before pool hard links are cleared, feeds it from refresh/ping/dead-server events, and exposes seed/snapshot/target accessors for Part 1b and Part 2.
- `Myster.java` now computes the local CID and passes it to `Tracker`.

## Files changed

- `src/main/java/com/myster/identity/Cid128.java`
- `src/main/java/com/myster/Myster.java`
- `src/main/java/com/myster/tracker/ExternalName.java`
- `src/main/java/com/myster/tracker/IdentityNeighborSet.java`
- `src/main/java/com/myster/tracker/IdentityTracker.java`
- `src/main/java/com/myster/tracker/MysterServerPool.java`
- `src/main/java/com/myster/tracker/MysterServerPoolImpl.java`
- `src/main/java/com/myster/tracker/Tracker.java`
- `src/main/java/com/myster/threedns/ThreeDnsFingerEntry.java`
- `src/main/java/com/myster/threedns/ThreeDnsServerList.java`
- `src/test/java/com/myster/identity/TestCid128RingMath.java`
- `src/test/java/com/myster/tracker/TestIdentityTracker.java`
- `src/test/java/com/myster/tracker/TestMysterServerPoolImpl.java`
- `src/test/java/com/myster/threedns/TestThreeDnsServerList.java`
- `docs/design/Myster 3DNS.md`
- `docs/conventions/myster-coding-conventions.md`

## Key decisions

- Kept the ordered CID index in `IdentityTracker`, as planned, and did not add a second CID map to `MysterServerPoolImpl`.
- Made `ExternalName` public because `MysterServer` already exposes it and 3DNS persistence needs to restore external names from `com.myster.threedns`.
- Stored 3DNS persistence as flat keys under the `ThreeDns` preferences node (`bit.N.left`, `bit.N.right`) so tests work with `MapPreferences`.
- `ThreeDnsServerList.snapshot()` retains restored entries even when currently down; `seeds(...)` and `forTarget(...)` filter to currently usable/up servers.
- Replaced Mockito usage in `TestMysterServerPoolImpl` with a concrete test protocol because this JDK/sandbox cannot reliably self-attach Byte Buddy.

## Deviations from the plan

- `Cid128` became an immutable final class with cached `hi`/`lo` fields rather than a public `record(long hi, long lo)`, preserving existing `new Cid128(byte[])` callers.
- `ThreeDnsServerList` considers each usable refreshed server for both retained sides, then side-specific distance sorting keeps the closest two per target/side.
- Part 1a does not add a 3DNS list-changed UI notification; that remains in Part 1b.

## Tests run

- `mvn -q -DskipTests compile` passed.
- `mvn -q -Djava.awt.headless=true -Dtest=TestCid128RingMath,TestIdentityTracker,TestMysterServerPoolImpl,TestThreeDnsServerList test` passed.
- `mvn -q -Djava.awt.headless=true test` was attempted. It reached 363 tests but failed for existing environment/unrelated issues:
  - Mockito/Byte Buddy cannot self-attach on this JDK/sandbox.
  - Some Java Preferences tests could not acquire file locks.
  - UDP socket bind tests could not bind sockets.

## Follow-up

- Part 1b should connect the `Tracker` 3DNS accessors to TrackerWindow/TypeChoice.
- Part 2 should add the public-key/address wire candidates and validation flow on top of `findClosestByCid`.
