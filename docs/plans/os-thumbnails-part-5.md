# OS thumbnails — Part 5: search-result thumbnails and hover previews

> **Related plan:** [Thumbnail transfer protocol](./os-thumbnails-part-2.md) — depends on: reuses section 79 and existing image-size bounds.

> **Related plan:** [Client preview pane](./os-thumbnails-part-3.md) — predecessor: supplies cancellable remote acquisition and preview sizing.

> **Related plan:** [Client file-list thumbnails](./os-thumbnails-part-4.md) — predecessor: supplies the working visible-row loader to adapt for flat search lists.

> **Related plan:** [Tabbed Search Window](./tabbed-search-window.md) — follow-up: adds thumbnail demand and disposal to each existing search tab.

> **Related plan:** [Split Type Columns from Search-Only Columns](./split-type-columns-from-search-columns.md) — follow-up: decorates the name renderer while preserving type, Server and Ping columns.

> **Related plan:** [Metadata Type Registry Refactor](./metadata-type-provider.md) — depends on: uses the selected type's explicit metadata profile for eligibility.

> **Related plan:** [Custom Myster Type Metadata Profile](./custom-type-metadata-profile.md) — depends on: custom Image and Video types qualify like built-in types.

> **Related plan:** [Arbitrary Display Scaling](./ui-display-scaling.md) — depends on: derives request sizes from actual monitor scale.

> **Related plan:** [Myster resource links](./myster-resource-links.md) — follow-up: preserves expected-public-key identity in thumbnail requests.

> **Related plan:** [Peer-to-Peer Connection Server Picker](./peer-to-peer-server-picker.md) — context: supplies existing server-pool statistics/key resolution informing the connection bootstrap prerequisite.

> **Related plan:** [AsyncTaskTracker completion context](./async-task-tracker-context.md) — context: consumer cancellation preserves independently owned work and rejects stale callbacks.

Implemented from the approved plan. See the [implementation summary](../impl_summary/os-thumbnails-part-5.md)
for validation and remaining desktop/multi-peer checks.

## Design Section (for the owner/reviewer)

### 1. Summary

Show thumbnails beside eligible visible search results in every search tab, reusing the connection
window's loader, remote cache, protocol and icons. Add a larger hover preview. Preserve existing
rows, columns, sorting, selection and download actions, with drawing outside the table widget.

### 2. Non-goals

- New wire formats, OS providers, original-file downloads, persistent caches or outside-viewport prefetch.
- Background-tab loading, larger result rows, global caches, parallel transfers per tab or pooling
  connections across successive thumbnail requests.
- Refactoring `TreeMCList` drawing; the tree widget remains unchanged.
- Implementing future hostname/CID/public-key address variants or migrating all datagram callers.

### 3. Assumptions & open questions

- Owner-confirmed: 500 ms hover, maximum logical image side 192, existing 256-device-pixel cap,
  visible-only row demand, stable result identity and external icon rendering.
- Retain the existing 75 ms viewport settling delay and current row height.
- Only Image/Video profiles qualify, including custom/private types. Other profiles keep their
  current text-only appearance. Eligible files use the generic file icon until an image exists.
- Current search producers give every result the engine's injected protocol; one tab cache can
  therefore use that stream for many endpoints.
- The reported intermittent stall has an unknown original trigger. A specific stalled-image
  condition was reproduced in the debugger and fixed: a visible item's success flag survived
  eviction of its pixels from the shared cache. Keep the TODO for checking the original symptom.
- Further real-peer and mixed-monitor smoke testing remains useful; automated tests cannot
  establish that every OS/remote server can produce a thumbnail.

### 4. Proposed design

**Share the loader, preserve the widget.** Move the connection-window algorithm into an
EDT-owned visible-row controller accepting ordinary MCList items, request mapping and icon bounds.
`ClientWindow` adapts its existing tree provider and bounds; search supplies an external cell
renderer. Neither `JMCList` nor `TreeMCList` gains thumbnail code.

The bounds describe where the icon is actually drawn. Tree indentation moves that slot sideways;
a flat table puts it at the name cell's leading edge. Load only when the slot intersects the
viewport, including partially visible rows. Hidden icon columns, deselected tabs and hidden or
minimized windows have no row demand. Painting only reads available pixels.

**Keep the tab's identity stable.** Capture the type, profile and column handler at search start.
Wrap engine callbacks with that generation so queued old start/finish/results/status notifications
cannot affect a replacement search. Stop and completion preserve displayed demand. The tab's
support object owns its cache, list/hover consumers and item-key snapshots; window events supply
activity. Replacement resets the generation; closing is terminal and idempotent.

