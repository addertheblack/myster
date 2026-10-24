# Myster resource links

> **Related plan:** [Peer-to-Peer Connection Server Picker](./peer-to-peer-server-picker.md) — predecessor: provides the shared server picker and asynchronous server-stats resolver reused for missing-key opening.

> **Related plan:** [Bidirectional Server Stats Exchange](./bidirectional-server-stats.md) — depends on: provides the normal pool refresh and server identity used by address-only window discovery.

> **Related plan:** [Tracker 3DNS Target Slots UI](./tracker-3dns-target-slots-ui.md) — parallel work: 3DNS row activation passes its existing public-key identity into window navigation.

> **Related plan:** [Domain-Specific CID Types](./cid-domain-types.md) — depends on: keeps resource-link server CIDs distinct from type CIDs while identifying windows by public key.

> **Related plan:** [Private Type Join Links and Invitation Redemption](./private-type-join-links.md) — parallel work: shares native and single-instance URI delivery while preserving invitation routing.

> **Related plan:** [Private Types — Milestone 3: Type Metadata Resolution & Import](./private-types-access-lists-milestone3.md) — predecessor: provides type metadata and import requests now pinned to the immutable window identity.

> **Related plan:** [OS thumbnails — Part 3: client preview pane](./os-thumbnails-part-3.md) — parallel work: preview requests retain the window key and reset when its endpoint changes.

> **Related plan:** [OS thumbnails — Part 4: per-item tree icons and client file-list thumbnails](./os-thumbnails-part-4.md) — parallel work: file-row thumbnails share the identity-pinned cache and endpoint-reset lifecycle.

> **Related plan:** [Split Type Columns from Search-Only Columns](./split-type-columns-from-search-columns.md) — parallel work: shares type-selection and file-list rebuilding used by initial-data navigation.

> **Related plan:** [AsyncTaskTracker completion context](./async-task-tracker-context.md) — context: documents promise ownership and cancellation; window requests do not own shared pool lookups.

Scope revised on 2026-10-24 to require immutable public-key ClientWindow identities and
provider-owned discovery before address-only opening. See the
[design](../design/Myster%20Resource%20Links.md) and
[implementation summary](../impl_summary/myster-resource-links.md).

## Design Section

### 1. Summary

Open a server, a type on that server, or a named file through the existing ClientWindow
navigation. Every browser window represents one immutable public-key identity. Callers
without a key discover it before a window is created; saved windows retain their identity.
Explicit Copy File Link actions produce server/type/name links without starting downloads.

### 2. Non-goals

- File-hash selectors, hash searches, transfer startup, or download destination selection.
- A second browser navigation lifecycle, late identity merging, or address-based window identity.
- A new key-discovery protocol, address grammar, general endpoint-migration service, or changes
  to invitation redemption and ordinary manual downloads.

### 3. Assumptions & open questions

- Owner-confirmed: ClientWindow always requires a public-key identity, fixed at construction.
  Its endpoint may change while it continues to authenticate the same identity.
- Owner-confirmed: persist the full public key with the last endpoint and selected type.
- Owner-confirmed: missing-key opening uses the existing asynchronous server-stats lookup,
  temporary progress, and a failure dialog. Create/register no provisional ClientWindow.
- Existing keys from search, tracker, picker, 3DNS, and resource links must pass through.
- No architecture-blocking questions remain. Native desktop and Swing presentation smoke
  checks still require a working display.

### 4. Proposed design

Known identities open or reuse their existing browser immediately. Address-only requests first
consult the identity cache, then perform server-stats discovery while showing a cancellable
“Contacting server…” indicator. Successful discovery must provide a public-key identity before
construction or type listing. Failure dismisses progress and reports the problem without
creating a browser window. The temporary indicator belongs outside the normal frame registry.

The browser displays its address read-only and offers Refresh. Navigation may change type,
file, or endpoint within that identity. An endpoint change cancels old workers and clears
address-bound rows/caches before loading fresh data with the same key. Another identity uses
another window, including when both identities use the same address.

Pending initial-data navigation follows the ordinary selection events: type-list completion
selects the type, that selection starts file listing, and file-list completion reveals the
exact reference. Compatible intermediate callbacks retain the request; fulfillment, terminal
failure, Refresh, or incompatible selection clears it.

### 5. Architecture connections

Native and single-instance resource URLs enter the shared URI router. Resource preparation
resolves an exact verified 3DNS peer or a confirmed own-server destination, then hands its
identity/address/type/name to the provider. Existing UI callers either supply their known
identity or use provider discovery. The provider owns window reuse; the protocol layer owns
key verification. Workers receive snapshots of the window's required key.

