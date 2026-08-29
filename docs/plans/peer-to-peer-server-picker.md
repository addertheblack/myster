# Peer-to-Peer Connection Server Picker

## 1. Summary

Replace the free-form `AskDialog` opened by **New Peer-to-Peer Connection...** with a searchable
known-server chooser shared with the custom-type member picker. Preserve direct address entry in
both workflows: when the chooser recognizes a dotted IP address or DNS-style hostname, it performs
an asynchronous server-stats lookup, adds the resolved server to the chooser, and obtains the
public-key identity required to derive a member `ServerCid`.

## 2. Non-goals

- Do not change how `ClientWindow`, `ClientWindowProvider`, or the peer-to-peer protocol establishes a connection after an address has been selected.
- Do not add general crawling/discovery, bookmark management, or periodic background refresh to the
  picker. Its only active lookup is the address explicitly typed by the user.
- Do not change which servers are eligible to become custom-type members; a member must still have a `PublicKeyIdentity` from which a `ServerCid` can be derived.
- Do not require a server to be currently up before the user can select it. Known down or untried servers remain selectable when they otherwise satisfy the caller's requirements.
- Do not change any network or persistence format.
- Do not add raw CID entry. Membership identity must come from the server-stats public key.
- Do not add an explicit Retry button, link, menu item, or key binding for failed direct lookups.
- Do not add IPv6 literal entry in this milestone; the current `MysterAddress` colon parsing is not
  IPv6-safe and must be redesigned separately.

## 3. Assumptions & open questions

- Assumption: “a list of servers” means the complete local `MysterServerPool` snapshot, matching the source used by the existing member picker, rather than only the servers visible under one tracker type.
- Assumption: the connection picker should include every known server that currently has a `getBestAddress()` value. Servers with no usable address cannot be opened in `ClientWindow` and are omitted.
- Owner-confirmed: direct address entry must work in both the connection and member uses of the
  shared chooser.
- Owner-confirmed: resolving a new address performs an on-the-fly server-stats exchange so the
  chooser obtains the responding server's identity/CID rather than treating the address itself as
  identity.
- Assumption: address recognition is syntactic and non-blocking. Accept a dotted IPv4 literal or a
  DNS-style hostname containing at least one dot, with an optional numeric port. Ordinary search
  text remains a local name/address filter and does not trigger DNS or network I/O.
- Owner-confirmed: a syntactically valid candidate starts a cancellable asynchronous chain with an
  approximately one-second delay before DNS or network work. Every subsequent character edit
  immediately cancels the entire previous chain and starts a new delayed chain only if the new text
  is still a valid candidate.
- Owner-confirmed: the dialog has no explicit Retry button or retry action. Failure remains visible;
  a later text edit or reopening the dialog naturally creates a new lookup without spending UI
  complexity on an uncommon workflow.
- Assumption: selecting or resolving a server still opens or focuses a normal `ClientWindow`; only
  the chooser's address-resolution path performs the preliminary server-stats probe.
- Assumption: the dialog remains document-modal. The menu action should use the currently active application window as its owner; a missing owner is allowed for the macOS global menu-bar case.
- There are no blocking open questions for this design.

## 4. Proposed design

Move the current `ServerPickerDialog` out of the custom-type package and make it a general tracker/UI
component. Preserve its searchable three-column table, placeholder row, selection behavior,
double-click behavior, and modal `showAndWait()` flow. Replace its member-specific
`PickedServer(ServerCid, displayName)` result with the selected `MysterServer` itself. The caller
already owns the meaning of the selection and should perform the final conversion.

The existing filter field becomes a combined search/address field. A small pure parser recognizes
only unambiguous dotted IPv4 or DNS-style hostname input, optionally followed by a valid port. Each
valid edit creates one cancellation-owned promise chain using Myster's concurrency library:
approximately one-second delay, asynchronous DNS/`MysterAddress` resolution, then asynchronous
server-stats resolution. A further edit cancels that owning promise, which cancels every created
stage and makes any underlying operation that cannot physically stop immediately moot. DNS
resolution and server I/O never run on the EDT.

The dialog displays the chain's current state inline: waiting during the one-second quiet period,
resolving the IP/domain, contacting/checking server stats, responding/success, or a concise DNS/no-
response failure. Cancelling because of another keystroke clears the old state before showing the
new candidate's waiting state. On success, the returned `MysterServer` is inserted or refreshed,
selected, and made available to the caller if it passes the caller's eligibility predicate. There
is no Retry control; editing the address is the only in-dialog way to initiate another attempt.

