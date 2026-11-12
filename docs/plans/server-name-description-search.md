# Search for Servers by Name and Description

**Feature slug:** `server-name-description-search`

**Date:** 2026-11-11

**Status:** Implemented; automated verification and independent review passed. Live-network/desktop smoke checks remain manual.

**Implementation summary:** [Search for Servers by Name and Description](../impl_summary/server-name-description-search.md)

> **Related plan:** [Tabbed Search Window](./tabbed-search-window.md) — follow-up: adds an independently selected and persisted search mode to each tab.

> **Related plan:** [Bidirectional Server Stats Exchange](./bidirectional-server-stats.md) — depends on: reuses the pool-owned stats exchange, description cache, identity extraction and corrected advertised port.

> **Related plan:** [Peer-to-Peer Connection Server Picker](./peer-to-peer-server-picker.md) — follow-up: reuses independently cancellable pool-backed resolution for servers encountered in the selected type’s crawl.

> **Related plan:** [AsyncTaskTracker completion context](./async-task-tracker-context.md) — depends on: uses the existing crawler's dynamic child ownership and explicit exhaustion/empty-work completion.

> **Related plan:** [Search-result thumbnails and hover previews](./os-thumbnails-part-5.md) — parallel work: preserves captured file-result state and engine-owned cancellation and suspends file thumbnail demand when server results replace files.

> **Related plan:** [Split Type Columns from Search-Only Columns](./split-type-columns-from-search-columns.md) — parallel work: keeps server result columns separate from the existing file-type column handlers.

> **Related plan:** [Typed Search-Result Metadata](./typed-search-result-metadata.md) — parallel work: preserves the file-only SearchResult contract and its protected typed metadata.

> **Related plan:** [Search Download To Shared Folder](./search-download-to-shared-folder.md) — parallel work: preserves file download actions and excludes them from server result rows.

> **Related plan:** [Myster Resource Links](./myster-resource-links.md) — depends on: reuses identity-keyed ClientWindowProvider opening and server/type navigation.

## Design Section (for the owner/reviewer)

### 1. Summary

Add a **Files / Servers** dropdown to each search tab, defaulting to Files. Server searches find servers whose name or description contains the query within the selected Myster type's network, starting from that type’s tracker list and crawling through its usual top-server requests. For each encountered server, check cached stats first. Cache misses use the existing pool-backed server-stats exchange instead of file search. Double-clicking a server opens its client window or brings the existing window forward.

### 2. Non-goals

- Changing file matching, downloads, metadata, thumbnails, or the file-search protocol.
- Creating a new remote server-search protocol, tracker index, persistent server cache, or search service.
- Searching every type at once, enumerating the entire server pool as search candidates, ranking results by textual relevance, fuzzy matching, regular expressions, or parsing addresses/CIDs from the search text.
- Automatically refreshing every cached server, adding a cache freshness threshold, or subscribing displayed results to ongoing pool changes.
- Adding identity aliases/equivalence grouping or changing the pool's identity/address model.
- Adding server descriptions to other windows or changing the existing server picker filter.

### 3. Assumptions & open questions

Owner-confirmed: server search is per type. Candidate discovery starts with the selected type’s tracker list and follows top-ten requests for that same type. The wider server pool is only a cache of stats for encountered servers; do not enumerate it to generate results or seeds.

The following are planning choices, rather than owner-confirmed requirements:

- Use a dropdown, with **Files** first and **Servers** second, beside the existing type selector. New tabs always start in Files; restored tabs retain their saved mode.
- Match the trimmed query as one case-insensitive substring in **either** the name or description. Use locale-independent case normalization. An empty/whitespace query lists all eligible servers. Do not concatenate fields and accidentally match across their boundary.
- A server belongs to the selected type when cached/retrieved stats say it knows that type, including a zero file count. Merely appearing in another peer's top-ten response is a discovery hint, not proof of type support.
- An encountered cached down/untried server may match when it advertises the type and has a best address; show its cached ping/status. Cache data is a best-effort snapshot, not a claim of current reachability. Do not re-probe just because an entry is down, old, unnamed, or lacks a description.
- Mode/type/query edits prepare the next search. They do not reinterpret existing rows or automatically launch/cancel a search. Starting the next search replaces the displayed generation.
- Opening a result selects the type captured by its search and supplies no filename. This uses the existing client navigation behavior. A known public key is always passed through.

