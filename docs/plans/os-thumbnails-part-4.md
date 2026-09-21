# OS thumbnails — Part 4: per-item tree icons and client file-list thumbnails

## Design Section (for the owner/reviewer)

### 1. Summary

Add a per-item icon provider to `TreeMCList`, preserving the existing file/folder defaults and
all tree navigation. Use it to display asynchronously acquired thumbnails for visible eligible
client files, sharing the preview's completed-image cache and single transfer monitor.

### 2. Non-goals

- Adding icon, network or cache ownership to `TreeMCListItem`.
- Search-result integration (Part 5), larger rows, outside-viewport prefetch or persistent caches.
- Protocol changes, original-media decoding, changes to file references or server authorization.
- A priority scheduler, reserved worker slots, forced socket cancellation or parallel transfers
  within one client window.
- Sharing an in-flight promise between independent consumers; completed images are shared.

### 3. Assumptions & open questions

- The provider is the preferred extension point. It can customize both containers and leaves;
  the thumbnail implementation itself uses the default folder icons.
- The icon provider only reads available cached images. It never starts loading, owns a promise
  or requests repainting. The list's loading controller handles acquisition and repainting.
- Default file/folder icons already belong to `TreeMCList.create` and its renderer. Empty provider
  output asks that renderer to use its configured default; the provider does not need fallback,
  selection or chevron context.
- Client file rows currently contain an opaque `String` reference, not a `MysterFileStub`.
  The lambda-backed adapter combines that reference with the captured listing address/type,
  which is the same identity represented by a stub. There is no need to change the row model.
- Check `isContainer()` before reading a row as a file. Only resolved Image and Video metadata
  profiles are eligible, including custom types; Audio, Generic and unknown profiles use defaults.
  An eligible row with no cached thumbnail still uses the standard file icon while acquisition is
  pending. Metadata resolution is required before acquisition because it supplies the eligibility
  decision and the request's type identity; absence of a resolved eligible profile is not a
  thumbnail-load failure. If the profile is not yet resolved, continue the existing metadata
  lookup asynchronously using the project's `PromiseFuture`/`PromiseFutures` conventions; never
  block the EDT. Keep the standard file icon until the current listing identity still matches
  and the lookup resolves to an eligible profile.
- Preserve the renderer's existing rule: logical icon size comes from the current row height.
  Multiply by the actual scale reported by Java, including arbitrary fractional factors and
  factors greater than 2, and round up to device pixels within the protocol size limit. For
  example, a 20-unit row requests 25, 30, 40 or 60 pixels at 1.25x, 1.5x, 2x or 3x respectively.
  Keep the same logical icon slot and row height. Use `ThumbnailUiUtils.devicePixelSize` and
  the table's actual graphics configuration. Do not hardcode a logical size or a set of scales.
- The previous Part 4 assumptions about demand handles, two list workers and a reserved preview
  slot are superseded by the implemented Part 3 cache and the owner's simplicity preference.
- Preview requests may wait for an already running list transfer. Strict preview priority is
  not required here. Separate windows retain independent transfer monitors.

### 4. Proposed design

**Reusable tree API.** Supply an optional per-item provider when creating the tree. The renderer
asks it for the current item's icon each time that row is configured. An empty result selects
the caller's existing file/folder default. The renderer retains text, indentation, selection,
chevrons and hit geometry. The client supplies a small cache-lookup function, capturing its
per-window context outside the generic tree API. Once a loader adds an image to the cache and
repaints, the next lookup returns it. No separate mutable icon-provider state or row replacement
is needed.

**Loading independently of rendering.** A small client file-thumbnail controller watches viewport,
model and lifecycle changes. It identifies eligible visible requests, waits for a 75 ms settling
delay, and loads one missing thumbnail at a time through `RemoteThumbnailCache`. Completion
records the outcome on the EDT, repaints the affected items, and considers the next currently
visible request. An item-based repaint helper resolves each item's current row at completion
time, so sorting during a download is safe; removed or collapsed items need no repaint.
Painting and provider calls only read what is already available. They cannot start a download.

