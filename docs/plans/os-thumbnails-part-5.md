# OS thumbnails — Part 5: search-result thumbnails

> Dependency update: Part 3 now has one preview promise and a synchronized transfer block,
> with no shared-demand handles, priority scheduler or reserved worker slots. Revisit the shared
> cache/concurrency assumptions below before implementing this part; these are future design
> work, not APIs already provided by Part 3.

## Design Section (for the owner/reviewer)

### 1. Summary

Add remote thumbnails to visible search-result rows in each search tab using the Part 3 cache
and Part 4 visible-row controller. Requests use the result's server, exact opaque reference,
and the type captured when that search began. Only selected, visible tabs in visible windows
produce demand.

### 2. Non-goals

- Reinterpreting existing results after the type selector changes.
- Changes to search discovery, result sorting/comparison, download scheduling, wire formats,
  persistent caches, or server-side authorization.
- Requests for Generic, Audio, unknown, or future metadata profiles.

### 3. Assumptions & open questions

- `SearchResult` exposes host, exact name/reference, and protocol but not a type getter.
  `SearchTab` therefore retains a results-generation type snapshot.
- Existing stale-result suppression remains authoritative; thumbnail generation checks are added
  at the UI boundary.
- A hidden tab or minimized/hidden window has zero demand. Stopping or completing discovery does
  not withdraw thumbnails for results still displayed.

### 4. Proposed design

Capture the selected search type at search start and retain it for the result generation. Attach
the visible-row controller to each tab's result list and install a passive name-column renderer
with a fixed logical thumbnail slot. Each tab owns a thumbnail cache/lifecycle, while requests
are made through the injected protocol's stream.

Tab selection, window visibility/state, incoming results, sorting/model changes, replacement
searches, and disposal reconcile or invalidate demand. Recolumnization reinstalls the renderer
without changing existing search columns, comparisons, selection, or download behavior.

### 5. Architecture connections

| New / changed thing | Owned / created by | Called / used by | Connects to (existing) |
|---|---|---|---|
| Search thumbnail renderer | Search UI package | Search result name column | Existing text, focus, selection, and `SearchColumnDecorator` |
| Per-tab thumbnail cache/controller | `SearchTab` | Result model, viewport, tab lifecycle | Part 3 cache and Part 4 visible-row controller |
| Results type snapshot | `SearchTab` | Request adapter and eligibility | Search start/type choice and resolved metadata profile |
| Activity notifications | `SearchWindow` | Tab controllers | `JTabbedPane`, window show/hide/iconify/deiconify |

Each request is keyed by endpoint, captured type, and exact file reference, so same-named files
from different servers remain independent. Images are not stored in sortable metadata. Renderers
only look up completed images and never perform I/O or submit work.

### 6. Key decisions & edge cases

- Do not read the current type selector while rendering or requesting an existing row.
- New searches reset the demand/cache generation. Old callbacks cannot repaint replacement rows.
- Switching tabs withdraws outgoing demand and recomputes the incoming viewport. Hidden tabs may
  retain bounded cache state but never load.
- Closing a tab/window is terminal and idempotent; unregister listeners and close the cache.
- Search completion and Stop preserve displayed-result eligibility.
- Multiple visible search windows have independent caches and lifecycle state.
- Reinstall the renderer after `recolumnize()` or column recreation and reset all reused renderer
  state on every invocation.

### 7. Acceptance criteria

- [ ] Visible search-result icons follow the same type eligibility, visibility, sizing, and
      cancellation rules as client file-list thumbnails.
- [ ] Requests use each row's host, exact reference, and captured search-generation type.
- [ ] Hidden/deselected tabs and minimized/hidden windows make no requests; switching tabs
      transfers demand to the newly visible viewport.
- [ ] Incoming results and sorting reconcile only currently visible rows.
- [ ] Replacement searches, tab/window closure, and stale callbacks cannot attach an image to
      another row.
- [ ] Stopping or finishing discovery preserves thumbnails for displayed results.
- [ ] Type-selector edits do not reinterpret existing rows.
- [ ] Worker, queue, cache, and cache bounds hold during rapid scrolling and tab switching.
- [ ] Generic and Audio results never request thumbnails.

---
## ✦ IMPLEMENTATION DETAILS (for the implementation agent)
---

### 8. Affected files / classes

- **New:** `src/main/java/com/myster/search/ui/SearchThumbnailCellRenderer.java` — passive
  name-column thumbnail/fallback renderer.
- **Modify:** `src/main/java/com/myster/search/ui/SearchTab.java` — results-type snapshot,
  per-tab cache/controller, renderer lifecycle, and generation/disposal handling.
- **Modify:** `src/main/java/com/myster/search/ui/SearchWindow.java` — tab and window activity
  notifications.
- **Reuse from Parts 3–4:** cache/task, sizing/eligibility helpers, and visible-row controller.
- **Tests:** search identity, lifecycle, renderer, tab/window, and cache-bound tests.

### 9. Step-by-step implementation

1. Capture the selected type in `SearchTab` during `startSearch`/`searchStart` and clear or
   replace it when results are replaced. Build requests from that snapshot plus each result's
   `getHostAddress()` and exact `getName()` reference.
2. Attach the shared visible-row controller to `SearchTab.fileList`. Use the injected protocol's
   stream and the cache's list demand handle. Reconcile `addSearchResults`, sort/model events,
   and relevant `searchStats` updates.
3. Implement `SearchThumbnailCellRenderer` to preserve existing text, alignment, focus, selection,
   and column behavior while painting a fixed logical thumbnail slot. Do not initiate acquisition.
4. Reinstall the renderer after `recolumnize()` or column recreation. Keep `SearchColumnDecorator`
   values and comparisons unchanged; images must not enter sortable metadata.
5. Notify tab controllers from the `JTabbedPane` change listener and window show/hide,
   iconify/deiconify, and disposal events. A controller is active only when selected, showing,
   and its icon area is visible.
6. Ensure adding/restoring tabs while hidden makes no requests. `stopSearch` and discovery
   completion leave displayed rows eligible. New searches reset generation/demand/cache.
7. Make `SearchTab.dispose` idempotent, close its cache/controllers, and unregister listeners.
   Prevent old callbacks from repainting replacement rows or repopulating disposed tabs.
8. Write `docs/impl_summary/os-thumbnails-part-5.md` after focused tests and multi-host/tab
   smoke checks.

### 10. Tests to write

- Search controller/`SearchTab` tests with same-named files on different hosts, exact references,
  captured type after selector edits, incremental results, and sorting.
- Block transfers while switching tabs; verify hidden/minimized/deselected tabs have zero demand,
  restored tabs recompute demand, and closing twice is safe.
- Verify replacement searches reject old callbacks, Stop/completion preserve visible results,
  and multiple visible windows do not share lifecycle state.
- Renderer tests cover recolumnization, reset of reused state, unchanged sorting/download
  selection behavior, and no acquisition from paint.
- Generic and Audio result tests must produce no thumbnail requests.
- Run existing search/list/thumbnail tests headlessly.

### 11. Docs / Javadoc to update

- Document `SearchTab`'s results-type snapshot, thumbnail visibility lifetime, generation
  invalidation, and idempotent cleanup.
- Document renderer passivity and preservation of existing search-column behavior.
- Update the OS thumbnail design document and `docs/impl_summary/README.md`.
