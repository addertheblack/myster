# Tracker 3DNS Target Slots UI Implementation Summary

## Summary

Implemented the TrackerWindow 3DNS target-slot inspection UI from `docs/plans/tracker-3dns-target-slots-ui.md`.

The 3DNS category now uses a dedicated panel backed by immutable target-slot snapshots and denormalized row objects, so the table can show target CID, offset, side, server CID, and server status without forcing 3DNS into the generic `MysterServer` tracker list.

## Files Changed

- `src/main/java/com/myster/threedns/ThreeDnsTargetSlotSnapshot.java`
  - Added an immutable structural snapshot for one retained 3DNS target slot.
  - Copies left/right entry lists and exposes a combined `entries()` view.

- `src/main/java/com/myster/threedns/ThreeDnsServerList.java`
  - Added `snapshotTargetSlots()` while keeping mutable `TargetSlot` private.
  - Preserved the existing flat `snapshot()`, seed, and target lookup behavior.

- `src/main/java/com/myster/tracker/Tracker.java`
  - Added `getThreeDnsTargetSlots()` for UI/debug inspection.

- `src/main/java/com/myster/tracker/ui/ThreeDnsTrackerRow.java`
  - Added the denormalized row record used by the 3DNS MCList.
  - Keeps a first-class `MysterServer` plus copied target CID, server CID, bit index, side, retained address, and update time values.

- `src/main/java/com/myster/tracker/ui/TrackerThreeDnsPanel.java`
  - Added the dedicated 3DNS table panel outside `TrackerWindow`.
  - Implements 3DNS-specific columns, row flattening, bookmark context actions, double-click open behavior, and refresh handling.
  - Renders offset labels with Swing HTML superscripts while sorting by numeric bit index.

- `src/main/java/com/myster/tracker/ui/TrackerWindow.java`
  - Added a `CardLayout` holder for the existing generic server list and the new 3DNS panel.
  - Kept 3DNS-specific table logic out of `TrackerWindow`; it only switches cards and delegates load/refresh.
  - Added a null guard to the generic list refresh path for component-show lifecycle safety.

- `src/test/java/com/myster/threedns/TestThreeDnsServerList.java`
  - Added coverage for target-slot snapshot count, bit order, target CID derivation, and immutable lists.

- `docs/plans/tracker-3dns-target-slots-ui.md`
  - Authoritative plan used for this implementation.

## Key Decisions

- The 3DNS UI stores denormalized `ThreeDnsTrackerRow` objects in its MCList rather than raw `MysterServer`, raw `ThreeDnsFingerEntry`, or target-slot snapshots.
- `ThreeDnsServerList.TargetSlot` remains private; the public boundary is immutable `ThreeDnsTargetSlotSnapshot`.
- The existing generic tracker list still handles file-type, LAN, and bookmark views.
- Offset display is presentation-only: target CID remains authoritative, bit index is used for sorting, and the label is rendered as `+2` with a superscript bit-index exponent.
- Tiny status/ping/bookmark sortables were duplicated in the 3DNS panel to keep the implementation scoped and avoid refactoring `TrackerWindow` internals during this feature.

## Deviations From Plan

- No automated Swing test was added for `TrackerThreeDnsPanel`; current UI test infrastructure does not make that practical without broader harness work. Manual smoke checks remain recommended.
- `OpenConnectionHandler` was left generic-list-only. The 3DNS panel implements its own double-click handling because it opens from `ThreeDnsTrackerRow.retainedAddress()`.

## Docs / Javadoc

- Added Javadoc for `ThreeDnsTargetSlotSnapshot`, `ThreeDnsServerList.snapshotTargetSlots()`, `Tracker.getThreeDnsTargetSlots()`, `ThreeDnsTrackerRow`, and `TrackerThreeDnsPanel`.
- Reviewed `docs/design/Myster 3DNS.md`; no update was needed because it describes core routing/maintenance behavior and does not document the tracker inspection UI.

## Tests

Passed:

```bash
mvn -q -Djava.awt.headless=true -Dtest=TestThreeDnsServerList test
mvn -q -Djava.awt.headless=true -Dtest=TestCid128RingMath,TestIdentityTracker,TestMysterServerPoolImpl,TestThreeDnsServerList,TestTypeChoiceThreeDns,TestMapPreferences test
```

Attempted full suite:

```bash
mvn -q -Djava.awt.headless=true test
```

The full suite reached 370 tests but failed in unrelated legacy/environment areas:

- `TestMultiSourceDownload` cannot create `/home/andrew/.myster/Incoming/testFilename.p` because the file appears read-only.
- `TestCustomTypeManager` has one delete assertion failure and several `java.util.prefs.FileSystemPreferences` file-lock errors.
- `TestAsyncDatagramSocket` cannot bind its UDP socket in this environment.

## Manual Smoke Checks

Still recommended:

- Open TrackerWindow, select `3DNS`, and verify the dedicated columns render.
- Confirm target CID, superscript offset, side, server CID, server name, address, status, ping, and uptime populate.
- Confirm the same server can appear multiple times for different target slots.
- Double-click a 3DNS row and verify it opens a client window for that retained address.
- Use bookmark and remove-bookmark context menu actions on a 3DNS row.
- Switch between 3DNS, LAN, bookmarks, and a file type and confirm each view reloads correctly.
