# Myster 3DNS - Part 2b: Routing-Table Maintenance and Bootstrap

Related plans:

- [Part 1a: Core Data Structures](myster-3dns-part-1a.md)
- [Part 2a: FIND_CLOSEST Protocol and Expected-Key Hook](myster-3dns-part-2a.md)
- [Part 3: Iterative CID Resolution](myster-3dns-part-3.md)

> Preliminary plan: revisit constants and scheduling details after Part 2a is implemented and measured.

## 1. Summary

Bootstrap and maintain the tracker-owned 3DNS target table by querying positive power-of-two targets on startup and roughly hourly thereafter, with broad per-node/per-target jitter and immediate repair when retained nodes become damaged or unreachable.

## 2. Non-goals

- Do not define or change the `FIND_CLOSEST` wire protocol; that is Part 2a.
- Do not expose the general CID resolution API; that is Part 3.
- Do not replace pool liveness checks or 3DNS persistence.
- Do not require all 128 targets to run simultaneously or on a synchronized wall-clock boundary.

## 3. Assumptions & open questions

- Targets remain `localCid + 2^bitIndex`, so maintenance approaches each target from the predecessor/LEFT side and must not prefer a successor that overshoots it.
- Exact is always best; otherwise LEFT/predecessor candidates are the primary maintenance path. RIGHT results may be retained as resilience information but are not preferred for the next maintenance query for a positive-offset target.
- Healthy entries receive an approximately hourly refresh with very broad jitter. Jitter applies both to a node's cycle start and to individual target work so fleets do not synchronize or burst 128 requests.
- A damaged target slot is re-queried immediately, subject to in-flight deduplication and a small failure backoff to avoid a tight retry loop.
- Startup uses persisted usable fingers and existing pool servers as seeds; an empty installation still needs at least one externally learned seed from bookmark/mDNS/manual discovery.
- Part 2a intentionally provides no candidate-validation service. Part 2b should first confirm that maintenance needs one, then implement the narrowest production-facing validation/onboarding path rather than a speculative general pool API.
- Open question for finalization: choose exact jitter bounds after measuring request cost. Initial proposal is a random interval in the 30-90 minute range per healthy target, with target dispatch spread across the cycle.

## 4. Proposed design

`ThreeDnsMaintenance` owns scheduling and bootstrap state; `ThreeDnsServerList` continues to own retained slots. On startup, maintenance inspects immutable target snapshots, prioritizes empty/damaged slots, and schedules healthy slots across a jittered window.

For each positive-offset target, the worker asks known seeds for `FIND_CLOSEST(target, limit)`. It selects exact and LEFT/predecessor candidates, proves the selected address/key association through Part 2a's expected-key hook, and only then uses normal onboarding so pool/listener behavior can reconsider the server for retention. It does not select RIGHT/successor as the normal next query because that crosses the target in the positive routing direction.

Down/dead/list-removal events enqueue an immediate repair for the affected bit/side rather than waiting for the hourly cycle. A per-target state machine deduplicates repair against scheduled or running work. Failures use bounded exponential backoff with jitter while successful healthy targets return to the broad hourly schedule.

## 5. Architecture connections

| New / changed thing | Owned / created by | Called / used by | Connects to (existing) |
|---|---|---|---|
| Maintenance scheduler | `ThreeDnsMaintenance` | Myster lifecycle | `MysterGlobals` shutdown, scheduler/timer |
| Target repair notifications | `ThreeDnsServerList` / `Tracker` | Maintenance | Existing down/dead/list-change paths |
| One-target maintenance query and candidate proof | `ThreeDnsMaintenance` (plus a narrow shared helper only if justified) | Scheduled and damage-triggered work | Part 2a `findClosest`, expected-key requests, normal onboarding |
| Bootstrap seed selection | Tracker/maintenance | Startup work | persisted fingers, pool closest results, bookmarks/mDNS discoveries |

Data flows from a scheduled or damage event to one target job, then through one-hop `FIND_CLOSEST`, selected-candidate proof, and normal onboarding, then back into the pool refresh listener that updates `ThreeDnsServerList`. Maintenance does not directly write retained entries or preferences.

