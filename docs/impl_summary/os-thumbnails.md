# OS thumbnails and standalone preview

Implemented `com.myster.thumbnail.Thumbnails.summonThumbnail(Path, int)` and its
`PromiseFuture` companion, with OS providers, bounded memory caching and a standalone
Swing JPEG/AVI/MKV/MP4 grid that fills progressively and logs acquisition sources and timings.

## Files

- `src/main/java/com/myster/thumbnail/`: facade, cache/coalescing service, platform
  providers, freedesktop PNG cache reader, scaling helper, preview main and package docs.
- `src/test/java/com/myster/thumbnail/`: 12 focused tests across two test classes.
- `src/main/java/com/general/thread/PromiseFutures.java`: pre-start cancellation check
  and interrupt preservation in the existing executor overload; `TestPromiseFuture`
  adds four regression tests for scheduling, cancellation and exception delivery.
- `pom.xml`: dbus-java 5.2.1 core/JDK socket transport, shaded service registrations,
  native-access manifest entry and packaged JVM option.
- `Myster.iml`, `Myster.ipr`: IntelliJ's imported dependency entries.
- `docs/design/Myster OS Thumbnail Integration Design.md`: implemented API, threading,
  limits, platform details, source references and launch instructions.
- `docs/plans/os-thumbnails.md`, plan index and codebase structure: scope and discovery.
- `docs/conventions/myster-important-patterns.md`: executor-overload usage and the
  Windows native-thread requirement.

## Decisions and scope

- Public blocking API matches the requested call; reject it on the EDT. Swing callers
  use `summonThumbnailAsync(...).useEdt()`. Returns `BufferedImage`, assignable to `Image`.
- Maximum dimension is 1–1024, with aspect ratio preserved and no upscaling or padding.
  Missing/unsupported/unreadable files return null; shared images are read-only to callers.
- Four daemon platform workers preserve COM thread affinity. Equal requests coalesce;
  the cache retains at most 256 entries and approximately 16 MiB of pixels, keyed by
  normalized path, size, identity, timestamps and file length.
- The executor comment, design and threading conventions explain why a `BoundedExecutor`
  over virtual threads cannot directly replace those workers: pinning covers individual
  native calls, while the Windows COM sequence requires the same native thread across calls.
  This documentation clarification changes no runtime behavior.
- Cancellation skips queued requests and invalidates the caller's result. Running
  extraction may finish and cache its result; cancellation does not affect other callers.
- The asynchronous thumbnail entry point uses
  `PromiseFutures.execute(() -> Shared.service.load(path, size), Shared.workers)`.
  Scheduling and exception delivery use the existing helper; its cancellation pre-check
  prevents queued callables from starting, and it preserves thread interruption when a
  callable throws `InterruptedException`.
- OS-only acquisition follows the supplied design. No ImageIO original-image fallback,
  unrelated media programs, application disk cache or MCList integration was added.
- The grid scans immediate JPG/JPEG, AVI, MKV and MP4 files, handles extension case, limits in-flight work
  to four, reports per-file/overall timings and ignores stale callbacks after reload.
- A compact implementation plan was recorded from the user's explicit implementation
  request and supplied design; there was no pre-existing thumbnail plan.

## Verification

- IntelliJ build: successful; platform provider inspections report no issues.
- `mvn -q test`: **705 tests, 0 failures, 0 errors, 0 skipped** after the promise refactor.
- Most recent focused headless run: **33 tests passed** across `TestPromiseFuture`,
  `TestCancellationTracking`, `TestThumbnails` and `TestFreedesktopThumbnailCache`.
  The 12 thumbnail tests cover sizing, invalid arguments/EDT use,
  missing files, memory reuse, freshness, coalescing, interrupted-waiter isolation,
  eviction, corrupt metadata and Tumbler fractional timestamps.
- `mvn -q -DskipTests compile jar:jar shade:shade@default`: successful. Verified the
  native-access manifest entry and D-Bus transport service registration in the jar.
- Live Linux API smoke: a new generated JPEG was acquired through Tumbler/D-Bus and
  freedesktop cache, then reused from memory. One measured run was 76 ms then 1 ms.
