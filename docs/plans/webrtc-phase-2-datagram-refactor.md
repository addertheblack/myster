# WebRTC phase 2: Datagram socket refactor

Status: PRELIMINARY — not ready to execute. Refresh against completed earlier phases before implementation.

Sequence: Requires [phase 1](webrtc-phase-1-native-feasibility.md). Next: [phase 3](webrtc-phase-3-shared-transport.md).

Execute one phase at a time. This document owns this phase only; [the phase index](webrtc-video-call.md) is navigation, not an implementation plan.

## Design Section

### 1. Summary

Independently refactor `AsyncDatagramSocket` to one reader and one writer per datagram socket, and expose small non-blocking media submission with send-attempt/readiness metadata. This phase changes networking infrastructure without importing WebRTC into its tests.

### 2. Non-goals

- Native/JNI adapter changes, WebRTC initialization, call UI and TCP media integration.
- Blocking UDP producers, retrying already-sent packets, or applying packet-drop rules to TCP.
- Breaking existing `DatagramSender.sendPacket` caller contracts.

### 3. Assumptions & open questions

**Connectivity ownership:** Myster’s protocols provide peer reachability and NAT traversal. This project consumes that transport capability; it does not implement, evaluate or validate NAT traversal, external STUN/TURN services, router mappings or relay infrastructure. Native ICE/STUN checks may still be needed to establish the WebRTC session over the Myster-provided path; those checks are adapter/session mechanics, not a separate NAT-traversal project.

PRELIMINARY: refresh after phase 1 identifies required metadata/error semantics. A four-packet media queue is an initial experiment, not a proven optimal capacity. Legacy protocols retain their submission contract; specify how legacy and bounded media queues share the sole writer before execution. Thread count becomes two I/O workers per socket; existing callback workers are separate.

### 4. Proposed design

Use a fixed small media queue. Full admission rejects/discards the new packet immediately; it does not block or evict an admitted head. The writer peeks, attempts one complete datagram, and removes that exact entry on full-length success. Zero/would-block retains the head until write readiness; definitive failure removes/fails it, and socket closure fails pending work. Freeing capacity triggers the appropriate readiness event.

The writer owns all sends, including legacy traffic, while the reader concurrently receives on the same channel. Do not leave the old writer competing with the new one. Keep selection-compatible non-blocking channel mode; disable write interest when unnecessary to avoid spinning. Queue IDs/metadata travel with whole datagrams, never byte ranges. Record native-compatible send-attempt events separately from actual success/failure. Measure bursts/latency and tune capacity from evidence.

References: [Java channel threading/send](https://docs.oracle.com/en/java/javase/26/docs/api/java.base/java/nio/channels/DatagramChannel.html), [write readiness](https://docs.oracle.com/en/java/javase/26/docs/api/java.base/java/nio/channels/SelectionKey.html#OP_WRITE).

### 5. Architecture connections

| New / changed thing | Owned / created by | Called / used by | Connects to (existing) |
|---|---|---|---|
| Reader/writer lifecycle | `AsyncDatagramSocket` | Existing datagram consumers | Shared channel, selectors, callback invoker |
| Scoped bounded media submission | Socket/manager access API | Future phase-3 bridge | `DatagramProtocolManager`, legacy `DatagramSender` |
| Per-datagram result/readiness metadata | Writer | Native-independent callbacks | Queue item identity and send attempt |

This phase adds local APIs only, no new wire protocol. Keep `TransportManager` scoped; define safe send handle lifetime and generation without leaking the manager itself.

### 6. Key decisions & edge cases

Coordinate bind readiness, asynchronous close/retry/recreate and selectors across both workers. One worker failure cannot leave the other running on a dead generation. One reader and one writer may run concurrently; no JNI/native callback assumptions belong in this layer. Legacy network delivery remains asynchronous. If receive queue limits prove necessary, handle them explicitly; they are not automatically part of the writer refactor.

### 7. Acceptance criteria

- [ ] Revised phase-2 plan specifies exact APIs based on phase-1 findings before execution.
- [ ] One reader/one writer per socket; shutdown/recreation has no leaked workers or competing sends.
- [ ] Full media queue rejects without blocking; successful sends remove once; zero send retains and waits without spinning.
- [ ] Definitive errors and closed sockets cannot jam the queue.
- [ ] Capacity/readiness and send metadata callbacks are tested without native code.
- [ ] Ping/stats/transactions and default/custom-port changes preserve behavior.
- [ ] Burst and congestion measurements justify chosen queue size and scheduling.

---
## ✦ IMPLEMENTATION DETAILS (for the implementation agent)
---

### 8. Affected files / classes

- `src/main/java/com/general/net/AsyncDatagramSocket.java`: split I/O lifecycle and bounded media submissions.
- New proposed `src/main/java/com/general/net/DatagramSendRequest.java` and `DatagramSendResult.java`: native-independent item metadata/results; finalize after phase 1.
- `src/main/java/com/myster/net/datagram/DatagramProtocolManager.java`: scoped lifetime-safe endpoint access and result forwarding.
- Existing `DatagramSender` callers: inspect compatibility; preserve fire-and-forget API.
- New tests under `src/test/java/com/general/net/` and `src/test/java/com/myster/net/datagram/`.
- `docs/impl_summary/webrtc-phase-2-datagram-refactor.md`: behavior and measurement results.

### 9. Step-by-step implementation

1. Refresh this preliminary plan using phase-1 required metadata and exact proposed API signatures. Specify legacy/media scheduling and channel lifecycle before code changes.
2. Isolate channel send operations behind a fakeable test seam; define whole-datagram metadata/results and non-blocking media admission.
3. Refactor existing socket to sole writer plus reader, coordinating binding readiness, close and recreation. Preserve existing invoker/legacy semantics.
4. Implement peek/send/remove, temporary zero-send retention, readiness wait/rearm and definitive-error removal. Ensure producers never remove the writer's admitted head.
5. Test bounded admission and metadata/result/readiness events; test both workers under close and channel failure.
6. Verify existing datagram protocols/port changes, then measure controlled bursts and congestion. Update phase 3 with actual APIs and stop after this phase.

### 10. Tests to write

Test queue full rejection, exact head identity, zero-send retention, no duplicate send, correct buffer position, permanent-error removal, no busy loop, readiness full-to-available transitions, idle writer wakeup, close during send/receive, rebind failure and generation isolation. Use fake channels for deterministic errors plus loopback real sockets for concurrent I/O. Run existing relevant datagram/port-change tests and compare legacy latency/progress under controlled media submissions. No native dependency is required.

### 11. Docs / Javadoc to update

Document local queue/result/readiness contracts, retry versus actual-send distinction, worker ownership, callback threads and scoped endpoint lifetime. Write phase-2 implementation summary and update transport conventions from measured behavior.