| New / changed thing | Owned / created by | Called / used by | Connects to (existing) |
|---|---|---|---|
| Resource URI and preparation | Resource-link service | Router, dispatcher, copy actions | ServerCid, type IDs, exact verified 3DNS, saved shared index |
| URI routing | Application bootstrap | Native desktop and single-instance delivery | Existing invitation dispatcher and resource dispatcher |
| Identity-bearing navigation | Caller; consumed by ClientWindow | Provider and resource dispatcher | Existing initialData, type and file selection events |
| Address-only opening | ClientWindowProvider | Search, tracker, picker, incoming-download browsing, legacy restoration | Cached identity resolver, KnownServerSource server-stats lookup |
| Temporary lookup indicator | One pending provider opening | Address-only discovery | Modeless JDialog, cancellation, failure alert; outside MysterFrame registry |
| Window registry | ClientWindowProvider | All browser opening paths | PublicKeyIdentity keys and exact identity/window cleanup |
| Persisted identity | Each ClientWindow via window keeper | Startup restoration | Public key, last endpoint, selected type, asynchronous restored count |
| Copy actions | Current owner and selection | ClientWindow, SearchTab, File Manager | Authenticated peer key or local identity, clipboard on EDT |

Resource URLs accept exactly server, server/type, or server/type/name. The form is
`myster://link/v1?server=<server CID>[&type=<type CID>[&name=<UTF-8 reference>]]`.
CIDs are 32 lowercase hexadecimal characters in their own domains. Names are opaque,
percent-encoded UTF-8 references. Unknown/duplicate/empty fields, invalid combinations,
malformed encoding/CIDs, authorities, fragments, credentials, ports, unsupported versions,
hash fields, and URLs over 16 KiB are rejected.

Preferences add `Server Public Key`, storing the full encoded public key through the byte-array
preferences API, alongside existing `Ip Key` and `Type Key`. The public key is authoritative;
the endpoint does not establish identity. Existing invitation URLs and wire protocols retain
their formats.

### 6. Key decisions & edge cases

- No ClientWindow or ClientWindowData exists without a PublicKeyIdentity. The optional-key
  boundary exists only on the provider's outer opening request.
- A supplied or saved key cannot be replaced by the address cache. Listings, metadata,
  statistics, thumbnails, copying, and type import retain the window's key expectation.
- Stats without a public-key identity fail opening. Unreachable address-only destinations
  produce an error dialog instead of an empty registered browser.
- Cancellation propagates through the opening chain into its API request. The server-pool API
  isolates callers with separate request futures over its shared refresh. Repeated close notifications cannot remove a replacement window.
- Legacy preferences without any key use discovery. Corrupt stored keys are skipped rather
  than treated as a legacy address-only request. Missing/malformed saved addresses are skipped.
- Snapshot preferences before resolving DNS, outside the keeper's monitor. DNS and network
  work stay off the EDT; window creation and UI callbacks stay on it.
- Startup waits for the successful restored count. A failed or cancelled legacy opening
  counts as zero without discarding other successful restorations.
- Own-server links require Yes/No/Cancel confirmation; only Yes proceeds. Named local files
  must exist in the saved shared index and on disk. Resource opening never starts transfers.
- One Copy File Link command produces server/type/name. Stale or failed copy work leaves
  the clipboard unchanged. Keep existing invitation routing and resource queue bounds.

### 7. Acceptance criteria

- [x] All three URL forms navigate through initialData without starting a download.
- [x] Cached rows, delayed listings, missing destinations, and supersession behave correctly.
- [x] Every browser has an immutable public-key identity and a read-only address display.
- [x] Refresh retains identity; another endpoint can refresh the same server's window.
- [x] Another identity uses another window, even at the same endpoint.
- [x] Missing-key opening uses progress and server stats before browser construction.
- [x] Failure and cancellation create no provisional window; shared lookups remain usable.
- [x] Saved windows persist identity; legacy and corrupt records follow their distinct policies.
- [x] Late close notifications cannot remove a replacement window.
- [x] Copying, invitation handling, and ordinary manual downloads remain compatible.

---
## ✦ IMPLEMENTATION DETAILS (for the implementation agent)
---

### 8. Affected files / classes

- New `src/main/java/com/myster/link/` URI/service/router/UI classes: resource contract,
  preparation, routing, queueing, and copy actions.
- `src/main/java/com/myster/client/ui/ClientWindow.java`: required identity, navigation,
  Refresh, endpoint changes, pinned browsing/type import, and saved identity restoration.
- `src/main/java/com/myster/client/ui/ClientWindowProvider.java`: identity-only registry,
  address-only discovery, temporary progress, cancellation, and failures.
- `src/main/java/com/myster/client/ui/IdentityResolver.java`: cache-only public-key lookup.
- `src/main/java/com/myster/client/ui/TypeListerThread.java`, `FileListerThread.java`,
  `FileInfoListerThread.java`, and `TypeMetadataCache.java`: expected-key snapshots and
  completion/cancellation events.
