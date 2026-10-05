# WebRTC phase 4: Basic calling capability

Status: PRELIMINARY — not ready to execute. Refresh against completed earlier phases before implementation.

Sequence: Requires [phase 3](webrtc-phase-3-shared-transport.md). Next: [phase 5](webrtc-phase-5-release-validation.md).

Execute one phase at a time. This document owns this phase only; [the phase index](webrtc-video-call.md) is navigation, not an implementation plan.

## Design Section

### 1. Summary

Add one-to-one calls to another selected Myster server using the media engine and shared transport proven in prior phases. Use default microphone and speaker devices and the first available camera, an incoming Answer/Decline dialog, and a call window showing remote live video with a large Hang Up button. Myster owns signaling, peer identity, the existing UDP socket and TCP listener; WebRTC owns media processing and its normal ICE/DTLS/SRTP logic behind a Myster-aware native socket adapter. Supported targets are Windows x86-64, Linux x86-64, macOS x86-64 and macOS ARM64.

### 2. Non-goals

- Group meetings, communities, channel membership, SFU forwarding or audio mixing.
- Screen sharing, recording, virtual backgrounds, remote control and chat integration.
- Device settings UI, preview tiles, speaking indicators and hotplug migration.
- Linux ARM and Windows ARM support.
- Automatic reconnection, ICE restart, renegotiation or multiple simultaneous calls.
- Operating a public TURN service or introducing a commercial conferencing dependency.
- Additional externally reachable media ports or silently substituting ordinary WebRTC sockets.
- Full TURN interoperability, a generic relay service or guaranteed reachability between two arbitrary NATs in this first milestone.

### 3. Assumptions & open questions

PRELIMINARY: requires phase-3 shared UDP/TCP media proof and stable lifecycle APIs. Recheck proposed files and symbols before execution.

A selected server is the person's Myster installation, not a conference relay. One pending/active call per installation. Use OS-default audio devices and first available camera because the inspected API lacks a default-camera accessor. Missing camera permits audio-only; missing audio prevents setup. No capture before acceptance. Require public-key-backed selected identity and pinned TLS signaling; an unknown caller's key proves possession, not human trust.

### 4. Proposed design

#### User flow

The File menu adds “New Video Call…”. It opens the existing server picker with a Call button. After selection, the caller sees the call window with the remote server name, “Calling…” and a large enabled Hang Up button. A dedicated TLS signaling connection requests the call from that server.

The receiving desktop reserves its call slot and displays a modeless incoming-call dialog with Answer and Decline. It shows a resolved caller name when available and a shortened caller CID, labeling an unresolved caller as unknown. Caller-supplied display text never establishes identity. Closing the dialog declines. Ringing lasts at most 30 seconds and cancellation dismisses the dialog immediately. No microphone or camera capture begins during ringing.

After Answer, both peers open their media devices and exchange SDP and ICE through the same connection. The caller always creates the offer and the receiver creates the answer. Each call window shows remote video, a Connecting/Connected/error status, and Hang Up. Audio-only peers show a placeholder. Resizing preserves video aspect ratio and rotation. Closing the call window hangs up.

Hangup, decline, timeout, signaling loss or setup failure releases the single slot and devices. Show a terminal status in the call window, replace Hang Up with Close, and permit another call without restarting Myster.

### 5. Architecture connections

Myster startup owns the call service and injects the phase-3 adapter, signaling handler and UI presentation bridge. The existing server picker chooses a target; a dedicated pinned TLS connection owns one call's setup/liveness.

| New / changed thing | Owned / created by | Called / used by | Connects to (existing) |
|---|---|---|---|
| Call service | `Myster` startup | Menu, signaling, media callbacks | Phase-3 media/lifecycle API and shutdown listeners |
| Incoming call section | `ServerFacade` | Remote caller | TLS `ConnectionContext`, stream handler |
| Selection action | Menu bar | User | `ServerPickerDialog`, `KnownServerSource`, `PublicKeyIdentity` |
| Call window/dialog | UI controller | Service presentation snapshots | `MysterFrameContext`, EDT/window management |

#### Signaling protocol v1

Reserve TCP section `0x5643414C` (“VCAL”), subject to a fresh collision check at implementation time. The caller first performs existing STLS negotiation, then selects VCAL; `ServerStreamHandler` writes the normal one-byte section acknowledgement. Unknown-section rejection from an older peer becomes “This server does not support calls.” Section acknowledgement does not mean the person has answered.

After acknowledgement, each direction carries length-prefixed MessagePaks, using existing data-stream framing. One dedicated connection owns one call for its entire lifetime. Close it on terminal state rather than returning it to a connection pool or attempting another Myster section. Never mix media bytes into this connection.

