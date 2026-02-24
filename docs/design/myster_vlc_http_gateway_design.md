# Myster VLC HTTP Gateway — Concise Design Doc

## Goal
Expose Myster-hosted media to LAN clients (especially VLC) using zero‑config discovery and a tiny embedded HTTP server, without modifying VLC or turning it into a P2P client.

---

## High-Level Model
- **Discovery:** mDNS/DNS‑SD advertises each Myster node as `_http._tcp` (or `_https._tcp`).
- **Access Protocol:** Plain HTTP(S) for browsing + streaming.
- **Data Plane:** HTTP requests are translated into Myster byte‑range reads.
- **Scope:** Each node advertises only its own virtual library; multi‑source logic stays internal to Myster.

---

## VLC Interaction Pattern
- Browses via HTTP directory listings (HTML pages with links).
- Plays files via `GET` + `Range` headers.
- Issues frequent `HEAD` requests.

No JSON APIs or custom protocols are required for VLC compatibility.

---

## HTTP Surface
### Browsing
Return HTML directory listings with `<a href>` links:
- `/browse/`
- `/browse/music/`
- `/browse/videos/`
- paging allowed: `?page=N`

Listings are backed by in‑memory filename/path indexes (no media tag scanning).

### Streaming
- `/content/<object-id>` or `/browse/.../file.ext`
- Supports:
  - `Range`
  - `206 Partial Content`
  - `Content‑Range`
  - `Content‑Length`
  - `Accept‑Ranges: bytes`

### Metadata (non‑VLC / future clients)
- `/items/<id>` → JSON

Optional; not required for VLC.

---

## Performance Rules
- **Browse = cheap:** filenames + sizes only.
- **HEAD = cheap:** must not trigger network scans or tag parsing.
- **Tag parsing / rich metadata:** background or on‑demand only.

---

## Internal Mapping
HTTP Gateway layer:
- resolves path / object‑id → Myster object
- issues Myster read(offset, length)
- optionally prefetches / caches
- handles retry/fallback transparently

---

## Non‑Goals (Initial Phase)
- No UPnP/DLNA implementation.
- No SAP/RTP.
- No distributed browsing UI.
- No VLC plugins.

---

## Future Extensions (Optional)
- `_https._tcp` advertisement.
- Background metadata index.
- Curated virtual trees (by extension, folder, recent).
- Multi‑source streaming hidden behind gateway.
- Signed redirect URLs to other peers.

---

## Summary
mDNS advertises Myster → VLC connects via HTTP → browses HTML listings → streams via Range → Myster supplies bytes. Simple gateway, zero client changes, maximal reuse of existing Myster capabilities.