The controller owns the list's current promise and visible outcomes. It retains overlapping
items, drops departed ones and cancels a request that is no longer needed; running I/O may finish
normally. Hiding or replacing the listing withdraws demand immediately. There is no queue of
pre-created tasks for the full listing and no dependency on paint order to make progress.

**Shared cache.** Refactor the preview-specific cache API into per-request futures. Preview and
list each own their current future and cancel only their own demand. The cache retains images,
miss/error suppression, its transfer monitor and outstanding promises needed for reset/close.
The window owns the shared cache's lifetime. Task execution remains the Part 3 callable and
synchronized block, so cancelled workers cannot make another transfer overlap.

### 5. Architecture connections

| New / changed thing | Owned / created by | Called / used by | Connects to (existing) |
|---|---|---|---|
| Tree icon-provider contract | Supplied to `TreeMCList.create` | Existing first-column renderer | `TreeMCListItem<E>`, Swing `Icon`, existing default icons |
| Item repaint helper | `JMCList` | Loading controller's EDT completion listener | Existing item-identity lookup and Swing row repaint bounds |
| Cache-lookup provider | Installed by `ClientWindow` | Tree renderer | Available cached images and logical icon size; renderer owns defaults |
| Visible file-thumbnail controller | `ClientWindow` | Viewport/model/lifecycle listeners | File-list items, resolved endpoint, captured listing type, shared thumbnail cache |
| Passive thumbnail icon | Cache-lookup provider | Existing tree renderer | Completed `BufferedImage`, logical icon slot, actual graphics transform |
| Shared request cache | One per `ClientWindow` | Preview and file-thumbnail controllers | `PromiseFuture`, `RemoteThumbnailTask`, existing transfer monitor |

The renderer passes the actual model item and logical slot size to the provider. The provider's
per-window closure/controller reference supplies the cache context; generic item objects do not
supply server or monitor details. The loading controller maps eligible leaf items to the resolved
server address, captured listing type, opaque reference returned by the item's object accessor,
and a device-pixel size. It requests images through the existing cache and repaints on completion.
Default icons remain during loading and after misses/errors. The tree package has no knowledge
of Myster types, servers or promises.

The existing section 79 framing, authorization, 256-device-pixel limit and 256 KiB body bound
remain unchanged. No file format is introduced.

### 6. Key decisions & edge cases

- **A provider belongs to the view.** It is a read-only lookup function with captured per-window
  context. `TreeMCListItem` remains a model object. Loading and repaint ownership belong to the
  controller, not the renderer or provider.
- **Fallback is explicit.** Empty provider output means the configured default icon. Custom
  folder icons are allowed without taking over the expand/collapse chevron.
- **Stable layout.** Supplied icons fit within the existing logical slot. Photo pixels retain
  their colors under selection. The renderer resets its state for every row.
- **Direct composition.** Paint child icons using the actual component and graphics transform.
  Reuse the direct compositor introduced by the [display scaling correction](ui-display-scaling.md),
  which removed the fixed-2x raster merge. Preserve live icon/component context at every scale.
- **Visible items only.** Include partially visible rows when their item-icon slot intersects
  the viewport and name-column bounds. Collapsed descendants, hidden icon slots and folders do
  not acquire thumbnails. Enumerate only the viewport's row range, not the full listing.
- **Shared eligibility check.** Both passive lookup and loading use the same file-request adapter.
  It rejects containers and non-Image/Video profiles before accessing cached thumbnails or starting
  work. Resolve local type definitions first, then transient remote metadata. Do not decide from
  filename extensions or the local OS thumbnail whitelist. Reconcile when an unknown profile resolves.
  An unresolved profile is a pending metadata state, not an ineligible result. Its asynchronous
  completion publishes on the EDT and triggers reconciliation only when the captured
  address/type/listing generation is still current.
  For a resolved eligible file, the adapter checks the completed cache and remembered failure state;
  if neither is present, the controller requests the thumbnail. Until completion, the renderer
  returns empty so the tree keeps its standard file icon.
- **Identity survives sorting.** Resolve actual items after model events; asynchronous results
  are keyed by request identity, never by a saved row index. The model sorts/rebuilds itself.
  Completion updates the available image before repainting the current matching items. Repaint
  emits no model events and leaves selection and scrolling alone; stale listing callbacks are ignored.
