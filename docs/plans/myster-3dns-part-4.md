# Myster 3DNS - Part 4: Routing-Table Maintenance and Bootstrap

Prerequisites:

- [Part 2b: Candidate Identity Verification](myster-3dns-part-2b.md)
- [Part 3: Iterative CID Resolution](myster-3dns-part-3.md)

> Preliminary plan: revisit constants and scheduling details after Part 3 is implemented and measured.

## 1. Summary

Bootstrap and actively maintain the tracker-owned 3DNS target table by resolving positive power-of-two targets on startup and roughly hourly thereafter, securely onboarding selected verified peers, and immediately repairing retained slots when nodes become damaged or unreachable.

## 2. Non-goals

- Do not define or change the `FIND_CLOSEST` wire protocol; that is Part 2a.
- Do not reimplement candidate identity proof or iterative routing; use Parts 2b and 3.
- Do not replace pool liveness checks or 3DNS persistence.
- Do not require all 128 targets to run simultaneously or on a synchronized wall-clock boundary.
- Do not make routing-table maintenance a prerequisite for callers that already have a usable lookup seed.

## 3. Assumptions & open questions

- Targets remain `localCid + 2^bitIndex`. Part 3 owns direction-aware routing and returns a verified exact or closest-known peer without overshooting through an invalid route.
- Healthy entries receive an approximately hourly refresh with very broad jitter. Jitter applies both to a node's cycle start and to individual target work so fleets do not synchronize or burst 128 requests.
- A damaged target slot is re-resolved immediately, subject to in-flight deduplication and a small failure backoff to avoid a tight retry loop.
- Startup can use currently usable retained fingers, existing pool servers, or an externally learned bookmark/mDNS/manual/on-ramp server as resolver seeds. An empty installation remains idle until at least one usable entry point is learned.
- Part 3 returns verified peers rather than mutating the pool. Part 4 owns the additional expected-key stats fetch and pool insertion required for persistent tracker retention.
- Open question for finalization: choose exact jitter bounds after measuring Part 3 lookup cost. Initial proposal is a random interval in the 30-90 minute range per healthy target, with target dispatch spread across the cycle.

## 4. Proposed design

`ThreeDnsMaintenance` owns scheduling and bootstrap state; `ThreeDnsServerList` continues to own retained slots; `ThreeDnsLookup` owns network traversal. On startup, maintenance inspects immutable target snapshots, prioritizes empty/damaged slots, and schedules healthy slots across a jittered window.

For each positive-offset target, maintenance invokes the Part 3 resolver instead of implementing a second one-hop selection loop. An exact or closest-known `VerifiedThreeDnsPeer` is then securely onboarded: fetch server stats using the peer's expected key, require the stats identity to equal the verified public key, and pass the verified stats through a narrow pool insertion/update operation. Normal pool/listener behavior reconsiders the resulting `MysterServer` for retention; maintenance never writes retained entries directly.

Down/dead/list-removal events enqueue immediate repair for the affected target slots rather than waiting for the hourly cycle. A per-target state machine deduplicates repair against scheduled or running work. Failures use bounded exponential backoff with jitter while successful healthy targets return to the broad hourly schedule.

## 5. Architecture connections

| New / changed thing | Owned / created by | Called / used by | Connects to (existing) |
|---|---|---|---|
| Maintenance scheduler | `ThreeDnsMaintenance` | Myster lifecycle | `MysterGlobals` shutdown, scheduler/timer |
| Target repair notifications | `ThreeDnsServerList` / `Tracker` | Maintenance | Existing down/dead/list-change paths |
| One-target resolution | `ThreeDnsLookup` from Part 3 | Scheduled and damage-triggered work | Part 2b verified queries, tracker seed snapshots |
| Verified-peer onboarding | Narrow `MysterServerPool` operation | Maintenance | expected-key server stats, identity comparison, normal pool refresh events |
| Bootstrap seed selection | Tracker/maintenance | Part 3 lookup startup | retained usable fingers, pool servers, bookmarks/mDNS/on-ramp discoveries |

Data flows from a scheduled or damage event to one Part 3 lookup. A selected verified result is queried for stats with the same expected public key, identity-checked, and inserted/refreshed through the pool. The existing pool listener then lets `ThreeDnsServerList` reconsider the server for every target it improves.

There is no new wire or disk format. Existing retained-list preferences remain the only maintenance state persisted across restarts.

## 6. Key decisions & edge cases

