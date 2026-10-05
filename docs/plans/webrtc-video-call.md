# WebRTC calling — phase index

This is navigation only, not an executable implementation plan. Work on one phase at a time.
Phase 1 is the current implementation plan; phases 2–5 are preliminary and must be refreshed
from earlier results before execution.

| Phase | Plan | Status |
|---|---|---|
| 1 | [Native feasibility](webrtc-phase-1-native-feasibility.md) | Current |
| 2 | [Datagram socket refactor](webrtc-phase-2-datagram-refactor.md) | Preliminary |
| 3 | [Shared media transport](webrtc-phase-3-shared-transport.md) | Preliminary |
| 4 | [Basic calling capability](webrtc-phase-4-basic-calling.md) | Preliminary |
| 5 | [Target and release validation](webrtc-phase-5-release-validation.md) | Preliminary |

Shared Myster UDP/TCP sockets are mandatory; no extra inbound media port substitution.
Supported targets: Windows x86-64, Linux x86-64, macOS x86-64 and macOS ARM64.
The separate datagram-refactoring phase owns the reader/writer split and small non-blocking
queue; it is not part of the initial native spike.

Historical context: [design exploration](../design/Myster_WebRTC_Voice_Design_Exploration.docx).
