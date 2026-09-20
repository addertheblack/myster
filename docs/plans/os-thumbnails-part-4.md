# OS thumbnails — Part 4: client file-list thumbnails

> Dependency update: Part 3 now has one preview promise and a synchronized transfer block,
> with no shared-demand handles, priority scheduler or reserved worker slots. Revisit the shared
> cache/concurrency assumptions below before implementing this part; these are future design
> work, not APIs already provided by Part 3.

## Design Section (for the owner/reviewer)

### 1. Summary

Add remote thumbnails to the existing client file list by reusing Part 3's connection-scoped
thumbnail cache. Only eligible file rows whose icon area intersects the visible viewport
request images. Folders, collapsed descendants, offscreen rows, and horizontally hidden icon
slots remain unchanged.

### 2. Non-goals

- Preview-pane implementation (Part 3) or search-result thumbnails (Part 5).
- Prefetching outside the viewport, persistent caches, new wire formats, or changes to file
  listing, sorting, downloading, or server authorization.
- Thumbnail requests from renderers or for Audio, Generic, unknown, or future profiles.

### 3. Assumptions & open questions

- The existing `TreeMCList` name-column renderer remains responsible for text, indentation,
  selection, folders, and chevrons.
- Part 3's cache, task, eligibility, sizing, cache, and cancellation contracts are available.
- Partially visible rows count as visible. A row is eligible only when its actual icon square
  intersects the viewport.
- `TreeMCListItem.getObject()` is the opaque protocol reference; display basenames must never
  be sent as file identifiers.

### 4. Proposed design

Add a generic `VisibleThumbnailController<E>` that enumerates visible rows from viewport
geometry rather than scanning the full model. It reconciles the current visible demand on
viewport, model, sorter, hierarchy, layout, row-size, and lifecycle changes. New work starts
after a restartable 75 ms settle delay; abandoned work is removed or cancelled immediately.

Add a passive `ThumbnailIcon` and an optional leaf-icon resolver to `TreeMCList`. Renderers
only look up completed images and paint them into a fixed logical square. The client window
shares the Part 3 cache, with at most two list workers and the preview slot reserved.

### 5. Architecture connections

| New / changed thing | Owned / created by | Called / used by | Connects to (existing) |
|---|---|---|---|
| `VisibleThumbnailController<E>` | Client file-list integration | Viewport/model/lifecycle listeners | `JMCList`, `JScrollPane`, Part 3 demand handle |
| `ThumbnailIcon` | Thumbnail UI package | Tree renderer | `Graphics2D`, completed cache images |
| Leaf-icon resolver | `TreeMCList.create` caller | Tree leaf renderer | Existing folder/file defaults and chevrons |
| File-list request adapter | `ClientWindow` | Visible-row controller | Current endpoint, listing type, `TreeMCListItem` |

The controller maps only visible eligible leaf rows to requests containing the resolved host,
captured Myster type, opaque file reference, and logical/device pixel size. The cache owns
network work and image reuse. The renderer receives an optional image lookup and cannot submit
work, perform I/O, or change demand.

### 6. Key decisions & edge cases

- Enumerate rows with visible-rectangle and row-cell geometry, clamping below-data blank space
  and handling empty lists/top coordinates beyond the final row.
- Retain overlap between viewport reconciliations, cancel departed active work, and physically
  remove stale pending work. Worker limits include cancelled-but-exiting workers.
- Sort/model changes must reconcile even when the MCList mutates its model directly; do not rely
  only on `RowSorterListener`.
- Resolve current row objects immediately and ignore stale row indices in completion callbacks.
- Active visible rows retain their displayed images even if the completed cache is full, avoiding
  reload loops. Release active references when rows leave the viewport.
- Preserve chevrons, indentation, row height, folder icons, selection, hit geometry, and
  fractional/HiDPI behavior. Do not tint photo pixels or leak renderer state between rows.
- Hiding or disposal withdraws demand immediately. Repainting, measuring, or printing never
  starts network activity.

### 7. Acceptance criteria

- [ ] Only eligible visible or partially visible file rows request thumbnails.
- [ ] Folders, collapsed descendants, offscreen rows, and horizontally hidden icon slots issue
      no requests.