No blocking questions remain. The owner approved implementation with the per-type discovery scope and the five planned display columns.

### 4. Proposed design

**Reuse the existing crawler, without turning files into servers.** File text search currently coordinates its own crawl, while hash search already uses a reusable network crawler with a replaceable per-address operation. Use that shared crawler for the new server-search operation. Keep the existing file-result model and file matching intact. Both result modes live in the same search tab and share its search/stop controls. Each implementation owns cancellation checks at EDT callback dispatch, including the file engine’s lifecycle and status notifications.

The server-search flow is:

1. Capture query, type and mode when Search starts. Display server-specific columns and clear results from the previous generation.
2. Obtain seeds only from the **selected type’s tracker list**, away from Swing. Do not enumerate the entire pool or prepublish unrelated cached servers. The pool is consulted by address only as servers are encountered during this type’s search.
3. Prime the crawl with the existing ranked, up servers for the type, falling back to the selected type’s complete tracker list when startup has not established reachability. With four or fewer ranked usable seeds, also resolve the existing onramps asynchronously, matching ordinary file-search bootstrap behavior.
4. For each visited address, the shared crawler independently requests that type's top-ten servers and runs the server metadata operation. Top-ten results extend the deduplicated address queue regardless of whether the current server matches the text.
5. For each encountered address, the metadata operation checks the current tracker/server-pool cache first. A cache hit is matched locally and sends **no server-stats request**. A miss uses the existing pool resolver, which shares any in-flight lookup and returns the actual pool-owned server after stats/identity processing. Match that server's name and description locally, checking advertised type support again.
6. Deduplicate results by the existing server identity. Multiple endpoints for the same key produce one row; equal names on different keys remain separate servers. Let the existing address queue deduplicate network visits.
7. Continue until discovered work is exhausted or the user stops. Stop preserves displayed results. Replacement and tab/window disposal reject all callbacks from the previous generation.

**Results and actions.** Server rows have **Server**, **Description**, **Address**, **Files** (for the captured type), and **Ping** columns, using the existing sortable list conventions. Blank names have a display fallback to the address, but address text does not become searchable name data. Descriptions stay complete in the model; use normal clipping and a tooltip for the full description. The server list has its own **Open Server** and **Bookmark Server** actions. It has no Download, Download To, Reveal File, Copy File Link, file metadata, or thumbnail actions. Double-click and explicit Open Server activate the selected server through the existing client window provider. Invalid/empty selection is a no-op.

Mode is captured for result labels, counts and tab titles. Server searches identify themselves as server searches, including when the query is empty. Selecting a different input mode leaves the currently displayed table and actions consistent with the last search until Search is pressed again. File rows are cleared and their thumbnail consumers/cache reset when replaced by server results; file thumbnail loading resumes normally on a later file search.

### 5. Architecture connections

The search tab creates one cancellable operation for the captured mode. The server operation uses the selected type’s tracker list for seeds, supplies a metadata callback to the shared type-scoped crawler, and looks up cached stats by each encountered address. The pool adapter already supplied to windows resolves only cache misses. The pool remains responsible for converting stats to identities, updating the cache and handling corrected ports; the UI receives immutable row data. Opening passes the row's known key, endpoint and captured type to the client window provider, whose registry already guarantees one window per public-key identity.

| New / changed thing | Owned / created by | Called / used by | Connects to (existing) |
|---|---|---|---|
| Per-tab Files/Servers selection and displayed mode | SearchTab; saved/restored by SearchWindow | Search start, status/count/title rendering | Existing type selector, tab preferences and engine-owned cancellation |
| Finite server-search operation | Created/cancelled by SearchTab; state confined to a worker invoker | Search/Stop and tab disposal | AsyncNetworkCrawler, IPQueue, AsyncTaskTracker, Tracker |
| Per-address cached stats and cache-miss resolution | Tracker and existing Myster-created KnownServerSource adapter over MysterServerPool | Metadata operation for addresses encountered by the selected type’s crawl | Tracker.getQuickServerStats, resolveServer; existing in-flight sharing; no pool enumeration |
| Immutable server results | Server-search operation, retained by its tab | Server result table and activation/bookmark actions | MysterServer metadata and MysterIdentity/PublicKeyIdentity |
| Separate server result table | Created/disposed by SearchTab | Mode-specific results and selection actions | JMCList/MCListFactory, existing sortable value classes |
| Server activation | Existing ClientWindowProvider in MysterFrameContext | Server result table | openWindow, identity-keyed getOrCreateWindow, ClientWindowData initial navigation |

