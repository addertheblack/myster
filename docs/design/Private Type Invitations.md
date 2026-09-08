# Private Type Invitations

## Purpose and scope

Private-type invitations provide a synchronous, pre-authorized way to add one authenticated Myster
identity as a normal `MEMBER`. An administrator creates a short-lived invitation and shares its link
and password separately. The recipient resolves the named bootstrap through 3DNS, pins TLS to the
resolved public key, redeems the invitation, and imports the type only after the returned signed
access-list chain proves membership.

This is not the deferred manual-request workflow. There is no administrator inbox, pending state,
approval/denial action, polling, automatic retry, key transfer, or `ADMIN` grant. A future UI may
list and revoke still-open invitations; version 1 intentionally provides only creation and
redemption.

## Link contract

The canonical password-free link is:

```text
myster://join-type/v1?type=<32-lowercase-hex>&bootstrap=<32-lowercase-hex>&invitation=<32-lowercase-hex>
```

All three identifiers are exactly 128 bits. `type` identifies the signed type chain, `bootstrap`
identifies the invitation-creating server, and `invitation` is a random lookup identifier. The
complete URI is limited to 4 KiB. Parsing is order-independent, rejects duplicate known fields,
requires the exact action and version, and ignores unknown fields for forward compatibility.

An external producer may supply one UTF-8 percent-encoded `code` field of 1–256 Unicode code
points. Myster's creator never generates that field. `TypeJoinUri.toString()` and `toUri()` always
produce the password-free canonical representation, so diagnostic output cannot reproduce a parsed
secret.

## Creation and local persistence

The administered type's Members tab offers `Create Invitation…`. The administrator enters the
password twice and chooses 24 hours, 7 days, or 30 days; 7 days is the default. Copy remains disabled
until persistence succeeds.

Invitation records are bounded operational state under the injected
`MysterTypes/Invitations/{typeHex}/{invitationHex}` Preferences hierarchy. Version 1 stores:

- schema version, PBKDF2-HMAC-SHA256 algorithm, iteration count, 16-byte salt, and 32-byte verifier;
- creation and expiry epoch milliseconds;
- optional 16-byte `claimedBy` CID and a `redemptionComplete` flag.

Passwords are never stored. Production derivation uses 210,000 PBKDF2 iterations; the per-record
parameters allow later policy evolution. Creation and every claim/completion transition flush before
success is reported. At most 64 unexpired records are retained per type. Expired and malformed known
records are removed opportunistically, while unknown future schema versions are retained and
ignored. Completed records remain until expiry to support an idempotent same-identity retry.

## Redemption protocol

Section 126 is a TCP stream section used only after the ordinary connection has upgraded to mutually
authenticated TLS. There is no UDP form. Both payloads are length-prefixed MessagePak frames capped
at 4 KiB.

The schema 1 request contains a 16-byte type CID, 16-byte invitation id, and bounded password. It
does not carry a requester identity. The server derives that exclusively from
`ConnectionContext.callerCid()`; plaintext callers are rejected before the password frame is read.
The response contains one forward-compatible string status: `APPROVED`, `ALREADY_MEMBER`,
`INVITATION_NOT_ACCEPTED`, `TYPE_NOT_FOUND`, `NOT_AUTHORIZER`, or `ERROR`.

Wrong, expired, missing, and another-identity-consumed invitations all return
`INVITATION_NOT_ACCEPTED`. KDF work is concurrency-bounded, and failures use a bounded per
invitation/caller/address backoff. Internal exception text and invitation state are never sent.

## Mutation and crash behavior

`TypeMembershipService` is the shared path for invitation redemption and direct Members-tab add or
remove operations. It serializes mutation per type, reloads the current chain, verifies that the
local key is still a writer, appends to a copy, and publishes the new cached chain only after the
access-list save succeeds.

After password verification, redemption rechecks the record under that type lock. It flushes a
`claimedBy` transition before appending `AddMemberOp(caller, MEMBER)`, saves the signed chain, and
then flushes completion. Only the claiming identity can resume between those steps. A retry by that
identity returns `ALREADY_MEMBER` without a duplicate block; another identity receives the same
ambiguous rejection as other invalid invitations. A completed invitation cannot re-add its member
after a later manual removal.

## Recipient trust sequence

1. Parse the complete link without changing local type state.
2. Resolve the bootstrap CID through `MysterProtocol.getDnsLookup()`; its production
   `DnsLookupProtocol` implementation is `ThreeDnsLookup`. Accept only `EXACT_VERIFIED`.
3. Give `MysterStream` a `ParamBuilder` containing that peer's address and expected public key. It
   returns an ordinary connected `MysterSocket` whose TLS peer key has also been checked, then fetch
   the complete section 125 chain on that socket for a preview.
4. After explicit user confirmation, open another expected-key TLS `MysterSocket` and redeem
   section 126 through `MysterStream`.
5. On `APPROVED` or `ALREADY_MEMBER`, fetch a fresh complete section 125 chain on the same socket.
6. Validate the chain, require the link's type, and require the local server CID to be a member.
7. Persist/import the type with the remembered `Enable this type` selection.

A status is never sufficient proof. Failure before the final checks leaves the type registry and
local access-list storage unchanged. A public type needs no invitation and follows the validated
import path directly.

`MysterSocket` is the normal reusable stream connection; “expected-key” or “pinned” describes the
extra peer-key check performed while opening it, not a distinct socket type. Each connection section
that supports sharing accepts this caller-owned socket through `MysterStream`. The coordinator owns
the sequencing and socket lifetime, while section encoding remains behind the protocol facade.

## Desktop activation and UI

Pasted links, complete command-line arguments, arguments forwarded by the single-instance service,
and macOS `APP_OPEN_URI` events all feed one bounded EDT dispatcher. It always opens the confirmation
dialog and never redeems automatically. One modal is active at a time, with at most eight distinct
links queued behind it.

The application requires a local server identity before constructing the tracker and invitation
UI. `TypeManagerPreferences` and `TypeEditorPanel` receive their local CID, known-server source,
invitation manager, membership service, and join dispatcher as mandatory constructor dependencies.
Missing one is a bootstrap failure rather than a reduced-functionality UI mode. This also ensures
that direct Members-tab edits cannot fall back to bypassing `TypeMembershipService`.

The macOS bundle declares `myster` in `CFBundleURLTypes`. The Windows installer owns the
`Software\\Classes\\myster` protocol component for its install scope. The Linux DEB owns a normal
desktop entry containing `%u`, `Categories=Network;`, and
`MimeType=x-scheme-handler/myster;`. Package removal therefore removes the declarations it owns.
Raw Java, tests, and `-s` startup never mutate platform associations.
