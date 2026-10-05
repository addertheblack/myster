# WebRTC phase 1: Native feasibility

Status: Ready for phase-1 implementation. Feasibility is unproven; this plan defines the experiments and required evidence.

Sequence: Start here. Next: [phase 2](webrtc-phase-2-datagram-refactor.md).

Execute one phase at a time. This document owns this phase only; [the phase index](webrtc-video-call.md) is navigation, not an implementation plan.

## Design Section

### 1. Summary

Pin and build `webrtc-java`, then determine whether a small JNI/native extension can carry WebRTC media through Myster’s existing UDP multiplexer and TCP listener. Produce experimental evidence and the contracts needed by later phases; do not refactor production datagram scheduling or build calling UI.

### 2. Non-goals

- Production reader/writer split, queue redesign or backpressure rollout.
- Production call protocol, UI, device preferences, group relay and screen sharing.
- Extra inbound media ports as a substitute for shared transport.
- Completing four-target release packaging; identify toolchain risks now, finish in phase 5.

### 3. Assumptions & open questions

Shared sockets are mandatory. The previous [exploration](../design/Myster_WebRTC_Voice_Design_Exploration.docx) defines the UDP prefix and TCP handshake direction. Native packet-factory injection is plausible but absent from the inspected Java API; exact hook location depends on the pinned native revision. Resolve source/artifact availability before using documented APIs.

Questions to answer experimentally: correct virtual local addresses, logical ICE-port demultiplexing, active/passive TCP candidates on the existing listener, native callback/ownership/send-attempt semantics, and feasibility of patched builds for Windows x86-64, Linux x86-64, macOS x86-64 and macOS ARM64. These are spike outcomes, not missing product decisions. General NAT/TURN support and SFU routing remain unproven.

### 4. Proposed design

#### Feasibility assessment and limitations

| Area | Evidence and limitation | MVP consequence |
|---|---|---|
| Desktop targets | Published setup documentation lists native classifiers for all four requested targets. JNI binaries and system libraries remain runtime dependencies. | Suitable candidate, subject to packaged Java 26 smoke checks on each target. |
| Basic media | Camera tracks, microphone capture, playback and audio processing are documented. | No identified API-level blocker for ordinary two-endpoint calls; test actual media before building the full UI. |
| Swing rendering | Remote frames are delivered to a sink; pixel conversion helpers exist. The library supplies no Swing call widget. | Myster builds bounded frame conversion and rendering with correct native ownership. |
| Camera default | Audio default accessors exist; inspected `MediaDevices` exposes camera enumeration, not an OS-default-camera getter. | First available camera is the explicit MVP policy. |
| Shared networking | Native WebRTC has packet-factory injection; the inspected Java wrapper lacks the needed hook. Allocator flags do not hand over existing Myster sockets. | Highest-risk mandatory gate: JNI/native extension, virtual packet sockets, then real shared-port media. |
| NAT/firewall traversal | A prefixed STUN/TURN packet is not a standard STUN/TURN packet. Shared ports alone do not solve NAT traversal; TCP and UDP permissions differ. | LAN first; explicit Myster TCP media fallback second; reachable-server NAT tests third. Standard TURN requires a separate adapter design. |
| Group relay | Encoded transforms expose per-frame operations but do not establish an SFU's RTP routing, feedback, retransmission or subscription machinery. | Do not claim that this prototype validates the earlier server-relay design. |
| Future screen sharing | Desktop capture is documented; usable capture and permissions on each platform still require tests. Wayland support is not established by the sources inspected. | Deferred; camera calling must not be marketed as proof of screen-sharing support. |
| Native lifecycle | JNI objects need explicit disposal; API guides and main source may differ. | Release-specific ownership audit plus repeated-call tests are a prerequisite. |

