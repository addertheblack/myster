# Myster Signed Artifacts (.sip) — Design (v1)

## Goals

Provide a Myster-native, publisher-authenticated package format suitable for a distributed “store”:

- Keep payload + signatures + storefront metadata together.
- Allow **streaming verification** for multi‑GB payloads.
- Minimize bespoke formats: signatures are raw bytes; algorithms/versions live in signed metadata.
- Support a **pre-check** by downloading only the small metadata region (optional but recommended).

---

## Non-goals (v1)

- No multi-file bundles beyond the required entries below.
- No CA / certificate-chain trust model in v1 (publisher keys are pinned/learned by Myster).
  - Future versions may optionally support X.509 certificate chains / transparency / revocation, but this is out of scope for v1.
- No compression (STORE-only).

---

## Core Concepts

### Publisher identity

- `publisher_key_id`: existing Myster short hash of the publisher public key.
- Public keys are obtained via identity exchange, cache, pinning, or out-of-band.

Trust is explicit: **signatures prove authorship, not goodness**.

---

## File Format: `.sip` (v1)

### Overview

A `.sip` file is a ZIP container with strict rules:

- Exactly **4 entries** (v1): payload + payload signature + metadata + metadata signature.
- All entries use **STORE** (no compression).
- **ZIP64 MUST be used** when size/offset limits require it.
- No encryption.
- No directories.

### Entries (exact names)

1. `payload.<ext>`

   - The content (exe, msi, zip, etc.).
   - Original extension is preserved.

2. `payload.<ext>.sig`

   - Raw signature bytes (format depends on `metadata.mpak`).

3. `metadata.mpak`

   - MessagePack storefront + signing metadata (small).

4. `metadata.mpak.sig`

   - Raw signature bytes over `metadata.mpak` (see Signature Semantics).

---

## ZIP Constraints (reject on violation)

