# Implementation Summary: Domain-Specific CID Types

## Summary

Replaced the public generic `Cid128` API with two non-interchangeable domain values:
`MysterTypeCid` for MD5-derived type identities and `ServerCid` for truncated-SHA-256 server
identities. `MysterType` now composes `MysterTypeCid`; tracker, 3DNS, access membership,
authenticated transport, and server-selection APIs now use `ServerCid`. The shared numeric
representation is package-private and visible only to the two wrappers.

## Files changed

- Added `com.myster.cid.Cid128`, `MysterTypeCid`, and `ServerCid`; removed the public
  `com.myster.identity.Cid128`.
- Refactored `MysterType` to delegate bytes, hex, equality, hashing, parsing, and public-key
  derivation to `MysterTypeCid`.
- Removed ambiguous `com.myster.identity.Util.generateCid()` and `generateNakedCid()` helpers.
- Migrated tracker/pool, 3DNS models and datagram API, access-list member state/operations,
  TCP/UDP caller identity, transaction context, type-editor server selection, tracker UI, and
  application wiring to `ServerCid`.
- Changed MSD signature lookup/decryption metadata from raw `byte[] keyHash` to typed
  `ServerCid` at the packet boundary.
- Updated CID and affected integration tests, including stale four-byte synthetic Myster type
  fixtures.
- Updated codebase structure, coding conventions, 3DNS/private-type design docs, and active
  follow-on plans.

## Key design decisions

- Both public CID types are immutable, fixed at 16 bytes, and return defensive byte copies.
- Identical bytes in the two domains are not equal and cannot cross typed APIs.
- Only `ServerCid` exposes ordering and 3DNS ring arithmetic.
- Derivation bytes are unchanged: type CID is MD5 of the encoded type public key; server CID is
  the first 16 bytes of SHA-256 of the encoded server public key.
- Network and disk formats are unchanged. Incoming invalid type/server CID lengths are converted
  to checked I/O/decryption failures at parsing boundaries.
- `MysterType` remains the normal application-level type value, so type/search/file APIs do not
  leak `MysterTypeCid` merely because it is the internal compact identity.

## Deviations from the plan

- Removed the unused MSD `CID_SIZE` constant instead of renaming it. `ServerCid.LENGTH` is now the
  single length authority, avoiding a duplicate protocol constant.
- Removed `MysterType.toShortBytes(PublicKey)`. Its only production-adjacent caller was a unit
  test, while `MysterTypeCid.fromPublicKey()` is the actual production derivation API. This follows
  the convention against test-only production methods.

## Javadoc and design documentation

- Added derivation, immutability, fixed-width, byte-order, and ring contracts to the CID types.
- Updated Myster type, access-list member, tracker, 3DNS, transport caller-identity, and UI API
  documentation to name the correct domain.
- Documented the mixed access-list roles: `MysterTypeCid` identifies the list/type and
  `ServerCid` identifies members/admins.
- Updated `docs/codebase-structure.md`, `docs/design/Myster 3DNS.md`, the private-types design,
  active 3DNS follow-on plans, and the coding conventions.

## Verification

- Maven compile and test-compile pass with the project-target Java 25 runtime.
- Focused CID, Myster type parsing, MSD encryption, expected-key transport, access-list,
  tracker/pool CID, FIND_CLOSEST, and 3DNS suites pass.
- The unrestricted full Maven suite passes: **443 tests, 0 failures, 0 errors, 0 skipped**.
- Source audit confirms raw `Cid128` is referenced only by `Cid128.java`, `MysterTypeCid.java`,
  and `ServerCid.java`; no production `generateCid`, `generateNakedCid`, or raw `keyHash` CID APIs
  remain.
- `git diff --check` passes.

## Known issues / follow-up

- The full suite prints an asynchronous Jimfs `ClosedDirectoryStreamException` from
  `FileTypeList` teardown after tests complete, but Maven exits successfully with no reported test
  error. This was not caused by the CID conversion and was not changed here.
- The unrelated untracked `docs/.DS_Store` was left untouched.