- **Independent consumers.** Cancelling list work cannot cancel the preview. Completed larger
  images satisfy smaller requests. Two simultaneous misses may fetch sequentially; avoid
  reference-counted shared cancellation machinery in this milestone.
- **Bounded work.** One current list promise and one current preview promise share one physical
  transfer monitor. Reset may leave cancelled workers finishing or skipping their monitor wait;
  there is no separate physical-completion counter.
- **Stable fallback.** Remember a completed miss/error for the current visible demand so repaint
  cannot create a retry loop. A new visible demand may retry subject to the cache's existing TTL.
  Keep visible successful images referenced until departure so cache eviction cannot cause a
  repaint/reload cycle when the viewport holds more entries than the completed cache. While an
  eligible request is pending, or after a miss/error, the normal file icon remains visible.
- **Shared lifetime.** Hiding one consumer only withdraws that consumer. Reconnect/close cancels
  both consumers, clears cache outcomes and invalidates callbacks. Keep the same monitor across
  reset so an old transfer cannot overlap its replacement.

### 7. Acceptance criteria

- [ ] A provider can customize a specific leaf or container without subclassing or replacing it.
- [ ] Existing tree callers retain their default icons, layout, selection and navigation.
- [ ] Eligible files retain the standard file icon until a cached or newly loaded thumbnail is
      available; ineligible/unresolved types never start thumbnail acquisition.
- [ ] Unresolved custom/remote metadata is fetched asynchronously without blocking the EDT, and
      a current Image/Video resolution causes eligible visible rows to enter thumbnail loading.
- [ ] A changed provider result becomes visible after repaint without a model rebuild.
- [ ] Successful asynchronous loads trigger an EDT repaint of the affected current rows without
      user interaction; sorting or removing items during loading cannot repaint a saved, stale row.
- [ ] Visible eligible client files progressively replace their defaults with thumbnails.
- [ ] Offscreen/collapsed files, folders, ineligible profiles and clipped icon slots start no work.
- [ ] Scrolling, sorting and listing replacement keep images attached to the correct files.
- [ ] Image misses and failures settle to defaults without repeated fetch/repaint loops.
- [ ] Preview and list cancellation are independent, with at most one transfer per client window
      through socket cleanup. Separate windows still progress independently.
- [ ] Custom icon colors and tree geometry remain correct at fractional and HiDPI scales.
- [ ] Renderer, provider and icon-paint calls never start acquisition, enqueue work or repaint.
      Printing and repeated renderer measurements create no thumbnail requests.
- [ ] Icon size follows the current row height; request size is rounded up after multiplying
      by Java's actual display scale, within the protocol limit. Fractional and greater-than-2x
      scales work without growing the logical icon slot. Moving between monitors updates demand.
      A sufficiently large cached preview can supply those pixels without a second download.

---
## ✦ IMPLEMENTATION DETAILS (for the implementation agent)
---

### 8. Affected files / classes

- **Modify:** `src/main/java/com/general/mclist/TreeMCList.java` — provider contract/overloads,
  generic renderer, shared icon geometry and direct icon composition.
- **Modify:** `src/main/java/com/general/mclist/JMCList.java` — item-based row repaint helper.
- **New:** `src/main/java/com/myster/client/ui/ClientFileThumbnailController.java` — visible-item
  observation, one current list promise, outcome retention and passive cached-image lookup.
- **New:** `src/main/java/com/myster/thumbnail/ui/ThumbnailIcon.java` — passive aspect-fit bitmap
  painting at explicit logical dimensions.
- **Modify:** `src/main/java/com/myster/thumbnail/RemoteThumbnailCache.java` — per-request future
  API, cached-image lookup and reset/close ownership for independent consumers.
- **Modify:** `src/main/java/com/myster/client/ui/ClientPreviewController.java` — own the selected
  preview's future, preserving existing geometry/debounce behavior without owning cache lifetime.
- **Modify:** `src/main/java/com/myster/client/ui/ClientWindow.java` — provider installation,
  captured listing identity, shared cache ownership and consumer lifecycle wiring.