- Compression method MUST be STORE (0).
- Exactly four entries with the exact names above.
- No duplicate names.
- No paths, separators, or traversal (`/`, `\`, `..`).
- Signature entries must be reasonably bounded (e.g., ≤ 4 KiB each).
- `metadata.mpak` must be reasonably bounded (e.g., ≤ 1 MiB; configurable).

### ZIP size handling

- Classic ZIP is limited to \~4 GiB per entry/offset.
- ZIP64 removes this limit and is widely supported.

**Implementation rule (writer):** force ZIP64 if:

- `payload_length >= 0xFFFFFFFF`, OR
- any entry offset or central-directory offset would exceed `0xFFFFFFFF`.

Readers must accept both classic ZIP and ZIP64.

---

## `metadata.mpak` Content (v1)

`metadata.mpak` is MessagePack. (Maps are fine here; only the signed byte stream matters. Use Myster standard "map-of-maps" convention)

Minimum required fields:

- `format`: string, must be `"MYS-SIP-META-V1"`
- `publisher_key_id`: bytes (Myster short public-key hash)
- `sig_alg`: int enum (v1: `1 = RSA_SHA256`)
- `created_utc`: optional int/string

### Optional fields for future CA / certificate-chain support

These fields are **ignored in v1** but reserved for forward compatibility:

- `signer_cert_der`: bytes (DER-encoded end-entity X.509 certificate)
- `cert_chain_der`: array of bytes (DER certs, typically intermediates; omit root)
- `trust_model`: int enum
  - `0 = PINNED_KEY_ID` (v1 behavior)
  - `1 = X509_PKIX` (future: verify chain to trusted roots)
  - `2 = TOFU_CERT_FINGERPRINT` (future optional)
- `cert_fingerprint_sha256`: bytes (optional convenience / pinning)
- `revocation`: map (future)
  - e.g., `{ "mode": "none" | "ocsp" | "crl", "urls": [...] }`

Design note:

- Even with CA support, `publisher_key_id` remains useful for store indexing and identity continuity.

Note: v1 does **not** require persisting or advertising a payload hash.

---

## Signature Semantics

There are two signatures:

1. **Metadata signature** (`metadata.mpak.sig`) — authoritative for alg/version/publisher id.
2. **Payload signature** (`payload.<ext>.sig`) — authenticates the payload bytes.

Both signature files contain **raw signature bytes**; the algorithm is defined by `metadata.mpak` and is authenticated by the metadata signature.

### Canonical signed stream

All signatures sign a domain-separated, length-delimited stream:

```
PREFIX = ASCII(<DOMAIN>) || 0x00
LEN    = u64(message_length_bytes)      // big-endian
MSG    = PREFIX || LEN || message_bytes
SIG    = Sign(privKey, MSG)
```

Domains:

- Metadata signature domain: `"MYS-SIP-META-SIG-V1"`
- Payload signature domain:  `"MYS-SIP-PAYLOAD-SIG-V1"`

### What is signed

**A) Metadata signature**

- `message_bytes = metadata.mpak` (exact bytes as stored in the zip)
- Signature: `metadata.mpak.sig = Sign(privKey, PREFIX||LEN||metadata_bytes)`

**B) Payload signature (binds to metadata)**

To prevent mixing a payload with unrelated metadata, the payload signature signs:

- `message_bytes = metadata.mpak || u64(payload_length) || payload_bytes`

So:

```
PAYLOAD_MSG = metadata_bytes || u64(payload_length) || payload_bytes
payload.<ext>.sig = Sign(privKey, PREFIX||LEN(PAYLOAD_MSG)||PAYLOAD_MSG)
```

Notes:

- This keeps verification streaming-friendly: metadata is small; payload is streamed.
- Including `metadata_bytes` binds storefront info + algorithm selection to the payload.

### Algorithms

v1 supports **RSA‑2048** (matching current `Identity`).

- `sig_alg = 1` means `SHA256withRSA` over the canonical streams above.

Future:

- Additional `sig_alg` values can be introduced (e.g., Ed25519).
- If CA support is enabled (`trust_model = X509_PKIX`), verifiers may accept signatures validated by the embedded certificate chain, subject to local trust store policy.

---

## Verification Flow

### Pre-check (recommended)

To avoid downloading multi‑GB junk, clients should preferentially:

1. Range-fetch or otherwise download `metadata.mpak` + `metadata.mpak.sig`.
2. Verify `metadata.mpak.sig` using the publisher public key resolved by `publisher_key_id`.
3. Only then download the full `.sip` (or at least the payload region).

This is optional but strongly recommended for the “store” use case.

### Post-download (authoritative)

After full `.sip` download:

1. Parse ZIP, enforce constraints.
2. Verify `metadata.mpak.sig`.
3. Verify `payload.<ext>.sig` by streaming payload bytes and using the bound `metadata_bytes`.
4. If any check fails:
   - Delete `.sip`.
   - Record failure.
5. If all checks pass:
   - Materialize/extract payload for the user.
   - Optionally keep `.sip` for reseeding.

---

## Protocol-level Metadata

The protocol may hash/advertise the `.sip` container for transport/dedupe:

- `publisher_key_id`
- Optional: `sip_sha256`

Optional (store/indexing):

- A cached copy of `metadata.mpak` (or selected fields) to support browsing without immediate range fetch.

No payload hash is required in v1.

---

## Bad-data Handling

### Local bad-`.sip` blacklist

Maintain a local cache keyed by `sip_sha256` (when available):

- Records failed `.sip` fingerprints and failure reason (bad signature, invalid ZIP, etc.).
- Prevents repeated downloads of the same invalid data.

If `sip_sha256` is not known until after download, still store it once computed.

### Peer penalty

- Track peers that repeatedly serve bad `.sip` data.
- Deprioritize or block as appropriate.

---

## UX Notes

- Associate `.sip` with Myster.
- Double-clicking a `.sip` triggers verification then extraction.
- Failed `.sip` files are never exposed to the user as payloads.

---

## Future Extensions

- Multi-item bundles.
- Rich store metadata (icons, screenshots, dependencies, update channels).
- Ed25519 signatures for smaller/faster verification.
- Optional CMS/PKCS#7 `.p7s` for interoperability outside Myster.

---

## Open Questions

- Preferred outer naming: **thing.sip** (v1 default).
- Whether protocol should cache full `metadata.mpak` or only selected fields: **cache only the fields it needs**.
- Size limits for icons/metadata fields: **TBD during planning phase** (keep configurable; decide with real store UX in mind).