The reusable dialog accepts caller-supplied presentation and eligibility settings: dialog title,
confirmation-button text, a `Predicate<MysterServer>`, and concise text explaining why a resolved
server is ineligible. The connection action configures it with **Connect** and an eligibility rule
requiring `getBestAddress().isPresent()`. The type editor configures it with **Add Selected** and an
eligibility rule requiring `PublicKeyIdentity`, then derives the `ServerCid` defensively after
selection.

Replace the type-editor-specific enumeration interface with a shared `KnownServerSource` in the tracker UI boundary. It enumerates all known `MysterServer` objects and retains the existing `ServerCid`-to-display-name lookup needed by the Members tab. `Myster` creates one adapter over `MysterServerPool` and places it in `MysterFrameContext`; both the menu action and `TypeManagerPreferences` use that same adapter. Eligibility belongs in each picker invocation rather than in the source, because connection and membership have different constraints.

The pool-backed resolver reuses the existing TCP-ping plus bidirectional-server-stats pipeline. It
returns a future for the resulting `MysterServer` after normal pool identity extraction/update has
completed, rather than requiring the dialog to watch global pool events. Concurrent lookup of the
same address shares the in-flight work. Because direct entry is an explicit user action, it may
retry an address in the dead-address cache; ordinary passive `suggestAddress(...)` behavior remains
unchanged. A member is selectable only if `/Identity` decodes to `PublicKeyIdentity`; an address-only
response can still be used for a connection but cannot become an access-list member.

On confirmation, `NewClientWindowAction` reads the selected server's best address and constructs the
same `ClientWindowData` it does today. It then uses
`ClientWindowProvider.getOrCreateWindow(...)`, shows the window, and brings it forward. Cancellation,
closing the dialog, an empty pool, or an empty filtered result does nothing. If the selected server
loses its best address between list population and confirmation, show a concise `AnswerDialog`
alert and do not open a blank client window.

## 5. Architecture connections

The server pool remains the owner of known server state and the only component that turns server
stats into a cached `MysterServer`. `KnownServerSource` is a narrow UI-facing adapter for both pool
enumeration and explicit asynchronous address resolution. `ServerPickerDialog` renders the known
snapshot plus the state of the one typed-address lookup, and the two callers interpret the chosen
`MysterServer` according to their own domain rules.

| New / changed thing | Owned / created by | Called / used by | Connects to (existing) |
|---|---|---|---|
| Shared `KnownServerSource` | `Myster` creates an adapter over `MysterServerPool`; `MysterFrameContext` carries it | `ServerPickerDialog`, `TypeEditorPanel` member-name rendering | `MysterServerPool.forEach(...)`, explicit address resolution, `lookupIdentityFromCid(...)`, `MysterServer` |
| Address-candidate parser | tracker UI; pure syntax only | combined chooser search/address field | dotted IPv4/DNS hostname grammar, optional Myster port |
| Cancellable address lookup chain | tracker UI helper owned by the open dialog | one active chain per valid text value | `PromiseFutures.delay(...)`, `PromiseFutures.execute(...)`, `PromiseFuture.mapAsyncInline(...)`, `KnownServerSource.resolveServer(...)` |
| Explicit server resolver | `MysterServerPoolImpl` | pool-backed `KnownServerSource` adapter | existing TCP ping, bidirectional server stats, `/Identity`, pool update and in-flight deduplication |
| General `ServerPickerDialog` | `com.myster.tracker.ui` | `NewClientWindowAction`, `TypeEditorPanel.addMember()` | `MCList`, `KnownServerSource`, debounce/stale-result guard, caller predicate, `MysterServer` |
| Connection-picker invocation | `NewClientWindowAction` | File-menu action | `MysterFrameContext`, `MysterServer.getBestAddress()`, `ClientWindowProvider` |
| Member-picker invocation | `TypeEditorPanel` | Members tab's **Add Member...** action | `PublicKeyIdentity`, `ServerCid.fromPublicKey(...)`, `AddMemberOp` |
| Shared source wiring | `MysterFrameContext` and application bootstrap in `Myster` | all frame-owned menu bars and Type Manager preferences | existing pool adapter logic currently embedded near `TypeManagerPreferences` construction |

Data flow for a new peer-to-peer connection:

1. The menu action obtains `KnownServerSource` from `MysterFrameContext` and opens
   `ServerPickerDialog` with the address-present predicate.
2. The dialog enumerates the pool and applies the live local filter. If the input is a syntactically
   valid address candidate, it cancels the prior lookup owner and starts a new chain in `Waiting`
   state.
