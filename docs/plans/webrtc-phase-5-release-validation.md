# WebRTC phase 5: Target and release validation

Status: PRELIMINARY — not ready to execute. Refresh against completed earlier phases before implementation.

Sequence: Requires [phase 4](webrtc-phase-4-basic-calling.md).

Execute one phase at a time. This document owns this phase only; [the phase index](webrtc-video-call.md) is navigation, not an implementation plan.

## Design Section

### 1. Summary

Finish reproducible patched native packaging and real-device validation for Windows x86-64, Linux x86-64, macOS x86-64 and macOS ARM64. Establish the supported release matrix after the call capability works.

### 2. Non-goals

- Linux ARM/Windows ARM, feature expansion, group calls, screen sharing and generic TURN service.
- Treating artifacts merely existing as proof of runtime support.
- Silently marking unavailable target hosts passed.

### 3. Assumptions & open questions

PRELIMINARY: exact native sources/artifact coordinates/toolchains come from phases 1/3; launcher/media requirements come from phase 4. Begin target toolchain discovery in phase 1 to reveal blockers early, but final execution requires phase 4. Preserve matching Java/JNI revisions and existing native-access settings.

### 4. Proposed design

Build the same source lock/patch for four target classifiers. Verify shaded native resource paths/extraction, no JNI relocation/minimization, Java 26 launch and real device permission behavior in distributed bundles. Add macOS usage descriptions/signing permissions and Linux runtime library metadata where supported. Run all-target calling and cleanup matrix; report limitations without fabricating results.

### 5. Architecture connections

| New / changed thing | Owned / created by | Called / used by | Connects to (existing) |
|---|---|---|---|
| Four classified native artifacts | Pinned build scripts | Application/diagnostics | Source lock, patch, Maven dependencies |
| Packaging metadata | Maven/jpackage | Installed launcher | Existing OS build profiles and native-access flags |
| Validation report | Packaged diagnostic/call tests | Release reviewer | Phase-3 transport proof, phase-4 call UI |

No new wire protocol. Classifiers: `windows-x86_64`, `linux-x86_64`, `macos-x86_64`, `macos-aarch64`. Inspect actual artifact/resource layout before selecting universal shaded resources versus per-target packaging.

### 6. Key decisions & edge cases

macOS permission behavior must be tested in signed/distributed bundles, not just the IDE. Linux libraries and audio/camera access remain environmental requirements. Target unavailable means validation incomplete. Successful LAN calls do not prove arbitrary NAT or standard TURN; WR service incompatibility remains as described in phase 3. No extra inbound media ports may appear through packaging/configuration changes.

### 7. Acceptance criteria

- [ ] Reproducible pinned patched builds for all four classifiers; matched Java/JNI API.
- [ ] Packaged Java 26 native load and actual mic/playback/camera work on each target.
- [ ] Every target makes and answers a cross-platform call over shared UDP and forced TCP.
- [ ] Twenty successive calls and ten-minute sustained calls pass without leaked hardware/native growth.
- [ ] Permissions, device absence, kill/disconnect, busy/old peer and port changes have clear outcomes.
- [ ] Packet/socket evidence shows no extra inbound media listeners.
- [ ] Final report records target versions, test results and unresolved NAT/SFU/screen-sharing limitations.

---
## ✦ IMPLEMENTATION DETAILS (for the implementation agent)
---

### 8. Affected files / classes

- `pom.xml`: consume distinct-version patched JNI artifacts matching Java API; package only four target classifiers; opt-in diagnostics profile.
- `src/main/jpackage/macos/Info.plist`: microphone and camera usage descriptions; inspect signing setup for required permissions.
- Existing Linux packaging metadata located during implementation: add required native runtime dependencies where applicable; document when packaging has no dependency metadata.
- New `native/webrtc/build-linux.sh`, `native/webrtc/build-macos.sh`, `native/webrtc/build-windows.ps1`: reproducible patched artifact builds; host architecture checks and classified output.
- `native/webrtc/build-macos.sh`, `native/webrtc/build-windows.ps1`: target scripts matched to phase-1 Linux build/source lock.
- Existing OS-specific Maven profiles and `src/main/jpackage/` resources: permission/dependency corrections.
- Opt-in `WebRtcDiagnostic` and target test instructions/report templates.
- `docs/impl_summary/webrtc-phase-5-release-validation.md`: exact artifact and runtime evidence.

### 9. Step-by-step implementation

1. Refresh this preliminary plan from prior summaries and exact artifact coordinates.
2. Complete target build scripts/checksums and reproduce artifacts from the same source lock/patch.
3. Verify Maven native dependency/resource selection; package four allowed architectures, without JNI relocation/minimization. Use per-target profiles if universal resource layout conflicts, documenting the choice.
4. Preserve native-access options; add permission descriptions/signing requirements and Linux runtime dependencies.
5. Run child-JVM diagnostics and packaged real-device/cross-platform matrix. Capture target/JDK/build identities and redacted failure evidence.
6. Run applicable regression checks and record unresolved limitations. Complete this phase only after real results satisfy acceptance; do not infer results from a local host.

### 10. Tests to write

Manual smoke matrix:

| Target | Native/packaged launch | Real mic/playback/camera | 20 calls/cleanup | Permission/missing-device | Cross-platform LAN call |
|---|---|---|---|---|---|
| Windows x86-64 | Required | Required | Required | Required | Required |
| Linux x86-64 | Required | Required | Required | Required | Required |
| macOS x86-64 | Required | Required | Required | Required | Required |
| macOS ARM64 | Required | Required | Required | Required | Required |

Each target must make and answer a call against another target; include Windows↔Linux and Intel Mac↔ARM Mac paths. Test a 10-minute sustained call, speaker echo behavior and Hang Up/window close during ringing and negotiation. Confirm camera indicator clears and another app can immediately acquire hardware. Test camera absent/denied, audio denied, minimized/unfocused receiver, caller kill, signaling network loss, busy receiver and an older Myster peer. Capture WR packets and VMED handshakes and inspect OS binds on both endpoints. Block UDP and verify native ICE-TCP media still uses the existing Myster listener. Run concurrent downloads and ping/stats while calling; artificially slow sends to prove bounded memory and fair progress. Change the server port during ringing and active calls. Separately test a NATed caller against a reachable server at its existing port; report failure conditions accurately. Do not enable standard TURN URLs or claim arbitrary-NAT support before an unprefixed-service demultiplexing design is implemented and tested.

Use opt-in native diagnostics with hard timeouts and archive redacted output. Add build/resource inspection checks where meaningful; no fake tests that only mirror Maven declarations.

### 11. Docs / Javadoc to update

Document reproducible native builds, required runtime libraries/permissions, target support and launch diagnostics. Write final phase-5 summary with checksums/provenance and actual matrix evidence. Link prior phase summaries; do not replace them with an unqualified feature-complete claim.