- Live Swing smoke from the shaded jar: 12 generated JPEGs loaded, with no unavailable
  cells; rapid 16→64 size changes/reloads completed without stale grid updates. Visually
  checked the grid, image proportions, labels, controls and status display.
- Initial sandboxed GUI tests could not connect to X11; focused tests were rerun
  headlessly, then the full suite and real desktop smoke tests ran with desktop access.

## AVI/MKV preview extension

- Added case-insensitive `.avi` and `.mkv` acceptance alongside `.jpg`/`.jpeg` in
  `ThumbnailPreview`; updated its Javadoc, initial prompt, folder chooser and empty state.
- Updated the plan acceptance criteria, design and codebase overview to match. Video
  requests use the existing OS providers; extraction depends on installed thumbnail
  handlers and codec support. No provider changes or new dependencies were needed.
- IntelliJ compilation and `mvn -q -DskipTests compile` passed. The 12 existing thumbnail
  tests passed after compiling those two test classes directly and running
  `mvn -q -Djava.awt.headless=true -Dtest=TestThumbnails,TestFreedesktopThumbnailCache surefire:test`.
- The normal Maven test lifecycle is currently blocked by unrelated compile errors in
  `TestFileStatsBatch`, whose calls pass a third argument to a two-argument method.
- The user subsequently confirmed video thumbnail extraction works on their desktop.
  No new unit tests were needed for the filter update.

## MP4 preview extension

- Added case-insensitive `.mp4` acceptance through the same OS thumbnail path, with
  matching preview prompts, Javadoc, plan, design and codebase overview.
- IntelliJ compilation and Maven production compilation passed; all 12 thumbnail tests
  passed using the focused compilation/Surefire procedure above. MP4 extraction itself
  remains a manual desktop check.

## Runtime finding

The initial Linux smoke returned null even though Tumbler produced a PNG. An IntelliJ
non-suspending logpoint confirmed cached `Thumb::MTime=1789430819.929273` versus the
whole-second comparison `1789430819`, for file time `2026-09-15T00:06:59.929273118Z`.
The reader now compares at the cache timestamp's precision; tests verify fractional
timestamps and same-second changes. The logpoint was removed and debug session stopped.

## Concurrent Linux generation and diagnostics

- Reproduced missing high-resolution thumbnails against Tumbler 4.18.1. With the desktop
  cache cleared above `normal`, four concurrent requests left 19 of 44 files without
  thumbnails at 512 pixels; the second launch left 8. No request timed out, and the
  slowest first-pass request took 248 ms.
- Debugger logpoints confirmed the requests finished without usable cache entries.
  A D-Bus trace showed `Ready` notifications naming another request's URI, followed by
  `Finished` for files whose cache PNGs did not exist. One-at-a-time generation produced
  all 43 files the backend could decode at both 512 and 1024 pixels.
- `LinuxThumbnailProvider` now serializes service generation with an interruptible
  semaphore. Cache reads remain parallel, with a second lookup after acquiring the
  permit. The permit is released on all completion, failure and interruption paths.
  This changes Linux generation concurrency only; no retry loop or smaller-image
  fallback was added.
- Added service `Error` signal diagnostics, request size/flavor and explicit logging
  for completion without a usable cache entry. Corrected `Finished` Javadoc: processing
  can finish after either success or failure. Error URI arrays use `List<String>` to
  match dbus-java's signal constructor binding; live failure logging was verified.
- Updated the plan, OS thumbnail design and threading conventions. These changes extend
  the original plan to address the observed service concurrency bug.
- Verification: isolated D-Bus sessions and brand-new temporary caches reproduced 14
  missing files before the fix at 512. After the fix, concurrent callers produced 43/44
  on the first pass at 512 (3.026 s) and 1024 (5.858 s). The same remaining video reports
  service error code 8, `Unrecognized image file format`, at every size.
- IntelliJ and Maven production compilation passed, and all 12 existing thumbnail tests
  passed using the focused compilation/Surefire procedure above. No debugger sessions
  or agent breakpoints remain. The existing unrelated test-compilation limitation remains.