- **Reuse:** `RemoteThumbnailTask`, `AbstractCancellableCallable`, `PromiseFutures.delay`,
  `ThumbnailUiUtils`, existing profile resolution and protocol.
- **Tests:** tree-provider renderer tests, controller loading/visibility tests, shared-cache tests
  and existing preview regressions.

### 9. Step-by-step implementation

1. Add a nested functional interface `TreeMCList.IconProvider<E>`:

   ```java
   Optional<Icon> getIcon(TreeMCListItem<E> item, int logicalSize);
   ```

   It only looks up an available icon. It performs no I/O, starts no asynchronous work and
   requests no repaint. Empty tells the renderer to select its configured default. Supplied
   icons report logical dimensions; fit them into the slot preserving aspect ratio. Do not
   expose futures, addresses or thumbnail-specific types in this interface.

2. Add `create(columns, root, provider)` and
   `create(columns, root, customFolderIcon, customFileIcon, provider)` overloads. Existing two-
   and four-argument overloads delegate using an empty provider. Require a non-null provider;
   preserve the existing null-means-built-in behavior of default SVG arguments. No setter is
   necessary for dynamic loading: filling the shared cache and repainting suffices.

3. Make `createTreeFirstColumnRenderer` generic in `E`; remove its hardcoded `String` item/model
   assumptions. Convert view/model indices where appropriate and use the actual `TreeMCListItem`.
   Invoke the provider for both containers and leaves. Apply existing foreground tint only to
   fallback SVG icons and chevrons; do not recolor provider icons. Retain the existing renderer
   restoration in `TreeMCListImpl.tableChanged` across `HEADER_ROW` events.

4. Preserve `mergeIcons` direct child painting with the supplied component/graphics, already
   implemented by the display scaling correction. Keep empty chevron space for leaves without
   allocating an empty bitmap. Share the item-slot geometry between rendering and a public
   `TreeMCList.getItemIconBounds(JMCList<?> list, int viewRow)` helper, which returns a fresh
   rectangle in table coordinates or an empty rectangle if the name column is unavailable.
   Account for indentation, chevron width, spacing and actual row height; preserve mouse hit
   geometry. Intersection with the name cell/visible rectangle happens in the loading controller.

   Add `JMCList.repaintItem(MCListItemInterface<?> item)` as an EDT-only helper. Reuse
   `findRowIndexOfItem` to locate the current model row by identity, convert to its view row,
   then repaint that row's bounds across the table width. Return without action when the item
   is absent/collapsed/filtered or there are no columns. Do not change selection, scroll, emit
   model events or add this Swing-specific helper to the broader `MCList` interface.

5. Refactor `RemoteThumbnailCache` to `RemoteThumbnailCache(MysterStream)`,
   `PromiseFuture<BufferedImage> load(Request)` and a public passive `lookup(Request)` for available
   images. A successful null still means unavailable; existing cached misses suppress downloads.
   Each `load` call owns its own promise. Use ordinary EDT listeners to cache success/failure.
   Preserve all cache bounds, TTLs, endpoint suppression and the one private transfer monitor.
   Remove the single `currentRequest`, single `future`, `onLoaded` callback and `replace` API.
   Track only outstanding promises in an EDT-owned set for reset/close and remove them on
   completion. This set is cancellation ownership, not a scheduler or concurrency counter.
   Reset cancels a snapshot before clearing outcomes; close is terminal. Return an already
   cancelled future for loads after close. Do not add an in-flight coalescing/refcount layer.

6. Move preview future ownership into `ClientPreviewController`: cancel its previous future on
   replacement/hide/close, call `cache.load(request).useEdt()` (or `withInvoker` if the cache already
   assigns one), and publish via ordinary listeners. Preserve same-request reuse, same-file
   provisional pixels, 150 ms resize delay and immediate clearing on file changes. Closing the
   preview controller must not close the shared cache. Keep existing Part 3 tests passing.

7. Add `ThumbnailIcon`, reporting the supplied logical slot size derived from row height and
   painting a completed image with `ThumbnailUiUtils.aspectFit` using the supplied graphics.
   No network activity, invalidation or
   model mutation occurs in `paintIcon`. Retain alpha and aspect ratio; do not upscale the request
   pixel limit beyond 256.

