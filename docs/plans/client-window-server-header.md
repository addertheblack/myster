# Client Window Server Header

Implemented; see the [implementation summary](../impl_summary/client-window-server-header.md)
for verification and remaining desktop smoke checks.

> **Related plan:** [Myster resource links](./myster-resource-links.md) — predecessor: establishes the window's fixed public-key identity, mutable endpoint, and read-only address display.

> **Related plan:** [Domain-Specific CID Types](./cid-domain-types.md) — depends on: uses the existing ServerCid representation for the displayed server CID.

> **Related plan:** [OS thumbnails — Part 3: client preview pane](./os-thumbnails-part-3.md) — parallel work: shares the client header and preserves the existing rightmost preview/statistics toggle.

## Design Section (for the owner/reviewer)

### 1. Summary

Extend the direct-connection/client window's existing top row to show the server name,
address with port, and server CID, followed by Refresh and the existing preview/statistics
toggle. The three values look like labels but support text selection and copying. The CID
has the lowest space priority. This is a small presentation change to the existing browser.

### 2. Non-goals

- Chat, tabs, or changes to the type/file browser and preview pane.
- Editing the destination, new connection controls, or new copy buttons/menus.
- Network requests solely for this header, live server-name subscriptions, or polling.
- Changes to identity, connection authentication, window reuse, or saved preferences.
- Address-model/parser changes or a general redesign of the window title.

### 3. Assumptions & open questions

- Owner-specified layout: server name with hostname/IP and port directly underneath,
  then CID, Refresh, and the existing toggle alongside the stacked text.
- Owner-confirmed: the identifier is the server **CID**.
- Planning choice: show the full existing 32-character lowercase hexadecimal ServerCid,
  derived from the constructor-bound public key.
- Planning choice: always include the port, including the default 6669. Use the current
  resolved IP because MysterAddress does not preserve the originally entered hostname as a
  reliable presentation value. Do not perform reverse DNS for this feature.
- Planning choice: use the existing cached name; retain a previously known name for this
  identity on a cache miss, otherwise display “Unknown server”. Refresh/navigation rechecks the cache. No
  guarantee of immediately following a remote rename while the window sits idle.
- No blocking questions remain. Exact spacing and compact name/address widths are left to
  implementation and desktop verification, subject to the priority rules below.

### 4. Proposed design

Keep one header above the browser. The server name is the leading, visually primary
value, with the address and port directly underneath in the same column. The name and CID
caption/value align with the top edge of the Refresh button. A small “CID” caption identifies the
hexadecimal value. Refresh and the current ≡ button remain at the right in that order.

Use enabled, focusable, noneditable Swing text components with no visible field border or
filled text-box background. Reuse the existing MessagePanel label-like text styling locally,
with wrapping disabled for these single-line values. Preserve normal mouse selection,
keyboard selection, and Copy. Give each value an accessible name and a tooltip containing
the full value. The CID caption is outside the selectable CID text so copying yields the
hexadecimal ID itself.

At ordinary widths, reserve useful compact space for the name and address and let the CID
use the remaining space, up to its full width. As the window narrows, reduce the CID's
visible width first, down to zero if necessary, including its caption/gap when collapsed.
Only after that may name/address text compress. Buttons retain their natural sizes. Very
long names must not force the CID or controls offscreen or expand the default window.
Keep full underlying text when clipping; selection and Copy must still retrieve the full
value. A larger window reveals more of the values without wrapping any individual value
or adding a horizontal scrollbar for the entire header.

Populate address and CID immediately from the window's existing state. Read the name from
the existing quick tracker cache only when that entry's identity matches the window's
fixed identity. Update the header during construction, navigation, and the existing
refresh callbacks, including type-list completion. A failed refresh retains the identity
and current endpoint; an already known name for that identity can remain visible.

### 5. Architecture connections

The window already owns the two authoritative inputs: its immutable PublicKeyIdentity and
current MysterAddress. ServerCid derives locally from that public key. The tracker supplies
optional display metadata, never the identity used for the CID. Existing listing callbacks
already reach the EDT, so the header needs no new background task or listener lifecycle.
The preview toggle continues controlling the same split pane.