- The workaround controls Myster's requests, not requests from other desktop applications.
  A permanent isolated-D-Bus integration test for concurrent cold-cache generation would
  be useful follow-up; the live before/after reproduction is the regression check here.

## Metadata collector review refactor

- `FreedesktopThumbnailCache.collectText(Node)` now creates and returns its metadata
  map, merging child results; its caller no longer passes a mutable output argument.
  Traversal order and duplicate-key behavior are unchanged.
- Recorded the collection-return preference in the coding conventions. No public
  contract or design documentation changed.
- Replaced ImageIO PNG tree, text-entry and attribute string literals with descriptive
  constants. Entry names identify uncompressed Latin-1, compressed Latin-1 and UTF-8
  text. Preserved the corrected `zTXtEntry` mapping to `text` already in the working file;
  documented the naming preference in the coding conventions.
- Named the thumbnail metadata keys for the source file URI, modification time and
  size in bytes; the ImageIO tree format already uses `PNG_METADATA_FORMAT`.
- Named the normal, large, x-large and xx-large flavor indices used by size selection.
- IntelliJ and Maven compilation passed; all 5 freedesktop cache tests passed using
  focused compilation and Surefire execution.

## Linux provider constant cleanup

- Named the D-Bus service identifiers and paths, environment and property keys,
  cache directories, scheduler, concurrency limit, generation timeout, connection
  sharing setting and zero request-handle sentinel in `LinuxThumbnailProvider`.
  Behavior is unchanged.
- IntelliJ and Maven compilation passed; all 12 thumbnail facade and freedesktop
  cache tests passed using focused compilation and Surefire execution.
- Separated `THUMBNAILER_INTERFACE_NAME` for `@DBusInterfaceName` from
  `THUMBNAILER_SERVICE_NAME` for service lookup and routing. Their values match,
  but the names now express their different roles. Recorded this naming preference
  in the coding conventions. IntelliJ compilation and all 12 focused thumbnail
  tests passed after this follow-up.

## Optional platform acquisition results

- Changed `ThumbnailProvider.load` to return `Optional<BufferedImage>` and documented
  that empty means the platform cannot supply a thumbnail. Updated the Linux, macOS,
  Windows and unsupported-platform providers, including Linux service generation.
- `ThumbnailService` handles the optional before scaling/caching; the public
  `Thumbnails` API keeps its existing nullable result. Exception propagation,
  resource cleanup and cache behavior are unchanged.
- Updated provider mocks in `TestThumbnails` and extended the existing failure test
  to verify empty results return no thumbnail and allow subsequent acquisition.
  Updated the plan and common-abstraction design documentation with the contract.
- IntelliJ and Maven production compilation passed. All 12 focused thumbnail tests
  passed after compiling their current sources and running Surefire directly; the
  previously recorded unrelated test-compilation limitation remains.

## macOS MKV timeout investigation

- Reproduced on macOS 13.7.8 with eight MKVs directly in the selected Movies folder.
  All eight reached `MacThumbnailProvider`'s 15-second process timeout. Four workers
  process them in two waves, explaining approximately 30 seconds for the folder.
- IntelliJ logpoints confirmed the timeout branch for all eight files and `image=null`
  in the preview result callback for the second wave, with matching generation IDs.
  The missing thumbnails originate in acquisition, before Swing rendering.
- Ran `/usr/bin/qlmanage -t -s 64 -o <temporary-directory> <file>` independently of
  Java, outside the tool sandbox. A representative MKV produced no output file and
  had to be terminated after 18.019 seconds. A repository JPEG produced its PNG in
  166 ms. The sandbox itself prevents `qlmanage` from initializing, so sandboxed
  command results are not valid host capability checks.
- All three repository JPEGs passed through the same provider and preview callback
  as non-null images (64x20, 64x20 and 64x5). Debugger-instrumented requests took
  948–978 ms; these are not normal-run performance measurements.
