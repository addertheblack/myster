# WebRTC phase 1: TCP audio feasibility

Status: Implemented and validated on Linux x86-64 / JDK 26. Shared TCP synthetic audio passes. See [implementation evidence](../impl_summary/webrtc-phase-1-native-feasibility.md) for proof and remaining target/toolchain limitations.

Sequence: Start here. Next: [phase 2](webrtc-phase-2-datagram-refactor.md).

Execute one phase at a time. This document owns this phase only; [the phase index](webrtc-video-call.md) is navigation, not an implementation plan.

## Design Section

### 1. Summary

Pin and build `webrtc-java`, then determine whether a small JNI/native extension can carry WebRTC audio through Myster’s existing TCP listener only. Produce experimental evidence and the contracts needed by later phases; do not refactor production datagram scheduling or build calling UI.

### 2. Non-goals

- UDP media, datagram framing/demultiplexing, packet-drop/backpressure work and the datagram reader/writer refactor.
- Camera enumeration/capture, video tracks, synthetic video and video rendering; those belong to [phase 4](webrtc-phase-4-basic-calling.md).
- Production call protocol, UI and device preferences.
- Extra inbound media ports as a substitute for shared transport.
- Completing four-target release packaging; identify toolchain risks now, finish in phase 5.

### 3. Assumptions & open questions

**Connectivity ownership:** Myster provides the connected TCP path, including reachability. Phase 1 only adapts that stream to native WebRTC; connectivity protocols and datagram transport are outside its scope.

Shared sockets are mandatory. The previous [exploration](../design/Myster_WebRTC_Voice_Design_Exploration.docx) defines the TCP handshake direction. Only that variant is investigated here; UDP feasibility belongs to phase 3. Native packet-factory injection is plausible but absent from the inspected Java API; exact hook location depends on the pinned native revision. Resolve source/artifact availability before using documented APIs.

Questions to answer experimentally: active/passive TCP candidates on the existing listener, native stream framing, partial reads/writes, callback and stream ownership, and feasibility of patched builds for Windows x86-64, Linux x86-64, macOS x86-64 and macOS ARM64. These are spike outcomes, not missing product decisions. Peer reachability is supplied by Myster.

### 4. Proposed design

#### Feasibility assessment and limitations

| Area | Evidence and limitation | MVP consequence |
|---|---|---|
| Desktop targets | Published setup documentation lists native classifiers for all four requested targets. JNI binaries and system libraries remain runtime dependencies. | Suitable candidate, subject to packaged Java 26 smoke checks on each target. |
| Audio | Native audio tracks and capture/playback are documented. | Validate synthetic audio delivery first; no camera or video requirements in this phase. |
| Shared networking | Native WebRTC has packet-factory injection; the inspected Java wrapper lacks the needed hook. Allocator flags do not hand over existing Myster sockets. | Highest-risk mandatory gate: JNI/native extension, virtual packet sockets, then real shared-port media. |
| Myster-provided connectivity | Myster owns peer reachability and traversal. The bridge must use its provided TCP path. | Validate media integration only; no connectivity-protocol or router tests. |
| Native lifecycle | JNI objects need explicit disposal; API guides and main source may differ. | Release-specific ownership audit plus repeated-call tests are a prerequisite. |