3. After approximately one second without another edit, the chain asynchronously resolves the
   domain/IP into `MysterAddress`, transitions to `Contacting`, and composes into the pool's server-
   stats future.
4. The pool runs its existing stats flow, extracts `/Identity`, updates normal pool state, and
   completes with the resulting `MysterServer`.
5. The dialog's EDT-dispatched completion shows responding/failure state, inserts/selects the
   current successful result, and returns the selected server on confirmation. Cancelled chains
   cannot mutate the dialog.
6. `NewClientWindowAction` obtains the selected server's current best address and creates
   `ClientWindowData` with the address and empty type/file selections.
7. `ClientWindowProvider` reuses an existing identity-associated client window when possible or
   creates a new one, exactly as it does today.

Data flow for adding a custom-type member:

1. `TypeEditorPanel` opens the same dialog with the public-key-identity predicate.
2. Known eligible servers remain immediately selectable. A typed address goes through the same
   server-stats resolution; the dialog enables confirmation only if the returned server has a
   `PublicKeyIdentity`.
3. After selection, the panel pattern-matches `PublicKeyIdentity`, derives `ServerCid`, and appends
   the existing signed `AddMemberOp`.
4. Existing access-list save and Members-table refresh behavior remains unchanged.

There are no new or changed wire, on-disk, or preferences contracts. The feature reuses the existing
server-stats protocol and adds only local Java APIs that expose its asynchronous result.

## 6. Key decisions & edge cases

- Return `MysterServer`, not a union of address and CID fields. The server is the shared selection domain; address choice is connection-specific and CID derivation is membership-specific.
- Keep eligibility caller-defined. A connection requires an address but not a public key; a member requires a public key but may have no current address.
- The chooser's text has two interpretations: every value is a local filter, but only a strict
  dotted IPv4/DNS candidate becomes a network lookup. Do not use a loose `text.contains(".")` check
  as the validator; validate hostname labels and optional port before scheduling work.
- Implement debounce as the first cancellable promise in the lookup chain, not as disconnected
  Swing timer state: `delay (~1 second) -> DNS/MysterAddress task -> server-stats task`.
- Every document edit cancels the previous owning promise immediately. The owner must track the
  delay and every mapped stage explicitly because `PromiseFuture.mapAsyncInline(...)` deliberately does
  not cancel its source future. Cancellation propagates where supported and always makes later
  results moot.
- Dispatch stage/status and final callbacks on the EDT. DNS, ping, and server-stats work must not
  execute on the EDT, and a cancelled completion must not change selection or status.
- The explicit resolver returns the actual pool-created/refreshed `MysterServer`. Do not duplicate
  server-stats decoding, public-key parsing, CID derivation, or server construction in Swing code.
- A stats response without a usable `/Identity` is not a member identity. It may remain connection
  eligible, but the member picker reports that the server does not advertise a public identity and
  keeps confirmation disabled.
- A typed explicit address is allowed to retry a dead-cache entry. This exception belongs on the
  explicit resolver API; passive pool suggestions retain current dead-cache suppression.
- Start at most one chain per current text value. The one-second cancellable delay prevents transient
  valid prefixes from reaching DNS/network stages, while pool-level in-flight sharing prevents two
  separate dialogs from multiplying server-stats work for the same resolved `MysterAddress`.
- Do not add a Retry button, retry link, or special retry key binding. A failure remains visible
  until the text changes, a known server is selected, or the dialog closes.
- Enumerate all known pool servers in `KnownServerSource`. Do not bake either caller's eligibility policy into the shared source.
- Replace `TypeEditorServerSource` rather than making the connection menu depend on a type-editor package. The shared interface belongs beside tracker UI because it exposes a read-only view of known server state for UI consumers.
- Preserve the existing status labels: `Untried`, `Down`, and `Up`. Down and untried rows are selectable because opening a client window is allowed to attempt the connection.
- Treat server names as nullable despite existing picker assumptions. Display a stable fallback such as the best address or “Unnamed Server”, and make filtering null-safe.
- A server may lose its best address after the dialog snapshot is populated. The connection action must re-read `getBestAddress()` and alert if absent.
- Empty pool and no filter matches use the existing non-selectable “No servers found” placeholder and leave the confirmation button disabled.
- Cancel, Escape, window close, and confirmation with no real row selected return an empty result and perform no action. Enter should activate the confirmation button when a real row is selected.
- The dialog owns no pool listener. Each opening takes a fresh snapshot; live pool updates while a modal picker is already open are outside this milestone.
- The future returned for a typed address supplies the newly resolved server directly, so the dialog
  still does not need a general pool listener. Disposing the dialog cancels or makes its pending UI
  completion moot; it must never update disposed Swing state.