**Network contract:** no wire change. Discovery still calls the existing type-scoped top-server operation. On cache misses, the pool resolver currently performs TCP ping and then bidirectional UDP server stats (`102`), selecting legacy `101` before local file lists initialize. Preserve that existing behavior, including **no fallback to 101 after a sent 102 fails**. Existing `/ServerName`, `/ServerDescription` (up to 255 Unicode characters, absent means empty), `/NumberOfFiles/<type>`, `/Identity` and `/Port` fields supply all required data. Never issue file searches, file stats or thumbnail requests for server mode. Preserve existing encrypted transport and private-type visibility checks; missing type advertisement is not permission to infer membership.

**Local persistence contract:** add a per-tab `TabMode_<index>` preference containing stable `files` or `servers` values, not an enum ordinal or display label. Missing/unrecognized values restore Files, preserving old saved windows. Do not persist results or automatically rerun restored searches. No existing on-disk server format changes.

### 6. Key decisions & edge cases

- **Reuse boundary:** use AsyncNetworkCrawler's pluggable address operation rather than copy MysterSearch or generalize its file-result interface. SearchResult currently requires download and file-metadata behavior; server results must not implement fake downloads or file stubs.
- **Avoid duplicate onboarding:** the shared crawler accepts a callback for discovered addresses. Do not pass Tracker.addIp for this search: that could begin stats before the metadata callback checks the cache. Use a no-op discovery callback; the pool-backed cache-miss operation performs necessary onboarding and tracker notifications.
- **Per-type candidates, per-address cache:** use only the selected type’s tracker list and its top-ten discovery to obtain candidates. The per-type tracker list is capped at 100; discovering more servers happens through the selected type’s crawl, not a pool-wide scan. For each visited address, consult the live cache before resolving. A cached server outside the selected type’s tracker list can match only if the crawl encounters it and its stats advertise the selected type.
- **Passive discovery scope:** use the existing pool resolver only after an address cache miss; it shares in-flight refreshes and returns an independently cancellable request. Its explicit-resolution API can retry dead-cached addresses; accept at most one attempt per address per search through IPQueue. Do not change global passive-suggestion/dead-cache policy or introduce a separate retry loop.
- **Cache absence versus empty data:** a cached older peer with no description is still a cache hit. Null name and missing description match as empty strings. A minimal/startup card with no selected-type advertisement produces no result; top-ten traversal still proceeds.
- **Discovery is independent:** a nonmatching, cached, or failed metadata check cannot prevent discovering neighbors. A top-ten failure cannot discard already matched cached stats or stop other branches. DNS/ordinary peer failures are per-address failures, not whole-search failures.
- **Identity and addresses:** deduplicate by actual MysterIdentity equality, prefer public keys when supplied, and do not merge by name, IP alone, or proposed alias equivalence. Use the resolved server's corrected/best endpoint in its row. Address-only identities can be displayed; opening goes through the provider's normal key-resolution/error path, without creating a provisional window. Known keys never get downgraded to address-only lookup.
- **Cancellation and completion:** search implementations own EDT dispatch and recheck their cancellation state when invoking callbacks; SearchTab does not maintain a generation counter. Cancellation emits no lifecycle callback, and the tab updates its stopped UI immediately. Dynamic DNS, type-specific seed preparation, onramp resolution, top-ten work and cache-miss request futures all belong to the enclosing search. A normal completion promise tells the UI when to finish and allows cancellation to own all child work; it is not an unused worker-only future. Natural exhaustion explicitly publishes completion; zero launched work explicitly completes too. Stop makes this search's callbacks moot but may leave a shared pool-owned refresh updating the cache for other callers.
- **Threading and scale:** prepare type-specific seeds and match metadata on a worker; confine crawl state and child launch to a subsystem invoker; mutate Swing only on the EDT. Batch row/status publication so a large crawl does not post one EDT task per server. Deliver queued result batches before final completion. Retain existing crawler scheduling instead of introducing a new global concurrency controller.
- **Immutable displayed generation:** result rows capture identity/address/name/description/count/ping and search type. Editing the type or mode cannot change their interpretation or activation. Future cache changes are visible on the next search, not via a new subscription.

