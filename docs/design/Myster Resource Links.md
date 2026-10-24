# Myster resource links

Resource links navigate in ClientWindow. They never start downloads. The current scope,
revised on 2026-10-19, supports server, type, and exact filename destinations only.
File-hash identification and validation are deferred entirely.

## URL contract

```text
myster://link/v1?server=<server CID>
myster://link/v1?server=<server CID>&type=<type CID>
myster://link/v1?server=<server CID>&type=<type CID>&name=<encoded reference>
```

Server is mandatory; name requires type. CIDs are exactly 32 lowercase hexadecimal characters
in their respective server/type domains. File names are opaque network references, percent-encoded
as UTF-8. Preserve plus signs, Unicode, punctuation, slash characters, and literal percent signs
without form decoding, case folding, or path normalization.

`MysterLinkUri` validates creation and parsing identically and emits canonical field order.
It rejects unknown/duplicate/empty fields, malformed UTF-8/percent escapes, invalid parent
combinations, fragments, credentials, ports, unsupported actions/versions, and URLs longer
than 16 KiB. `hash` and `hash_type` are unsupported fields; they are rejected even alongside
a valid name. The draft `file/v1` action is not an alias.

## Resolution and routing

Native desktop and single-instance/CLI delivery enter `MysterUriRouter`. Invitation links
continue through `TypeJoinUriDispatcher` and their existing parser/manual entry. Resource
links enter `MysterLinkDispatcher`; malformed input uses one visible error plus one pending
aggregate alert, including modal reentry.

The resource dispatcher coalesces active/waiting duplicates and bounds preparation to one
active request plus eight waiting distinct requests. A later deliberate reopen is allowed.
Each preparation captures its originating window; disposal makes its result moot. The queue
advances after handing the destination to ClientWindow, rather than waiting for a separate
window navigation completion callback.

`MysterLinkService` resolves remote CIDs using exact verified 3DNS results, checks the returned
CID, and carries the verified identity/address and optional type/name to the UI. It does not
fetch named-file statistics or search by file hash. File existence is determined through the
normal ClientWindow listing and selection path.

For an own-server CID, the dispatcher asks Yes/No/Cancel before lookup or window work. Only
Yes proceeds. The local path uses the local public key, loopback, and the current configured
port. For a named local file, the saved type must be shared, its exact indexed reference must
exist, and its current filesystem entry must be a regular file. Pending File Manager edits
are never applied implicitly.

## Existing ClientWindow navigation

`ClientWindowData` requires a `MysterAddress` and `PublicKeyIdentity`; only the type and
filename are optional. Each ClientWindow stores that identity in a final constructor field.
Its address display is read-only, and Refresh reloads the same server using that identity.
Window preferences save the full encoded public key, the last endpoint, and selected type.

`initialData` is the pending destination, used by new and recycled windows alike:

1. `setInitialData()` connects when necessary, or uses existing type/file rows.
2. Type-list completion selects the requested type after all type rows have arrived.
3. The ordinary type selection listener starts file listing.
4. File-list completion calls `selectElement()` to find the exact non-folder reference,
   expand its parent folders, select it, and scroll to it.
5. Fulfillment or terminal failure clears `initialData`. An incompatible user selection,
   manual Refresh, or disposal cancels it. Compatible intermediate events preserve it.

If the requested type is already selected but its file listing is still running, a filename
not yet present leaves initialData pending until completion. With a completed listing, a
missing file clears the request and reports a status message. A type-only destination also
completes when selecting an existing type in a reused window.

No per-window hash, navigation completion callback, identity callback, generation counter,
or collection of navigation flags is added. Existing worker cancellation suppresses queued
callbacks after incompatible reconnect/type changes. The file-lister handle is cleared when
its work completes; it is not a second navigation state machine.

## Authentication and window reuse

Authentication belongs to the stream/protocol layer. The UI supplies the window's immutable
public key to type/file/statistics/metadata/thumbnail requests and type import. Workers capture
that expectation; UDP listing and TCP fallback both retain it. Navigation never adds or replaces
a window's identity, and no worker owns the authoritative identity.

`setInitialData()` rejects a different public-key identity before changing any state. A new
endpoint for the same identity updates the read-only display, cancels old listing workers,
clears address-bound rows and thumbnail caches, and starts a fresh pinned listing. A different
identity gets a different window even when both use the same address. Refresh cancels pending
automatic navigation and reloads the current endpoint with the same identity.

`ClientWindowProvider.getOrCreateWindow()` creates or reuses a window only from identity-bearing
ClientWindowData. Its registry is `Map<PublicKeyIdentity, ClientWindow>`; there are no address
fallback entries or late identity merges. Cleanup removes the exact identity/window pair, so
a late close notification cannot remove a replacement window.

### Address-only opening

`ClientWindowProvider.openWindow()` is the outer opening path for callers whose key may be
missing. Callers pass existing public-key identities from search results, tracker rows, and
the server picker. 3DNS rows and resource links already supply their identity directly.
A supplied identity is authoritative and cannot be replaced by an address-cache entry.

Without a supplied key, the provider checks its non-blocking identity cache. A cache miss
starts the existing asynchronous server-stats lookup and shows a temporary, cancellable
“Contacting server…” dialog. This dialog is an ordinary modeless JDialog, outside the
MysterFrame/window-preferences/provider registries. A successful lookup must produce a
public-key identity before a ClientWindow is constructed or types are requested. Failure,
including stats without a public key, dismisses progress and shows an error dialog without
creating a provisional window. Cancellation suppresses late window creation but leaves the
pool-owned lookup available to other callers.

The provider composes `KnownServerSource.resolveServer()` directly with
`mapAsync(..., Invoker.EDT)` for identity validation and window creation. Cancellation propagates
normally into that API request. The server-pool API gives each caller an independently
cancellable future and retains ownership of the shared refresh, so cancelling one caller does
not affect others. The mapper returns completed or failed promises; progress cleanup and error
reporting use ordinary finally/exception listeners.

### Saved windows

Preferences persist the full public key with the address as the last known connection
endpoint. Restoration passes that saved identity through without rediscovering a potentially
different server at the old address; subsequent browsing authenticates against the saved key.
Legacy records without any identity use the same address-only discovery flow and acquire a
persisted key when their window is created. Corrupt stored keys are skipped, never treated
as permission to rediscover a replacement identity.

Boundary address text is resolved off the EDT. Snapshot saved preferences before resolution
because the window keeper removes their nodes and holds its monitor while reading them.
Restore windows on the EDT and count successful openings asynchronously for startup's
default-window decision; failed or cancelled legacy lookups count as zero. Records with
missing or malformed addresses are skipped.

## Copy File Link

Every MysterFrameContext service is required and checked for null at construction; link actions
are always installed.
ClientWindow, SearchTab, and File Manager each expose one explicit Copy File Link command
for exactly one real file. It produces server/type/name. Selecting a row alone does not
resolve or copy anything.

Remote creation opens an authenticated connection, pins it when a key is known, and derives
the server CID from the protocol layer's authenticated peer key. No file metadata is needed.
Local creation uses the local CID and saved shared indexed reference (`FileItem.getName()`).
File Manager excludes missing/indexing/placeholder/count rows, pending sharing/folder changes,
and multiple selections. Its existing 350-file display limit remains in force.

Copying snapshots its selection and owner; only the latest still-current action can update
the clipboard. Failure, disposal, or changed selection leaves existing clipboard contents
untouched. Clipboard writes and UI callbacks run on the EDT; network/filesystem preparation
runs off it.

Normal manual Download actions are unchanged. No link activation chooses a destination,
creates a download entry or output file, or invokes the hash crawler.
