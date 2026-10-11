# 3DNS invitation diagnostics

Invitation preview errors now distinguish no usable tracker candidates, queries with no verified
responses, exhausted routing through verified neighbors, the query limit, and the overall
deadline. Failures after exact resolution identify TLS connection establishment, access-list
retrieval, or connection closure instead of reporting a missing bootstrap.

## Changes

- `ThreeDnsLookupDiagnostics` retains the usable tracker seed snapshot, bounded per-query
  CID/address/outcome observations, expected I/O causes, and elapsed time.
- `ThreeDnsLookupResult` exposes those observations. `ThreeDnsLookup` forwards unexpected
  exceptions already delivered by peer futures unchanged and cancels outstanding work.
- `BadPacketException` supports cause-preserving construction. Existing
  `DatagramEncryptUtil.DecryptionException` now extends it, and the datagram client preserves
  that subtype instead of replacing it with a message-only exception.
- `TypeJoinCoordinator.BootstrapLookupException` retains the lookup result and candidate
  failures as suppressed exceptions. Expected validation and preference failures also retain
  their causes.
- `JoinPrivateTypeDialog` offers selectable/copyable failure details and logs the same text.
  `TypeJoinDiagnosticsUtils` receives only the operation, bootstrap CID, and exception, never
  the invitation URI, password, or invitation ID.
- `docs/conventions/myster-coding-conventions.md` records the convention of using checked
  exception subclasses to distinguish failures and preserving their causes when forwarding.
- Updated `docs/design/Myster 3DNS.md` and `docs/design/Private Type Invitations.md`.

## Validation

74 targeted tests passed, covering the 3DNS resolver/frontier/peer client, tracker seed snapshots,
encrypted datagrams, FIND_CLOSEST decoding, invitation coordination/diagnostic formatting/protocol,
and download source recovery. New cases cover exact-candidate timeouts, retained decryption and
decoder causes, unchanged runtime failures and cancellation, distinct terminal messages, TLS
failure phases, and credential-free diagnostic formatting.

The first targeted run passed. A subsequent build encountered disappearing class files in
`target/classes`, which is shared with IntelliJ. Final validation used an isolated Maven project
at `/tmp/myster-3dns-diagnostics.t5xd7kwj`, with its `src` symlinked to the working tree and its own
output directories. Command:

```sh
mvn -o -q '-Dtest=TestThreeDns*,TestTrackerThreeDnsCandidates,TestMysterDatagramExpectedKey,TestMysterDatagramFailureDetails,TestFindClosestDatagramProtocol,TestDatagramEncryptUtil,TestTypeJoinCoordinator,TestTypeJoinDiagnosticsUtils,TestTypeJoinProtocol,TestDownloadSourceRecovery' test
```

The earlier debugger audit confirmed that an expected failed candidate query was absorbed and
produced closest-peer exhaustion without retaining its cause; the new diagnostics address that
observed path. This change does not reproduce or establish the cause of the friend's incident.

## Limits and follow-up

- Tracker observations describe usable seeds at lookup start. An absent exact seed does not prove
  the identity is unknown; it may have been excluded by liveness/address checks.
- A transaction timeout means no authenticated response arrived, not proof of an offline server
  or a wrong public key. In-flight entries had no completed response when lookup terminated.
- Direct runtime panics still propagate on their executing thread. The new future handling
  specifically preserves unexpected exceptions already delivered by a peer future.
- Retry/negative-cache behavior, address selection, and lookup verification requirements are
  unchanged. The tracker onboarding catch and shared worker boundary described in the audit
  were outside this focused change.
- The Details dialog and clipboard action need a desktop smoke check; diagnostic content is
  covered by tests without constructing a Swing window.