8. Add `ClientFileThumbnailController` for the actual client tree. Inject the table, cache,
   a request adapter using the current listing's captured endpoint/type, and an activity
   predicate. Derive the logical slot size from the table's current row height, matching the
   renderer. Reuse `ThumbnailUiUtils.devicePixelSize` to multiply by Java's reported scale and
   round up within the protocol cap: a 20-unit slot requests 30 pixels at 1.5x or 60 at 3x,
   while `ThumbnailIcon` still reports 20 units. Do not bucket display scales into 1x and 2x.
   Observe viewport, table model, hierarchy,
   graphics configuration, row-height/font and column geometry changes. Use model events because
   `TreeMCListTableModel.resortAndRebuild` fires `fireTableDataChanged` without a Swing row sorter.
   Rebind model/column listeners if their objects change; detach all owned listeners on close.

9. The controller enumerates the visible row range using `getVisibleRect`, `rowAtPoint` and
   `getCellRect`, clamping the first/last rows and excluding below-data blank space. Use
   `getItemIconBounds` to exclude horizontally clipped slots. Keep a map from actual visible item
   to its current `Request`, plus completed outcomes for current requests (including misses).
   Retain overlapping outcomes; release departed ones. Geometry/model changes can update this
   snapshot on the next EDT turn if the model is in the middle of rebuilding. Withdraw promptly
   on hide/dispose/reset, and cancel any current list promise no longer in the snapshot.

10. After a restartable `PromiseFutures.delay(Duration.ofMillis(75))`, choose the next missing
    request from the current visible snapshot and call `cache.load` if no list promise is pending.
    Reuse available cached images first. On success/exception record the visible outcome; on
    completion clear only that current promise and continue with the current snapshot. After
    publishing a successful image, call `list.repaintItem(item)` on the EDT for current visible
    items matching the completed request. Validate the listing/request identity first and find
    items from the current snapshot; never capture a row number when submitting the load. Keep
    this callback in the controller so the cache has no Swing row or repaint ownership.
    Record unavailable outcomes for this demand so completion cannot create a retry loop.
    Cancellation is not a miss. Reconciliation retains a still-needed current request and cancels
    departed work; the shared task monitor, not promise completion, enforces physical serialization.
    Expose a passive lookup used by the tree provider: resolve the item's current request, return
    its retained cached image or `cache.lookup` hit as a `ThumbnailIcon`, otherwise return empty.
    This lookup does not fetch, settle, advance the loader or mutate request ownership. Retained
    visible image references only keep already loaded pixels available through cache eviction;
    do not add a second independently populated icon cache.

11. In `ClientWindow.init`, install a provider lambda that delegates to the eventual
    controller's passive lookup, returning empty before it is attached. Construct the cache and
    both consumers after the list/pane exist. Share one request adapter between passive lookup
    and loading. Check `item.isContainer()` first; then resolve the captured listing type's metadata
    profile using local `TypeDescription` before `TypeMetadataCache`, and apply
    `ThumbnailUiUtils.isEligible`. For eligible leaves, use the current `TreeMCListItem<String>`
    object's exact reference plus captured address/type. These are the three `MysterFileStub`
    fields; do not cast the row object to a stub or use the displayed basename/folder path.
    Capture the listing type at `startFileList`, and reconcile after resolved address and profile
    changes. Stop/cancel consumers before resetting the cache on reconnect;
    close consumers before closing the cache on disposal. Hiding the details pane must not
    disable list thumbnails. Preserve the existing default icon overload used by
    `ProgressManagerWindow`.

12. Ensure the request adapter treats a resolved eligible row with no cached image as a
    pending acquisition (not as an immediate fallback result): the provider returns empty
    during the transfer, while the controller owns loading and repainting. Treat cached
    misses/errors according to the existing TTL and keep the default file icon visible.
