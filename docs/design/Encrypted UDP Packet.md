# Myster Secure Datagram (MSD) — Rev C (Encrypted Section 1)

## Overview

This document describes the Rev C version of the **Myster Secure Datagram (MSD)** protocol. MSD is a minimal, UDP-based request/response packet format designed to be stateless, idempotent, and cryptographically secure. Each datagram is self-contained and consists of three length-prefixed sections.

Myster uses a **signed-then-sealed envelope pattern**: the client signs the message first (Section 2) and then seals the payload (Section 3) using encryption. This approach lets the server verify authenticity and select the correct key before decryption. It is consistent with established precedents such as **OpenPGP**, **Age**, and **JWT** message formats.

---

## Framing

Each UDP datagram is composed of three sections:

```
[ len1 | sec1_ciphertext ] [ len2 | sec2_cleartext ] [ len3 | sec3_ciphertext ]
```

* Each section begins with a 16-bit unsigned big-endian length field.
* Multi-field content within sections uses **MessagePack**.
* Retries resend the **exact same bytes**.

---

## Section 1 — Encrypted Keying Info

**Purpose:** Allows the server to recover the symmetric key and decryption parameters for Section 3, while keeping those details confidential and tamper-proof.

### Structure

Section 1 is a **sealed ciphertext** encrypted with the server’s **public key** (using any ECIES/HPKE-style envelope). Only the server can open it.

**Plaintext inside Section 1:**

```mpack
{
  alg:   "chacha20poly1305" | "aes-256-gcm",   // symmetric cipher for sec3
  key:   <32B symmetric key>,                  // random per request
  nonce: <12B nonce>                           // random per request
}
```

> **Note:** "Wrapping" a key simply means encrypting the symmetric key using the server’s public key so only the server can unwrap (decrypt) it.

### Key Identification

To help the server choose which private key to use, the client includes a short identifier (`srv_kid`) in **Section 2**.

---

## Section 2 — Client Signature Block

**Purpose:** Proves who sent the packet, when it was sent, and that it covers these exact ciphertext bytes.

### Structure

```mpack
{
  ts:       <int unix ms>,
  sig:      <bin>,                // signature by client SK
  pub?:     <bin>,                // client pubkey if server may not know it
  cid?:     <bin(16)>,            // short lookup = Trunc128(SHA-256(pub))
  sig_alg?: "ed25519",           // default
  cid_alg?: "sha256-128",        // default
  srv_kid?: <int|bin>             // identifies which server key to use for Section 1
}
```

### Signature

```
context = "MSD-REQ"
to_sign = context
          || Hash(Section1_ciphertext_bytes)
          || Hash(Section3_ciphertext_bytes)
          || ts
sig = Sign(client_sk, to_sign)  // e.g., Ed25519
```

* **Bind Section 1:** Including the hash of Section 1’s ciphertext ensures attackers cannot swap crypto parameters or server key IDs.
* No request ID is required at this layer (deduplication is handled in higher protocol layers).

---

## Section 3 — Encrypted Payload

**Purpose:** Carries the actual application data, encrypted with the symmetric key from Section 1.

**Ciphertext:** AEAD(key=`key`, nonce=`nonce`, plaintext=`app_payload`, AAD=optional)

* AEAD = Authenticated Encryption with Associated Data (e.g., ChaCha20-Poly1305 or AES-GCM).
* **Optional AAD:** `Hash(sec1_ciphertext)` ensures that if Section 1 is modified, decryption fails.

**Randomness:** Each new request uses a fresh random `key` and `nonce`. Retries of the same request resend identical datagram bytes, including the same nonce.

---

## Send / Receive Flow

### Client Send

1. Generate random `key` (32B) and `nonce` (12B).
2. Build **Section 1 plaintext** (`{alg,key,nonce}`) and encrypt to server’s public key → `sec1_ciphertext`.
3. Encrypt `app_payload` with `(alg,key,nonce)` → `sec3_ciphertext` (optional AAD = `Hash(sec1_ciphertext)`).
4. Compute signature:

   * `h1 = Hash(sec1_ciphertext)`
   * `h3 = Hash(sec3_ciphertext)`
   * `sig = Sign(client_sk, "MSD-REQ" || h1 || h3 || ts)`
5. Build **Section 2**: `{ts,sig,pub?,cid?,sig_alg?,cid_alg?,srv_kid?}`.
6. Transmit: `[len1||sec1][len2||sec2][len3||sec3]`.
7. Retries resend **identical datagram bytes**.

### Server Receive

1. Parse **Section 2**, use `srv_kid` to select private key.
2. Decrypt **Section 1** with server private key → `{alg,key,nonce}`.
3. Recompute `h1`,`h3`, verify `sig` with `pub` (or via `cid` lookup).
4. Decrypt **Section 3** using `(alg,key,nonce)`; apply AAD if used.
5. Forward decrypted payload to transaction/request layers.
6. Optionally enforce a timestamp window (e.g., ±120 s) to reject stale packets.

---

## Response (Optional Signature)

Responses can mirror the request structure, optionally including a server signature block.

**Server Signature Example:**

```
to_sign = "MSD-RES"
           || Hash(resp_section1_bytes)
           || Hash(resp_section3_ciphertext_bytes)
           || ts
sig = Sign(server_sk, to_sign)
```

**Response Sections:**

* **Section 1:** `{alg, nonce}` (plaintext)
* **Section 2:** optional signature block
* **Section 3:** encrypted payload under the same symmetric key

---

## Security Notes

* Section 1 is encrypted to the server’s public key (confidential).
* Section 2 signature covers both ciphertext sections (integrity).
* Section 3 uses a random key + nonce each time (uniqueness).
* Retransmits resend identical packets (safe reuse because plaintext is identical).
* Timestamps provide basic freshness (replay filtering can occur in higher layers).

---

## Terminology Mapping (for crypto folks)

| Concept              | Industry Equivalent                                   |
| -------------------- | ----------------------------------------------------- |
| Section 1 encryption | ECIES / HPKE sealed envelope                          |
| Section 2 signature  | Signed-then-sealed envelope style (OpenPGP, JWT, Age) |
| Section 3 encryption | AEAD (ChaCha20-Poly1305 / AES-GCM)                    |
| Signature scheme     | Ed25519 or similar                                    |
| Identity hash (cid)  | Public key fingerprint                                |
| Nonce                | AEAD IV (unique per encryption)                       |

---

## Field Summary

| Section | Field      | Type    | Description                                        |
| ------- | ---------- | ------- | -------------------------------------------------- |
| 1       | alg        | str     | Symmetric cipher used for payload                  |
| 1       | key        | bin(32) | Random symmetric key, encrypted with server pubkey |
| 1       | nonce      | bin(12) | Random per request                                 |
| 2       | ts         | int     | Timestamp (ms)                                     |
| 2       | sig        | bin     | Client signature                                   |
| 2       | pub        | bin     | Client public key (optional)                       |
| 2       | cid        | bin(16) | Hash-based identity hint                           |
| 2       | srv_kid    | int/bin | Server key selector                                |
| 3       | ciphertext | bytes   | AEAD-encrypted payload                             |

---

*End of Rev C Spec*
