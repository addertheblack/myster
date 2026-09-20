# OS thumbnails — Part 3: client preview pane

## Design Section (for the owner/reviewer)

### 1. Summary

Connect section 79 remote thumbnails to the existing client window's hideable right-hand
details pane. Eligible Image and Video files get a centered, aspect-preserving preview above
the existing statistics. Requests are visibility-gated, HiDPI-aware, cancellable, and tied to
the current selection and connection generation.

### 2. Non-goals

- Client file-list or search-result thumbnails; those are Parts 4 and 5.
- New thumbnail wire formats, persistent remote caches, connection pooling, playback, animation,
  original-media decoding, or additional OS providers.
- Requests for Audio, Generic, unknown, or future metadata profiles.
- Protocol limits above the existing 256-device-pixel request and 256 KiB body bounds.

### 3. Assumptions & open questions

- The preview is added to the existing right-hand details area without removing or changing
  the current statistics text or show/hide behavior.
- The preview uses the largest square that fits while retaining at least three metadata lines.
  A short window may reduce the square to zero.
- Eligibility comes from the resolved Myster metadata profile, including custom types.
- Hidden, minimized, disconnected, or closing owners have no demand. Reopening recomputes from
  the current selection without requiring a new selection event.
- Device pixels are calculated from the component's current graphics transform, rounded up and
  capped at 256. The received image may be enlarged for display at the protocol cap.

### 4. Proposed design

Create a connection-scoped remote-thumbnail cache and a preview controller. The controller
reconciles selection, pane geometry, visibility, monitor scale, connection identity, and type
profile into one current request. A 150 ms resize debounce delays only new acquisition; hiding,
selection changes, reset, and disposal withdraw demand immediately.

The cache owns one current `PromiseFuture` and bounded image/outcome entries. It uses
`PromiseFutures.execute(...).useEdt()` for background work and EDT publication. The controller
uses `PromiseFutures.delay` for resize settling. A synchronized block in `RemoteThumbnailTask`
serializes connection, transfer and socket cleanup using a monitor owned by the cache. Separate
windows have independent monitors. Cancellation is checked inside the block before connecting
and again before requesting the image. In-flight reads may finish
and their cancelled promises discard the result. UI painting only reads available images.
Statistics loading remains independent of thumbnail loading.

This deliberately replaces the earlier scheduler/physical-completion design following the owner's
simplification request. Part 3 implements one preview consumer; shared list scheduling belongs in
Part 4 when it has an actual caller.

### 5. Architecture connections

| New / changed thing | Owned / created by | Called / used by | Connects to (existing) |
|---|---|---|---|
| `RemoteThumbnailCache` | One per client window | Preview controller | `MysterStream`, `PromiseFutures`, cancellable workers |
| `RemoteThumbnailTask` | Thumbnail cache | `PromiseFutures.execute` | `MysterStream.makeStreamConnection()` and section 79 |
| `ThumbnailUiUtils` | `com.myster.thumbnail.ui` | Preview and later list consumers | Metadata profiles and `GraphicsConfiguration` |
| Preview pane/controller | `ClientWindow` | Selection, layout, visibility, connection lifecycle | Existing stats panel, split pane, `getCurrentFile()` |
| Resolved type profile | `TypeMetadataCache` | Preview eligibility | Access-list metadata and type description events |

The existing server remains responsible for authorization, shared-file resolution, and OS
thumbnail generation. The client sends the type CID, opaque file reference, and one pixel-size
limit using section 79. Empty MessagePack means unavailable; success is the existing bounded
PNG or raw ARGB32 response. No new wire or disk format is introduced.

### 6. Key decisions & edge cases

- Demand, cache state, and UI publication are EDT-owned; network work and decoding run on
  virtual workers.
- Each cache retains one transfer monitor across resets. A worker owns and closes its socket
  off the EDT while holding that monitor. Cancellation
  neither interrupts nor closes a socket on the EDT, and never releases the monitor early.
- Cancelling the current promise makes pending EDT publication moot on replacement/reset/close.
  No independent generation counter or running-worker accounting is needed.
- Images and miss/error outcomes share a 128-entry LRU cache, bounded to 8 MiB of decoded pixels.
  Unsupported endpoints have a separate 128-entry bound, cleared on connection reset.
- Cache misses for 30 seconds and transient failures for 5 seconds. Do not retry in a tight
  completion loop; cancellation is not negative-cached.
- Prefer exact or smallest completed larger-size requests for the same file. A smaller image
  may remain provisionally visible during an upgrade but cannot satisfy a larger demand.
- Known local type definitions override transient remote profiles. Generic, Audio, and unknown
  profiles remain ineligible until a resolved Image or Video profile exists.
- Closing is terminal: stop timers, remove listeners, withdraw demand, clear ownership, and
  invalidate callbacks. Temporary hiding withdraws demand but permits later reuse.

### 7. Acceptance criteria

- [x] A selected eligible file in a visible details pane displays a centered, aspect-preserving
      thumbnail without changing the pane divider or statistics layout.
- [x] Folders, empty selections, ineligible profiles, hidden panes, minimized windows, and
      disconnected windows issue no requests.
- [x] Reopening the pane loads the current selection without a new selection event.
- [x] 1x, fractional, and 2x monitor scales calculate device pixels correctly and apply the
      256-pixel cap after scaling.
