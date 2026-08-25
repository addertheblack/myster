# Rolling Overall Download Rate — Implementation Summary

## Overview

The download manager's parent row now reports an approximately one-second rolling byte rate instead of averaging all bytes over the download's lifetime. Explicit queue, pause, resume, and other lifecycle changes reset the sampler, while child connection counters retain their existing behavior. Sparse progress events use their longer measured interval so low-throughput downloads remain visible.

## Files changed

- `src/main/java/com/myster/progress/ui/RollingByteRate.java` — added the monotonic rolling-window calculator.
- `src/main/java/com/myster/progress/ui/ProgManDownloadHandler.java` — integrated the calculator into parent-row progress and lifecycle callbacks.
- `src/test/java/com/myster/progress/ui/TestRollingByteRate.java` — added six deterministic unit tests.
- `docs/plans/rolling-download-rate.md` — added the authoritative design and implementation plan.
- `docs/plans/README.md` — indexed the new plan.
- `docs/impl_summary/rolling-download-rate.md` — added this implementation report.

## Key decisions

- Used `System.nanoTime()` so wall-clock adjustments cannot distort elapsed-rate calculations.
- Kept the nearest sample at or before the one-second cutoff, yielding an approximately one-second window at the engine's roughly 100 ms progress-event cadence.
- Preserve the previous sample when progress events are more than one second apart. Those sparse events are normal at low throughput and their longer interval is the best available rate measurement.
- Reset explicitly at start, queue, pause, resume, stop, and completion boundaries and clear the displayed speed immediately.
- Preserved the existing `int` speed model and clamp extreme calculated values to `Integer.MAX_VALUE`.

## Deviations from the plan

The initial implementation treated a one-second progress-event gap as inactivity. Live use showed that block-driven events can legitimately be sparser below roughly 10 KiB/s, causing every sample to reset and the speed to remain blank. The plan and implementation were corrected to distinguish explicit inactive lifecycle events from sparse progress-event timing.

## Tests and verification

- `mvn -Dtest=TestRollingByteRate test` — passed, 6 tests.
- `mvn test` — passed, 544 tests with no failures, errors, or skips.
- `git diff --check` — passed.

The new tests cover sub-second extrapolation, rolling-window advancement, sparse low-throughput events, explicit reset, cumulative-count regression, and numeric clamping.

## Javadoc and design docs

- Added class, constructor, `update`, and `reset` documentation to `RollingByteRate`, including time units, sparse-event behavior, reset behavior, result clamping, and EDT confinement.
- Reviewed `docs/design/`; none of the current documents describe download-manager rate semantics, so no design document changed.

## Known issues and follow-up

- A live-peer UI smoke test was not run in this environment. The remaining manual checks are queue-to-transfer behavior, pause/resume behavior, and confirmation that expanded child rows are unchanged.
- No additional automated tests are currently required. A future UI-level listener test could verify model updates across full lifecycle callbacks if lightweight `ProgressManagerWindow` test seams are introduced.