- `src/main/java/com/myster/thumbnail/RemoteThumbnailCache.java` and `RemoteThumbnailTask.java`:
  pinned thumbnail requests and endpoint-reset behavior.
- `src/main/java/com/myster/Myster.java` and `ui/MysterFrameContext.java`: startup, key-cache
  adapter, shared services, URI routing, and asynchronous restored-window count.
- `src/main/java/com/myster/tracker/PublicKeyIdentity.java`: require a non-null public key.
- `src/main/java/com/myster/tracker/MysterServerPool.java`, `MysterServerPoolImpl.java`, and
  `tracker/ui/KnownServerSource.java`: independently cancellable API requests over pool-owned
  shared refreshes, so clients forward cancellation without knowing implementation details.
- `src/main/java/com/myster/ui/menubar/event/NewClientWindowAction.java`,
  `tracker/ui/OpenConnectionHandler.java`, `tracker/ui/TrackerThreeDnsPanel.java`,
  `search/ui/SearchTab.java`, and `server/ui/DownloadInfoPanel.java`: pass known identities or
  invoke provider discovery before opening.
- `src/main/java/com/myster/filemanager/ui/FmiChooser.java`: guarded local Copy File Link.
- Class-named tests under `src/test/java/com/myster/client/ui/`, `link/`, and affected UI packages.

### 9. Step-by-step implementation

1. Restrict resource URI parsing/serialization/preparation to server/type/name; preserve invitation
   routing and remove obsolete hash paths.
2. Require `PublicKeyIdentity` in `ClientWindowData` and the ClientWindow constructor; store it in
   final `serverIdentity` independently of workers and initialData.
3. Key `ClientWindowProvider.activeWindows` only by PublicKeyIdentity. Apply navigation to the
   same identity and remove entries with the exact identity/window pair.
4. Add `openWindow(address, optionalIdentity, type, fileName)`. Use `IdentityResolver.getCached`
   before `KnownServerSource.resolveServer`; require a public-key result, show temporary
   progress, and report failures before construction. Forward cancellation into the API request;
   the server-pool API owns the boundary protecting unrelated callers of shared refreshes.
5. Pass identities through all production callers. Keep `getOrCreateWindow` for mandatory
   identity-bearing requests, including resource links and 3DNS rows.
6. Make the address display read-only, label the button Refresh, and use `refresh()` to start
   pinned listing. `setInitialData` rejects another identity; a new endpoint resets old rows,
   workers, and thumbnail demand. Pin type import as well as the existing browsing workers.
7. Persist `Server Public Key` via `savePreferences`. Snapshot encoded key/address/type before
   background parsing/DNS. Keep saved identities authoritative, discover only missing keys,
   skip corrupt records, and aggregate successful EDT openings for startup.
8. Verify navigation, identity, discovery, cancellation, persistence, existing copy/invitation
   behavior, and affected metadata/thumbnail/pool regressions. Update current documentation.

### 10. Tests to write

- `TestClientWindow`: real initialData/type/file events, compatible and incompatible selections,
  missing/empty destinations, immutable identity, endpoint changes clearing old rows, Refresh
  preserving the required key, typed endpoint use, and preference save/restore with legacy and
  corrupt records plus partial failures.
- `TestClientWindowProvider`: identity reuse across endpoints, different keys at the same
  endpoint, replacement cleanup, cache/supplied-key paths, deferred construction, unreachable
  and keyless responses, retries, cancellation, and shared pool lookups.
- Resource parser/service/router/dispatcher/copy tests: contract validation, exact CID resolution,
  own-server confirmation, clipboard races, invitation coexistence, and no implicit transfer.
- `TestMysterServerPoolImpl`: distinct request futures over one exchange, cancellation isolation,
  later callers reusing pending work after cancellation, and pool updates after all requests
  are cancelled.
- Existing listing, metadata, thumbnail, server-pool, picker/address-lookup, search, and launch
  tests; full Maven suite with isolated preferences and local socket access where required.
- Manual smoke checks: open known/unknown/unreachable servers, cancel progress, Refresh, restart
  with saved windows, resource links, and clipboard/highlighting on a working native display.

Validation on 2026-10-24: 874 tests exercised; 869 passed in the restricted sandbox and the five
local-socket tests passed when rerun with local socket access. Native display checks remain
outside automated headless coverage.

### 11. Docs / Javadoc to update

Update ClientWindow/ClientWindowData, provider opening/reuse/cancellation, IdentityResolver,
key snapshots, and preference restoration comments. Keep
`docs/design/Myster Resource Links.md`, `docs/conventions/myster-important-patterns.md`, and
`docs/impl_summary/myster-resource-links.md` aligned with the mandatory identity contract,
address-only fallback, persistence, and actual validation limits. Preserve relevant network
address/DNS conventions and reciprocal related-plan links.
