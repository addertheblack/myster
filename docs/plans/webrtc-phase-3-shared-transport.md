# WebRTC phase 3: Shared media transport

Status: PRELIMINARY — not ready to execute. Refresh against completed earlier phases before implementation.

Sequence: Requires [phase 2](webrtc-phase-2-datagram-refactor.md). Next: [phase 4](webrtc-phase-4-basic-calling.md).

Execute one phase at a time. This document owns this phase only; [the phase index](webrtc-video-call.md) is navigation, not an implementation plan.

## Design Section

### 1. Summary

Extend the TCP adapter proven in phase 1 with UDP socket support, then integrate it with phase-2 datagram APIs and Myster’s existing TCP listener. Prove real production shared-port UDP and forced TCP media, including registration/port-change lifecycle, before calling UI.

### 2. Non-goals

- Call signaling/ringing/UI, group relay and screen sharing.
- Extra inbound media listener/port.
- NAT traversal, external STUN/TURN integration and relay infrastructure; connectivity belongs to Myster.

### 3. Assumptions & open questions

**Connectivity ownership:** Myster’s protocols provide peer reachability and NAT traversal. This project consumes that transport capability; it does not implement, evaluate or validate NAT traversal, external STUN/TURN services, router mappings or relay infrastructure. Native ICE/STUN checks may still be needed to establish the WebRTC session over the Myster-provided path; those checks are adapter/session mechanics, not a separate NAT-traversal project.

PRELIMINARY: refresh after phases 1/2. Replace proposed native handles and endpoint methods with proven API signatures. UDP injection, candidate/address reporting and ufrag/tuple demultiplexing remain unproven after phase 1 and are explicit feasibility gates in this phase. Shared IPv4 is mandatory; advertise IPv6 only after proof. One future application call may create multiple logical ICE sockets. Patched native artifacts must match their Java API.

### 4. Proposed design

Prove UDP packet-factory injection and logical ICE-port routing here; phase 1 proves TCP only. Use the resulting UDP factory and phase-2 scoped sender for unchanged ICE/DTLS/SRTP over WR datagrams. Register transport at the configured main port and close logical endpoints without closing the physical socket. Implement active/passive native ICE-TCP through VMED on the existing listener. Gates A/B now require production-stack evidence, not the diagnostic harness.

### 5. Architecture connections

#### Shared media wire contract and ownership

UDP reserves transport code `0x5752` (“WR”), after a collision check. The complete wire datagram is the two-byte unsigned network-order code followed by the original WebRTC packet (ICE/STUN, DTLS, SRTP or SRTCP). No extra call ID or encryption is prepended. Myster's dispatcher currently passes the **whole prefixed packet** to its registered transport; the bridge removes exactly two bytes before enqueueing to the native network thread. Outgoing peer packets get the prefix exactly once. Preserve UDP datagram boundaries and source address/port; never pass a truncated packet as valid media.

The factory exposes logical local endpoints to ICE at the configured physical port. Wildcard binding must still report correct interface-local addresses. Route connectivity checks to the correct logical port using STUN username/ICE ufrag and route authenticated established traffic through the nominated tuple, with native ICE/DTLS retaining validation. A Java call-ID map alone cannot demultiplex the media wire format. The first version supports one application call, but must prove that its own candidate/interface gathering cannot deliver a packet twice or consume another logical port's checks. Transport registration stays alive while native endpoints exist; closing one logical socket must not close the shared Myster socket.

Candidate gathering must not create ephemeral native UDP sockets or separate TCP listeners. Constrain the bridge to explicitly supported host and peer-reflexive candidates for the first LAN spike. TCP candidates advertise the existing Myster server port on the receiver, with a real active/passive ICE pairing rather than invented SDP rewriting. The caller may use an ephemeral TCP source port, as ordinary outgoing connections do; that requires no extra inbound port forwarding.

TCP reserves media section `0x564D4544` (“VMED”), separate from VCAL signaling. For this first bridge, the active side opens an ordinary connection to the existing Myster listener, writes the 32-bit media section and waits for its one-byte acceptance. Only after acceptance may the native socket report connected and begin **its own pinned-revision ICE-TCP packet framing**. On the accepted side, the section handler supplies that existing stream to the passive native socket and remains its sole reader/writer owner until closure. The handler must not return into `ConnectionRunnable` while native code still owns the stream. No raw file-descriptor assumption: the Java stream adapter is the initial approach, with ownership independent of whether the context wraps plain TCP or TLS.

