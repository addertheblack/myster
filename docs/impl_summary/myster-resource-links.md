# Myster resource links

Implemented the [revised plan](../plans/myster-resource-links.md). As requested on 2026-10-19,
this change supports server/type/name navigation only. All file-hash support has been removed.

## Delivered behavior

- `myster://link/v1` accepts exactly three field sets: server; server/type; server/type/name.
  Unknown/empty/duplicate fields, invalid hierarchy/encoding/CIDs, unsupported authorities or
  versions, fragments/credentials/ports, and links over 16 KiB are rejected. Hash fields are
  unsupported even when a name is present.
- Remote preparation resolves the exact verified 3DNS server and passes its key/address and
  optional type/name to ClientWindow. It performs no file-statistics preflight or hash lookup.
- Own-server links ask Yes/No/Cancel before lookup/navigation. Yes uses the local identity,
  loopback, and current port. Named local files must exist in the saved shared index and on disk.
- ClientWindow follows its existing initialData sequence: type-list completion selects the type; the
  ordinary selection event starts file listing; listing completion reveals/selects/scrolls
  to the exact file. Reused windows can navigate through already loaded types and files.
- A file request arriving while the selected type's listing is running stays pending until
  completion. Missing files/types and listing failures clear it. Incompatible type/file
  selection, manual Refresh, or disposal cancels it. A newer destination supersedes it.
- ClientWindowData requires a MysterAddress and PublicKeyIdentity; only type and filename
  are optional. ClientWindow stores its identity in a final constructor field. All browsing
  workers, thumbnails, copying, and type import receive the same expected key. There are no
  identity upgrade callbacks, unpinned windows, navigation generation counters, or extra flags.
- ClientWindowProvider uses only public-key identities for window reuse. A different endpoint
  can refresh the same identity's window; this cancels old workers and clears address-bound
  rows/caches. A different identity gets another window, even at the same address. Cleanup
  removes the exact identity/window pair, protecting replacements from late close events.
- Address-only opening checks the key cache, then uses the existing asynchronous server-stats
  lookup with a cancellable modeless “Contacting server…” dialog. A public-key identity is
  required before creating/registering a ClientWindow or loading types. Unreachable servers
  and results without a key show an error dialog; cancellation suppresses late window creation
  without cancelling a pool refresh shared with another caller. Known keys are passed through
  from search, tracker, picker, 3DNS, and resource-link callers.
- The address display is read-only and Connect is now Refresh. Refresh retains the immutable
  identity even when no initialData remains. It clears pending automatic navigation.
- Preferences persist the full public key, last endpoint, and selected type. Restoration
  keeps that identity authoritative; old records without a key use provider discovery, while
  corrupt saved keys are skipped. Boundary address text resolves off the EDT after preferences
  have been snapshotted outside the keeper's monitor. Startup counts successful restorations,
  treating failed/cancelled lookups as zero, before choosing a default window.
- Each of ClientWindow, SearchTab, and File Manager offers one Copy File Link command producing
  server/type/name. Remote copying obtains the authenticated peer CID through the protocol
  layer without fetching file metadata; local copying uses the saved shared index. Clipboard
  writes require a current live selection. File Manager placeholder/indexing/pending-edit/
  multiple-selection/count guards remain intact.
- Shared native/CLI routing preserves existing invitations and launch bounds. Resource
  preparation keeps one active plus eight distinct waiting links and coalesces duplicates.
  The queue advances after delivery to initialData, with navigation status handled by the
  window. No resource link starts a download, chooser, crawler, progress entry, or output file.

## Files and documentation

Added URI/service/router/dispatcher/copy classes under `com.myster.link`. Updated ClientWindow,
its provider, listing/statistics/metadata workers, thumbnail requests, application/context
wiring, SearchTab, and FmiChooser. Removed the obsolete ClientLinkSelectionUtils hash-selection
helper and its test. Class-named tests cover each component; combined browsing tests live with
the corresponding lister, metadata cache, and thumbnail class.

Updated the authoritative plan, [design](../design/Myster%20Resource%20Links.md), invitation
routing cross-reference, conventions, and plan index. The former hash-bearing architecture
is superseded by this narrower scope. Unrelated IDE, skill, and TODO edits were preserved.

## Validation

Before the promise-composition cleanup, the expanded immutable-identity change exercised
**874 tests** with isolated preferences and
user state. **869 passed** in the restricted sandbox; the five application/datagram socket
tests could not bind there and **all five passed** when rerun with local socket access.
There are no remaining test failures or skipped tests.

The full run used:

```sh
mvn -o -Dmaven.repo.local=/home/andrew/.m2/repository \
  -Djava.awt.headless=true \
  -Duser.home=/tmp/myster-identity-test-home \
  -Djava.util.prefs.userRoot=/tmp/myster-identity-test-prefs test
```

The rerun used the same isolation options with
`-Dtest=TestApplicationContext,TestAsyncDatagramSocket` and local socket permission. Actual runs
used fresh temporary directories for each invocation.

`TestClientWindow` contains **15 tests** exercising actual setInitialData, selection listeners,
type/file workers, and real Swing list/tree models, plus preference restoration. They cover
new/reused navigation, parent expansion, delayed listings, cached-file and type-only reuse,
empty types, missing files, supersession, manual Refresh, immutable identity, endpoint changes,
and persisted identity restoration including legacy and corrupt records. Native JFrame
construction and unrelated statistics/display actions are bypassed.

The **12 provider tests** cover cache hits, supplied identities, missing-key discovery,
unreachable/keyless servers, retries, cancellation propagation into the API request, identity
reuse, and replacement-window cleanup. Parser/service/dispatcher/copy, invitation, listing, metadata,
thumbnail, pool, picker, and launch regressions passed with the suite.

The provider now composes discovery with `mapAsync(..., Invoker.EDT)` and completed/failed
promise factories, forwarding cancellation normally into the API request. The server-pool API
returns a separate cancellable future to each caller over its pool-owned shared refresh; clients
need no knowledge of that sharing. Pool tests cover independently cancellable callers, reuse of
one in-flight exchange after cancellation, and learning server stats even when all requests
are cancelled. All **70 focused tests** in TestMysterServerPoolImpl, TestClientWindowProvider,
TestClientWindow, TestServerAddressLookup, and TestPromiseFuture passed after this change,
including API cancellation, cancellation before EDT dispatch, and window-construction failure.

Earlier focused API Javadoc for all five new resource classes passed with `-Xdoclint:all,-missing`.
`git diff --check` passed for the current change.

## Remaining validation limits

Native desktop/OS cold-start delivery, actual clipboard behavior, and visual highlighting/
scrolling between two running GUI instances still need manual smoke checks on a working
display. Automated tests do not establish native window behavior. The prior repository-wide
Javadoc run found preexisting errors in unrelated files; this revision checked the new
resource APIs directly. Hash support is deliberately deferred to a later change.

## Required link service

MysterFrameContext requires every service to be non-null, including MysterLinkService; the
optional component and eight-argument fallback constructor are removed. ClientWindow and SearchTab install Copy
File Link directly. The standalone fake-data progress preview now runs from
`src/test/java/com/myster/progress/ui/ProgressManagerWindowPreview.java`, where it supplies
mocked services without making application dependencies optional.

The identity follow-up also checks all context services for null at construction and supplies
mocks for the standalone preview's unused dependencies. Validation is included in the full
suite results above.