### 7. Acceptance criteria

- [x] Every new tab defaults to Files; saved server tabs restore Servers, and older/invalid preferences restore Files.
- [x] Existing file search, columns, download actions, thumbnails, hover and tab behavior continue working.
- [x] Server searches match case-insensitive name **or** description substrings, handle missing fields, and treat blank input as all eligible servers.
- [x] Candidate discovery uses only the selected type’s tracker list and same-type top-ten crawl. An unrelated matching cached server is never included merely because it exists in the pool; no pool enumeration occurs. Zero-file known types are eligible and unadvertised types are excluded.
- [x] Cached servers do not incur search-triggered server-stats exchanges, even with blank descriptions or down/untried state. Cache misses use the shared pool resolver and populate the normal cache.
- [x] Top-ten crawling remains type-scoped, continues through nonmatches/failures/cache hits, and bootstraps from existing onramps when seeds are sparse.
- [x] One server identity appears once across repeated/multiple addresses; different identities with the same name remain distinct.
- [x] Server rows show name, description, endpoint, selected-type file count and ping/status, with server-only actions and no file requests/downloads/thumbnails.
- [x] Double-click/Open Server opens or raises the one ClientWindow for that identity and selects the captured type without selecting a file.
- [x] Stop, natural completion, empty work, search replacement and closing tabs/windows complete cleanly; late callbacks cannot alter stopped/replaced UI or other tabs.
- [x] Type-specific seed preparation, metadata matching and DNS/network work keep the EDT responsive, including a large synthetic crawl.

---
## ✦ IMPLEMENTATION DETAILS (for the implementation agent)
---

### 8. Affected files / classes

All Java paths below are relative to `src/main/java/` unless prefixed otherwise.

- **New:** `com/myster/search/ServerSearch.java` — finite cache-first server search composed with the existing crawler; returns a cancellable completion promise and emits batches of immutable server results.
- **New:** `com/myster/search/ServerSearchResult.java` — immutable snapshot of identity, endpoint, name, description, selected-type count and ping; no SearchResult implementation.
- **New:** `com/myster/search/ui/SearchMode.java` — Files/Servers display and stable preference conversion with Files fallback.
- **New:** `com/myster/search/ui/ServerSearchResults.java` — typed server JMCList, columns, description tooltip, open/bookmark commands and selection handling.
- **Changed:** `com/myster/search/ui/SearchTab.java` — mode dropdown, two result panes, captured displayed mode/type, operation cancellation, typed server batches and direct EDT callbacks guarded by the originating search’s cancellation state.
- **Changed:** `com/myster/search/ui/SearchWindow.java` — per-tab mode persistence/restoration, TabData and addNewTab overloads; keep existing callers defaulting to Files.
- **Changed if needed:** `com/myster/search/ui/SearchThumbnailSupport.java` — explicit reusable generation reset/suspension only if existing beginGeneration/setActive cannot safely release the replaced file generation.
- **Javadoc only:** `com/myster/tracker/ui/KnownServerSource.java`, `DefaultKnownServerSource.java`, `com/myster/tracker/MysterServerPool.java` — document using the shared resolution path for cache misses encountered during user-initiated, type-scoped server crawling. The search does not use pool enumeration.
- **Changed:** `com/myster/search/MysterSearch.java`, `SearchEngine.java` — suppress all listener/status callbacks at EDT dispatch using the existing cancellation flag; cancellation no longer emits searchOver.
- **Expected reuse without behavior changes:** `com/myster/search/AsyncNetworkCrawler.java`, `IPQueue.java`, `StandardMysterSearch.java`, `SearchResult.java`, `MysterSearchResult.java`, `com/myster/tracker/Tracker.java`, `MysterServerPoolImpl.java`, and `com/myster/client/ui/ClientWindowProvider.java`.
- **New/extended tests:** `src/test/java/com/myster/search/TestMysterSearch.java`, `src/test/java/com/myster/search/TestServerSearch.java`, `src/test/java/com/myster/search/ui/TestServerSearchResults.java`, `TestSearchTabServerMode.java`; existing search/thumbnail/provider tests as applicable.
- **Docs:** this plan, `docs/plans/README.md`, reciprocal related-plan links, `docs/conventions/myster-important-patterns.md`; implementation summary later at `docs/impl_summary/server-name-description-search.md`.