Accept VMED only while an accepted call is negotiating or active and a signaling-bound peer candidate/source is expected. Do not infer authentication from source IP; ICE credentials and DTLS fingerprints carried by the pinned TLS signaling connection authenticate the media association. Anonymous/unknown streams cannot create dialogs or capture devices. Limit provisional incoming streams to four per active call with a five-second ICE-validation deadline and close excess connections. Initial media TCP is ordinary TCP plus DTLS/SRTP; it does not require a second TLS envelope. If a later build carries it inside STLS, document the extra encryption rather than accidentally layering it through the existing TLS-only client factory.

Use a fixed small UDP media queue, initially four packets for phase-2 validation, with non-blocking admission. When full, reject/discard the newly submitted packet and map the rejection to the pinned native API's temporary-send error; never block a producer or evict an admitted head behind the writer. Measure native video bursts, queue age and throughput before tuning capacity. Do not apply UDP discard policy to TCP bytes or reliable signaling frames.

The single writer peeks at the head and attempts its complete datagram. Remove that exact entry only after `DatagramChannel.send` returns its full length. If it returns zero, retain the head, wait for write readiness and retry without busy-spinning; later producers may fill the remaining slots and then receive immediate rejection. A definitive packet error removes/fails that entry so it cannot jam the queue forever; a closed socket terminates/fails pending entries. Signal queue readiness on the relevant full-to-available transition and match sent-attempt notification semantics to the pinned native implementation. Retrying a zero-byte local attempt is not retransmitting an already-sent UDP packet.