13. When the captured type has no local metadata profile, use the existing asynchronous
    metadata-cache lookup rather than resolving it from the renderer or blocking the controller's
    EDT reconciliation path. Track the listing generation/address/type with the lookup; on
    completion, publish the resolved profile and reconcile only if that identity is still current.
    Keep unresolved rows on their default icons and do not classify them as cache misses or
    thumbnail failures.
14. Write `docs/impl_summary/os-thumbnails-part-4.md` after focused tests and manual list checks.

### 10. Tests to write

- **Tree provider:** actual generic item and logical size, different icons per item, custom
  container icon, empty fallback with renderer-owned caller defaults, newly cached images plus repaint,
  selection colors, no renderer-state leakage, chevron keyboard/mouse behavior and renderer
  survival after column reconstruction.
- **Composition:** logical dimensions, supplied component propagation, actual fractional/HiDPI
  graphics scale, transparent leaf chevron slot and image aspect fit without recoloring. Verify
  multiple row heights at 1x, 1.25x, 1.5x, 1.75x, 2x, 2.5x and 3x, including a non-integral pixel
  result that must round up. Logical layout dimensions remain unchanged by display scale.
  Changing row height updates the slot and request size together; changing monitor scale updates
  device-pixel demand while keeping the logical size.
- **Visibility/loading:** partial rows, empty list, viewport below data, horizontally hidden
  item slots, folders/collapsed descendants, late profile resolution and request work bounded
  to one list promise. Repeated renderer/provider/paint calls must create zero loads. A settling
  viewport must begin loading even if no renderer has run yet.
- **Async metadata:** unresolved custom/remote profiles remain responsive on the EDT, retain
  default icons, start eligible thumbnail loading after asynchronous Image/Video resolution,
  and ignore resolution callbacks after listing replacement, reconnect or close.
- **Identity/eligibility:** a folder whose label resembles an image filename still uses the
  folder default and never enters thumbnail lookup/loading. Audio/Generic/unknown profiles also
  bypass thumbnails; custom Image/Video profiles are eligible. Preserve the exact opaque file
  reference even when its display name differs, and use the captured listing address/type.
- **Dynamic results:** success replaces fallback on repaint; failure/miss settles without a
  retry loop; sorting and A → B → C scrolling keep request identity correct; hidden/reset/closed
  controllers cannot publish stale images or start new work. Hide during the settle delay.
- **Completion repaint:** after sorting during a load, completion repaints the item's new row
  on the EDT with its image already available. Removed/collapsed/replaced items are harmless;
  repainting emits no model event and does not change selection or scrolling.
- **Shared cache:** cancelling one consumer leaves the other's request live; larger completed
  images satisfy smaller requests; reset/close cancels all outstanding results; cancelled I/O
  still prevents another same-cache transfer; different caches progress independently.
- **Eviction:** more visible rows than the completed-cache entry limit do not cause repeated
  acquisition while stationary; leaving rows releases retained images/outcomes.
- Run existing `TestTreeMCListTableModel`, `TestJMCListSelectionModel`, thumbnail cache/task tests,
  preview controller/pane tests and thumbnail geometry tests headlessly, plus focused new tests.
- Manually exercise a long mixed tree: scroll, resize, expand/collapse, sort, change type, toggle
  preview and reconnect at available display scales, including fractional and greater-than-2x.
  Move between monitors with different scales. Check download navigation and custom
  progress-window defaults. This planning pass runs no build or tests.

### 11. Docs / Javadoc to update

- `TreeMCList.IconProvider`, factory overloads and item-slot bounds: typed item, EDT/nonblocking
  contract, empty fallback, logical dimensions and repaint-driven updates.
- `JMCList.repaintItem`: EDT-only, item identity resolved at call time, absent items are a no-op.
- Composite/thumbnail icons: passive drawing, component/graphics context and selection colors.
- `RemoteThumbnailCache`: independent future ownership, reset/close scope and shared monitor;
  `ClientPreviewController`: consumer lifetime rather than shared-cache ownership.
- Client loading controller: visibility snapshot, single current request, settle delay, retained
  outcomes, passive cache lookup, request identity and listener cleanup.
- OS thumbnail design and Part 3 integration notes: shared cache and one transfer per window.
  Record implementation results in the Part 4 summary and implementation-summary index.