### 9. Step-by-step implementation

1. **Recheck the existing contracts.** Read `MysterSearch.searchThisIp/createPrimedIpQueue`, `AsyncNetworkCrawler.startWork`, `MultiSourceHashSearch.crawlNetwork`, `AsyncTaskTracker`, `Tracker.getTop/getAll/getQuickServerStats`, `MysterServer.knowsAboutType`, `MysterServerPoolImpl.resolveServer/refreshMysterServer`, and `ClientWindowProvider.openWindow`. Existing `MysterTypeServerList.getTop(int)` currently returns all up list entries rather than enforcing its argument; take at most 50 usable ranked seeds locally without expanding scope into a tracker fix. Handle null/empty tracker collections defensively.
2. **Define the result and matching contract.** Add ServerSearchResult with non-null identity/address and normalized non-null textual fields; capture raw searchable names separately from any display fallback. Use `trim().toLowerCase(Locale.ROOT)` on the query and independent name/description containment. Type eligibility is `knowsAboutType(type)`, not positive file count. Keep matching/snapshot helpers private or package-scoped in ServerSearch unless sharing proves necessary.
3. **Implement one finite ServerSearch operation.** Accept only dependencies actually needed: protocol, tracker, KnownServerSource, captured type/query, a batch result consumer and progress sink. Return `PromiseFuture<Void>` as the cancellation/completion handle consumed by the UI. Confine mutable state to a worker `Invoker`, following MultiSourceHashSearch; reuse a subsystem invoker rather than leaking one executor per search. Create a parent promise and `AsyncTaskTracker<Void>` with explicit exhaustion completion. Start its bootstrap work on that invoker. Use ordinary listeners for application state and `withInvoker` at boundaries.
4. **Prepare only type-specific seeds.** In a worker task, obtain `tracker.getTop(type, 50)`, cap usable ranked seeds at 50, and fall back to `tracker.getAll(type)` if no ranked usable addresses exist. Keep a short snapshot of those seed references/addresses for startup; do not call `source.forEachServer`, `pool.forEach`, or build an all-pool address map. Schedule bootstrap/crawl from the seed-preparation task’s invoker callback before that counted task can exhaust the tracker. All result matching happens through the per-visited-address operation, with immutable row batches and cancellation checks.
5. **Prime and crawl.** Populate IPQueue from the prepared selected-type seeds. Start onramp DNS as counted worker tasks when usable ranked seeds are four or fewer; each completion adds the address and resumes `AsyncNetworkCrawler.startWork` using the same queue and tracker. Invoke the crawler initially for cached seeds too. For each visited address, use a SearchIp callback that checks `tracker.getQuickServerStats(address)` and calls `source.resolveServer(address)` only on a cache miss. The pool resolver already issues the desired stats exchange and stores the result; do not call datagram stats again or manually construct MysterServer. Map a successful result to local eligibility/matching/result publication on the crawl invoker, then complete that child. Return completed promises for cache hits. Supply a no-op `addIp` callback to the shared crawler. Do not gate top-ten traversal on matching. Ensure a true empty bootstrap explicitly completes the parent. The existing crawler already owns returned-address DNS tasks; do not add a second queue/crawler.
6. **Preserve event ordering and cancellation.** Queue result batches before the final EDT completion notification. Each search implementation must recheck its existing cancellation state inside the EDT dispatch before invoking result/status/lifecycle callbacks. ServerSearch checks the returned promise’s cancellation state, including when cancellation follows worker completion but precedes EDT delivery. MysterSearch uses its existing endFlag for startup, results, stats, progress and natural completion. Cancellation itself emits no searchOver callback. Do not add or retain a separate SearchTab generation counter. Cancel the caller-owned resolver future, not the underlying shared pool refresh. Keep per-address metadata failures local with concise/fine logging; unexpected programming failures should remain visible rather than being converted to empty results.
7. **Build the separate server result pane.** Use `MCListFactory.buildMCList`/JMCList with existing sortable string/number/ping values and normal logical-pixel dimensions. Add tooltip support without changing generic list widgets. Support double-click and Open Server through one action. Pass `Optional<PublicKeyIdentity>` extracted from the captured identity, `Optional.of(capturedType)` and `Optional.empty()` filename to `context.clientWindowProvider().openWindow(...)`, on the EDT. The provider owns errors, identity discovery and window reuse. Bookmark the captured identity through `tracker.addBookmark(...)`. Guard invalid selection; no file-specific menu or chooser is installed on this pane.
8. **Integrate SearchTab.** Add SearchMode selection and a CardLayout (or equivalent) around the existing file list and new server pane; retain the existing file table/listeners rather than replacing their model type. Keep selected mode and displayed mode separate. Capture mode/type/query before starting either engine. Generalize `searchEngine` ownership to one `Cancellable` handle: file engine adapts `flagToEnd`, server mode uses its returned promise. Clear the handle before cancellation. Keep file SearchResultListener callbacks and server batch callbacks separate; consume them directly on the EDT because their producers own cancellation checks. Generalize clearing/status/count/title behavior to the displayed result kind; start server lifecycle explicitly, and use its promise completion for Search done. Stop cancels the originating search and restores Search button/status once while preserving rows and file thumbnail demand where applicable. Replacement/disposal cancel the old operation before changing the displayed state.
9. **Manage thumbnail replacement.** Before showing server results, reset replaced file hover/row consumers and cache and clear file rows, then suspend file thumbnail activity. Never initialize file metadata columns or a thumbnail-eligible generation for server results. Gate later `setThumbnailActive` calls from SearchWindow by displayed mode; simply switching back to a visible tab must not restart hidden file requests. Reuse `beginGeneration` reset machinery with an ineligible generic profile or extract a small reset method if needed. Stop/completion of an ordinary file search must retain its displayed thumbnail demand as today.
10. **Persist the mode.** Add `TAB_MODE_PREFIX = "TabMode_"`, stable mode serialization, and a mode member in SearchWindow.TabData. Preserve the existing two-argument addNewTab API via a Files-default overload; restoration uses a mode-aware overload. Restore mode before `restoreSearchState` so title labeling is correct, without starting work. Existing or malformed preference records default to Files. Ensure control edits participate in existing save timing.
11. **Verify and document.** Run focused tests below, check normal file search and provider reuse, then perform desktop smoke checks when available. Use `git diff --check`. Update the implementation summary with actual tests and any desktop checks that remain unavailable. Do not silently implement broader tracker, protocol, picker or alias changes discovered during this task.