**One cache already supports many servers.** Its key includes endpoint, type, opaque reference,
size and expected public key. Unsupported-server outcomes also include endpoint/key. Each result
supplies its own endpoint/reference; changing visible hosts never resets the cache. Keep one
list future and one hover future sharing the cache's transfer monitor. Cache limits remain 512
outcomes and 8 MiB decoded pixels, with 30-second misses and 5-second transport failures.

**Resolve identity inside connection establishment.** An explicit expected key takes precedence.
Otherwise consult verified/local and existing server-cache keys. On a miss, open one internal TLS
socket, fetch server stats on it, compare the reported key with its authenticated peer key and
return that same verified socket for the requested operation. Transport creation bounds TCP
connect to 30 seconds and sets a 120-second socket read timeout; stats discovery keeps those settings.
Failure closes it. This calls the open-socket stats section directly, avoiding recursive pool
refresh. `MysterProtocol` exposes the local public key initialized for its transports.

An initially address-only thumbnail request carries an empty optional expected key. Its worker
uses this connection path and observes the authenticated peer key before requesting the image.
The cache publishes pixels/suppression under a request containing that verified key, remembers a
bounded address-to-key binding for its generation, and search retains resolved keys per item.
This avoids a separate discovery socket or a search-owned stats/key resolver.

**Draw with a normal external cell renderer.** A generic icon-and-text renderer extends Swing's
standard table renderer. Callbacks provide the current item and passive icon; empty output keeps
text-only layout. Search supplies a cached thumbnail or themed file SVG. The renderer also
supplies exact slot bounds to the loader. Reinstall it after column rebuilding. All sortable
values, Server/Ping columns and selection behavior stay in their existing components.

**Hover independently.** After 500 ms over any cell of an eligible on-screen row, request up to
192 logical units through the same cache. Show a cached small image while upgrading, or wait until
pixels exist. Use a non-focusable image tooltip with explicit preferred size, stable popup bounds
and usable-monitor clamping. Do not select the row. Row/viewport/model changes, clicking, Escape,
owner deactivation, hiding and closing dismiss/cancel hover; stale callbacks cannot reopen it.

### 5. Architecture connections

Visible row items become request data in the search/client adapter. The shared controller loads
one missing image after settling, receives its result on the EDT and repaints the actual current
item. It retains pixels for current visible demand so LRU eviction cannot strand a success flag.
The renderer only reads those pixels. Hover is a second independent consumer of the same cache.

| New / changed thing | Owned / created by | Called / used by | Connects to (existing) |
|---|---|---|---|
| Shared visible-row controller | `ClientWindow` or per-tab support; borrows cache | Viewport/model/geometry/activity events | Former client loader, MCList items, promises, item repaint |
| Generic icon/text renderer | Installed by per-tab support on name column | Standard table painting and slot visibility lookup | Swing renderer styling and passive `Icon` callbacks |
| Search support | Created/closed by `SearchTab` | Generation, activity, columns and stats events | Shared loader, renderer, cache, protocol stream, hover |
| Per-owner remote cache | Client window or per-tab support | Row and preview/hover consumers | Existing transfer task/monitor and bounded memory cache |
| Verified connection setup | `MysterStreamImpl`, created at application startup | Blocking worker operations, including thumbnails | Existing TLS factory, key-cache lookup, open-socket server stats |
| Local public key | Immutable protocol aggregate, injected at startup | Consumers of protocol identity | Same initialized identity used by transports |
| Hover controller/image tooltip | Per-tab support | Pointer and owner/list lifecycle events | Shared cache, Swing popup/tooltip, thumbnail icon |
| Tab activity | `SearchWindow` | Selected tab and window lifecycle | Existing tabbed pane, visibility, frame state and monitor changes |

No wire or disk-format changes. Section 79 retains its 256-device-pixel maximum side and 256 KiB
body limit. A 192-logical-unit tooltip requests 192 pixels at 1× and at most 256 at larger scales.
The optional key is client-side request state, not a new protocol field.

### 6. Key decisions & edge cases

- Identity and endpoint remain distinct. Known keys never downgrade to an address-only retry.
  Cache/source discovery happens in protocol code, not through the nullable result server object.
- Current `MysterAddress` remains an eagerly resolved endpoint. Future hostname/IP/CID/public-key
  targets belong behind the protocol boundary; UI does not parse target display strings.
- Explicit-key TLS verification is performed by the existing factory. Address-only bootstrap
  compares stats identity with the TLS peer before returning the connection or publishing pixels.
- Each consumer cancels only its future. Owners reset/close caches after cancelling consumers.
  The transfer monitor survives reset; cancelled running I/O may finish normally.
- Async callbacks check future/generation identity. Settling and cancellation clear ownership
  before cancellation dispatch. Model reconciliation waits until the model has settled.
- Visible image references survive cache eviction. Failures do not trigger repaint retry loops;
  new visible demand can retry under the cache's TTL rules.
