# Rolling Overall Download Rate

## 1. Summary

Change the overall multi-source download speed shown in the download manager from a lifetime average to a rolling, approximately one-second byte rate, so time spent locally queued, paused, or otherwise inactive no longer depresses the displayed speed.

## 2. Non-goals

- Do not change the per-connection/segment speed calculation in this milestone.
- Do not change download scheduling, local or remote queue behavior, transfer event frequency, or network protocols.
- Do not redesign the download manager columns, labels, or byte-rate formatting.
- Do not change the legacy standalone `FileProgressWindow` rate display.

## 3. Assumptions & open questions

- “Entire download” means the parent download row managed by `ProgManDownloadHandler`; child connection rows retain their existing independent counters.
- “Bytes over the last second” is treated as an approximately one-second rolling window over cumulative progress events. The engine limits overall progress events to at most about ten per second, but low-throughput transfers can emit them less than once per second because events follow completed data blocks.
- When progress events are sparser than the one-second window, the most recent two samples provide the best available transfer-rate interval. Explicit queue, pause, resume, start, stop, and completion events reset sampling so known inactive lifecycle time is still excluded.
- No open question blocks implementation.

## 4. Proposed design

Add a small package-private rolling byte-rate calculator owned by each `ProgManDownloadHandler`. It receives cumulative byte counts and reads monotonic time. It retains only the samples needed to compare the current count with a baseline approximately one second old.

The calculator starts a fresh sampling period on the first progress event and after explicit download lifecycle transitions. Once two samples exist, it reports bytes per second using their byte and monotonic-time deltas. As dense samples accumulate, the baseline advances through the rolling one-second history. When the samples themselves are more than a second apart, it retains the prior sample and calculates over that longer interval instead of repeatedly returning zero. This keeps local queue time and pause time out of the divisor, permits low rates to remain visible, and avoids wall-clock adjustments.

`ProgManDownloadHandler` writes the calculated rate to the parent `DownloadItem` on every overall progress event. Existing lifecycle handlers continue to display zero while paused, queued, stopped, or complete and also reset the calculator so resumed transfers do not inherit stale history.

## 5. Architecture connections

The multi-source engine continues to publish cumulative `MultiSourceEvent` progress on the EDT. `ProgManDownloadHandler` remains the adapter from those engine events to the `ProgressManagerWindow` model. The new calculator is UI-side transient state: it neither changes the event contract nor introduces persistence or wire-format changes.

| New / changed thing | Owned / created by | Called / used by | Connects to (existing) |
|---|---|---|---|
| Rolling overall byte-rate calculator | One instance per `ProgManDownloadHandler` | Overall progress and lifecycle callbacks | Cumulative `MultiSourceEvent.getProgress()` values and `DownloadItem.setSpeed(int)` |
| Parent-row rolling rate behavior | `ProgManDownloadHandler` | `ProgressManagerWindow` rendering | Existing Speed column formatting and repaint flow |

There are no new or changed protocols, persisted formats, or public APIs.

## 6. Key decisions & edge cases

- Use monotonic nanosecond time, not wall-clock milliseconds, because elapsed-rate calculations must not react to clock changes.
- Treat cumulative byte count regression as a new sampling period. This safely handles a restarted or replaced progress source without displaying a negative rate.
- Do not infer inactivity solely from a gap between progress events. At low transfer rates, completing one progress block can legitimately take longer than a second; use the last two sparse samples as the best available rate interval.
- Return zero until two samples from the same active period exist; the UI already renders non-positive speed as blank.
- Clamp rates beyond the existing `int` speed model range rather than allowing overflow.

## 7. Acceptance criteria

- [ ] The parent download row reports a recent transfer rate derived from approximately the last second when event density permits, or the most recent two progress samples when events are sparser.
- [ ] Time spent in the local queue before transfer starts is excluded from the rate calculation.
- [ ] Pausing and resuming cannot mix pre-pause samples into the resumed rate.
- [ ] Low-throughput downloads with progress-event gaps longer than one second continue to display a non-zero measured rate.
- [ ] Child connection speed behavior and displayed formatting remain unchanged.
- [ ] Focused automated tests cover rolling-window behavior, inactive gaps, resets, count regression, and numeric clamping.

---
## ✦ IMPLEMENTATION DETAILS (for the implementation agent)
---

## 8. Affected files / classes

