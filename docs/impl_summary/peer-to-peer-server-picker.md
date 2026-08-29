# Peer-to-Peer Server Picker Implementation Summary

## What was implemented

The New Peer-to-Peer Connection action and custom-type Add Member workflow now use one shared,
searchable server picker. The picker accepts strict dotted IPv4 or DNS-style address input,
debounces both ordinary known-server filtering and direct address checks for approximately one
second, performs DNS and server-stats work off the EDT, cancels superseded work, and shows inline
waiting/resolving/contacting/responding/failure state.

The server pool now exposes explicit future-returning address resolution. It reuses the existing
TCP-ping and bidirectional-stats pipeline, returns the exact pool-owned `MysterServer`, shares
in-flight work by address, records failures in the dead cache, and permits an explicit user lookup
to retry a passively dead-cached address.

`PromiseFuture.mapAsyncInline(...)` names the inline/source-completion-thread behavior explicitly,
while `mapAsync(mapper, Invoker)` chooses where the mapper that creates the next promise runs. The
pool uses the scheduled form for its actor-confined stats update instead of adapting
`TrackerUtils.INVOKER` into an executor for `PromiseFutures.execute(...)`.

Pool resolution failure handling and in-flight cleanup use ordinary listeners on
`TrackerUtils.INVOKER`. Cleanup is intentionally eventual relative to future completion, so a
caller during that brief window may receive the same already-completed in-flight future.

The picker selects its first real row whenever initial or filtered results are rendered. A direct
address result is placed first as well, so it retains the same default-selection rule. The shared
MCList selection model now treats negative and past-the-end selection queries as unselected,
preventing Swing focus traversal from dereferencing row `-1`.

When the picker table owns focus, its focused Enter key binding now clicks the confirmation button
instead of allowing JTable's built-in Enter action to consume the key. This restores the behavior
of the historical AWT `MCListEventHandler`, which explicitly mapped Enter to its activation action.
While the search field owns focus, Up and Down are consumed as list-navigation actions. They move
the selected real row by one, scroll it into view without moving focus out of the field, and remain
clamped at the first and last rows.

## Files changed

- Added `KnownServerSource`, `ServerAddressCandidate`, `ServerAddressLookup`,
  `ServerPickerModel`, and the generalized `ServerPickerDialog` under
  `com.myster.tracker.ui`.
- Added cancellable `PromiseFutures.delay(Duration)` and made `MysterAddress.DEFAULT_PORT` public
  for canonical address rendering.
- Added and documented `PromiseFuture.mapAsyncInline(mapper)` and
  `mapAsync(mapper, invoker)`, including their preferred usage pattern in
  `docs/conventions/myster-important-patterns.md`.
- Added `MysterServerPool.resolveServer(...)`; refactored `MysterServerPoolImpl` to return resolved
  servers and retain in-flight futures; made `MysterServerImplementation` return a stable interface
  reference.
- Added the shared source to `MysterFrameContext`, created its pool adapter in `Myster`, and updated
  the standalone progress-window context.
- Migrated `NewClientWindowAction`, `TypeEditorPanel`, and `TypeManagerPreferences` to the shared
  picker/source.
- Removed the type-editor-specific `ServerPickerDialog` and `TypeEditorServerSource`.
- Added or extended tests for delay behavior, parsing, lookup stages/cancellation, picker modeling,
  explicit pool resolution, and MCList's empty-selection contract; updated the 3DNS fake pool for
  the new interface method.

## Key implementation decisions

- Address recognition is pure syntax: dotted IPv4 or a valid multi-label DNS name, with an optional
  port from 1 through 65535. Bare words, URLs, paths, malformed input, and IPv6 literals remain
  ordinary search input and never initiate network work.
- Caller predicates retain policy ownership: Connect requires a best address; Add Member requires
  `PublicKeyIdentity` and derives `ServerCid` only after selection.
- Immutable row snapshots are filtered on a worker thread. Swing mutation and lookup-stage display
  are dispatched to the EDT and guarded by both cancellation and an identity token.
- A directly resolved hostname is forcibly retained in the displayed result set even when the
  server reports a numeric address or friendly name that does not contain the typed hostname.
- The pool returns a stable `MysterServer` facade so the future result is the same object visible
  through subsequent cache lookup.
- A mapper invoker schedules only the `mapAsync` function that creates the next promise. It does
  not leak into ordinary-listener dispatch on any promise in the chain.
- The in-flight map may briefly retain an already-completed future until its ordinary finally
  listener runs. Exact-value removal prevents that cleanup from deleting a newer entry.

## Deviations from the plan

- The final owner decision delayed ordinary known-server filtering and moved it off the EDT because
  the pool may become very large. The written plan still says ordinary filtering is immediate.
- `PromiseFuture.mapAsyncInline(...)` was fixed before this implementation to cancel both its source
  and active mapped stage. `ServerAddressLookup` therefore composes the delay, DNS, and stats
  futures directly instead of adding the plan's workaround owner that manually tracks every mapped
  stage.
- Headless picker coverage uses the extracted `ServerPickerModel` rather than constructing a
  `JDialog`. A `NewClientWindowAction` factory seam was not introduced solely for tests because it
  would add production indirection without improving the underlying lookup/model coverage.

## Javadoc and design review

Javadoc was added or updated for the shared source, address value/parser, lookup state machine,
picker, delay primitive, explicit pool API, and affected editor/context contracts.

The design documents under `docs/design/` and `docs/codebase-structure.md` were reviewed. They
describe server-stats wire behavior, identity, and the pool only at a level unchanged by this local
UI/API refactoring, so no design-document update was necessary.

## Verification

- Focused suite: 43 tests passed before the final picker-model regression was added.
- Picker/MCList follow-up regressions: 9 tests passed.
- PromiseFuture/pool/address-lookup focused verification: 36 tests passed.
- Final full suite: `mvn -Djava.awt.headless=true test` exited successfully with 594 tests,
  0 failures/errors/skips. One existing negative-path test intentionally prints its caught
  `IllegalStateException` stack trace.
- `mvn -DskipTests compile` and `mvn -DskipTests test-compile` both passed.
- `git diff --check` passed.

## Follow-up and manual checks

No known automated-test failure remains. The plan's manual Swing/network smoke checks still need a
running GUI and reachable Myster peers: verify modality, button/Enter/double-click behavior,
Escape/window-close cancellation, live stage text, and real public-key member derivation. IPv6
literal entry remains intentionally out of scope.