- No extra scheduler, discovery future, all-results queue or strict hover priority is added.
  A slow transfer can delay another visible peer in the same tab until completion/timeout.
- Every listener is removed on close. Escape works while the table or another window component
  has focus, and previous bindings are restored.
- The tree adapter derives the actual file slot from existing composite bounds; the tree widget
  itself stays unchanged.

### 7. Acceptance criteria

- [x] Eligible visible search rows display aspect-fitted thumbnails at existing row height, with
      generic file fallback and unchanged sortable values/selection/download actions.
- [x] Audio/Generic/unknown profiles remain text-only and create no thumbnail demand.
- [x] Only on-screen icon slots load; inactive tabs/windows withdraw demand, and painting is passive.
- [x] Endpoint/type/reference/key scope separates same-named files and unsupported peers.
- [x] Address-only connections use cache-first key discovery with same-socket stats verification;
      discovery failure prevents thumbnail execution and cannot recurse through pool refresh.
- [x] Type/profile/columns and callbacks belong to a search generation; Stop/completion retain
      displayed demand while replacement/disposal invalidate old callbacks.
- [x] Hover upgrades pixels through the shared cache without selection, dismisses on relevant
      lifecycle events and has bounded, correctly painted native popup geometry.
- [x] Fractional scale sizing and protocol limits use existing helpers; tree drawing is unchanged.
- [x] Stationary-viewport progress and the reproduced visible-image eviction bug have regression tests.
- [ ] Confirm the original reported intermittent stall is gone in the owner's triggering scenario.
- [ ] Complete live multi-peer/private-type and mixed-monitor desktop smoke checks.

---
## ✦ IMPLEMENTATION DETAILS (for the implementation agent)
---

### 8. Affected files / classes

- **Moved/generalized:** `client/ui/ClientFileThumbnailController.java` becomes
  `thumbnail/ui/VisibleRowThumbnailController.java` — generic items/geometry, retained pixels,
  guarded progress, column/viewport/model listeners and borrowed-cache cleanup.
- **Changed:** `client/ui/ClientWindow.java` — existing tree request/provider adapter and owner cache closure.
- **New:** `general/mclist/IconTextCellRenderer.java` — passive reusable column renderer/slot layout.
- **New:** `search/ui/SearchThumbnailSupport.java` — per-tab composition, identity snapshots and renderer install.
- **New:** `search/ui/SearchThumbnailHoverController.java` — dwell, image tooltip, positioning and cleanup.
- **Changed:** `search/ui/SearchTab.java`, `SearchWindow.java` — generation and activity lifecycle.
- **Changed:** `thumbnail/RemoteThumbnailCache.java`, `RemoteThumbnailTask.java` — optional pending
  key, canonical verified image keys, bounded key bindings and generation guards.
- **Changed:** `net/client/MysterStream.java`, `net/stream/client/MysterStreamImpl.java` — same-socket
  connection discovery and verification with transport-owned timeouts. Cached-key lookup stays private to
  connection establishment; the stream facade exposes no key-cache query.
- **Changed:** `net/TLSSocket.java`, `net/stream/client/StandardSuiteStream.java` — retain the
  standard TCP connect/read timeouts configured during transport creation. Server stats does not
  change timeouts. Per-operation watchdogs and idle-socket tracking are outside this policy.
- **Changed:** `net/client/MysterProtocol.java`, `MysterProtocolImpl.java`, `Myster.java` — injected local key.
- **Changed:** `net/datagram/client/PublicKeyLookupImpl.java` — safe startup cache miss before pool binding.
- **Unchanged:** `TreeMCList`, `JMCList`, `SearchColumnDecorator`, wire codecs and OS thumbnail providers.
- Package paths above are relative to `src/main/java/com/` (with `myster/` for Myster packages).

### 9. Step-by-step implementation

1. Reproduce the visible-image loss using the original controller, debugger and a controlled
   successful transfer with no remaining cached image. Observed state: success=true, cached=false,
   pending=false. Retain successful pixels for current demand when extracting the controller.
2. Implement explicit-key/cache/JIT stats branches in `MysterStreamImpl`. Use its low-level
   connection factory once and `getServerStats(socket)` directly on a miss, compare keys, retain
   transport timeouts, cache verified identity and close on failure. Bound discovered identities to 512.
   Read the existing server cache via injected `PublicKeyLookupImpl.getCached`, not pool refresh.
3. Extend the remote request's expected key to `Optional<PublicKey>` with a pinned-key constructor
   for existing callers. The task exposes its verified key after completion. Canonicalize cache
   writes/suppression before consumer callbacks, bind address-only targets within the generation,
   and reject old-generation cache callbacks. Bound resolution bindings to 512.