Evidence: [setup and target classifiers](https://jrtc.dev/guide/get-started), [audio device selection](https://jrtc.dev/guide/audio/audio-devices), [camera capture](https://jrtc.dev/guide/video/camera-capture), [pixel conversion](https://jrtc.dev/tools/video/video-buffer-converter), [allocator configuration](https://jrtc.dev/guide/networking/port-allocator-config), [factory source on main](https://github.com/devopvoid/webrtc-java/blob/main/webrtc/src/main/java/dev/onvoid/webrtc/PeerConnectionFactory.java), [TURN guidance](https://webrtc.org/getting-started/turn-server), [encoded transforms](https://jrtc.dev/guide/advanced/encoded-transforms), [desktop capture](https://jrtc.dev/guide/video/desktop-capture), and [device API source](https://github.com/devopvoid/webrtc-java/blob/main/webrtc/src/main/java/dev/onvoid/webrtc/media/MediaDevices.java). Main source is supporting evidence, not a guarantee about a released artifact.

**Assessment:** native WebRTC provides a plausible extension point, so proceed with a tightly scoped shared-transport spike. The stock Java dependency is insufficient evidence for the required architecture. Shared UDP, accepted TCP integration, candidate semantics and four-target native builds must pass before call UI implementation. An efficient central SFU is still a separate unresolved feasibility question.

Native evidence: [packet factory interface](https://webrtc.googlesource.com/src/+/refs/heads/main/api/packet_socket_factory.h), [peer/factory dependency definitions](https://chromium.googlesource.com/external/webrtc/+/refs/heads/main/api/peer_connection_interface.h), and [upstream injection change](https://webrtc.googlesource.com/src/+/905c3a6c73d293882ef11942066ccda52a9e14d1%5E%21/). These establish an upstream extension mechanism, not compatibility with a particular wrapper revision or proof of Myster integration.

Use opt-in diagnostics in child JVMs with a hard timeout. First build unmodified native code and run an isolated ordinary-socket baseline to distinguish library failures from adapter failures. Then test an injected packet factory with synthetic audio/video, in-memory signaling and Myster framing. The baseline is diagnostic only; it is never a shipped fallback or shared-transport acceptance result.

Attempt real shared-UDP dispatch via `DatagramProtocolManager`, and accepted TCP streams via existing connection sections in a diagnostic server fixture. Do not change `AsyncDatagramSocket` production queue/thread behavior. Its fire-and-forget sender may prevent accurate send-result validation: record that limitation for phase 2. A diagnostic-only harness may expose send results to test native semantics, but explicitly distinguish that evidence from existing-production-stack proof. If substantial production work is necessary to continue, record the dependency rather than widening phase 1.

### 5. Architecture connections

The experimental factory converts native packet operations into Java bridge operations; Java adds/removes Myster framing and returns packets on the native network thread. Native ICE, DTLS and SRTP remain responsible for validation and media security.

| New / changed thing | Owned / created by | Called / used by | Connects to (existing) |
|---|---|---|---|
| Pinned source and native patch | Diagnostic build scripts | Child-JVM experiment | Wrapper JNI and its exact WebRTC revision |
| Diagnostic packet bridge | Test fixture | Injected native sockets | `DatagramProtocolManager`, `DatagramTransport` |
| Diagnostic TCP handoff | Test server fixture | Active/passive native sockets | `ConnectionSection`, `ConnectionRunnable`, `MysterSocket` |
| Evidence/report | Diagnostic runner | Next-phase author | Source lock, captured packet/socket inventory |

Provisional UDP code `0x5752` (WR): uint16 network-order discriminator followed by unchanged ICE/STUN/DTLS/SRTP/SRTCP datagram. Existing dispatch passes the prefix intact; strip exactly two bytes in the bridge. Preserve source tuple and boundaries. Provisional TCP media section `0x564D4544` (VMED): int32 selector, one-byte acceptance, then the pinned native ICE-TCP framing; no application per-packet prefix. Collision-check both before use. Test real logical ICE candidate association rather than rewriting SDP ports.

### 6. Key decisions & edge cases

No JNI work under the transport-manager lock; do not let `TransportManager` escape its access callback. Closing a virtual UDP endpoint must not close the shared socket. An accepted media section must retain stream ownership until bridge closure so `ConnectionRunnable` cannot become a competing reader.

Inspect the exact native send return/error, sent-packet and ready-to-send contract. Some upstream adapters emit sent-attempt events even on failure; do not impose success-only semantics. A later queue drop cannot retroactively change a synchronous result. Document findings for phase 2 without implementing a production queue redesign.

Disable external STUN/TURN initially: WR-prefixed packets are incompatible with standard services. Prove IPv4 first; report IPv6 status. Keep encryption enabled. No hidden native sockets, SO_REUSEPORT trick or extra listener can count as shared-port success. Distinguish missing tooling/target machines from architectural failure.

### 7. Acceptance criteria

- [ ] Wrapper release, source commit and underlying WebRTC commit are pinned and the current-host unmodified build works, or a concrete build blocker is recorded.
- [ ] The Java/native injection hook is demonstrated against that exact revision.
- [ ] Shared-port UDP ICE/DTLS/synthetic media is demonstrated with packet and socket evidence, or a reproducible blocker and next dependency are documented.
- [ ] Accepted TCP/active-passive ICE-TCP media is demonstrated with UDP disabled, or its specific blocker is documented.
- [ ] Send-error/readiness/thread/ownership findings and four-target toolchain risks are recorded.
- [ ] No production datagram thread/queue refactor or call UI is included.
- [ ] Summary gives an explicit go/no-go/conditional outcome. A negative outcome completes the investigation, not the overall feature; no claim of feasibility without evidence.

---
## ✦ IMPLEMENTATION DETAILS (for the implementation agent)
---

### 8. Affected files / classes

- New `native/webrtc/README.md`, `native/webrtc/source-lock.json`: exact revisions, build prerequisites and diagnostic commands.
- New `native/webrtc/patches/myster-packet-sockets.patch`: minimal native/JNI factory injection and diagnostic bridge; upstream file names resolved from pinned source.
- New `native/webrtc/build-linux.sh`: reproduce the current-host patched artifact; identify other target toolchains without requiring their final scripts in this phase.
- New `src/test/java/com/myster/call/media/WebRtcDiagnostic.java`: child-JVM load/media runner.
- New `src/test/java/com/myster/call/transport/DiagnosticPacketBridge.java` and `DiagnosticTcpMediaSection.java`: opt-in experiment fixtures.
- `pom.xml`: diagnostic-only Maven profile using matched Java/JNI artifacts; no startup/runtime integration.
- `docs/impl_summary/webrtc-phase-1-native-feasibility.md`: evidence, outcome and next-phase contracts.

Do not edit production `AsyncDatagramSocket`, menu, startup or server-port lifecycle in this phase.

### 9. Step-by-step implementation

1. Read the DOCX and repository transport contracts. Pin actual stable artifacts and source revisions; map `PeerConnectionFactory.initialize`/peer creation to the native dependency injection point. Store real hashes, not guessed values.
2. Build unmodified JNI code on the current host and run isolated native-load, synthetic media and teardown diagnostics. Record JDK/OS/architecture and prerequisites.
3. Add minimal custom packet-factory injection, bridge handles and explicit JNI reference/thread ownership. Unsupported operations fail explicitly rather than opening native OS sockets.
4. Route diagnostic peer packets through WR prefix/demux, report correct local candidate addresses and test ufrag/nominated-tuple association. Try the existing Myster UDP manager first. Where its missing send-result API prevents proof, use a clearly labeled diagnostic-only sender and record what production phase 2 must supply.
5. Exercise VMED selection and passive accepted stream ownership using a diagnostic server fixture; disable UDP and prove native framed media over active/passive ICE-TCP without a new listener. Probe partial I/O, reject/close and callback lifetimes.
6. Run child-JVM timeouts, packet/socket inspection, synthetic media counters and repeated teardown. Report any ordinary native binds that invalidate the shared-port result.
7. Identify four-target build dependencies and availability. Produce explicit go/no-go/conditional evidence and update the preliminary phase-2/3 documents with discovered contracts only; stop after phase 1.

### 10. Tests to write

- Child-JVM native-load and unmodified-media baseline, with timeout and redacted crash output.
- Injection tests for no OS-socket fallback, local endpoint reporting, native-thread callback delivery, send-attempt metadata and JNI reference disposal.
- Diagnostic UDP prefix round trip, multiple logical ICE ports, candidate nomination, packet/socket capture and teardown.
- Forced TCP synthetic media, native framing under partial I/O, accepted-stream lifetime and no extra listener.
- Repeat at least 20 diagnostic sessions on available current-host tooling; no claim of real-device or all-target support from synthetic tests.
- Record unavailable packet-capture tools or target hosts as limitations. Do not run broad application regression suites for diagnostic-only changes.

### 11. Docs / Javadoc to update

Document the diagnostic bridge’s callback/thread/handle ownership and exact native revision contracts. Write the phase-1 summary with reproducible commands, packet/socket evidence, what remains unproven, and scope required for phases 2/3. Preserve the historical DOCX. Later phases stay preliminary until reviewed against this evidence.