- The active window is used as owner when the action comes from a global/shared menu bar. A null owner must remain supported.

## 7. Acceptance criteria

- [ ] Choosing **New Peer-to-Peer Connection...** opens a searchable server picker instead of an `AskDialog`.
- [ ] The connection picker shows known, addressable servers with Server Name, Address, and Status columns.
- [ ] Typing in the filter narrows rows case-insensitively by server name or address.
- [ ] In both connection and member pickers, entering a valid dotted IPv4 address or DNS-style
  hostname, with an optional valid port, shows a pending state and triggers one asynchronous server
  check only after approximately one second without another edit.
- [ ] Typing another character immediately cancels the prior delay/DNS/stats chain; rapid typing
  does not accumulate network lookups.
- [ ] Plain search text, malformed hostnames, and invalid ports perform no DNS or server I/O.
- [ ] Address resolution and server stats never block the EDT; the dialog shows checking and
  failure state without preventing selection of existing rows.
- [ ] The dialog visibly distinguishes waiting, address resolution, contacting/checking stats,
  responding success, DNS failure, and no-response failure.
- [ ] Editing the input while a lookup is running prevents its stale completion from changing the
  current rows, status, or selection.
- [ ] A successful direct lookup adds/selects the resolved server and allows **Connect**.
- [ ] Selecting a row and clicking **Connect**, pressing Enter, or double-clicking opens or focuses the corresponding `ClientWindow`.
- [ ] Cancel, Escape, or closing the picker opens no client window.
- [ ] Servers with no best address are not offered for peer-to-peer connection.
- [ ] Known down and untried servers with an address remain selectable.
- [ ] An empty pool or a filter with no matches shows the existing placeholder and disables confirmation.
- [ ] The custom-type Members tab uses the same picker implementation and retains its current appearance and behavior.
- [ ] The member picker continues to show only servers with `PublicKeyIdentity`, and adding a selection appends the same `AddMemberOp` using the correct `ServerCid`.
- [ ] A successful direct member lookup derives its `ServerCid` from the public key returned in
  server stats; an address-only/no-identity response cannot be added and gets a clear inline reason.
- [ ] Two concurrent requests for the same normalized address share pool-level lookup work, and an
  explicit typed address can retry an address currently present in the passive dead-address cache.
- [ ] The dialog exposes no explicit Retry button or retry action.
- [ ] Server names that are null do not crash population or filtering.
- [ ] No network protocol, access-list format, or preference format changes.

---
## ✦ IMPLEMENTATION DETAILS (for the implementation agent)
---

## 8. Affected files / classes

- `src/main/java/com/myster/tracker/ui/KnownServerSource.java` — new shared UI-facing source interface replacing the type-editor-only source and exposing explicit asynchronous address resolution.
- `src/main/java/com/myster/tracker/ui/ServerAddressCandidate.java` — **NEW**, pure syntactic parser/value for dotted IPv4 or DNS-style input with optional port.
- `src/main/java/com/myster/tracker/ui/ServerAddressLookup.java` — **NEW**, cancellation-owning delay/DNS/stats chain with stage notifications.
- `src/main/java/com/myster/tracker/ui/ServerPickerDialog.java` — new location and generalized implementation of the current picker, including cancellation-owned delayed lookup and inline resolution state.
- `src/main/java/com/general/thread/PromiseFutures.java` — add a reusable cancellable delay future backed by Myster's timer utility.
- `src/main/java/com/myster/tracker/MysterServerPool.java` — expose an explicit future-returning server resolver distinct from passive address suggestion.
- `src/main/java/com/myster/tracker/MysterServerPoolImpl.java` — return the existing server-stats pipeline's resolved `MysterServer`, share in-flight address work, and permit explicit dead-cache retries.
- `src/main/java/com/myster/type/ui/ServerPickerDialog.java` — remove after moving/generalizing the class.
- `src/main/java/com/myster/type/ui/TypeEditorServerSource.java` — remove after replacing usages with `KnownServerSource`.
- `src/main/java/com/myster/type/ui/TypeEditorPanel.java` — consume the shared source, invoke the generic picker, and derive `ServerCid` from the selected server.
- `src/main/java/com/myster/type/ui/TypeManagerPreferences.java` — replace `TypeEditorServerSource` constructor/field types with `KnownServerSource`.
- `src/main/java/com/myster/ui/MysterFrameContext.java` — carry the shared known-server source for frame/menu actions.
- `src/main/java/com/myster/ui/menubar/event/NewClientWindowAction.java` — replace address prompting/parsing with server selection and best-address handling.
- `src/main/java/com/myster/Myster.java` — construct one known-server adapter, include it in the frame context, and pass it to Type Manager preferences.
- `src/main/java/com/myster/progress/ui/ProgressManagerWindow.java` — update the local/demo `MysterFrameContext` construction with an empty fake source.
- `src/test/java/com/myster/tracker/ui/TestServerPickerDialog.java` — new focused tests for non-modal picker population/filtering helpers if the MCList harness is headless-safe.
- `src/test/java/com/myster/tracker/ui/TestServerAddressCandidate.java` — **NEW**, address recognition and normalization coverage without DNS.
- `src/test/java/com/myster/tracker/ui/TestServerAddressLookup.java` — **NEW**, deterministic chain, cancellation, and stage-state coverage.
- `src/test/java/com/general/thread/TestPromiseFuture.java` — cover delay completion and cancellation.
- `src/test/java/com/myster/tracker/TestMysterServerPoolImpl.java` — extend for explicit resolution result, identity extraction, deduplication, failure, and dead-cache retry behavior.
- `src/test/java/com/myster/ui/menubar/event/TestNewClientWindowAction.java` — new focused action tests if the dialog invocation is injected/testable; otherwise cover this path with the manual smoke tests below.