- [x] Rapid selection and resizing never displays another file's image or blocks interaction.
- [x] Misses, denials, unsupported endpoints, and transport failures use a quiet fallback.
- [x] Cancelling a promise withdraws its result immediately. A replacement cannot connect until
      the old transfer and socket cleanup finish; cancelled waiters skip network work.
- [x] A stalled transfer does not block a separate cache; reset preserves serialization within
      the original cache until its cancelled transfer finishes.
- [ ] Manual client-window smoke check for divider behavior, real monitor changes and minimization.

---
## ✦ IMPLEMENTATION DETAILS (for the implementation agent)
---

### 8. Affected files / classes

- **New:** `src/main/java/com/myster/thumbnail/RemoteThumbnailCache.java` — EDT-owned
  current promise, cache and lifecycle.
- **New:** `src/main/java/com/myster/thumbnail/RemoteThumbnailTask.java` — cancellable blocking
  transfer using an injected `MysterStream`.
- **New:** `src/main/java/com/myster/thumbnail/ui/ThumbnailUiUtils.java` — profile eligibility,
  device-pixel sizing, and aspect-fit geometry.
- **New:** `src/main/java/com/myster/client/ui/ClientFilePreviewPane.java` — preview/statistics
  composite and passive painting.
- **New:** `src/main/java/com/myster/client/ui/ClientPreviewController.java` — selection,
  visibility, promise-based resize debounce and lifecycle.
- **Modify:** `src/main/java/com/myster/client/ui/ClientWindow.java` — install the composite,
  wire lifecycle events, and provide the resolved endpoint/type/reference.
- **Modify:** `src/main/java/com/myster/client/ui/TypeMetadataCache.java` — retain the resolved
  metadata profile alongside the display name.
- **New:** `src/main/java/com/general/thread/AbstractCancellableCallable.java` — reusable volatile
  cancellation flag and accessor for cooperative callables.
- **Tests:** focused cache, task, UI geometry, preview controller, and metadata-cache tests.

### 9. Step-by-step implementation

1. Add `ThumbnailUiUtils` with Image/Video eligibility, zero-area handling, transform-based
   sizing, fractional rounding, cap-after-scaling, and aspect-fit geometry. Use a package-private
   transform seam for deterministic tests.
2. Implement `RemoteThumbnailTask` with `AbstractCancellableCallable`. Hold a cache-owned monitor
   around connection, cancellation checks, section 79 and try-with-resources socket cleanup.
3. Implement `RemoteThumbnailCache` with one current promise, exact/larger-size reuse,
   bounded cache/outcomes and ordinary EDT listeners. Cancel that promise on replacement/reset.
4. Use `PromiseFutures.delay` for resize settling. Retain same-file pixels and skip cancellation
   when the effective device-pixel request is unchanged.
5. Extend `TypeMetadataCache` values with an optional `MetadataTypeId`. Prefer local type
   descriptions, preserve attempted/pending/failure semantics, reset per connection, and ignore
   callbacks from cancelled generations.
6. Replace the client details split-pane right component with `ClientFilePreviewPane`, retaining
   the existing statistics panel and show/hide/divider behavior. Compute a fixed square from
   available inner bounds and metadata minimum height; paint centered aspect-fit images only.
7. Attach `ClientPreviewController` to selection, split/divider/layout, hierarchy showing,
   window state, graphics configuration, connection reset, `stopStats`, `stopFileListing`, and
   `refreshIP`. Withdraw immediately on hidden/zero/clipped demand; debounce only new requests.
8. Re-evaluate on show/layout and monitor movement. Keep same-file cached pixels during resizing,
   clear immediately when file identity changes, and never gate thumbnail demand on statistics
   completion.
9. Write `docs/impl_summary/os-thumbnails-part-3.md` after focused tests and manual preview
   smoke checks.

### 10. Tests to write

- `TestThumbnailUiUtils`: eligibility, 1x/1.25x/1.5x/2x sizing, unequal transforms, cap,
  zero area, and portrait/landscape aspect fit.
- `TestRemoteThumbnailTask`: exact request identity, no EDT I/O, success/miss/error cleanup,
  cancellation while waiting, during connect, and during body read/socket cleanup.
- `TestRemoteThumbnailCache`: repeated demand, stale callbacks, bounded cache/outcomes,
  larger-size reuse, endpoint rejection, reset, independent caches and terminal close. TTL-expiry coverage remains
  follow-up; immediate suppression is covered.
- `TestClientPreviewController` and `TestClientFilePreviewPane`: hidden startup, reopening,
  hide/collapse/minimize/dispose, selection policy, debounce, monitor changes, stale callback
  rejection, independent statistics completion, and late profile resolution.
- Extend `TestTypeMetadataCache` for profile retention, future-profile ineligibility, reconnect
  isolation, and late-callback suppression.
- Run the existing thumbnail protocol/client tests plus the focused new tests headlessly.

### 11. Docs / Javadoc to update

- Document cache EDT ownership, current-promise lifetime, cache bounds and failure suppression.
- Document task socket ownership, cooperative cancellation and the per-cache transfer monitor.
- Document UI logical/device-pixel sizing, visibility rules, debounce, profile eligibility, and
  listener cleanup.
- Update `docs/design/Myster OS Thumbnail Integration Design.md`,
  `docs/impl_summary/README.md`, and this plan's completion state.
