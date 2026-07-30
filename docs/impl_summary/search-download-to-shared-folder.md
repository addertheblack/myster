# Search Download To Shared Folder

## Summary

Implemented the shared download destination flow. Search and ClientWindow download commands now choose or validate a destination directory at the UI command level and pass that directory into the download startup path. The per-file multi-source target resolver no longer opens a folder chooser when no base directory is supplied; it reports destination setup failures through the new `DownloadStartException` hierarchy.

## Files Changed

- `com.myster.net.stream.client.msdownload.MultiSourceUtils` — renamed from `MultiSourceUtilities`; final target-file resolution now requires an explicit base directory, validates it through non-GUI code, and takes an injected existing-target decision callback.
- `com.myster.net.stream.client.msdownload.DownloadDirectoryValidator` — new non-GUI validator for already selected or configured destination folders.
- `com.myster.ui.DownloadDirectoryChooser` — new shared folder chooser used by SearchTab, ClientWindow, and FileListAction flows. It owns both direct folder prompting and the “try configured folder first, then offer another folder if invalid” flow, including the `alwaysAskForDirectory` command decision and configured type-path lookup.
- `com.myster.ui.DownloadStartErrorDialog` — new shared UI helper that turns `DownloadStartException` into EDT-marshalled `AnswerDialog` alerts and supplies callbacks for `MSDownloadParams`.
- `DownloadStartException`, `DownloadServerConnectionException`, `DownloadInterruptedException`, `DownloadTargetException`, `InvalidDownloadDirectoryException`, `UnwritableDownloadDirectoryException` — new checked download startup exception hierarchy.
- `SearchResult` / `MysterSearchResult` — added destination-aware `downloadTo(Path)` and validation before scheduling.
- `SearchTab` — multi-select `Download` and `Download To...` resolve one base directory and use it for every selected result.
- `ClientWindow` / `FileListAction` — direct and context-menu downloads now validate or ask for a destination at the window/action level.
- Progress/download listener wrappers — propagate `DownloadStartException` from target-file preparation and provide UI-owned overwrite/cancel decisions.
- Tests — renamed `TestMultiSourceUtils`, updated search-result stubs, and added validator/chooser tests.
- Docs/conventions/logging — removed the completed `MultiSourceUtilities` standing refactor, updated the `Utils` example, and updated the logging category comment.

## Key Decisions

- Used `DownloadStartException extends Exception` as the root instead of exposing raw `IOException` at UI-facing startup boundaries.
- Kept `DownloadTargetException` as the destination-specific branch for local folder/target setup, with structured path data on the concrete destination exceptions.
- Added `DownloadServerConnectionException` for server contact/startup communication failures and `DownloadInterruptedException` for interrupted startup.
- Kept `MysterStream.downloadFile(...)` asynchronous and `void`; failures that happen after scheduling still use the existing progress/error path.
- Added an async start-failure callback to `MSDownloadParams` for failures that happen after scheduling but before the download can start.
- Kept per-file overwrite/cancel decisions file-specific, but moved the UI prompt out of `MultiSourceUtils` behind `ExistingDownloadTargetHandler`.
- Centralized configured-folder lookup, validation, and retry/cancel prompting in `DownloadDirectoryChooser` so SearchTab, ClientWindow, and FileListAction share the same behavior.
- Kept EDT/error-dialog behavior out of `MSDownloadParams`; the record remains download-core data, while `DownloadStartErrorDialog` owns the Swing callback wiring.
- Moved startup alert strings into `DownloadStartErrorDialog.messageFor(...)`, keyed by exception subclass instead of `exception.getMessage()`, so future i18n work has a clear translation boundary.

## Deviations From Plan

- The plan originally described `DownloadTargetException` as the checked root in a few places. During implementation, the agreed model became a broader `DownloadStartException` root with `DownloadTargetException` under it. The plan file was updated to match.
- `FileListAction` was updated even though the plan initially focused on `ClientWindow`, because it is another ClientWindow download entry point that directly creates `MSDownloadParams`.

## Verification

- `mvn -q -DskipTests compile` — passed.
- `mvn -q -DskipTests test-compile` — passed.
- `mvn -q -Dtest=TestMultiSourceUtils,TestDownloadDirectoryValidator,com.myster.ui.TestDownloadDirectoryChooser,com.myster.ui.TestDownloadStartErrorDialog,TestClientGenericHandleObject,TestClientMPG3HandleObject test` — passed.
- `git diff --check` — passed.

## Full Suite Status

`mvn -q test` did not pass in this environment. The failures appear unrelated to this change:

- Swing/X11 initialization failures: cannot connect to X11 `DISPLAY=:0.0`.
- Java Preferences file-lock failures in custom type tests.
- UDP socket bind failures in `TestAsyncDatagramSocket`.
- Existing `~/.myster/Incoming` read-only state causing multi-source download tests to fail.

## Follow-Up

- Add UI/manual smoke coverage for selecting multiple search results and confirming only one folder chooser appears.
- Consider a future plan for returning an async result/future from `MysterStream.downloadFile(...)` if server/file-missing failures need to be handled by top-level UI commands.
- Consider a future download-boundary cleanup that introduces an intermediate UI/session download-start module. `MSDownloadParams` currently reduces coupling by bundling the data the download initiator needs, but it still carries `MysterFrameContext`; a cleaner split would let the frontend assemble GUI/session dependencies outside `msdownload` and pass only core download request data into the download engine.