## 9. Step-by-step implementation

1. Introduce the shared server source.
   - Add `com.myster.tracker.ui.KnownServerSource`.
   - Replace `TypeEditorServerSource.forEachServer(BiConsumer<MysterServer, ServerCid>)` with `void forEachServer(Consumer<MysterServer>)` so the source enumerates all known servers without applying member policy.
   - Retain `Optional<String> resolveDisplayName(ServerCid cid)` for rendering existing access-list members.
   - Add `PromiseFuture<MysterServer> resolveServer(MysterAddress address)` for explicit user-entered
     candidates after the lookup chain has completed its DNS/address stage. Network work remains off
     the EDT and completion follows the existing promise/invoker conventions.
   - Document that enumeration is a fast, non-blocking view of locally known pool entries and that callers apply their own eligibility rules.
   - Remove `TypeEditorServerSource` after all references have migrated; do not leave a type-package alias unless an external compatibility need is discovered during compilation.

2. Add strict, non-blocking address-candidate recognition.
   - Add `com.myster.tracker.ui.ServerAddressCandidate` as a small immutable value/parser used by
     the chooser before any DNS work.
   - Accept trimmed dotted IPv4 input or a DNS hostname with at least two valid labels. Permit an
     optional decimal port in the range 1–65535; omission retains the Myster default port.
   - Reject whitespace, empty labels, labels beginning/ending in `-`, illegal hostname characters,
     malformed/out-of-range IPv4 octets, nonnumeric/out-of-range ports, bare search words, URLs,
     paths, and IPv6 literals.
   - Normalize DNS names case-insensitively and normalize the port representation so equivalent
     stable input keys deduplicate. Do not perform DNS resolution in this parser.

3. Add a cancellable delay primitive and address-lookup chain.
   - Add `PromiseFutures.delay(Duration)` returning `PromiseFuture<Void>`. Implement it with
     `com.general.util.Timer`, and register `Timer.cancelTimer()` through the promise's
     `AsyncContext.trackForCancellation(...)` so cancellation prevents an unelapsed delay from
     firing.
   - Add package-private/testable `ServerAddressLookup` to build one chain for a
     `ServerAddressCandidate`: `PromiseFutures.delay(~1 second)` ->
     `PromiseFutures.execute(() -> MysterAddress.createMysterAddress(...))` ->
     `KnownServerSource.resolveServer(address)`.
   - Represent the local stage as an ordinary Java enum (not strings): `WAITING`, `RESOLVING`, and
     `CONTACTING`. Final success/failure remains the `PromiseFuture<MysterServer>` outcome. Notify
     stage changes through the supplied EDT/invoker boundary so Swing does not infer stages from
     timing.
   - Wrap the mapped chain in one owning `PromiseFuture<MysterServer>` whose `AsyncContext` tracks
     the delay future and every mapped future as they are created. This explicit ownership is
     required because cancelling a `mapAsyncInline(...)` result does not cancel its source. Forward the
     final `CallResult` synchronously into the owner; cancellation must make later callbacks moot.
   - Keep the default delay approximately one second and inject the delay operation/duration in
     tests so cancellation and stage ordering are deterministic without sleeping.