- [ ] Rapid scrolling replaces stale pending demand and cancels abandoned transfers.
- [ ] Sorting, expansion/collapse, insertion, row-size changes, resizing, and scale changes
      keep thumbnails attached to the correct file.
- [ ] Existing navigation, indentation, selection, chevrons, folder icons, and row height remain
      correct at 1x, fractional, and 2x scales.
- [ ] Repainting and renderer measurement cause no network activity.
- [ ] Preview and list consumers share completed work without cancelling one another, and the
      reserved preview slot cannot be consumed by list traffic.

---
## ✦ IMPLEMENTATION DETAILS (for the implementation agent)
---

### 8. Affected files / classes

- **New:** `src/main/java/com/myster/thumbnail/ui/VisibleThumbnailController.java` — generic
  visible-row enumeration, reconciliation, debounce, and lifecycle.
- **New:** `src/main/java/com/myster/thumbnail/ui/ThumbnailIcon.java` — passive fixed-square
  image painting.
- **Modify:** `src/main/java/com/general/mclist/TreeMCList.java` — optional generic leaf-icon
  resolver while preserving existing callers/defaults.
- **Modify:** `src/main/java/com/myster/client/ui/ClientWindow.java` — attach controller,
  request adapter, cache sharing, and lifecycle hooks.
- **Reuse from Part 3:** remote cache/task, UI sizing and eligibility helpers.
- **Tests:** viewport/controller, tree renderer, and client/cache integration tests.

### 9. Step-by-step implementation

1. Implement `VisibleThumbnailController<E>` with injected `JMCList`, viewport, row adapter,
   `LIST` demand handle, and activity predicate. Enumerate only rows intersecting the viewport
   using `getVisibleRect`, `rowAtPoint`, and `getCellRect`; never scan every model row.
2. Include partially visible rows, exclude blank space below the final row, handle empty lists
   and top coordinates beyond the data, and require the actual icon square to intersect the
   visible rectangle.
3. Reconcile immediately on hide/reset/withdrawal. Debounce only additions with a restartable
   75 ms Swing timer. Reconcile viewport, model, hierarchy, sorter/model replacement, row
   height, font, column width, component, and display-scale changes.
4. Preserve overlapping requests, remove stale pending work, cancel departed active work, and
   recheck current demand after physical worker completion before starting more.
5. Extend `TreeMCList.create` with an optional passive leaf-icon resolver. Keep current callers
   delegating to folder/file defaults. Compose child icons directly into the supplied graphics
   rather than pre-rendering at a hardcoded 2x scale.
6. Make `ThumbnailIcon` report logical dimensions and center the bitmap within the requested
   square using the current graphics transform. Reset renderer state every call.
7. Attach the controller to `ClientWindow.fileList`, using the resolved endpoint and captured
   listing type. Skip `TreeMCListItem.isContainer()`, use `getObject()` for the opaque reference,
   and hook listing additions, recolumnization, resets, connection changes, and disposal.
8. Share the preview cache. Keep the preview slot reserved, allow at most two list workers,
   and retain active visible images independently of the completed-cache limit.
9. Write `docs/impl_summary/os-thumbnails-part-4.md` after focused tests and long-list/tree
   smoke checks.

### 10. Tests to write

- `TestVisibleThumbnailController`: first/last partial rows, empty/blank viewport, hidden icon
  slot, folders/collapsed children, model insertion, sorting, row/font changes, mixed scales,
  and demand proportional to visible rows in a large model.
- Drive viewport A → B → C with blocked workers. Verify stale pending removal, departed-task
  cancellation, overlap retention, final viewport capacity, and hide-during-debounce behavior.
- `TestTreeThumbnailRenderer`: chevrons/folders/hit area, selection, alternating image/file/folder
  rows without icon leakage, logical sizing at fractional/2x scale, and zero requests from paint.
- Cache integration: preview survives list scrolling, hiding preview leaves list demand intact,
  list work cannot consume the preview slot, and active visible images do not churn on eviction.
- Run relevant existing tree/list/thumbnail tests headlessly.

### 11. Docs / Javadoc to update

- Document controller visibility enumeration, debounce, cancellation, listener cleanup, and
  stale-row handling.
- Document the tree resolver's passive contract, fallback behavior, logical dimensions, and
  direct composition rationale.
- Update the OS thumbnail design document, implementation-summary index, and Part 3 integration
  notes where shared-cache behavior is introduced.