| New / changed thing | Owned / created by | Called / used by | Connects to (existing) |
|---|---|---|---|
| Copyable header values and layout | Each ClientWindow | User selection/copy; window layout | MessagePanel, Swing, GridBagBuilder |
| Displayed server CID | Derived by ClientWindow from its fixed identity | Header | PublicKeyIdentity.getPublicKey(), ServerCid.fromPublicKey().asHex() |
| Optional server name | Tracker/server pool owns cached metadata; window renders it | Header and existing title update | Tracker.getQuickServerStats(), MysterServer.getIdentity()/getServerName() |
| Address with explicit port | ClientWindow's current endpoint | Header | MysterAddress.getIP()/getPort(), setInitialData() |
| Header updates | ClientWindow | init(), setInitialData(), refreshIP(), refresh/list completion | Existing EDT callbacks and cancellation behavior |
| Refresh and preview toggle | ClientWindow | Existing button actions | Current type/file refresh and preview split pane |

There are no new wire contracts, disk formats, preferences, or public cross-module APIs.

### 6. Key decisions & edge cases

- The CID identifies the server, not its current address. Reusing the window at a new
  endpoint changes the displayed address immediately while retaining the same CID.
- PublicKeyIdentity.toString() is an encoded full key, and MysterServer.getExternalName()
  is a legacy identifier. Neither is the displayed ServerCid.
- An address-cache entry for another identity must never supply the name. Preserve the
  last known name for the window's identity, or use the fallback if none was known.
- A missing/blank name must not render as the literal string “null” or leave the first
  value mysteriously empty. Normalize line breaks/tabs in a name to spaces for a single row.
- Cache reads and all component updates occur on the EDT; no DNS or network work is added.
- Keep the existing title wording and loopback “myself” fallback. It may reuse the same
  identity-checked name so it does not contradict the header or show a null name.
- Header values are display data. Refresh continues using the stored MysterAddress and
  fixed public key, never reparsing selectable text.

### 7. Acceptance criteria

- [x] Server name and explicit IP/host:port form a left-aligned column, with the address
      below the name; CID, Refresh, and ≡ follow alongside it.
- [x] The server CID matches the existing ServerCid of the window's public key and is
      available immediately, including when the remote endpoint is offline.
- [x] All three values look like labels, with no visible text-field border/background,
      and allow mouse/keyboard selection and copying of the full underlying value.
- [x] The default 640-pixel window remains usable; resizing gives up CID space before
      name/address space, with both buttons visible and usable at supported narrow widths.
- [x] Long names and full CIDs do not cause wrapping, overlapping controls, or window growth.
- [x] Unknown/blank names use a clear fallback; a mismatched cache identity cannot supply a name.
- [x] Navigation to another endpoint of the same server updates address/port while retaining CID.
- [x] Refresh and the preview/statistics toggle keep their existing behavior.
- [x] Chat, tabs, browser contents, protocol behavior, and saved-window formats are unchanged.

Verification used the real header in an offscreen Swing harness, selection/clipboard transfer,
light/dark rendering, and the existing refresh/navigation tests. Native mouse/keyboard gestures,
real monitor changes, and a live remote-server preview smoke check remain manual follow-ups.

---
## ✦ IMPLEMENTATION DETAILS (for the implementation agent)
---

### 8. Affected files / classes

- `src/main/java/com/myster/client/ui/ClientWindow.java` — build the header, derive the CID,
  format the endpoint locally, and update cached name presentation through existing events.
- `src/test/java/com/myster/client/ui/TestClientWindow.java` — adapt existing injected header
  fields if their types/setup change; retain the existing real refresh/navigation coverage.
- `docs/conventions/myster-important-patterns.md` — record current header integration traps
  near the resource-link/window identity constraints (recorded during planning).
- `docs/impl_summary/client-window-server-header.md` — new implementation summary after delivery.

Reuse `com.general.util.MessagePanel`, `com.general.util.GridBagBuilder`, and
`com.myster.cid.ServerCid` without changing their shared behavior. Prefer private window-local
helpers; a new shared UI framework or tracker API is unnecessary.

### 9. Step-by-step implementation