Every frame contains `/version` (integer 1), `/callId` (16 random UUID bytes) and `/kind` (string). Require an authenticated incoming connection before presenting an invitation. The first frame is INVITE from caller; its call ID remains fixed. All subsequent frames must match that ID and version. Reject unknown kinds and invalid ordering rather than attempting partial interoperability.

| Kind | Sender / meaning | Additional fields |
|---|---|---|
| INVITE | Caller requests a call | No SDP or device capture |
| RINGING | Receiver confirms dialog is being shown | None |
| ACCEPT | Receiver authorizes media setup | None |
| DECLINE | Receiver ends invitation | `/reason`: declined, busy, unavailable or unsupported-version |
| OFFER | Caller sends one offer after acceptance | `/sdp`: UTF-8 string |
| ANSWER | Receiver sends one answer | `/sdp`: UTF-8 string |
| ICE | Either peer sends trickled candidate after its SDP has been enqueued | `/candidate`: string, `/sdpMid`: string, `/sdpMLineIndex`: nonnegative integer |
| ICE_COMPLETE | Either peer finishes candidate gathering | None |
| HANGUP | Either peer ends pending or active call | `/reason`: cancelled, ended, timeout or failed |
| PING / PONG | Connection liveness | `/nonce`: integer echoed by PONG |

Maximum frame size is 256 KiB before allocation; SDP is at most 128 KiB UTF-8, candidate at most 8 KiB, mid at most 256 bytes and kind/reason at most 64 bytes. Limit ICE to 256 candidates per sender per call, with at most 256 buffered while remote-description application is pending. Limit outgoing queued frames to 512 and 2 MiB total; overflow terminates the call. Duplicate SDP or acceptance, candidate overflow, malformed UUID, oversized frame or wrong call ID terminates that connection. Unknown optional fields are ignored; unknown mandatory values fail.

Send a PING every 10 seconds; terminate after 30 seconds without any received frame or an unanswered ping deadline. Receive-side heartbeat handling continues while the user is deciding. Setup must connect within 30 seconds after ACCEPT. Use monotonic time for deadlines. Serialize output through one writer so native callbacks, heartbeat and hangup cannot interleave frames. Stop the writer and close the socket on terminal transitions; never wait for a remote acknowledgement to release local devices.

### 6. Key decisions & edge cases

- Model states explicitly: IDLE, OUTGOING_RINGING, INCOMING_RINGING, CONNECTING, CONNECTED and ENDING. Terminal outcomes carry a reason but release the active slot only after native teardown completes. All state transitions run on a per-service serial platform executor; blocking socket I/O runs separately. Native callbacks enqueue immutable events tagged with session generation. EDT receives presentation snapshots only.
- Reserve a call slot before queuing an incoming dialog. Headless/uninitialized/unsupported installations answer unavailable; a busy installation creates no extra dialog. Both sides reject self-call by CID. The receiver enforces 30 seconds from INVITE; the caller starts its deadline after INVITE is sent. Late Answer after cancellation does nothing.
- Before sending, require a public-key-backed server identity and usable address. Revalidate when the call starts. Address-only servers are ineligible with a clear explanation. Pin the selected key; no fallback to unpinned TLS or plaintext. Discovered keys are only as trustworthy as their discovery source; do not imply universal verified human identities.
- Hardware creation occurs only after ACCEPT. Use OS-default capture/render devices; select the first available camera. Prefer a camera-supported mode up to 640×480 at 30 fps, falling back to another supported mode with the smallest practical size. Negotiate ordinary compatible codecs; prefer Opus audio and VP8 video when supported. No mandatory H.264 or hardware-acceleration assumption.
- Camera failure is audio-only, including camera removal midcall. Audio capture/playback failure or removal ends the call with a device error. No automatic device switching this milestone.
- Maintain the native ownership contract of the pinned release, not blindly copied guide snippets. Do not retain native frame references on the EDT. Convert on a bounded platform worker; replace pending frames so slow rendering cannot accumulate native buffers. Apply rotation and pixel-channel conversion explicitly. Release every owned retained reference on replacement, exception and shutdown.
- Keep native library initialization lazy and outside general startup classes. Missing binaries/dependencies produce an unavailable call feature while file sharing and other Myster functions keep working. A native crash cannot be caught as a Java exception; diagnostic tests run in a subprocess.
- Disable external STUN/TURN in the first shared-socket adapter. Host/peer-reflexive ICE and native ICE-TCP are validated explicitly. Test an Internet caller against a known reachable Myster server on its existing configured port; symmetric-NAT and standard-relay reachability remain outside the demonstrated guarantee.
- Myster TCP media fallback must be demonstrated through VMED and native ICE-TCP framing. Port ranges and WebRTC shared-socket flags do not imply access to Myster's existing socket. Keep UDP and TCP media enabled only when their respective bridge gates pass; do not fall back to ordinary native sockets.
- When `ServerFacade.changePort` runs, end pending/active calls before rebinding, unregister the old media transport and register the new main-port transport. Do not continue advertising candidates for the old port or migrate live ICE silently. Register through an explicit lifecycle hook, never leak the `TransportManager` outside `mutateTransportManager`.
- Native packaged artifacts must match architecture. Linux requires documented runtime libraries and real audio/camera access; Wayland desktop capture is a later gate. macOS bundles need camera/microphone usage descriptions and applicable signing permissions; test actual distributed bundles, not just IDE execution. The existing native-access manifest and jpackage option already exist and should be preserved.
- Do not log raw SDP, ICE candidates or TURN secrets. Diagnostics record release/target, phase, counters, coarse connection state and redacted failures.