- The host's generator listing contained Apple's movie generator and no MKV-specific
  generator. This points to missing Quick Look format support on this host. The
  OS-only plan does not promise support for every extension accepted by the preview.
  A compatible Quick Look video extension is the next host check; installing one and
  verifying these MKVs remains follow-up. No provider or UI behavior was changed.
- Increasing the timeout would extend the wait without establishing format support.
  Possible later improvements are failure backoff and clearer unsupported/timeout
  diagnostics; neither supplies a missing decoder.

## Skip macOS MKV acquisition

- At the user's request, `MacThumbnailProvider.load` now returns empty for `.mkv`
  case-insensitively, before creating a temporary directory or launching Quick Look.
  The guard applies to every macOS thumbnail caller, including the standalone preview.
  Files remain listed as unavailable. Other platforms retain MKV acquisition.
- This intentionally skips MKVs even on Macs with a suitable extension installed.
  Updated provider Javadoc, the plan and the design to document that policy.
- IntelliJ build and Maven's focused test lifecycle passed: 12 thumbnail/cache tests,
  zero failures or errors. The earlier unrelated test-compilation blocker did not recur.
- Rebuilt and ran the preview against the same eight Movies-folder MKVs: all were
  skipped with no Quick Look launch or timeout. First-wave requests took 52–59 ms
  including initial logging; the remaining requests took 0–1 ms.

## Per-platform extension whitelists

- Replaced the macOS MKV exception with `ThumbnailPlatform`, the single definition
  of platform selection, provider creation and separate immutable extension sets.
  Windows/Linux allow jpg, jpeg, avi, mkv and mp4; macOS initially allows jpg, jpeg
  and mp4; unknown platforms allow none. The conservative macOS default also excludes AVI.
- `ThumbnailService` rejects unlisted final extensions case-insensitively before
  filesystem/cache checks or provider calls. `Thumbnails.allowedExtensions()` and
  `isAllowed(Path)` expose the policy without filesystem/native access.
- `ThumbnailPreview` uses the same whitelist for scanning, chooser text and empty-state
  labels. Excluded files are now omitted from the grid; direct API calls return null.
  Removed the redundant MKV guard from `MacThumbnailProvider`.
- Updated the plan, design, package/API/provider Javadoc and codebase structure.
  Existing OS-only acquisition, sizing, caching and cancellation behavior is preserved
  for allowed files. A whitelist entry permits acquisition but cannot guarantee codec support.
- IntelliJ build passed. All 15 focused thumbnail/cache tests passed, including three
  new tests covering provider non-invocation for rejected files, mixed-case accepted
  inputs across platforms, platform selection and public policy exposure.
- An initial test run overlapped with IntelliJ compilation into the shared Maven output
  directories and failed with missing classes. Running sequentially passed; recorded the
  build sequencing requirement in the coding conventions.
- Live macOS preview: the Movies folder's eight MKVs produced an empty accepted list
  (debugger logpoint `files.size() == 0`) and no Quick Look acquisition. A separate JPEG
  preview run successfully acquired all three repository images in 395–432 ms per request.
  Agent logpoints and debug sessions were removed after verification.
- No known implementation blockers or additional tests are required for this change.
  Native successful-video checks on macOS and native Windows checks remain follow-up.

## Host checks and practical limits

- Windows still needs live COM-handler/bitmap-alpha smoke tests. macOS JPEG acquisition
  and callback delivery have been exercised; video success with a suitable Quick Look
  extension and further native failure/resource tests remain unverified.
- Native Windows handlers execute in-process and cannot be forcibly timed out safely;
  running extraction may continue after caller cancellation. Quick Look has a 15-second
  process limit. Linux has a 15-second generation wait plus dbus-java's 20-second default
  timeout for individual D-Bus method replies.
- The pure-Java socket transport cannot use Linux abstract Unix sockets; a desktop
  exposing only that transport can return unavailable. The tested desktop uses a path socket.
- Whole-second freedesktop entries inherently cannot distinguish equal-size source changes
  inside the same second. Fractional Tumbler entries are validated at their finer precision.
- No further unit tests are required for this scope. Native resource/failure-path integration
  tests on Windows/macOS would strengthen coverage before wiring thumbnails into MCList.