1. In ClientWindow.init(), replace the bare serverAddressField construction with three
   label-like text components created through a private helper based on MessagePanel.createNew().
   Set one row, lineWrap=false, wrapStyleWord=false, natural font/theme colors, accessible
   names, and full-value tooltips. Keep the components enabled/focusable and preserve Copy.
   Do not alter MessagePanel globally, since other callers rely on multiline wrapping.
2. Derive the CID with ServerCid.fromPublicKey(serverIdentity.getPublicKey()).asHex() during
   header initialization. Store the full text in the text component; never replace it with
   an ellipsis or a substring. No additional mutable identity state is needed.
3. Format the endpoint locally using getIP() and getPort(), explicitly including the port.
   If the resolved address contains colons, bracket the IP for display before appending the
   port. Do not change MysterAddress.toString(), parsing, or preference serialization.
4. Replace the current top-row additions with a small header panel spanning the existing
   three outer grid columns, leaving splitPane/msg spans alone. Use GridBagBuilder for
   resizable grid layout. Bound content-derived preferred sizes, keep control widths
   protected, and explicitly implement the priority described in section 4. Simple equal
   weights are insufficient to guarantee CID-first compression. Hide the CID caption/gap
   when its text collapses. Use logical Swing/font measurements, not manual display scaling.
   Choose the smallest local layout solution that satisfies those rules.
   Place name/address in a nested two-row GridBagLayout column whose reserved width is
   the larger of the bounded name width and address width. Give CID only the remaining space.
5. Add a private name/header update path. Query tracker.getQuickServerStats(serverAddress),
   check serverIdentity.equals(server.getIdentity()), and only then consume getServerName().
   Normalize a valid name for the single row. For a missing/mismatched/blank result, retain
   an already valid displayed name for this fixed identity, otherwise show “Unknown server”.
   Keep the component's text as the presentation state rather than adding flags or requests.
6. Invoke the update path once the header exists in init(), in setInitialData() after updating
   serverAddress, at refresh start, from refreshIP(), and from TypeListener.finished().
   Reuse the checked name when assembling the existing title in refreshIP(), preserving its
   prefix/address/loopback behavior. Do not make the name dependent on preview visibility.
7. Keep all existing refresh actions, initialData handling, expected-key snapshots, window
   reuse, worker cancellation, and preview toggle listeners intact. Existing cancelled-worker
   callback suppression remains sufficient; add no generation counter or subscription.
8. Update TestClientWindow's reflected field setup to create the actual text component types
   used by production. Preserve refreshUsesTheCurrentEndpointRatherThanTheDisplayField and
   the existing endpoint-change/identity tests. Run the focused class, then perform the manual
   checks below. Write the implementation summary with actual validation and any limitations.

### 10. Tests to write / verification

No new automated test suite is required for this small presentation change. Update existing
fixtures as needed and run `mvn -Dtest=TestClientWindow test` with the repository's Java 26
toolchain. Run Maven and IntelliJ builds sequentially because they share target output.

Manual desktop checks are the main acceptance evidence:

1. Open a named server at the default window size. Check order, CID correctness against its
   resource link/tracker entry, and explicit default and nondefault port presentation.
2. Select and copy each value with mouse and keyboard, including Select All/Copy for a clipped
   CID and long name. Confirm CID copying yields only the complete hexadecimal value.
3. Resize wide, default, and narrow. Verify CID compresses first, its caption does not remain
   stranded, name/address stay useful, both buttons remain visible, and nothing wraps/overlaps.
4. Check an absent/blank name, a long name, and an offline known identity. For cache identity
   mismatch, use existing Mockito setup if reproducing manually is impractical.
5. Reopen the same identity at another port/endpoint; check the updated endpoint and unchanged
   CID. Refresh and toggle the preview, checking ordinary type/file browsing still works.
6. Check light/dark themes and fractional/2x display scaling where available. Text must remain
   selectable, readable, aligned, and free of input-field chrome.

### 11. Docs / Javadoc to update

- Document the private header update helper's identity check and cache-only name behavior.
- Add a short comment explaining CID-first width allocation where it is implemented.
- Keep ClientWindow class/navigation documentation accurate about fixed identity and endpoint.
- Keep this plan current and write `docs/impl_summary/client-window-server-header.md` on
  implementation. No chat/tab design document is part of this change.