### 10. Tests to write

Use controlled PromiseFuture contexts, Mockito, explicit invoker/EDT barriers and latches rather than sleeps or real network peers.

- **TestServerSearch — matching/cache:** name-only, description-only, mixed case, locale-independent behavior, blank input, null/blank name, empty legacy description, no cross-field match, type present with zero files, unknown type omitted, encountered down/untried cached server included, and address display fallback not searched. Include a matching cached server omitted from the selected type’s tracker list and all top-ten responses; assert it is not emitted and `source.forEachServer` is never called. Verify cache hits cause no resolver/stats calls.
- **TestServerSearch — traversal/cache misses:** seed A returns B, including repeat/self/loop addresses; nonmatching A still discovers matching B. A known B uses cache; an unknown B resolves once, emits the pool-returned server snapshot and uses its corrected/best endpoint. Discovery callback must not call Tracker.addIp and race a second onboarding. Assert selected type is passed to all getTopServers requests and server mode never calls getSearch, file stats, hash search or thumbnails. Test a cached server outside the initial selected-type list is matched only after a same-type top-ten response discovers its address. Test a cache entry appearing after seed preparation but before its visit avoids resolution.
- **TestServerSearch — identity/failures:** two addresses with one public key yield one row; two keys sharing a name yield two. A top-ten error and a stats error leave other branches working. Missing type in a minimal card is not a match. Absent description does not trigger a refresh.
- **TestServerSearch — lifecycle:** no seeds plus successful/failing onramp DNS; no usable work; onramp discovered after initial seeds finish; cancellation during seed preparation, DNS, top-ten and stats; already-completed cache children; foreign-invoker futures; final batch delivered before completion; completion once. Simulate independently cancellable resolver futures and verify stopping one search does not cancel another caller's pool refresh. Keep the existing pool cancellation tests as the authority for that implementation contract.
- **TestServerSearch — responsiveness:** prepare seeds and match a large synthetic type-scoped crawl off the EDT, check bounded batch delivery instead of one event per row, and verify that stopping makes queued batches moot. No brittle wall-clock throughput threshold.
- **TestServerSearchResults:** real table selection and column values, full description retained, zero/invalid selection safe, bookmark uses identity, activation passes captured identity/address/type and no filename. Reuse `TestClientWindowProvider` to verify two activations of the same identity raise one window even through different endpoints; test address-only results use the provider's existing discovery path.
- **TestSearchTabServerMode:** new tab Files default; running Files and Servers in different tabs; mode/type input edits do not reinterpret displayed rows/actions; server result counts/labels; Stop preserves rows; replacement and disposal reject late file/server lifecycle/results/status callbacks. Verify server mode has no download/reveal-file/copy-file-link menus and no thumbnail requests, then switch back to Files and verify existing column/thumbnail behavior.
- **Preference coverage:** mode round-trip, missing/unknown mode fallback, restored server title and no network work on restoration. Prefer extracting/parsing through production helpers already needed for persistence; do not add test-only public APIs.
- **Existing regressions:** `TestStandardMysterSearch`, `TestMysterSearchResult`, `TestMultiSourceHashSearch`, `TestSearchTabThumbnailLifecycle`, `TestSearchThumbnails`, `TestSearchThumbnailHoverController`, `TestClientWindowProvider`, and relevant MysterServerPool tests.