There is no new wire or disk format.

## 6. Key decisions & edge cases

- Positive-offset target maintenance is predecessor-biased: exact, then LEFT. This avoids overshooting the target.
- Very jittered means independent randomized scheduling, not one hourly task that refreshes every target at once.
- Damaged entries trigger immediate repair; healthy entries are hourly-ish.
- Immediate repair is deduplicated and backs off after failures so an offline region cannot spin.
- Shutdown cancels queued/running ownership cleanly; late futures must not reschedule after stop.
- Empty seed sets are a valid idle state. New external pool discoveries should wake bootstrap work.
- Sparse or malicious responses are tolerated; only validated candidates influence trusted retention.

## 7. Acceptance criteria

- [ ] Empty/damaged target slots are queried promptly at startup when a usable seed exists.
- [ ] Healthy targets refresh at broadly jittered hourly intervals without a synchronized 128-request burst.
- [ ] A retained node becoming down/dead triggers immediate deduplicated repair of affected targets.
- [ ] Positive-offset maintenance prefers exact then LEFT/predecessor candidates and does not overshoot via RIGHT as its normal next hop.
- [ ] Every retained discovery proves its advertised key through an expected-key request before normal pool/list retention.
- [ ] Failed repairs back off with jitter and successful repairs return to the healthy schedule.
- [ ] Maintenance stops cleanly on application shutdown.
- [ ] Existing retained-list persistence remains the only 3DNS maintenance state on disk.

---
## ✦ IMPLEMENTATION DETAILS (for the implementation agent)
---

## 8. Affected files / classes

- New `src/main/java/com/myster/threedns/ThreeDnsMaintenance.java` - lifecycle, target scheduling, and repair orchestration.
- `src/main/java/com/myster/threedns/ThreeDnsServerList.java` - expose affected-target notifications or immutable repair inputs without exposing mutable slots.
- `src/main/java/com/myster/tracker/Tracker.java` - supply snapshots/seeds and bridge damage events.
- Optional narrow candidate-proof/onboarding helper, introduced only if the maintenance implementation demonstrates reuse or ownership that does not fit `ThreeDnsMaintenance`.
- `src/main/java/com/myster/Myster.java` - construct/start/stop maintenance after Part 2a protocol wiring exists.
- Focused scheduler tests under `src/test/java/com/myster/threedns`.

## 9. Step-by-step implementation

1. Finalize scheduling constants after Part 2a measurement. Keep time/random/scheduler injectable for deterministic tests.
2. Model per-target state: healthy due time, queued, running, damaged, consecutive failures, and generation/cancellation token.
3. Add a narrow tracker/list notification that identifies affected bit indices when retained entries are removed or become unusable. Do not expose mutable `TargetSlot`.
4. Bootstrap from persisted usable fingers, tracker seeds, and pool closest candidates. Listen for later seed availability when startup has none.
5. For a target job, query bounded seeds and process exact then LEFT candidates. Implement the smallest required expected-key proof/onboarding path here; share it outside maintenance only if a concrete caller justifies that API. Rely on normal pool events to populate retention.
6. Schedule successful targets independently around one hour with broad jitter; spread initial healthy target work. Schedule damaged targets immediately unless equivalent work is queued/running.
7. Apply bounded jittered backoff after failure and cancel all future rescheduling on shutdown.
8. Write `docs/impl_summary/myster-3dns-part2b.md` after implementation.

## 10. Tests to write

- Deterministic fake-clock tests for broad jitter bounds and absence of a synchronized batch.
- Startup tests for persisted seeds, pool seeds, empty seed state, and wake-up on later discovery.
- Direction tests proving exact then LEFT selection for positive-offset targets and no normal RIGHT overshoot.
- Damage tests for immediate scheduling, affected-target scoping, in-flight deduplication, and backoff.
- Lifecycle tests for shutdown and late-future completion.

## 11. Docs / Javadoc to update

- Update `docs/design/Myster 3DNS.md` with the finalized jitter range and predecessor-biased maintenance rule.
- Document why `ThreeDnsMaintenance` does not directly mutate the retained table.
- Add `docs/impl_summary/myster-3dns-part2b.md` during implementation.