4. Expose explicit pool resolution without duplicating server construction.
   - Add a future-returning method to `MysterServerPool` for an explicitly requested
     `MysterAddress` and implement it in `MysterServerPoolImpl` by extracting/refactoring the current
     `refreshMysterServer(...)` pipeline.
   - Preserve TCP ping, bidirectional stats, expected-identity handling for existing callers,
     `MysterServerImplementation.extractCorrectedAddress(...)`, `/Identity` extraction, normal
     identity-tracker/cache updates, dead-cache failure recording, and `serverRefresh` events.
   - Make the result future complete with the exact created/refreshed `MysterServer` after pool
     state is updated. Refactor `serverStatsCallback(...)` to return that server rather than adding
     a second stats decoder in the UI adapter.
   - Share in-flight explicit work by normalized `MysterAddress`. Ensure completion/failure removes
     the entry exactly once. Passive `suggestAddress(...)` may continue discarding the returned
     future.
   - Explicit user resolution bypasses an existing dead-cache suppression check so the user can
     retry; a failed explicit attempt still refreshes the dead-cache entry.
   - The `KnownServerSource` adapter delegates the already resolved `MysterAddress` to this pool
     method. DNS belongs to `ServerAddressLookup`'s preceding worker stage; do not repeat it in the
     adapter.

5. Create the one pool-backed adapter during application bootstrap.
   - In `Myster`, construct `KnownServerSource` after `MysterServerPoolImpl pool` is available and before `MysterFrameContext` is constructed.
   - Implement `forEachServer` as delegation to `pool.forEach(...)` without filtering.
   - Preserve the current `resolveDisplayName` chain: `lookupIdentityFromCid` -> `PublicKeyIdentity` -> `getCachedMysterServer` -> `getServerName`, and ensure a null name resolves to `Optional.empty()`.
   - Add the adapter to the `MysterFrameContext` constructor and pass that same instance to `TypeManagerPreferences`.
   - Remove the later anonymous `TypeEditorServerSource` block so pool adaptation exists in one place.
   - Update the demo/test context in `ProgressManagerWindow` with a no-server implementation. Search all `new MysterFrameContext(...)` calls so the record-component change is complete.

6. Move and generalize `ServerPickerDialog`.
   - Move the implementation from `com.myster.type.ui` to `com.myster.tracker.ui` and update package/imports/Javadoc.
   - Change the source field and constructor parameter to `KnownServerSource`.
   - Add constructor inputs for:
     - dialog title;
     - confirmation-button label;
     - `Predicate<MysterServer>` eligibility;
     - caller-facing text for a directly resolved but ineligible server.
   - Change `showAndWait()` to return `Optional<MysterServer>` and reset dialog result/state on each call. If the implementation remains intentionally single-use, document and enforce that instead of silently accumulating components/items on a second call.
   - Remove `PickedServer`, `ServerCid` storage, and member-specific wording from the common class.
   - `populateAll()` should enumerate all servers, apply the eligibility predicate, and build the existing row items.
   - Rename the filter label to make both behaviors discoverable, for example `Search or enter a
     server address:`. Ordinary typing continues to filter known rows immediately.
   - On every document edit, immediately cancel the currently owned lookup future, clear its old
     status, and parse the new trimmed value with `ServerAddressCandidate`. If valid, start a new
     `ServerAddressLookup`; its first stage is the approximately one-second delay. If invalid/search-
     only, start no async work.
   - Render lookup state in a dedicated inline status component without replacing the known-server
     list: `Waiting to check <address>…`, `Resolving <address>…`, and
     `Contacting <resolved-address>…`. Use a small indeterminate progress indicator or themed status
     icon where consistent with current Swing components.
   - Attach the owning future's ordinary listeners through the EDT invoker. On success show a clear
     responding state before/while selecting the result. Distinguish DNS/invalid-host failure from
     server no-response/stats failure with concise user-facing text; do not expose stack traces or
     raw exception class names.
   - Cancellation is the primary stale-result guard. Also retain a current-owner identity/generation
     check at the Swing boundary as defense in depth because the promise contract cannot guarantee
     that underlying DNS or network work physically stops after cancellation.
   - On success, apply the caller predicate. If eligible, add/replace the matching `MysterServer`
     row, reapply the current filter, select it, and enable confirmation. If ineligible, keep it out
     of the caller-filtered list and display the supplied explanation. On failure, display a concise
     inline failure. Do not add a Retry button/link or make Enter retry failed input; only a later
     edit or a new dialog opening starts another attempt.
   - Preserve the current columns, sizes, live `DocumentListener`, double-click confirmation, placeholder, document modality, and centering.
   - Make display/filter handling null-safe:
     - normalize a nonblank name when present;
     - otherwise use the row's address string as the display fallback;
     - otherwise show `Unnamed Server`;
     - filter against the normalized display name and address.
   - Set the confirmation button as the root pane's default button. Bind Escape to clear the result and dispose, following the existing `AskDialog`/`AnswerDialog` input-map pattern.
   - Confirmation must accept only a real `ServerPickerItem`, assign its `MysterServer` to the result, and dispose.

