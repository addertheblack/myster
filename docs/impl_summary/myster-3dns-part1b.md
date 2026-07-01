# Myster 3DNS Part 1b Implementation Summary

## Summary

Implemented the TrackerWindow 3DNS UI bridge from `docs/plans/myster-3dns-part-1b.md`.

TrackerWindow now has a selectable 3DNS view, persists/restores that selection through the existing `SELECTED_TYPE` / `SELECTED_ITEM` window preference flow, and displays retained 3DNS servers using the existing `MysterServer` table.

## Files Changed

- `src/main/java/com/myster/util/TypeChoice.java`
  - Added the selectable `3DNS` extra item beside LAN and bookmarks.
  - Added `isThreeDns()` and `selectThreeDns()`.
  - Kept `getType()` empty for 3DNS because it is not a `MysterType`.
  - Preserved 3DNS selection across type-list rebuilds.

- `src/main/java/com/myster/tracker/Tracker.java`
  - Added `ListChangedListener.threeDnsServerAddedRemoved()`.
  - Wired `ThreeDnsServerList` change callbacks into the tracker dispatcher.
  - Added `getAllThreeDns()` for UI/debug display.

- `src/main/java/com/myster/tracker/ui/TrackerWindow.java`
  - Added the `THREE_DNS` persisted selection marker.
  - Restores 3DNS through `choice.selectThreeDns()`.
  - Saves 3DNS through the existing `WindowPrefDataKeeper` path.
  - Displays `tracker.getAllThreeDns()` when 3DNS is selected.
  - Reloads only the selected 3DNS view on 3DNS list changes.

- `src/test/java/com/myster/util/TestTypeChoiceThreeDns.java`
  - Added focused coverage for 3DNS selection and rebuild preservation.

- `docs/plans/myster-3dns-part-1b.md`
  - Tightened the implementation notes around existing TrackerWindow selection persistence.

## Key Decisions

- `Tracker.getAllThreeDns()` returns unique `MysterServer` rows in snapshot order. The raw 3DNS snapshot may contain the same server in many finger slots, but the existing table has no target/slot columns, so duplicate rows would not be useful in this first UI bridge.
- 3DNS uses the existing TrackerWindow table and bookmark context menu behavior. No custom columns or protocol/debug diagnostics were added.
- The 3DNS list-change notification was added to the existing `ListChangedListener` rather than introducing a separate listener type.

## Notable Cleanups

- Fixed TrackerWindow type-list reload detection while touching the same listener block. The old check compared a `MysterType` to `Optional<MysterType>`, so it could not match selected real types.
- Fixed TrackerWindow saved-type restore iteration to use `choice.getItemCount()`. The previous loop stopped at the first empty `getType(i)`, which is wrong now that `TypeChoice` contains non-selectable headers.
- Replaced the saved-item reference comparison against `""` with `isEmpty()`.

## Tests

Passed:

```bash
mvn -q -Djava.awt.headless=true -Dtest=TestTypeChoiceThreeDns,TestThreeDnsServerList test
mvn -q -Djava.awt.headless=true -Dtest=TestCid128RingMath,TestIdentityTracker,TestMysterServerPoolImpl,TestThreeDnsServerList,TestTypeChoiceThreeDns,TestMapPreferences test
mvn -q -Djava.awt.headless=true -Dtest=TestTypeChoiceThreeDns test
```

Attempted full suite:

```bash
mvn -q -Djava.awt.headless=true test
```

The full suite reached 369 tests but failed in unrelated legacy/environment areas:

- `TestMultiSourceDownload` writes under `/home/andrew/.myster/Incoming` and reports the partial file as read-only.
- `TestCustomTypeManager` has one delete assertion failure and several `java.util.prefs.FileSystemPreferences` file-lock errors.
- `TestAsyncDatagramSocket` cannot bind its UDP socket in this environment.

## Follow-Up

- Manual UI smoke check is still useful: open TrackerWindow, select 3DNS, verify empty/retained rows render normally, close/reopen, and confirm the selection restores.
- Later 3DNS UI work can add target bit/side/CID columns if duplicate slot-level inspection becomes important.