Suggested focused command (adjust names to actual added tests):

```sh
mvn -Djava.awt.headless=true -Dtest=TestServerSearch,TestServerSearchResults,TestSearchTabServerMode,TestStandardMysterSearch,TestMysterSearchResult,TestMultiSourceHashSearch,TestSearchTabThumbnailLifecycle,TestSearchThumbnails,TestSearchThumbnailHoverController,TestClientWindowProvider,TestMysterServerPoolImpl test
```

Run the repository's required full checks during implementation after focused verification, not during this planning task. Manual smoke: search a description-only term, a name-only term and empty input in Servers for a selected type; verify cache hits avoid stats while top-ten discovery continues; stop/restart across modes; open the same server twice and through an alternate endpoint; verify the existing window rises and selects the captured type; restore mixed-mode tabs; check file downloads/thumbnails still work. Check dropdown/table layout at normal and fractional/high display scales and with long descriptions.

### 11. Docs / Javadoc to update

- Document ServerSearch's finite lifecycle, cache-first/type/matching rules, callback invoker/batching and cancellation boundary; ServerSearchResult's snapshot semantics and identity handling; server pane action/navigation semantics.
- Update SearchTab/SearchWindow Javadoc to describe selected versus displayed mode and preference compatibility. Clarify the finite-crawl use of KnownServerSource and the pool's shared refresh ownership without claiming cancellation stops pool-owned network work.
- Update `docs/codebase-structure.md` Search section with server search/shared crawler connections when implemented. Update `docs/design/Myster Resource Links.md` only if implementation changes its documented navigation contract; this plan uses that contract as-is.
- Keep `docs/conventions/myster-important-patterns.md` aligned with the per-type candidate discovery, cache-only address lookup and no eager addIp trap, plus displayed-mode thumbnail ownership.
- Maintain this plan, the plan index and reciprocal cross-links. Implementation handoff writes `docs/impl_summary/server-name-description-search.md` with final behavior, verification and deviations. No server-stats wire design revision is needed because this uses its existing fields/protocol.