7. Migrate the custom-type member picker.
   - Change `TypeEditorPanel` and `TypeManagerPreferences` fields, constructor parameters, overloads, and Javadocs from `Optional<TypeEditorServerSource>` to `Optional<KnownServerSource>`.
   - Update `MemberItem` to use `KnownServerSource.resolveDisplayName(...)`; preserve its CID fallback behavior.
   - In `TypeEditorPanel.addMember()`, create the shared dialog with:
     - title `Add Member — Pick a Server`;
     - action label `Add Selected`;
     - predicate `server -> server.getIdentity() instanceof PublicKeyIdentity`;
     - ineligible text explaining that the responding server did not advertise a public identity
       and therefore has no usable member CID.
   - After a selection, defensively pattern-match the identity again. If it is not `PublicKeyIdentity`, alert and return; otherwise derive `ServerCid.fromPublicKey(pki.getPublicKey())` and execute the existing append/save/refresh sequence.
   - Keep access-list error handling unchanged and do not use the selected server's address for membership.
   - Update `TypeManagerPreferences.main(...)` and any other standalone constructors to pass an empty optional source as they do today.

8. Replace `NewClientWindowAction`'s prompt.
   - Remove `AskDialog`, `PromiseFutures`, `MysterAddress.createMysterAddress(...)`, `UnknownHostException`, and the invalid-address error path; a selected pool server already supplies a validated `MysterAddress`.
   - Resolve a `Window` owner from `KeyboardFocusManager.getCurrentKeyboardFocusManager().getActiveWindow()` or an equivalent helper that also tolerates a null owner.
   - Open the shared dialog using `context.knownServerSource()` with:
     - a concise title such as `Connect to a Server`;
     - action label `Connect`;
     - predicate `server -> server.getBestAddress().isPresent()`;
     - ineligible text explaining that the resolved server has no usable address.
   - On an empty result, return.
   - Re-read `selected.getBestAddress()` after the dialog closes. If it is absent, use `AnswerDialog.simpleAlert(...)` and return.
   - Build `ClientWindowData(Optional.of(address.toString()), Optional.empty(), Optional.empty())` and retain the current `getOrCreateWindow`, `show`, `toFrontAndUnminimize` behavior.
   - Keep the menu label in `MysterMenuBar` unchanged.

9. Compile and clean up references.
   - Search for `TypeEditorServerSource`, the old `com.myster.type.ui.ServerPickerDialog`, `PickedServer`, and all `MysterFrameContext` constructors.
   - Ensure the common picker Javadoc describes both connection and member consumers without importing access-list operations into its API.
   - Run formatting/import optimization only on touched Java files; preserve unrelated workspace changes.

## 10. Tests to write

- `TestServerAddressCandidate`:
  - accepts and normalizes dotted IPv4 and DNS-style hostnames with default or explicit valid ports;
  - treats hostname case and redundant default-port spelling as the same lookup key;
  - rejects bare search words, whitespace, URLs, paths, illegal/empty DNS labels, malformed IPv4,
    invalid ports, and IPv6 literals;
  - performs no DNS lookup while parsing syntax.
- `TestServerPickerDialog` or a package-private non-modal model/helper test:
  - source enumeration applies the caller eligibility predicate;
  - case-insensitive name filtering works;
  - address filtering works;
  - null server names use a fallback and do not throw;
  - empty/no-match data produces only the non-selectable placeholder;
  - a real row carries the original `MysterServer` result;
  - a valid candidate starts in visible waiting state, while ordinary search text causes no lookup;
  - every edit cancels the previously owned lookup before considering the new candidate;
  - changing text makes an earlier success/failure completion stale even if underlying work returns;
  - current success inserts/selects the resolved server and current failure leaves existing rows
    usable;
  - an ineligible resolved server shows the caller message and cannot be confirmed;
  - closing the dialog makes pending completion moot.