Evidence: [setup and target classifiers](https://jrtc.dev/guide/get-started), [audio device selection](https://jrtc.dev/guide/audio/audio-devices), [allocator configuration](https://jrtc.dev/guide/networking/port-allocator-config), [factory source on main](https://github.com/devopvoid/webrtc-java/blob/main/webrtc/src/main/java/dev/onvoid/webrtc/PeerConnectionFactory.java), and [device API source](https://github.com/devopvoid/webrtc-java/blob/main/webrtc/src/main/java/dev/onvoid/webrtc/media/MediaDevices.java). Main source is supporting evidence, not a guarantee about a released artifact.

**Assessment:** native WebRTC provides a plausible extension point, so proceed with a tightly scoped shared-transport spike. The stock Java dependency is insufficient evidence for the required architecture. Accepted TCP integration, candidate semantics and native stream ownership are the phase-1 feasibility gate. UDP is not tested or proven here; four-target completion belongs to phase 5.

Native evidence: [packet factory interface](https://webrtc.googlesource.com/src/+/refs/heads/main/api/packet_socket_factory.h), [peer/factory dependency definitions](https://chromium.googlesource.com/external/webrtc/+/refs/heads/main/api/peer_connection_interface.h), and [upstream injection change](https://webrtc.googlesource.com/src/+/905c3a6c73d293882ef11942066ccda52a9e14d1%5E%21/). These establish an upstream extension mechanism, not compatibility with a particular wrapper revision or proof of Myster integration.

The diagnostic is a separate `public static void main(String[] args)` under `src/test/java`, runnable from IntelliJ or a documented command in its own JVM. JUnit launches that same main class as a subprocess, applies a hard timeout, and checks its exit code/output; it does not duplicate the diagnostic implementation. SDP/ICE setup is passed in memory between endpoints in the local runner, while audio travels through real standard TCP sockets and the Myster section handshake. Use opt-in diagnostics in child JVMs with a hard timeout. First build unmodified native code and run an isolated audio-only ordinary-socket baseline to distinguish library failures from adapter failures. Then test an injected packet factory with synthetic audio, in-memory signaling and Myster framing. Use one audio track per peer and confirm received sample delivery; do not create video tracks or open cameras. Real-device audio can be a follow-up smoke check on available hardware, but hardware/UI work is not required to prove the transport. The baseline is diagnostic only; it is never a shipped fallback or shared-transport acceptance result.

Use accepted TCP streams via existing Myster connection sections in a diagnostic server fixture. Disable UDP gathering/transport in the experiment and prove media cannot escape onto a hidden native UDP path. Myster performs its section-selection handshake before native WebRTC considers the stream connected; WebRTC then owns its ICE-TCP framing and media security. No datagram queue or sender API changes are needed for this phase.

### 5. Architecture connections

The experimental factory converts native packet operations into Java bridge operations; Java performs the one-time TCP section handshake and bridges the resulting stream to native WebRTC on its required threads. Native ICE, DTLS and SRTP remain responsible for validation and media security.

| New / changed thing | Owned / created by | Called / used by | Connects to (existing) |
|---|---|---|---|
| Pinned source and native patch | Diagnostic build scripts | Child-JVM experiment | Wrapper JNI and its exact WebRTC revision |
| Diagnostic TCP handoff | Test server fixture | Active/passive native sockets | `ConnectionSection`, `ConnectionRunnable`, `MysterSocket` |
| Evidence/report | Diagnostic runner | Next-phase author | Source lock, captured packet/socket inventory |

Provisional TCP media section `0x564D4544` (VMED): int32 selector, one-byte acceptance, then the pinned native ICE-TCP framing; no application per-packet prefix. Collision-check before use. Test real active/passive TCP candidate association rather than rewriting SDP ports. Preserve stream byte order and handle partial reads/writes correctly.

### 6. Key decisions & edge cases

An accepted media section retains stream ownership until bridge closure so `ConnectionRunnable` cannot become a competing reader. The active side reports connected only after the Myster acknowledgement. Retain bounded unsent TCP bytes on a partial write; do not apply UDP packet-discard policies to a reliable byte stream. Keep blocking Java stream I/O off native callback threads and document cancellation/unblocking on close.

Inspect the pinned native TCP socket factory, ICE-TCP framing and callback ownership. Do not assume a Java stream is directly injectable or a native file descriptor is available. Use a minimal JNI/native extension if necessary. TCP avoids UDP packet-drop handling, but stream backpressure, framing and lifetime still need proof.

Use only the Myster-provided TCP connection. Keep UDP transport disabled and native media encryption enabled. No new native TCP listener may substitute for Myster's existing listener. Distinguish build/tooling failures from transport incompatibility. Native transport hooks: [packet factory interface](https://webrtc.googlesource.com/src/+/refs/heads/main/api/packet_socket_factory.h).

### 7. Acceptance criteria

- [x] Wrapper release, source commit and underlying WebRTC commit are pinned and the current-host unmodified build works, or a concrete build blocker is recorded.
- [x] The Java/native injection hook is demonstrated against that exact revision.
- [x] Synthetic audio passes through VMED and active/passive native ICE-TCP on the existing listener with UDP disabled, or its specific blocker is documented.
- [x] Socket/traffic evidence shows no UDP media or additional native TCP listener; partial I/O, stream ownership and closure are verified.
- [x] TCP framing/backpressure/thread/ownership findings and four-target toolchain risks are recorded.
- [x] No production datagram thread/queue refactor or call UI is included.
- [x] Summary gives an explicit go/no-go/conditional outcome. A negative outcome completes the investigation, not the overall feature; no claim of feasibility without evidence.

---
## ✦ IMPLEMENTATION DETAILS (for the implementation agent)
---

### 8. Affected files / classes

- New `native/webrtc/README.md`, `native/webrtc/source-lock.json`: exact revisions, build prerequisites and diagnostic commands.
- New `native/webrtc/patches/myster-packet-sockets.patch`: minimal native/JNI factory injection and diagnostic bridge; upstream file names resolved from pinned source.
- New `native/webrtc/build-linux.sh`: reproduce the current-host patched artifact; identify other target toolchains without requiring their final scripts in this phase.
- New `src/test/java/com/myster/call/media/WebRtcDiagnostic.java`: child-JVM load/media runner.
- New `src/test/java/com/myster/call/transport/DiagnosticTcpBridge.java` and `DiagnosticTcpMediaSection.java`: opt-in experiment fixtures.
- `pom.xml`: diagnostic-only Maven profile using matched Java/JNI artifacts; no startup/runtime integration.
- `docs/impl_summary/webrtc-phase-1-native-feasibility.md`: evidence, outcome and next-phase contracts.

Do not edit production `AsyncDatagramSocket`, menu, startup or server-port lifecycle in this phase.

### 9. Step-by-step implementation

1. Read the DOCX and repository transport contracts. Pin actual stable artifacts and source revisions; map `PeerConnectionFactory.initialize`/peer creation to the native dependency injection point. Store real hashes, not guessed values.
2. Build unmodified JNI code on the current host and run isolated native-load, synthetic audio and teardown diagnostics. Record JDK/OS/architecture and prerequisites.
3. Add minimal custom packet-factory injection, bridge handles and explicit JNI reference/thread ownership. Unsupported operations fail explicitly rather than opening native OS sockets.
4. Expose diagnostic active/passive TCP sockets through the injected factory. The active side connects to Myster's existing listener, writes VMED and waits for acknowledgement. A diagnostic section hands the accepted Java stream to the passive native adapter and retains ownership until closure. Use the pinned native ICE-TCP framing; preserve partial reads/writes and native callback affinity.
5. Exchange SDP/ICE through in-memory diagnostic signaling, explicitly disable UDP, and verify synthetic audio in both directions. Do not build the user-facing signaling/UI in this phase.
6. Test split reads/writes, slow readers, handshake refusal, unexpected close and repeated teardown in child JVMs. Capture socket/traffic evidence showing only the existing TCP listener and no UDP media.
7. Identify four-target build dependencies and availability. Write explicit go/no-go/conditional TCP evidence and update preliminary phase-3 TCP contracts. UDP remains unproven and phase 2 stays a separate datagram refactor. Stop after phase 1.

### 10. Tests to write

- Child-JVM native-load and unmodified-media baseline, with timeout and redacted crash output.
- Injection tests for no OS-socket fallback, local endpoint reporting, native-thread callback delivery, send-attempt metadata and JNI reference disposal.
- TCP-only synthetic audio, active/passive candidate pairing, partial I/O/framing, slow-reader backpressure, accepted-stream lifetime and no extra listener or UDP media.
- Repeat at least 20 diagnostic sessions on available current-host tooling; no claim of real-device or all-target support from synthetic tests.
- Record unavailable packet-capture tools or target hosts as limitations. Do not run broad application regression suites for diagnostic-only changes.

### 11. Docs / Javadoc to update

Document the diagnostic bridge’s callback/thread/handle ownership and exact native revision contracts. Write the phase-1 summary with reproducible commands, packet/socket evidence, TCP findings, explicit unproven UDP status, and contracts needed by phase 3. Do not assign datagram work to this phase. Preserve the historical DOCX. Later phases stay preliminary until reviewed against this evidence.

