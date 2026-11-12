# Search for Servers by Name and Description

Implemented [the plan](../plans/server-name-description-search.md). Each search tab now has a
persisted Files/Servers dropdown, defaulting to Files. Server mode matches name or description
within the selected type’s network and shows Server, Description, Address, Files and cached Ping.
Double-click/Open Server uses ClientWindowProvider with the captured identity/type and no filename,
so normal identity-based window reuse remains authoritative.

## Implementation and ownership

- Added `search/ServerSearch.java`: finite, cancellable search using AsyncNetworkCrawler,
  IPQueue and AsyncTaskTracker. Seeds come only from the selected type’s tracker list; top-ten
  requests remain scoped to that type. Onramps handle sparse/empty bootstrap. Each encountered
  address checks Tracker.getQuickServerStats before resolving a cache miss through KnownServerSource.
  There is no full-pool enumeration and no eager Tracker.addIp callback to race the cache check.
- Added immutable `search/ServerSearchResult.java`, `search/ui/SearchMode.java`, and
  `search/ui/ServerSearchResults.java`. Results deduplicate by identity, allow zero-file advertised
  types, match case-insensitively in either field, and retain full descriptions with tooltips.
  Blank input matches all eligible encountered servers. Cached blank descriptions/down status
  do not trigger refresh. Numeric file/ping sorting does not request active pings.
- Updated SearchTab to own one cancellable operation and separate file/server result panes.
  Input edits prepare the next search; displayed mode/type and actions stay attached to their
  search. Stop cancels the originating operation and retains results. Switching to Servers resets
  file thumbnail demand/cache and suspends acquisition; returning to Files restores normal behavior.
- Updated SearchWindow to save stable `files`/`servers` values in `TabMode_<index>`. Missing or
  unknown values restore Files. Restoration selects the correct empty pane without running a search.
- Updated resolver/source Javadoc, the codebase search overview, integration conventions, plan
  index and plan status. Reviewed server-stats/resource-link design contracts: no wire or client
  navigation contract changed, so those design documents needed no behavioral revision.

## Decisions and deviations

File matching and the SearchResult contract remain unchanged. A separate server result model avoids
fake file metadata/download behavior. Server-search results/progress are batched on one search
invoker and dispatched by ServerSearch to the EDT. At dispatch it checks the returned promise’s
existing cancellation state, even if the worker completed before cancellation. Completion listeners
use the promise’s normal EDT dispatch. The promise also owns dynamic child cancellation; shared
pool refreshes remain pool-owned.

Following owner review, removed SearchTab’s `generation`, `forGeneration` and `listenerFor` machinery.
MysterSearch now checks its existing endFlag when delivering every listener/status callback on the
EDT, including queued startup, stream results/stats and natural completion. Cancellation is
idempotent, suppresses queued delivery even after natural completion, and does not emit searchOver.
SearchTab updates its stopped state directly. Its callbacks need no second cancellation flag,
session token or generation counter. Callback dispatch is synchronized with off-EDT file-search
cancellation to preserve the existing any-thread API contract.

Seed preparation always launches one counted task, so an empty bootstrap naturally reaches the
tracker’s exhaustion callback rather than needing a separate zero-task completion branch. Existing
thumbnail generation reset with the generic profile was sufficient; no ThumbnailSupport API change
was needed. The server pane uses bulk list insertion and a local cached-ping sortable instead of
the file-search SortablePing, whose constructor initiates network work. These are implementation
choices within the plan’s behavior.

## Verification

- Added 21 server backend tests covering matching/missing fields, locale, selected-type scope, no pool
  enumeration, cache hits/misses, corrected endpoints, loops, identity deduplication, zero/unknown
  types, bootstrap/exhaustion, foreign invokers, cancellation, large batched delivery, independent
  neighbor discovery after stats failure, progress with no matches, and cancellation on the EDT
  after the worker has completed but before queued result batches are delivered.
- Added four MysterSearch tests for cancelled queued startup/progress, pending UDP delivery,
  queued stream callbacks, and exactly-once natural completion on the EDT. Added a real-engine
  tab replacement test proving only the current search emits startup/completion without tab
  generation state. Tests that directly called cancelled mock engines’ raw UI listeners were
  replaced with producer-side cancellation tests; raw callbacks now belong to that contract.
- Added server-table tests for columns/full-description tooltip, cached values, invalid selection,
  repeated activation, bookmarks and address-only opening.
- Added tab/mode tests for defaults, preference conversion/restoration, displayed/input separation,
  stale callbacks, Stop/disposal, file-column restoration, thumbnail inactivity in Servers, and
  successful completion preserving final rows. Adapted the existing thumbnail lifecycle test to
  locate the file table inside the new result-pane container.
- Final focused search/UI/provider/pool regression suite after cancellation cleanup: **88 tests
  passed**, no failures/errors/skips.
- Final full suite with isolated user data/preferences and local socket access: **1,023 tests run,
  0 failures, 0 errors, 1 skipped**. Isolation used a fresh `/tmp` directory for `user.home`
  and `java.util.prefs.userRoot`, with the existing Maven dependency cache.
- The initial sandbox-only full run hit normal-user-data/preferences write restrictions and
  loopback socket restrictions; these cleared in the isolated full run. No unrelated test changes
  were needed.
- Independent implementation review found one progress issue (nonmatches did not update the
  checked count). Fixed it and added a pending-top-ten regression. Re-review found no remaining
  actionable issues. The subsequent engine-owned cancellation cleanup also received independent
  review with no actionable findings.
- `git diff --check` passed, including new files checked separately before handoff.

## Remaining manual checks

Live-network/desktop smoke checks remain: mixed-mode window restoration, opening/raising real peers
through alternate endpoints, cache-hit packet observation, file-download/thumbnail gestures, and
layout/tooltips at fractional/high display scales. Headless tests cover the underlying actions and
state transitions; no known automated-test failure remains. The suite’s existing skipped test is
unchanged. No further production/test work is deferred within the implemented scope.
