# Download source reconnect

Multi-source downloads remember the identities of servers that have supplied their data. They use
3DNS to locate those servers again after a restart, even if their addresses or ports have changed.
3DNS locates a server identity; a separate file-hash query determines whether that server currently
offers the requested file.

## Files and persistence

The existing `.i` file holds downloaded data. The `.p` file holds a UTF-encoded metadata header
followed immediately by its block bitmap, extending to EOF. The new `.s` server-list file lives
beside `.p` in the incoming metadata directory. For example, `movie.mkv.p` and `movie.mkv.s` coexist;
the payload may be in another directory.

After a block and its bitmap bit are successfully written, the download records the supplying
server CID, derived from the authenticated transport key. Every distinct supplying CID is retained
for the lifetime of this partial download. There is no expiration, ranking or count limit, and
failed lookups do not remove identities. Public keys, endpoints and remote filenames are not stored.
Individual segments report the identity with their first accepted block; the store deduplicates
across segments and sessions.

Source-file operations use synchronized, blocking file access like the partial-file layer. Recovery
reads the list on a worker thread; supplying segment workers append and flush new records. An
ordinary close flushes and preserves the file before returning. Completion or explicit
abandonment serializes deletion with appends; closing the writer to new records prevents
late callbacks from recreating it. Failure to persist a source does not invalidate payload or bitmap
progress. Pending unsaved identities are retried on subsequent source notifications or close.

### Source-file format

All integers below use big-endian encoding.

| Portion | Encoding |
|---|---|
| Magic | Four bytes: `MSSS` |
| Header | Four-byte positive length, then MessagePak payload |
| Each source | Four-byte positive length, then MessagePak payload |

Header fields are `/version` (integer, currently 1), `/type` (16 bytes), `/length` (long),
`/hashCount` (integer), and `/hashes/<index>/name` plus `/hashes/<index>/value`. These fields bind the
server list to its owning partial download. A source record contains `/cid` (exactly 16 bytes).
Unknown fields are ignored. Each frame is limited to 64 KiB; there is no total file or source-count
limit. Records are read incrementally, with one bounded payload allocation per frame.

Initial header creation uses a short temporary filename in the same directory and atomic
publication. Subsequent updates append. A truncated final frame is ignored on reading and trimmed
to the last complete boundary before another append. Complete valid records are never removed by
this repair. Malformed payloads with valid frame boundaries are skipped. An invalid frame length
stops parsing and disables writing, preserving any usable prefix. An unknown version, invalid
header or ownership mismatch leaves the file untouched and disables its use for new appends.
New partial-file creation discards a stale sibling, within the existing same-name download limits.

## Reconnection flow

Local queue activation starts initial-address candidates immediately. It also starts a recovery
worker that reads the source list; neither waits for initial-address failure. Normal hash crawling
continues independently. Recovery first starts when the queue admits the download. Once started,
it continues through pause or requeue, remembering suggestions without starting segment connections.
Resume reuses a pending recovery; if it has finished, resume starts a fresh lookup of saved CIDs.

Recovery starts every saved CID lookup as soon as the source list arrives, without waiting for
other lookups or transfer setup. Each DNS lookup has a 30-second deadline. Only an
exact verified 3DNS result for the requested CID is accepted. The returned public key pins the
subsequent TLS connection using `ParamBuilder.withExpectedServerPublicKey`.

`DownloadSourceRecovery` is a one-shot operation with `start()` and `cancel()`, not a promise.
Independent virtual threads wait on the existing DNS promise API, then submit candidates to
`MultiSourceDownload` without waiting for connection setup. Recovery owns only its lookup workers.
`MultiSourceDownload` owns accepted segment downloaders from creation and stops them on pause/end.
There is no preparation coordination object or cancellation handoff. `newDownload()` is a
callback-based lifecycle command and returns no promise. Both hash crawling and recovery submit
through it: candidates and their expected keys are retained in memory while paused, then tried on
resume. Ending the download cancels recovery and rejects subsequent suggestions.

Timeout policy belongs to the socket layer. TLS client sockets use a 30-second TCP connect timeout
and a two-minute socket-read timeout, explicitly configured before plaintext negotiation and on
the layered TLS socket before handshake. Subsequent hash lookup, header, queue and block reads use
that same timeout. It bounds read inactivity rather than the total duration of setup or transfer;
it is not a write timeout. The segment catches I/O failures, closes its socket and removes itself.
Connection creation returns a connected TLS socket or throws `IOException`, including when a peer
refuses the TLS upgrade; it never returns null or falls back to an unencrypted client connection.
Recovery handles expected lookup I/O, timeout and cancellation failures, while unexpected wrapped
failures propagate from the worker instead of being logged as an unavailable source.

Both restored original-address candidates and resolved CID candidates call
`MysterStream.getFileFromHash` before requesting blocks. The existing section 150 returns the
server's current filename, or an empty result if unavailable. The returned name is used for the
existing download section on the same socket; there is no extra file-stat preflight. Normal
hash-crawler candidates already selected by hash retain their existing transfer path. Legacy
partials without hashes retain their address-based behavior and skip CID recovery.

`DownloadTarget` keeps endpoint, type, expected key and an optional remote filename separately.
Restored/recovered hash targets have no remote filename; they never use the partial download's
local filename as a placeholder. Hash-crawler results and legacy filename-only targets carry a
known remote filename. `needsHashLookup()` is derived from its absence. The segment builds its
file stub once the remote filename is known; an early end-connection event can have no file stub.

Active work is deduplicated by endpoint and, once the transport key is available, by CID. Each
activation has a generation to reject obsolete deferred transfer startup; recovery suggestions
survive activation changes. Pause stops connection setup and transfer work, while end also cancels
pending lookups. A socket returned after cancellation is closed without issuing a file query.
Segment cleanup and block acceptance check the actual active segment object so an old segment at
the same address cannot remove or write through a replacement created by resume.

## Dependencies and identity

`MysterStream` and `DnsLookupProtocol` are injected explicitly. Download invocation parameters carry
the DNS capability, avoiding the initialization cycle that would arise if the stream implementation
required DNS at construction. `MysterSocket.getAuthenticatedPeerKey()` exposes the transport's
proved key without requiring download code to inspect a concrete TLS socket. A transport without
peer authentication returns empty and cannot produce a saved CID.

The existing 3DNS resolver verifies the advertised key/CID relationship and key possession before
returning an exact peer. The download still authenticates its own TLS connection against the
returned key. Hash-to-filename selection requests the desired content; it does not cryptographically
verify individual received blocks.

## Remaining work

- 3DNS routing-table background population remains separate; recovery needs usable routing state.
- Per-block subhash verification, bad-block attribution and banning are tracked in `TODO.txt`.
- Live moved-port recovery should be smoke-tested with multiple running nodes. Automated tests cover
  resolver integration, pinned connection parameters, hash selection, restart progress and lifecycle
  independently using deterministic transport seams.