- `TestServerAddressLookup`:
  - stage order is `WAITING` -> `RESOLVING` -> `CONTACTING` -> successful server result;
  - cancelling during the delay cancels its timer and starts neither DNS nor stats;
  - cancelling during DNS cancels/moots DNS and never starts stats;
  - cancelling during stats cancels/moots the stats future and publishes no later result;
  - DNS and stats exceptions propagate distinctly enough for the dialog to select friendly failure
    text;
  - all stage futures are owned/cancelled despite `mapAsync` not cancelling its source.
- Extend `TestPromiseFuture`:
  - `PromiseFutures.delay(...)` completes once after the requested timer fires;
  - cancellation prevents delayed completion and reports cancellation to later listeners;
- Extend `TestMysterServerPoolImpl`:
  - explicit resolution completes with the same `MysterServer` inserted/refreshed in pool state;
  - returned stats with `/Identity` produce `PublicKeyIdentity` and the expected `ServerCid`;
  - stats without a public key remain address-identified and therefore member-ineligible;
  - two explicit requests for the same address share one ping/stats exchange;
  - an explicit request retries despite a passive dead-cache entry, while failure updates the cache;
  - failed ping, stats failure, and invalid identity complete exceptionally and clean up in-flight
    state.
- `TestNewClientWindowAction` if dialog creation is exposed through a small package-private factory seam without complicating production code:
  - cancellation does not call `ClientWindowProvider`;
  - selected address builds `ClientWindowData` with empty type and file values;
  - a server that loses its best address alerts and does not open a window.
- Do not add brittle tests that call `JDialog.setVisible(true)` in headless mode. Prefer testing extracted population/filter/result logic and manually smoke-test modality and event wiring.
- Run the focused tests plus the project's normal headless test command, at minimum `mvn -q -Djava.awt.headless=true test`, if the full suite is within the implementation turn's time budget.

Manual smoke checks:

- Open **File -> New Peer-to-Peer Connection...** and verify the server picker appears with **Connect**.
- Filter by part of a server name and by part of an address.
- Enter a previously unknown dotted IP and a DNS hostname, with and without a custom port. Verify
  the waiting indicator appears immediately, no DNS/network traffic begins for about one second,
  resolving/contacting states follow, and the resolved server becomes selected.
- Type ordinary words, a malformed hostname, and an invalid port; verify they only filter and do not
  trigger a lookup.
- Start one lookup and change the text before it finishes; verify the old result does not replace
- Trigger a failed lookup and verify a clear no-response/DNS message remains with no Retry control.
- Connect with the button, Enter, and double-click; verify each opens or focuses the correct client window.
- Cancel with the button, Escape, and window close; verify no client opens.
- Verify a down/untried addressable server remains selectable and a server without an address is absent.
- Open Type Manager -> Members -> **Add Member...** and verify the same picker appearance with **Add Selected**.
- Verify an address-only/no-public-key server is absent from the member picker and a public-key server can still be added with the correct displayed identity.
- Enter an unknown member address and verify server stats supply the public-key identity/CID before
  **Add Selected** becomes enabled. Verify a responding legacy/address-only server shows the
  no-public-identity message and cannot be added.
- Verify the no-server/no-match placeholder is non-selectable in both use cases.

## 11. Docs / Javadoc to update

- Add class and method Javadoc to `KnownServerSource` describing its UI boundary and caller-owned eligibility policy.
- Add Javadoc to `PromiseFutures.delay(Duration)` documenting loose minimum delay timing,
  cancellation, callback threading/invoker behavior, and its `Void` result.
- Document `ServerAddressCandidate` as syntactic recognition only, including accepted forms,
  normalization, rejected IPv6, and the guarantee that it performs no DNS/network work.
- Document `ServerAddressLookup` ownership and exact delay -> DNS -> stats sequence, stage callback
  threading, and the fact that cancellation renders results moot even when underlying work cannot
  stop immediately.
- Document the new `MysterServerPool` resolver's explicit-user semantics, future result, dead-cache
  retry, in-flight sharing, identity provenance, and failure behavior.
- Rewrite `ServerPickerDialog` Javadoc to describe the generic selected-`MysterServer` contract,
  cancellation result, caller predicate, document modality, debounced direct lookup, and stale-result
  handling.
- Update `TypeEditorPanel` and `TypeManagerPreferences` constructor Javadocs for `KnownServerSource`.
- Update `MysterFrameContext`'s class comment or record-component documentation to mention the known-server UI source.
- Update `NewClientWindowAction` class/method documentation if none exists while touching the class, particularly that it selects only from known addressable servers.
- After implementation, write `docs/impl_summary/peer-to-peer-server-picker.md`.