- Part 4 reuses iterative resolution; it does not grow a competing routing algorithm.
- Very jittered means independent randomized scheduling, not one hourly task that refreshes every target at once.
- Damaged entries trigger immediate repair; healthy entries are hourly-ish.
- Immediate repair is deduplicated and backs off after failures so an offline region cannot spin.
- A peer is onboarded only when expected-key stats succeeds and the stats identity equals the already verified key.
- Pool/listener behavior, not maintenance, owns creation and retention of `MysterServer` state.
- Shutdown cancels queued/running ownership cleanly; late futures must not onboard or reschedule after stop.
- Empty seed sets are a valid idle state. New external pool discoveries wake bootstrap work.
- Sparse or malicious responses terminate under Part 3 limits; only verified and identity-matching peers influence trusted retention.

## 7. Acceptance criteria

- [ ] Empty/damaged target slots are resolved promptly at startup when a usable seed exists.
- [ ] Healthy targets refresh at broadly jittered hourly intervals without a synchronized 128-lookup burst.
- [ ] A retained node becoming down/dead triggers immediate deduplicated repair of affected targets.
- [ ] Maintenance delegates network traversal and direction policy to Part 3 rather than implementing its own candidate loop.
- [ ] Every retained discovery has a verified address/key association and matching expected-key server stats before pool insertion.
- [ ] Failed repairs back off with jitter and successful repairs return to the healthy schedule.
- [ ] Maintenance stops cleanly on application shutdown and ignores late completions.
- [ ] Existing retained-list persistence remains the only 3DNS maintenance state on disk.
- [ ] Part 3 lookup remains usable without Part 4 when the caller already has a seed.

---
## ✦ IMPLEMENTATION DETAILS (for the implementation agent)
---

## 8. Affected files / classes

- New `src/main/java/com/myster/threedns/ThreeDnsMaintenance.java` - lifecycle, target scheduling, resolution, and onboarding orchestration.
- `src/main/java/com/myster/threedns/ThreeDnsServerList.java` - expose affected-target notifications or immutable repair inputs without exposing mutable slots.
- `src/main/java/com/myster/tracker/Tracker.java` - supply snapshots/seeds and bridge damage or newly available seed events.
- `src/main/java/com/myster/tracker/MysterServerPool.java` and `MysterServerPoolImpl.java` - narrow verified expected-key stats onboarding operation.
- `src/main/java/com/myster/Myster.java` - construct/start/stop maintenance after Part 3 lookup wiring exists.
- Focused scheduler/onboarding tests under `src/test/java/com/myster/threedns` and `src/test/java/com/myster/tracker`.

## 9. Step-by-step implementation

1. Finalize scheduling constants after Part 3 measurement. Keep time, random, scheduler, resolver, and onboarding dependencies injectable for deterministic tests.
2. Model per-target state: healthy due time, queued, running, damaged, consecutive failures, and generation/cancellation token.
3. Add a narrow tracker/list notification that identifies affected bit indices when retained entries are removed or become unusable. Do not expose mutable `TargetSlot`.
4. Bootstrap resolver seeds from persisted usable fingers, tracker/pool state, and later external discoveries. Remain idle and listen for seed availability when startup has none.
5. For a target job, call `ThreeDnsLookup.resolve(target, seeds)` and accept its verified exact or closest-known result according to the finalized Part 3 result contract.
6. Add the smallest pool operation that onboards a `VerifiedThreeDnsPeer` by performing expected-key bidirectional stats, comparing `/Identity` byte-for-byte with the verified key, and then invoking existing create/update and listener behavior. Share concurrent onboarding for the same identity/address only if maintenance can actually overlap it.
7. Schedule successful targets independently around one hour with broad jitter; spread initial healthy target work. Schedule damaged targets immediately unless equivalent work is queued/running.
8. Apply bounded jittered backoff after failure and cancel all future rescheduling/onboarding on shutdown.
9. Write `docs/impl_summary/myster-3dns-part-4.md` after implementation.

## 10. Tests to write

- Deterministic fake-clock tests for broad jitter bounds and absence of a synchronized batch.
- Startup tests for persisted seeds, pool seeds, empty seed state, and wake-up on later discovery.
- Delegation tests proving each target job calls Part 3 resolution and does not duplicate direction-selection logic.
- Onboarding tests for expected-key stats success, mismatched stats identity, timeout, conflicting cached key, pool refresh, and listener-driven retention.
- Damage tests for immediate scheduling, affected-target scoping, in-flight deduplication, and backoff.
- Lifecycle tests for shutdown and late lookup/stats completion.

## 11. Docs / Javadoc to update

- Update `docs/design/Myster 3DNS.md` with the finalized jitter range, resolver reuse, and verified-peer onboarding boundary.
- Document why `ThreeDnsMaintenance` does not directly mutate the retained table.
- Javadoc the pool onboarding operation's expected-key and identity-comparison guarantees.
- Add `docs/impl_summary/myster-3dns-part-4.md` during implementation.