- **New:** `src/main/java/com/myster/progress/ui/RollingByteRate.java` — package-private rolling byte-rate calculator with monotonic time injection for deterministic tests.
- `src/main/java/com/myster/progress/ui/ProgManDownloadHandler.java` — replace the parent row’s lifetime-average fields/calculation with `RollingByteRate` and reset it at lifecycle boundaries.
- **New:** `src/test/java/com/myster/progress/ui/TestRollingByteRate.java` — focused unit coverage with a controllable monotonic clock.
- `docs/plans/README.md` — index this plan.
- **New during implementation:** `docs/impl_summary/rolling-download-rate.md` — record implementation results and verification.

## 9. Step-by-step implementation

1. Add `RollingByteRate` in `com.myster.progress.ui`.
   - Store a deque of immutable `(timeNanos, cumulativeBytes)` samples.
   - Default to `System::nanoTime`; provide a package-private constructor accepting `LongSupplier` for tests.
   - Use a one-second window constant.
   - Expose `int update(long cumulativeBytes)` and `void reset()`.
   - On the first sample, a cumulative-count regression, or a non-increasing timestamp, clear history, store the current sample, and return zero.
   - Otherwise append the current sample and discard obsolete samples while retaining the nearest sample at or before the cutoff when available.
   - Calculate bytes per second from the current sample and retained baseline, using overflow-safe arithmetic and clamping the result to `Integer.MAX_VALUE`.
2. Update `ProgManDownloadHandler`.
   - Replace `startTime` with a handler-owned `RollingByteRate`.
   - In `progress(MultiSourceEvent)`, pass `event.getProgress()` to the calculator and assign the returned speed before repainting.
   - Reset the calculator and set speed to zero on start, resume, pause, local queue notification, end, and completion so inactive lifecycle time and stale displayed rates cannot leak across states.
   - Leave `ConnectionHandler` and its existing per-segment rate calculation unchanged.
3. Add `TestRollingByteRate` using a mutable `LongSupplier` test clock.
   - Verify a rate can be extrapolated from two sub-second active samples.
   - Verify the rolling baseline advances as samples age beyond one second.
   - Verify an event gap longer than one second still calculates a sparse low-throughput rate.
   - Verify explicit reset and cumulative-byte regression start fresh sampling periods.
   - Verify extreme deltas clamp to `Integer.MAX_VALUE`.
4. Run the focused test class, then the project test suite if the focused test passes.
5. Review `docs/design/`; no current design document describes progress-rate calculation, so update one only if implementation reveals a conflicting contract.
6. Write `docs/impl_summary/rolling-download-rate.md` with changed files, decisions, deviations, documentation review, and test results.

## 10. Tests to write

- `TestRollingByteRate#calculatesRateFromRecentActiveSamples`: two samples 250 ms apart produce the equivalent bytes-per-second rate.
- `TestRollingByteRate#usesApproximatelyLastSecondAsWindowAdvances`: several samples spanning more than a second discard old history and use the retained cutoff baseline.
- `TestRollingByteRate#calculatesRateAcrossSparseEvents`: samples more than one second apart still produce the measured low-throughput rate.
- `TestRollingByteRate#resetClearsPreviousSamples`: explicit lifecycle reset requires a new baseline.
- `TestRollingByteRate#byteCountRegressionStartsNewSamplingPeriod`: decreasing cumulative progress cannot produce a negative or stale rate.
- `TestRollingByteRate#clampsRatesToDownloadItemRange`: a representable byte delta over a very short interval returns `Integer.MAX_VALUE` rather than overflowing.

Manual smoke test when a peer is available:

1. Queue a download behind another local download and confirm its parent Speed cell stays blank while queued.
2. Let it start and confirm the parent Speed cell reflects current traffic rather than the queue-delayed lifetime average.
3. Pause and resume it; confirm the resumed rate restarts cleanly.
4. Expand the parent and confirm child connection speed rows still behave as before.

## 11. Docs / Javadoc to update

- Add class and method Javadoc to `RollingByteRate` describing cumulative-byte input, the approximate one-second window, sparse-event fallback, reset conditions, zero-before-baseline behavior, and `int` clamping.
- Update `ProgManDownloadHandler` class Javadoc only if needed to state that it owns transient overall-rate sampling; no public method contract changes are expected.
- No design document update is expected because the current `docs/design/` set does not describe the download progress UI or rate semantics.