### 7. Acceptance criteria

- [ ] Updated phase-4 plan consumes verified phase-3 adapter APIs.
- [ ] Existing picker selects a callable server; incoming modeless Answer/Decline appears once.
- [ ] No capture before acceptance; cancel/decline/timeout/busy do not open devices.
- [ ] Default audio and first available camera deliver two-way audible audio/live remote video over shared media transport.
- [ ] Audio-only placeholder, aspect/rotation, large Hang Up and close-as-hangup work.
- [ ] Key mismatch/plaintext/old peer/device denial/media failure yield clear outcomes.
- [ ] Repeated calls release hardware and reject stale callbacks; port change/shutdown ends calls.
- [ ] Available-host integration evidence passes; all-target release completion belongs to phase 5.

---
## ✦ IMPLEMENTATION DETAILS (for the implementation agent)
---

### 8. Affected files / classes

- `src/main/java/com/myster/Myster.java`: construct and inject service, register signaling handler, attach presentation bridge, shutdown cleanup.
- `src/main/java/com/myster/ui/menubar/MysterMenuBar.java`: inject call service into `initMenuBar` and add New Video Call action; update its callers/test fixtures.
- New `src/main/java/com/myster/ui/menubar/event/NewVideoCallAction.java`: existing picker flow, eligibility, captured target and service request.
- New `src/main/java/com/myster/call/CallService.java`: single-slot state machine, timers, dependency-injected media/signaling factories and listener registration.
- New `src/main/java/com/myster/call/CallSession.java`: session identity, direction, owned resources, generation and terminal reason.
- New `src/main/java/com/myster/call/CallListener.java`: immutable state presentation and incoming invitation events.
- New `src/main/java/com/myster/call/CallMedia.java`: native-independent media interface and callback contract for fake-based testing.
- New `src/main/java/com/myster/call/CallSignalingConnection.java`: bounded reader/writer, socket ownership, heartbeat and disconnect notification.
- New `src/main/java/com/myster/call/CallProtocolUtils.java`: frame records/codec and VCAL constants.
- New `src/main/java/com/myster/call/CallStreamServer.java`: `ServerStreamHandler`, verified caller context, service attachment and long-lived signaling section.
- New `src/main/java/com/myster/call/media/WebRtcCallMedia.java`: release-specific native device/session adapter, tracks, callback bridge and teardown.
- New `src/main/java/com/myster/call/media/WebRtcPlatform.java`: target classifier validation and lazy native readiness diagnostic.
- New `src/main/java/com/myster/call/ui/CallUiController.java`: EDT presentation bridge and mapping from session ID to window/dialog.
- New `src/main/java/com/myster/call/ui/CallWindow.java`: Myster-frame-compatible call window, status, video panel and Hang Up/Close action.
- New `src/main/java/com/myster/call/ui/IncomingCallDialog.java`: modeless Answer/Decline dialog tied to one session generation.
- New `src/main/java/com/myster/call/ui/RemoteVideoPanel.java`: paints owned Java pixels with bounded latest-frame handoff.
- New tests under `src/test/java/com/myster/call/` and `src/test/java/com/myster/call/ui/`.
- `docs/impl_summary/webrtc-phase-4-basic-calling.md`: call behavior and available-device evidence.

Refresh these proposed names against phase-3 native/media code. Do not duplicate its transport bridge or phase-2 socket refactor.

### 9. Step-by-step implementation

1. **Real media gate.** Extend `WebRtcDiagnostic` to use default audio, first available camera and camera capability selection over the shared bridge. Audit all returned native objects and callback frame ownership. Verify actual two-machine capture/playback and cleanup on available hardware before creating full application UI. No forked native API can be assumed compatible with upstream Maven binaries.

