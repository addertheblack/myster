# Private Type Join Links — Implementation Summary

## Outcome

Implemented the version 1 synchronous invitation flow described by
`docs/plans/private-type-join-links.md`. An administrator can create a password-protected,
time-limited invitation from an administered type's Members tab. A recipient can paste or open the
password-free `myster:` link, preview the signed type metadata through exact 3DNS and pinned TLS,
redeem over authenticated TCP stream section 126, and import only after a fresh signed chain proves
the local identity is a member.

An open-invitation list/revocation UI remains future work, as do unsolicited join requests and an
administrator approval inbox.

## Files changed

- Added the invitation model, persistence, KDF, limiter, membership service, server protocol,
  coordinator, and URI dispatcher under `src/main/java/com/myster/type/join/`; the package-private
  section 126 client codec lives with `MysterStreamImpl` under `com.myster.net.stream.client`.
- Added `CreateTypeInvitationDialog` and `JoinPrivateTypeDialog`, and integrated their actions into
  `TypeEditorPanel`, `TypeManagerPreferences`, and application startup in `Myster`.
- Reduced `TypeManagerPreferences` and `TypeEditorPanel` to one complete constructor each. Their UI
  services and local identity are mandatory, application startup fails fast if identity creation
  fails, and Members-tab mutations can no longer bypass `TypeMembershipService` through an
  optional-service fallback.
- Extended `ParamBuilder` and `MysterStream` so expected-key connection setup and reusable section
  125/126 calls remain inside the protocol facade, along with the type-import APIs needed by the
  trust sequence.
- Added `DnsLookupProtocol` to the immutable `MysterProtocol` aggregate, implemented it with
  `ThreeDnsLookup`, and narrowed `MysterServerPoolImpl` to its actual stream/datagram dependencies.
  This keeps DNS discoverable from the protocol stack without an initialization setter or a
  feature-layer dependency on the concrete resolver.
- Added native jpackage overrides under `src/main/jpackage/{linux,macos,windows}` and the associated
  Maven profile configuration.
- Added focused tests under `src/test/java/com/myster/type/join/` and extended the existing stream
  and type-import test classes, including `TestMysterStreamImpl` coverage for common connection
  parameter propagation.
- Updated the private-type plan, 3DNS and milestone design material, coding/pattern conventions, and
  added `docs/design/Private Type Invitations.md`.

## Implementation

- Added the strict, bounded `TypeJoinUri` value contract. Type, bootstrap, and invitation values are
  16 bytes represented as 32 lowercase hexadecimal digits. A parsed external `code` is accepted but
  is never emitted by generation or diagnostic string conversion.
- Added Preferences-backed, schema-versioned invitation records beneath
  `MysterTypes/Invitations`, including lazy expiry/malformed cleanup, future-version retention,
  durable create/claim/complete flushes, and a 64-unexpired-record per-type bound.
- Added PBKDF2-HMAC-SHA256 verification with 16-byte salts, 32-byte verifiers, a production cost of
  210,000 iterations, constant-time comparison, bounded concurrent KDF work, and bounded per
  invitation/caller/address retry backoff.
- Added `TypeMembershipService` as the shared per-type serialized add/remove path for invitation
  redemption and the Members tab. Mutation works on a validated copy and publishes only after the
  access-list save succeeds.
- Added crash-idempotent claim-before-append redemption. Only the claiming TLS identity can resume;
  same-identity retries do not append twice, and a completed invitation cannot re-add a manually
  removed member.
- Added TCP/TLS stream section 126 with bounded 4 KiB MessagePak request/response frames and
  forward-compatible `TypeJoinStatus` values. Requester identity comes only from the TLS
  `ConnectionContext`.
- Added caller-owned section 125 fetching, stricter declared-size/block bounds, and expected-key
  stream creation through `ParamBuilder` and `MysterStream`.
- Added section 126 to `MysterStream`; its blocking codec remains an implementation detail rather
  than a feature-layer RPC entry point.
- Added a cancellable coordinator that uses the protocol stack's `DnsLookupProtocol`, accepts only
  exact verified 3DNS results, asks the stream
  facade for TLS authenticated to the verified public key, closes its active socket on cancellation,
  validates the full chain, proves local membership, and then imports with the remembered enabled
  choice.
- Extended type import to support initially disabled imports and safe same/extended-chain refreshes
  without changing the legacy duplicate-rejecting overload.
- Added a distinct type-metadata update event so refreshed descriptions update existing Type Manager
  rows without sending duplicate enable notifications to tracker/file-list consumers. Refresh chain
  comparison reads an independent persisted snapshot, so a mutable cached `AccessList` cannot hide
  an extension through object aliasing.
- Added modal create/join dialogs, toolbar and Members-tab actions, explicit EDT promise callbacks,
  password clearing, close/cancel handling, and a bounded/deduplicated native URI dispatcher.
- Retained initial and single-instance relaunch argument arrays until GUI readiness. Complete
  `myster:` arguments and macOS `APP_OPEN_URI` events feed the same confirmation path; ordinary
  relaunch still brings a window forward.

## Native packaging

- macOS: added an OpenJDK 26 GA `Info.plist` override with `CFBundleURLTypes` for `myster`.
- Windows: added an OpenJDK 26 GA `main.wxs` override with a stable installer-owned protocol
  component, install-scope-aware Classes root, quoted launcher/`%1` command, and uninstall ownership.
- Linux: enabled `linuxShortcut`, configured the OS resource directory, and added a desktop entry
  with `%u`, `Categories=Network;`, and `MimeType=x-scheme-handler/myster;`.
- Audited the effective Maven plugin configuration independently for the Windows, macOS, and Linux
  profiles; each selected the intended package type and only its own resource directory.

The Linux DEB was built successfully on this host with JDK 26.0.2.1 and inspected. It contains
`/opt/myster/lib/myster-Myster.desktop`; the substituted command is
`Exec=/opt/myster/bin/Myster %u`, and its package scripts install/uninstall the desktop entry through
`xdg-desktop-menu`.

## Verification

- Full suite: `mvn -Djava.awt.headless=true test` on JDK 26.0.2.1
  - 633 tests, 0 failures, 0 errors, 0 skipped.
- Linux package: `mvn -DskipTests package` on JDK 26.0.2.1
  - successful DEB creation and desktop/package-script inspection.
- Focused coverage includes URI parsing/redaction, password verification, Preferences persistence,
  limiter behavior, membership and idempotent redemption, section 126 caller identity and codec,
  common stream-connection parameter propagation, exact-3DNS/expected-key proof, same-socket section
  reuse, disabled imports and disabled metadata refresh, bounded MessagePak input, and static native
  package declarations.
- `git diff --check` passes.

The standalone `mvn ... javadoc:javadoc` check remains red because of pre-existing Javadoc errors
outside this feature (including malformed HTML in `TrackerThreeDnsPanel`, `MultiSourceDownload`,
`MysterPreferences`, and `MCListTableModel`). The new invitation classes add record-component
warnings but no new fatal Javadoc diagnostic.

## Environment limitations and follow-up verification

The repository's un-overridden JDK 26 compilation, complete test suite, and Linux package build pass
on JDK 26.0.2.1. The macOS plist and Windows WiX bases were taken from the official OpenJDK
`jdk-26-ga` sources, but DMG, EXE/MSI, and actual URI activation/uninstall behavior must still be
smoke-tested on their native build hosts.

There were no intentional behavioral deviations from the revised plan. Native macOS/Windows package
tests and a future open-invitation list/revocation UI are the remaining follow-up tests and product
work. The new native templates and the updated 3DNS/design wording merit a maintainer double-check on
their respective platforms.