4. Extract the controller with an injected `RequestFactory` over `MCListItemInterface`,
   `IntFunction<Rectangle>` icon bounds and active supplier. Check activity before enumeration.
   Keep one owned future; use one outcome listener, guarded delay/future identity and later-EDT
   continuation. Retain current successful images and missed outcomes; drop departed demand.
   Borrow the cache, detach every listener on close, and provide an explicit consumer reset.
5. Migrate the client adapter/provider and explicit owner cache closure. Derive the actual file
   slot from existing `TreeMCList.getItemIconBounds` by skipping its chevron slot and clipping to
   the name cell. Keep the existing async remote metadata adapter. Do not edit the tree widget.
6. Install `IconTextCellRenderer` through `TableColumn.setCellRenderer`. Preserve Swing's text,
   selection, focus, border and orientation; use Swing label layout for actual clipped slot bounds.
   Search supplies generic fallback SVGs or completed `ThumbnailIcon`s and no-op text-only output.
7. Create one support object per tab. It borrows the engine/results' stream, owns cache/row/hover
   consumers and pins each item's resolved key. Unknown-key requests go through the normal
   thumbnail worker/connection, so no separate UI identity preparation path is necessary.
8. Capture type/profile/column handler before `SearchEngine.run()`. Wrap listener/status callbacks
   with generation guards. Clear/reset at replacement; preserve demand on Stop/completion.
   Install the renderer after column changes and on structure events.
9. Notify tab activity from selection and window visibility/state/graphics-configuration changes.
   Treat the trailing disabled add tab safely. List showing state additionally gates demand.
10. Add independently cancellable 500 ms hover; use row/cached pixels while loading a larger image.
    Explicitly size the image-only `JToolTip` because its text UI otherwise reports zero size.
    Fit within 192 logical units, clamp popup position, keep it clear of the pointer and retain
    stable geometry during upgrades. Dismiss/cancel on exit/change/click/scroll/model/owner events.
    Observe wheel events on the scroll pane rather than capturing them on the table, preserving
    native wheel forwarding. Restore both focused and focused-window Escape bindings on close.
11. Verify focused and full suites with isolated test data and local sockets. Run the optional native
    popup smoke test with a desktop, then update design, conventions, TODO and the summary.

### 10. Tests to write

Implemented regression coverage:

- `src/test/java/com/myster/thumbnail/ui/TestVisibleRowThumbnailController.java`: visible pixels
  survive eviction, completion/miss/failure progress without scrolling, visibility gating,
  sorting/cancellation and borrowed-cache cleanup.
- `src/test/java/com/general/mclist/TestIconTextCellRenderer.java`: icon/text reset, original values,
  selection, leading orientation and narrow-column bounds.
- `src/test/java/com/myster/search/ui/TestSearchThumbnails.java`: multi-server loading with null
  cached server objects, ineligible profiles, hidden tabs, renderer reinstallation and close.
- `src/test/java/com/myster/search/ui/TestSearchTabThumbnailLifecycle.java`: real tab UI with
  controlled engines; selector edits, captured columns/type, old start/finish/results, Stop and disposal.
- `src/test/java/com/myster/search/ui/TestSearchThumbnailHoverController.java`: any-cell dwell,
  small-to-large upgrade, unchanged selection/location, cancellation, misses, window-level Escape,
  cleanup, monitor clamping and actual scroll-bar movement from wheel events over the table.
- `src/test/java/com/myster/search/ui/TestSearchThumbnailGui.java`: optional desktop test for the
  actual painted popup, image pixels, no row selection and dismissal; skipped when headless.
- Extend `TestMysterStreamImpl`, `TestMysterProtocolImpl`, `TestRemoteThumbnailCache`: explicit/cached/
  discovered keys, same socket, timeout/failure cleanup, mismatches, local key and peer isolation.
- `TestTLSSocketTimeouts`: explicit TCP connect timeout, read timeouts installed before protocol
  negotiation/TLS handshake, and connection failure cleanup.
- Reuse existing task, protocol, client-preview, tree, scaling and search suites for regressions.

Validation uses Maven with `-Djava.awt.headless=true` plus an isolated `user.home` and preferences
root. Network tests need permission to bind loopback sockets. Optional native smoke uses
`-Djava.awt.headless=false -Dtest=TestSearchThumbnailGui test` on an available desktop.

Manual follow-up: live mixed Image/Video/custom/private results across peers; rapid scroll/sort,
hover/context menus/downloads, tab/window hide/minimize/restore and multiple display scales.
Recheck the original stall when its triggering sequence is known.

### 11. Docs / Javadoc to update

Documented the shared controller's passive/borrowed contracts, request key lifecycle, common
connection discovery, captured search generation, reusable renderer and hover cleanup. Update
`docs/design/Myster OS Thumbnail Integration Design.md`, conventions, codebase/index navigation,
`TODO.txt` and `docs/impl_summary/os-thumbnails-part-5.md` with actual validation and limitations.