2. **Call wire codec.** Implement `CallProtocolUtils` VCAL framing and schema above; collision-check WR/VCAL/VMED against source and plans. `CallSignalingConnection` has distinct reader/writer workers, bounded output, monotonic heartbeat/deadlines and idempotent close. Only dedicated TLS signaling is pooled into call lifetime; close rather than reuse it for another section.

3. **Service state.** Implement `CallService` single-slot states with injected clock, scheduler, signaling/media factories and serial platform executor. Provide `startCall(target)`, `accept(callId)`, `decline(callId)`, `hangUp(callId)` and `close()` with asynchronous completion/ownership contracts. Reserve before incoming presentation, disable capture before acceptance, and keep the slot until native teardown completes. Tag callbacks with generation; blocking network I/O never runs on the service executor or EDT.

4. **SDP/ICE adapter.** Implement `WebRtcCallMedia` against patched release symbols, caller-only offer and receiver-only answer. Queue local SDP before local candidates, buffer incoming candidates until successful remote-description application, and forbid renegotiation. Share only approved host/peer-reflexive/TCP candidates. Use default capture/playback devices, echo cancellation/noise suppression/AGC, optional first camera, up to 640×480 at 30 fps when supported. Prefer compatible Opus/VP8 rather than requiring hardware/H.264. Camera failure permits audio-only; audio failure ends setup/call.

5. **Rendering and cleanup.** At most one pending retained native frame, one conversion and one published Java image; drop/release replacements and coalesce repaint. Verify rotation, channel order, maximum 3840×2160 bounds and immutable painting ownership. On teardown detach callbacks, stop capture, drain/drop owned media packets/frames, dispose per-release native resources and unregister only owned logical endpoints. Never close the shared physical socket merely because a call ended.

6. **Signaling and startup.** `CallStreamServer` requires authenticated key/CID, bounded INVITE and valid ordering. Outgoing VCAL uses `MysterSocketFactory.makeStreamConnection(address, selectedKey)` with pinned key. In `Myster`, inject service/transport lifecycle before server startup, register VCAL/VMED, shutdown listener and unavailable-until-UI-ready behavior. On `ServerFacade.changePort`, terminate calls before moving registration, wait for old bridge shutdown and advertise only the new ready endpoint. No leaked old-port media transport.

7. **Requested UI.** Add `NewVideoCallAction` following the existing picker action; address/public-key eligibility and self-call rejection. Inject service through `MysterMenuBar.initMenuBar`. Build modeless Answer/Decline dialog, close-as-decline and late-click suppression. `CallWindow` shows remote video or placeholder, status and large theme-aware Hang Up; close hangs up. UI bridge receives immutable presentation snapshots on EDT and ignores stale generations.

8. Run targeted call tests and available-host real-device smoke tests. Update phase 5 with package/test prerequisites and stop after this phase.

### 10. Tests to write

- `TestCallProtocolUtils`: every message round trip; frame allocation limits, byte-size SDP/candidate bounds, missing/wrong fields, unsupported versions, unknown kind, UUID length, invalid candidate index and optional-field handling.
- `TestCallService`: fake media/signaling and deterministic clock; accept-before-capture ordering, decline/cancel/timeout races, one-call reservation, crossed calls, self-call, setup failure, permission failure, audio-only camera failure, repeated idempotent hangup, shutdown during initialization, late native events and cleanup-before-slot-release.
- `TestCallSignalingConnection`: duplex loopback/fake sockets; no frame interleaving, heartbeat through ringing/connected states, EOF/half-open timeout, blocked-writer close, queue overflow, unmatched call ID, invalid state order, candidate bounds and unknown-section failure.
- `TestCallStreamServer`: authenticated invitation reaches presentation exactly once; anonymous connection fails, CID/key mismatch fails, unavailable/busy returns quickly without hardware creation. Exercise real TLS pin success/failure with test identities if available, rather than mocking away the security contract.
- `TestRemoteVideoPanel`: synthetic Java images prove rotation, aspect ratio and pixel-channel order; fake owned-frame adapter proves replacement/close releases once and coalesced repaint does not leak native references. Avoid requiring the library in ordinary unit tests.
- `TestIncomingCallDialog`/action tests: Answer, Decline, close, remote cancellation, picker cancellation and disabled address-only targets. GUI cases may require a display-enabled test environment; separate them from headless state tests.

Manual: make/answer/decline/cancel calls, disconnect during ringing/negotiation, deny camera/audio access, exercise busy and unknown peer, close windows, reopen hardware and repeat 20 calls on available targets. Synthetic media alone does not establish audible audio or usable video.

### 11. Docs / Javadoc to update

Document call-service states/ownership/cancellation, VCAL wire fields/limits/deadlines, TLS identity binding, default device policy, renderer frame lifetimes and UI close semantics. Write phase-4 summary; update current design documentation without claiming group relay, screen sharing or arbitrary-NAT support.