**Phase 2, separate refactoring:** use a separate writer for all sends on the shared channel, with the existing reader operating concurrently. Java permits one reader and one writer concurrently on a `DatagramChannel`. Do not retain the old send loop while introducing a competing media writer. Keep the channel non-blocking while registered for selection; the writer waits for work and enables write readiness only when needed, without keeping an always-writable selector spinning. Preserve legacy protocol submission behavior and verify channel recreation, close and port-change ownership across both workers. Reference: [DatagramChannel threading](https://docs.oracle.com/en/java/javase/26/docs/api/java.base/java/nio/channels/DatagramChannel.html), [write readiness](https://docs.oracle.com/en/java/javase/26/docs/api/java.base/java/nio/channels/SelectionKey.html#OP_WRITE).

Java provides no later UDP transmission/delivery callback. A `DatagramChannel.send` call concerns one whole datagram and returns its byte count (or zero for insufficient room in non-blocking mode), or throws an error. If the pinned native API requires a sent-packet event, retain the native packet ID and relevant metadata alongside that queued datagram and reproduce the pinned revision's event timing and failure semantics, using a local timestamp. An inspected upstream `AsyncUDPSocket` emits its sent-packet event after the send attempt even when that attempt returns failure, so success-only notification is not a universal requirement. No byte-range tracking, kernel completion subscription, delivery confirmation or automatic retry is required. Verify the native contract and discard/error handling in the spike. Reference: [Java DatagramChannel.send](https://docs.oracle.com/en/java/javase/26/docs/api/java.base/java/nio/channels/DatagramChannel.html#send(java.nio.ByteBuffer,java.net.SocketAddress)).

The bridge must retain native send metadata (packet IDs, options, errors and completion timestamps) and receive timing/address metadata required by the pinned API. Existing `DatagramSender` is fire-and-forget and `AsyncDatagramSocket` has an unbounded queue and swallows send exceptions. Add the scoped send/result metadata needed by the pinned native API without changing legacy sender behavior; use the phase-2 small non-blocking media queue specified above and tune it from measurements. Do not invent success-only sent-packet semantics: inspect the pinned native adapter, which may report send attempts separately from the send return/error. Copy bytes before crossing JNI lifetimes, dispatch receive callbacks to the native network thread, and do no JNI work under the transport-manager monitor. Reject media submissions when the small queue is full and report the temporary send failure appropriately to the native transport; control/ICE failure cannot be hidden as successful send. The shared physical queue must also be bounded for new media submissions so a slow socket cannot move the unbounded backlog down one layer.

If receive admission limits prove necessary, enforce them before the asynchronous callback queue as well as at the JNI bridge; otherwise the existing invoker can accumulate packets before bridge limits apply. Preserve legacy protocol delivery. Queue admission drops rather than blocks; native callback affinity does not require all Myster I/O and media processing to share a thread. The separate reader/writer design above belongs exclusively to phase 2; verify its behavior rather than assuming native callbacks or all processing must use those I/O threads. Measure fairness under concurrent transaction traffic; draining continuous media indefinitely can starve receive processing.

The adapter consumes reachable peer endpoints/streams supplied by Myster and does not add external STUN/TURN services or relay infrastructure. Native ICE/STUN packets exchanged between these endpoints remain part of WebRTC session establishment and must pass unchanged through the shared bridge. No NAT-traversal design, validation or future standard-service demultiplexing work is assigned to this project.

### 6. Key decisions & edge cases

Use native ufrag/nominated-tuple association and validation, not a Java call-ID wire prefix. Keep logical/socket generations isolated. Send callbacks use actual phase-1 semantics through phase-2 metadata; queue full is immediate temporary rejection. Do no JNI work under manager locks.

On server-port change, stop media, unregister the old main-port transport and register the new ready endpoint. Do not migrate live ICE or leave old candidates valid. TCP handler retains ownership until bridge shutdown. Accepted streams are provisional/bounded until ICE/DTLS validates them. Future phase-4 signaling supplies credentials/fingerprint association; diagnostics supply equivalent test context for this phase.

### 7. Acceptance criteria

- [ ] Updated phase-3 plan consumes exact phase-1 native and phase-2 local APIs.
- [ ] Real ICE/DTLS/SRTP media travels through existing WR socket with no hidden binds.
- [ ] UDP-blocked media uses VMED active/passive ICE-TCP on the existing listener.
- [ ] Partial TCP I/O, provisional limits, teardown, shared-socket lifetime and stale callbacks are verified.
- [ ] Port-change registration and simultaneous legacy protocols work.
- [ ] Production evidence distinguishes local send attempt, rejection, loss and readiness.
- [ ] No UI/device capture/signaling authorization is falsely claimed from synthetic transport tests.

---
## ✦ IMPLEMENTATION DETAILS (for the implementation agent)
---

### 8. Affected files / classes

- New `src/main/java/com/myster/call/media/SharedMediaConfiguration.java`: configured Myster port, local endpoints, allowed candidate policy and peer association.
- New `native/webrtc/README.md`, `native/webrtc/source-lock.json`, `native/webrtc/patches/myster-packet-sockets.patch`: pinned upstream wrapper/WebRTC revisions, toolchain prerequisites, extension patch and packaging instructions. Do not fabricate commit hashes during planning; resolve exact source revisions at Gate A.
- New `src/main/java/com/myster/call/transport/MysterWebRtcTransport.java`: Java/native packet bridge and owned endpoint registration.
- New `src/main/java/com/myster/call/transport/WebRtcDatagramTransport.java`: WR prefix/demultiplexing and bounded dispatch.
- New `src/main/java/com/myster/call/transport/WebRtcTcpMediaServer.java`: VMED section and accepted-stream ownership.
- New `src/main/java/com/myster/call/transport/SharedDatagramEndpoint.java`: scoped main-port send handle, readiness and asynchronous send results.
- `src/main/java/com/myster/net/datagram/DatagramConstants.java`: WR code.
- `src/main/java/com/myster/net/server/ServerFacade.java`: injected main-port lifecycle hooks for registration and call termination before port change.
- New native bridge tests under `src/test/java/com/myster/call/transport/` plus C++ tests alongside the pinned wrapper patch.
- Native-dependent opt-in transport diagnostics remain under `src/test/java/com/myster/call/`.
- `docs/impl_summary/webrtc-phase-3-shared-transport.md`: production proof and exact APIs.

Phase-2 socket refactoring is a prerequisite, not repeated here. Phase-4 startup wiring for user calls is deferred; expose constructor-injected registration/lifecycle hooks now.

### 9. Step-by-step implementation

1. **Shared UDP endpoint.** Add a scoped endpoint API without leaking `TransportManager`; capture binding readiness and local-port generation. Register WR on the configured main port through `ServerFacade` lifecycle, without touching the LAN discovery port unless it is also the main port. Use existing physical socket. `WebRtcDatagramTransport.packetReceived` checks size, removes the prefix and queues a bounded immutable payload/address/timestamp event. The send path prepends once and obtains an actual asynchronous send result; native buffers cannot escape their ownership scope.

2. **Logical ICE ports.** Report interface-local candidates at the physical port rather than native ephemeral binds. Implement/reuse correct ufrag and remote-tuple routing in the native factory. Verify one-to-many logical port creation, STUN integrity, candidate pair nomination, BUNDLE behavior and stale-session filtering. Do not use `SO_REUSEPORT`, descriptor duplication or SDP-only port rewriting as substitutes for integration. Test IPv4 first; prove IPv6 or explicitly report its unsupported status before advertising IPv6 candidates.

3. **Gate A.** In a child JVM, send synthetic video/audio between two endpoints through real WR datagrams and in-memory signaling while Myster ping/stats operate. Use distinct Myster ports on localhost for two installations, then two machines at their ordinary ports. Record packet capture, candidate addresses, socket inventory, receive frame/audio counters and teardown. Enforce bounded shared send queues and observe drops/errors under artificial congestion. Stop application work if this cannot pass.

4. **TCP adapter.** Add VMED via existing `ConnectionSection`/`ServerStreamHandler` dispatch. Open active outgoing media with a plain TCP helper in the bridge, not TLS-only `MysterSocketFactory`. Send VMED once and wait for acknowledgement before native connect notification. Accept into a passive logical native socket on the existing listener. Section retains lifetime until media closes; no concurrent `ConnectionRunnable` reader. Let the native pinned API supply ICE-TCP length framing and partial-write handling. If ordinary ICE candidate creation cannot represent this topology, document a native allocator extension or architectural failure before proceeding.

5. **Gate B.** Block UDP between endpoints, force active/passive ICE-TCP, and prove synthetic audio/video through VMED. Require data-path evidence; successful TLS signaling is insufficient. Exercise rejected sections, provisional-stream limits, five-second validation timeout, reordered/split reads, slow receiver, disconnect and no extra inbound listener. Confirm selected TCP candidate pairing and latency/backpressure behavior. Do not add application framing to disguise a failed native handoff.

6. Integrate production registration and port lifecycle via `ServerFacade`; stop endpoints before rebinding and prove no old transport remains. Update phase 4 with stable media APIs and stop after this phase.

### 10. Tests to write

- `TestWebRtcDatagramTransport`/`TestSharedDatagramEndpoint`: one prefix add/remove, whole packet delivery from existing dispatcher, short/truncated packets, send-attempt notifications, four-packet admission/rejection, peek/zero-send retention, readiness rearming without spin, permanent-error head removal, concurrent reader/writer shutdown, lock-free JNI boundary, logical close without physical socket close and port-generation isolation.
- Native packet-factory tests: injected socket creation never falls back to OS binds; options/address reporting, ICE ufrag/tuple dispatch, duplicate logical sockets, JNI ref release, native network-thread delivery, late callbacks and packet metadata.
- `TestWebRtcTcpMediaServer`: VMED one-time acknowledgement, active/passive pairing, accepted-stream lifetime, native framing under partial I/O, rejection, bounded unauthenticated/provisional sockets and shutdown without competing stream readers.
- Port lifecycle tests: active call ends before main-port change, transport re-registers correctly (default→custom, custom→default, custom→custom), no media transport remains on old main port and ping/stats/file transfer behavior persists.

Use phase-2 tests for local queue semantics; phase-3 tests verify native mapping. Capture WR and VMED traffic, enumerate sockets, force UDP loss/disable, and run concurrent ping/stats/downloads. Record pending four-target evidence for phase 5.

### 11. Docs / Javadoc to update

Document WR/VMED framing, native/socket ownership, registration/port-generation lifecycle and verified API contracts. Write phase-3 summary with packet/socket proof and remaining shared-transport limitations. Preserve the prior exploration as historical context.


### Phase-1 evidence dependency

The [phase-1 implementation summary](../impl_summary/webrtc-phase-1-native-feasibility.md)
records shared TCP synthetic audio through VMED on Linux x86-64 / JDK 26, including
20-call teardown, DTLS/SRTP stats, TCP candidate selection and syscall evidence with
no native listener or Internet UDP socket. A patched JNI artifact remains necessary:
`PeerConnectionFactoryDependencies.packet_socket_factory` is used through modular
factory construction and bypassed if a peer supplies a custom port allocator.

Use native ICE-TCP framing after the Myster acknowledgement; retain accepted stream
ownership until closure. Keep blocking I/O off native callbacks, marshal notifications
on the network thread, and prevent OS-socket fallback (including native route probes).
Queue ICE signaling notifications instead of synchronous JNI calls across peers.
The prototype polls bounded Java stream buffers; its five-millisecond polling and
reflection-based transport object are diagnostic choices, not a production API.
UDP, IPv6 and the other desktop targets remain unproven. Phase 2 stays separate.
