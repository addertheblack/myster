# Search Download To Shared Folder

## 1. Summary

Search result multi-selection currently invokes each selected result's `downloadTo()` method independently, so every item opens its own destination chooser. Change top-level download commands to choose or resolve the destination folder before scheduling downloads, validate/report folder errors at that command level, and pass the shared base directory into each selected download. The lower multi-source target-file code should prepare one concrete file path from an already-decided folder, not ask the user for folders after download scheduling has started.

## 2. Non-goals

- Do not redesign multi-source download startup, queueing, progress windows, or partial-file handling.
- Do not change the normal `Download` menu item beyond incidental imports or helper reuse.
- Do not add folder-recursive search downloads; search results are still individual files.
- Do not attempt to recover every later filesystem failure up front. If a destination becomes unwritable after scheduling or a specific file cannot be created, the download/progress error path may report the failure.
- Do not change server, network, or search protocol behaviour.
- Do not make remote server connection/file-missing failures synchronously catchable by `ClientWindow` or `SearchTab` in this milestone. `MysterStream.downloadFile(...)` is currently asynchronous and returns `void`; changing that would require a broader future/result API.

## 3. Assumptions & Open Questions

- The affected broken flow is the search results table in `SearchTab`, not the `ClientWindow` file browser, because `ClientWindow` already chooses a base folder once for multi-selection.
- `Download To...` should always ask the user for a destination folder, even if the file type has a configured default download directory.
- Folder validation should happen before scheduling downloads: selected path exists, is a directory, and is writable.
- If validation fails, the user should get a clear alert and be returned to the chooser. Cancelling the chooser or the retry alert cancels the whole multi-download operation.
- Existing per-file collision prompts should remain per file. A single selected destination can still contain some existing target names and not others.
- All UI-started downloads should provide a destination directory in `MSDownloadParams.targetDir`. If no configured default exists for plain `Download`, the UI command should choose/ask once at the top level rather than relying on `getFileToDownloadTo(...)` to ask later.

## 4. Proposed Design

Extract the existing destination-folder chooser/validation behaviour that is currently embedded in `getFileToDownloadTo(...)` into two pieces: a GUI-owned chooser/error helper and a non-GUI directory validator. Use the GUI helper from `SearchTab` and `ClientWindow` for the `Download To...` command, and also when a plain `Download` command has no configured destination. Keep `SearchResult` as the abstraction for source-specific downloads by enhancing it to accept a caller-chosen destination folder; this preserves the historical ability for search results to come from different backends while letting the search UI make the one shared destination decision.

The search UI flow becomes:

- User selects one or more search result rows.
- User chooses `Download To...`.
- `SearchTab` asks once for a writable base directory.
- If the user cancels, no downloads are started.
- If the folder is invalid, `SearchTab` shows an alert through the shared helper and asks again.
- Once a valid base directory is chosen, `SearchTab` loops the selected rows and starts each `SearchResult` with that same `Path`.

The download data flow stays compatible with the existing download engine. `SearchTab` passes the base directory into `SearchResult.downloadTo(Path)`, `MysterSearchResult` creates `MSDownloadParams` with `targetDir = Optional.of(baseDir)` and `subDirectory = Path.of("")`, and the existing progress/download listeners eventually call `MultiSourceUtils.getFileToDownloadTo(...)`. After this change, that method handles target filename construction, `.i` partial suffixes, subdirectory creation, typed destination-preparation failures, and per-file collision decisions through an injected non-GUI policy/callback. It should not show a folder chooser and should not reference `DefaultDialogProvider`, `AnswerDialog`, `JFileChooser`, or other GUI code. The new chooser helper should reuse the folder-selection logic by extraction, not by calling the whole file-target method with a fake filename.

Introduce a small checked exception tree rooted in `DownloadStartException` for failures that prevent local download scheduling/preparation. Destination setup failures live under `DownloadTargetException`, with specific subclasses such as `MissingDownloadDirectoryException`, `InvalidDownloadDirectoryException`, and `UnwritableDownloadDirectoryException`. Top-level UI commands can catch these before scheduling where possible and show human text. If the folder becomes invalid after the async download starts, `DownloadInitiator` catches the same typed exception and reports it through the existing progress UI.

Use two separate callback concepts because they answer different questions:

- **Download start failure callback**: carried with the download request and called asynchronously if the download cannot start after scheduling, such as connection failure, missing file stats, or local target setup failure discovered in `DownloadInitiator`. UI callers wrap this callback onto the EDT before showing dialogs.
- **Existing target decision callback**: passed into final target-file preparation and called synchronously when a specific `.i` or final target file already exists. It returns a decision such as overwrite or cancel. It does not report errors and it does not choose folders.

Because `docs/conventions/standing-refactors.md` says `MultiSourceUtilities` must become `MultiSourceUtils` when touched, the implementation should include that mechanical rename as part of this work. The GUI chooser must live outside `msdownload`; the rename is still required because the implementation will touch `getFileToDownloadTo(...)` references and tests.

## 5. Architecture Connections

The new behaviour should be expressed at the boundary where the UI knows the user selected multiple rows and the download engine already accepts a base target directory. Avoid pushing multi-select knowledge into `DownloadInitiator` or progress-window classes.

| New / changed thing | Owned / created by | Called / used by | Connects to (existing) |
|---|---|---|---|
| `DownloadDirectoryChooser` (new) | `com.myster.ui` | `SearchTab`, `ClientWindow`, `FileListAction` | Extracted folder-selection loop, `JFileChooser`, `AnswerDialog`, `DownloadDirectoryValidator` |
| `DownloadDirectoryValidator` (new) | `com.myster.net.stream.client.msdownload` | `DownloadDirectoryChooser`, `MultiSourceUtils`, source-specific download implementations | `Files` validation, typed destination exceptions |
| `MultiSourceUtils` (renamed from `MultiSourceUtilities`) | `com.myster.net.stream.client.msdownload` | Existing download/progress code | Existing target-file resolution and partial-download helpers |
| Existing-target decision callback (new or renamed from current dialog provider) | `com.myster.net.stream.client.msdownload` interface, UI implementation in progress/client UI | `MultiSourceUtils.getFileToDownloadTo(...)` | Per-file `.i`/final-file collision decisions without GUI dependency in core |
| `DownloadStartException` tree (new) | `com.myster.net.stream.client.msdownload` | `SearchResult`, `MultiSourceUtils`, download UI commands, `DownloadInitiator` | Existing progress/error reporting |
| Download start failure callback | `MSDownloadParams` / source-specific request creation | `DownloadInitiator` | UI error handler wrapped onto EDT by `SearchTab` / `ClientWindow` common helper |
| `SearchResult.downloadTo(Path baseDirectory)` | `com.myster.search.SearchResult` | `SearchTab` multi-select handler | Existing `MysterSearchResult`, search result list items |
| `MysterSearchResult.downloadTo(Path baseDirectory)` | `com.myster.search.MysterSearchResult` | `SearchResult` callers | Existing `MSDownloadParams`, `MysterProtocol.getStream().downloadFile(...)` |
| `SearchTab` `Download To...` handler | `com.myster.search.ui.SearchTab` | User context menu action | Existing `JMCList<SearchResult>`, `ContextMenu.createDownloadToItem(...)` |
| `ClientWindow` helper reuse, if practical | `com.myster.client.ui.ClientWindow` | Existing `Download To...` handler | Existing recursive download traversal |

No protocol or file format changes are introduced. The long-lived Java API changes are adding a destination-aware search result download method and typed destination-setup exceptions. These are local to the application. The new chooser is UI plumbing only: it selects and validates a base folder, but it does not start downloads. The download-core package remains GUI-free.

## 6. Key Decisions & Edge Cases

- Choose the base folder before starting any selected downloads. This prevents partial scheduling where the first item prompts and later selected items behave differently.
- Preserve search-source abstraction. `SearchTab` should not construct `MSDownloadParams`; it only chooses a folder and asks each selected `SearchResult` to download to that folder.
- Reuse by extraction, not by misuse. Do not call `getFileToDownloadTo(...)` during multi-select folder choice because it is intentionally file-specific and may prompt for overwrite on a particular filename.
- Remove folder-chooser fallback from `getFileToDownloadTo(...)`. By the time it runs, the download engine no longer knows whether this is one item or part of a multi-download, so interactive folder recovery there is the wrong layer.
- Keep GUI dependencies out of `msdownload` destination preparation. `getFileToDownloadTo(...)` may validate paths and throw typed exceptions, but it must not ask the user anything.
- Keep `SearchResult.downloadTo()` as a convenience/default method if that minimizes churn for single-item callers and tests, but make the multi-select handler call the new `downloadTo(Path)` overload.
- Convert/require the selected folder path to an absolute path before building `MSDownloadParams`, because `getFileToDownloadTo(...)` rejects relative base directories.
- Keep per-file overwrite/cancel decisions. A shared directory does not imply a shared overwrite decision. However, the dialog itself belongs in UI/progress code; `getFileToDownloadTo(...)` should receive the decision through a narrowly named callback/interface.
- Keep the start-failure callback and existing-target decision callback separate. One is async notification that startup failed; the other is a synchronous answer to a per-file collision question.
- Validation is intentionally limited to the selected base directory. Per-download subdirectory creation and final file creation remain in the existing download path.
- Preserve the `Download` versus `Download To...` distinction: `Download` uses the configured type directory when present and asks once only if no destination exists; `Download To...` always asks.
- Do not broaden this milestone into a full async download result API. Network/server/file-missing failures still surface in the progress UI unless a separate plan changes `MysterStream.downloadFile(...)` to return a future/result.

## 7. Acceptance Criteria

- [ ] In search results, selecting four rows and choosing `Download To...` opens exactly one folder chooser before downloads are scheduled.
- [ ] All selected search downloads receive the same chosen base directory.
- [ ] Cancelling the folder chooser starts no downloads.
- [ ] Choosing a non-existent path, a file, or a non-writable directory shows an alert and lets the user choose another folder or cancel.
- [ ] `MultiSourceUtils.getFileToDownloadTo(...)` no longer opens a folder chooser; missing/invalid/unwritable supplied destination is reported as a typed `DownloadStartException`.
- [ ] `MultiSourceUtils.getFileToDownloadTo(...)` and the non-GUI validator do not depend on `DefaultDialogProvider`, `AnswerDialog`, `JFileChooser`, or Swing UI classes.
- [ ] Existing per-file duplicate-name prompts and late download errors still work.
- [ ] `ClientWindow` multi-select `Download To...` continues to ask once and download all selected top-level items recursively.
- [ ] Automated tests cover the shared folder validation helper and the destination-aware search result path.

---
## ✦ IMPLEMENTATION DETAILS (for the implementation agent)
---

## 8. Affected Files / Classes

- `src/main/java/com/myster/net/stream/client/msdownload/MultiSourceUtilities.java` -> `MultiSourceUtils.java` — apply standing rename and keep existing target-file/download utility behaviour intact.
- `src/main/java/com/myster/net/stream/client/msdownload/DownloadDirectoryValidator.java` (new) — validate a known writable base folder without any GUI dependencies.
- `src/main/java/com/myster/ui/DownloadDirectoryChooser.java` (new) — select and validate a writable base folder without starting downloads.
- `src/main/java/com/myster/net/stream/client/msdownload/DownloadStartException.java` (new) — checked base class for failures that prevent local download scheduling/preparation.
- `src/main/java/com/myster/net/stream/client/msdownload/DownloadTargetException.java` (new) — checked base class for destination setup failures.
- `src/main/java/com/myster/net/stream/client/msdownload/MissingDownloadDirectoryException.java` (new) — thrown when a download is started without a destination directory.
- `src/main/java/com/myster/net/stream/client/msdownload/InvalidDownloadDirectoryException.java` (new) — thrown when the destination path is missing or not a directory.
- `src/main/java/com/myster/net/stream/client/msdownload/UnwritableDownloadDirectoryException.java` (new) — thrown when the destination or target subdirectory cannot be written/created.
- `src/main/java/com/myster/search/SearchResult.java` — add `downloadTo(Path baseDirectory)` API and allow synchronous destination setup failures if validation is done before scheduling.
- `src/main/java/com/myster/search/MysterSearchResult.java` — implement destination-aware `downloadTo(Path)` by creating `MSDownloadParams` with `Optional.of(baseDirectory)`.
- `src/main/java/com/myster/search/ui/SearchTab.java` — choose/validate one folder at the command level and pass it to every selected result.
- `src/main/java/com/myster/client/ui/ClientWindow.java` — use the shared helper for `Download To...`, and for plain `Download` only when no configured type directory exists.
- `src/main/java/com/myster/client/ui/FileListAction.java` — use the same helper for direct file-list downloads.
- `src/main/java/com/myster/net/stream/client/msdownload/DownloadInitiator.java` — treat typed destination setup failures as user-readable progress errors rather than triggering another chooser.
- `src/main/java/com/myster/net/stream/client/msdownload/ExistingDownloadTargetHandler.java` (new, name flexible) — non-GUI interface for per-file overwrite/cancel decisions.
- `src/main/java/com/myster/net/stream/client/msdownload/MSDownloadParams.java` — carry a download-start failure callback, defaulting to a no-op for callers that do not care.
- `src/main/java/com/myster/net/stream/client/msdownload/DownloadInitiator.DownloadInitiatorListener` — allow `getFileToDownloadTo(...)` to throw `DownloadStartException`.
- `src/main/java/com/myster/progress/ui/ProgressManagerDownloadListener.java` — update utility class references after rename.
- `src/main/java/com/myster/progress/ui/EdtFileProgressWindow.java` — update utility class references after rename.
- `src/main/java/com/myster/progress/ui/EdtDownloadInitiatorListener.java` — propagate the `DownloadStartException` signature from `DownloadInitiatorListener.getFileToDownloadTo(...)`.
- `src/main/java/com/myster/progress/ui/FileProgressWindow.java` — propagate the `DownloadStartException` signature from `DownloadInitiatorListener.getFileToDownloadTo(...)`.
- `src/main/java/com/myster/net/stream/client/msdownload/MSPartialFile.java` — update utility class references after rename.
- `src/test/java/com/myster/net/stream/client/msdownload/TestMultiSourceUtilities.java` -> `TestMultiSourceUtils.java` — apply rename and preserve existing target-file utility tests.
- `src/test/java/com/myster/net/stream/client/msdownload/TestDownloadDirectoryValidator.java` (new) — cover non-GUI base-directory validation.
- `src/test/java/com/myster/ui/TestDownloadDirectoryChooser.java` (new) — cover chooser cancellation and retry behaviour.
- `src/test/java/com/myster/search/ui/TestClientGenericHandleObject.java` — update `SearchResult` stub for the new overload if needed.
- `src/test/java/com/myster/search/ui/TestClientMPG3HandleObject.java` — update `SearchResult` stub for the new overload if needed.

## 9. Step-by-Step Implementation

1. Apply the standing utility rename:
   - Rename `MultiSourceUtilities` class/file to `MultiSourceUtils`.
   - Rename `TestMultiSourceUtilities` to `TestMultiSourceUtils`.
   - Update all imports and fully qualified references from `MultiSourceUtilities` to `MultiSourceUtils`.
   - Do this mechanically before behavioural edits so any later failures are easier to isolate.

2. Add a non-GUI directory validator in `com.myster.net.stream.client.msdownload.DownloadDirectoryValidator`:
   - Public entry point: `public static Path validateDownloadDirectory(Path path) throws DownloadTargetException`.
   - Convert the supplied path to an absolute normalized path.
   - Validate `Files.exists(path)`, `Files.isDirectory(path)`, and `Files.isWritable(path)`.
   - Throw specific `DownloadTargetException` subclasses for missing, non-directory, and unwritable paths.
   - Do not import or reference Swing, `AnswerDialog`, `JFileChooser`, `Frame`, `DefaultDialogProvider`, or any other GUI class.

3. Add a shared UI directory chooser in `com.myster.ui.DownloadDirectoryChooser`:
   - Move the existing base-directory selection loop from `MultiSourceUtils.getFileToDownloadTo(...)` into this class where possible.
   - Public entry point: `public static Optional<Path> chooseWritableDownloadDirectory(Frame parentFrame, String title)`.
   - Package-private testable overload using a small UI-local dialog abstraction if needed; do not reuse an `msdownload` dialog provider.
   - Use `JFileChooser` for selection and `AnswerDialog` for validation retry/cancel alerts.
   - Return `Optional.empty()` if the user cancels.
   - Validate by calling `DownloadDirectoryValidator.validateDownloadDirectory(...)`.
   - For validation failure, show an alert with `OK` and `Cancel`. `OK` loops back to the chooser; `Cancel` returns `Optional.empty()`.
   - Keep validation messages specific enough to distinguish missing path, non-directory path, and read-only/unwritable path.

4. Add typed download startup exceptions:
   - Base class: `DownloadStartException extends Exception`.
   - Destination base class: `DownloadTargetException extends DownloadStartException`.
   - Include user-readable messages suitable for a dialog or progress text.
   - Add subclasses only where they help callers render better text:
     - `MissingDownloadDirectoryException`
     - `InvalidDownloadDirectoryException`
     - `UnwritableDownloadDirectoryException`
   - Keep these focused on local destination setup. Do not model server/network failures here in this milestone.

5. Keep `getFileToDownloadTo(...)` responsible for final target-file resolution:
   - Remove its folder chooser path. If `absolutePathToDownloadFolderBaseDir` is empty, throw `MissingDownloadDirectoryException`.
   - Preserve `relativePath` and absolute-base precondition exceptions, or convert invalid destination cases to typed `DownloadTargetException` where appropriate.
   - Validate the supplied base directory through `DownloadDirectoryValidator.validateDownloadDirectory(...)`.
   - Try to create `targetDirectory = baseDir.resolve(relativePath)` and throw `UnwritableDownloadDirectoryException` if creation fails or the result is not writable.
   - Preserve final path construction with `fileName + ".i"`.
   - Preserve per-file duplicate-name behaviour for `.i` and final-file collisions, but ask through a narrowly named non-GUI callback such as `ExistingDownloadTargetHandler`.
   - Keep returning `null` only when the handler reports user cancellation of a per-file overwrite decision; use checked exceptions for destination setup failures.
   - Remove `DefaultDialogProvider`, `DialogProvider`, `AnswerDialog`, `JFileChooser`, `Frame`, and folder-picker code from `MultiSourceUtils`.
   - Move any real dialog implementation for existing-file overwrite decisions into progress/UI code where a parent frame is available.

6. Add the asynchronous download-start failure callback:
   - Add a `Consumer<DownloadStartException>` or small named functional interface to `MSDownloadParams`.
   - Default it to a no-op so existing callers are not forced to handle startup failures.
   - `DownloadInitiator` calls it only when the download cannot start after the async request has been accepted: connection failure, startup interruption, file-stat failure, target setup failure, or other pre-`MultiSourceDownload.start()` failure.
   - Do not call it for normal post-start segment/download failures; those remain progress-window/download-listener concerns.
   - UI callers must wrap the handler onto the EDT. `MysterStream`, `DownloadInitiator`, and `MSDownloadParams` must not know about Swing threading.

7. Update `SearchResult`:
   - Add `void downloadTo(Path baseDirectory);` with `java.nio.file.Path` import.
   - If destination validation is performed before scheduling, declare `throws DownloadStartException` on this method.
   - Remove or de-emphasize no-arg `downloadTo()` where practical. It encourages the source implementation to ask for a folder after the UI has lost multi-select context.
   - Update all implementations/test stubs explicitly.

8. Update `MysterSearchResult`:
   - For `download()`, resolve the configured type directory at this source-specific layer only if that remains the established pattern, but do not pass `Optional.empty()` to `MSDownloadParams`.
   - If no configured type directory exists for plain `download()`, prefer having the UI command ask once and call a destination-aware method rather than letting `MysterSearchResult` prompt later.
   - Add `downloadTo(Path baseDirectory)`:
     - require non-null `baseDirectory`;
     - validate it synchronously with `DownloadDirectoryValidator.validateDownloadDirectory(...)` before scheduling if the interface allows throwing;
     - pass `Optional.of(baseDirectory)` to `MSDownloadParams`;
     - pass `Path.of("")` for `subDirectory`.
   - Do not use `Optional.empty()` as a signal to prompt later.

9. Update `SearchTab.addPopUpMenus()`:
   - For plain `Download`, collect selected rows and resolve a base directory once:
     - use the configured type directory if present and valid;
     - otherwise call `com.myster.ui.DownloadDirectoryChooser.chooseWritableDownloadDirectory(...)`;
     - if cancelled or invalid, start no downloads.
   - In the `downloadToMenuItem` listener, collect `int[] indexes = fileList.getSelectedRows()`.
   - If no rows are selected, return.
   - Ask once:
     - `Optional<Path> baseDir = com.myster.ui.DownloadDirectoryChooser.chooseWritableDownloadDirectory(...)`.
     - Use a parent `Frame` consistent with existing dialogs. `AnswerDialog.getCenteredFrame()` is acceptable if there is no reliable frame on `SearchTab`; `SwingUtilities.getWindowAncestor(this)` can be used if converted safely to a `Frame`.
   - If empty, return.
   - Loop indexes and call `fileList.getMCListItem(i).getObject().downloadTo(baseDir.get())`, catching typed destination exceptions and routing them to the common human-readable dialog handler.
   - Do not call the old no-arg `downloadTo()` in this multi-select path.

10. Update `ClientWindow` and `FileListAction` to use the same helper:
   - Replace the manual `new DefaultDialogProvider().askForFolder(...)` block in the `Download To...` handler with `com.myster.ui.DownloadDirectoryChooser.chooseWritableDownloadDirectory(ClientWindow.this, "Select a folder to save the file in")`.
   - Keep the `Download` handler's configured-directory-first behaviour, but validate the configured directory. If no configured directory exists or it is rejected and the user chooses to retry, ask once at this top level.
   - Confirm recursive folder downloads still pass `Path.of("")` at the root and `relativePath.resolve(...)` for children.
   - Catch typed destination exceptions from preflight or immediate scheduling and show the shared human-readable dialog.
   - Use the same helper in `FileListAction`, which is a separate ClientWindow download entry point.

11. Update `DownloadInitiator` and progress listeners:
   - If `DownloadInitiatorListener.getFileToDownloadTo(...)` gains `throws DownloadStartException`, update every implementation/wrapper.
   - In `DownloadInitiator.downloadFile(...)`, catch `DownloadStartException` separately before the broad `IOException` catch and set a clearer progress message using the exception message.
   - Do not open a folder chooser from inside `DownloadInitiator` or its listener implementations.

12. Compile and fix references after the rename:
   - Search with `rg "MultiSourceUtilities"` and ensure none remain.
   - Search with `rg "downloadTo\\(" src/test src/main` and confirm every `SearchResult` implementation/stub handles the new overload.
   - Search for `Optional.empty()` in `MSDownloadParams` construction and remove any UI-started paths that rely on it to prompt later.

## 10. Tests To Write

- `TestDownloadDirectoryValidator.validateDownloadDirectory_validDirectory_returnsAbsolutePath`
  - Pass a temp directory.
  - Assert the result is absolute and normalized.

- `TestDownloadDirectoryValidator.validateDownloadDirectory_missingPath_throwsInvalidDownloadDirectory`
  - Pass a missing path.
  - Assert the specific typed exception.

- `TestDownloadDirectoryValidator.validateDownloadDirectory_filePath_throwsInvalidDownloadDirectory`
  - Pass a regular file path.
  - Assert the specific typed exception.

- `TestDownloadDirectoryChooser.chooseWritableDownloadDirectory_validSelection_returnsAbsolutePath`
  - Mock the UI-local folder prompt abstraction to return the temp directory.
  - Assert the result is present, absolute, and equals the normalized temp path.

- `TestDownloadDirectoryChooser.chooseWritableDownloadDirectory_userCancels_returnsEmpty`
  - Mock `askForFolder` to return `null`.
  - Assert no alert is required and the result is empty.

- `TestDownloadDirectoryChooser.chooseWritableDownloadDirectory_missingPath_allowsRetry`
  - First chooser result is a missing path.
  - Alert returns `OK`.
  - Second chooser result is a valid temp directory.
  - Assert result is the second path.

- `TestDownloadDirectoryChooser.chooseWritableDownloadDirectory_filePath_canCancel`
  - Chooser returns a regular file.
  - Alert returns `Cancel`.
  - Assert empty result.

- `TestDownloadDirectoryChooser.chooseWritableDownloadDirectory_unwritablePath_canRetryOrCancel`
  - If reliable on the target platforms, mark a temp directory unwritable and verify the alert path.
  - If permissions are unreliable on CI, isolate the validation predicate behind package-private methods or skip only this exact scenario with a clear reason.

- `MysterSearchResult.downloadTo_withBaseDirectory_passesTargetDir`
  - Prefer a focused unit test only if `MysterProtocol`/stream can be stubbed without excessive machinery.
  - Otherwise verify through a small fake `SearchResult` in `SearchTab` only if the UI handler is practical to exercise.

- `TestMultiSourceUtils.getFileToDownloadTo_missingBaseDirectory_throwsMissingDownloadDirectory`
  - Call the package-private overload with `Optional.empty()`.
  - Assert `MissingDownloadDirectoryException`.

- `TestMultiSourceUtils.getFileToDownloadTo_invalidBaseDirectory_throwsDownloadTargetException`
  - Use a missing path or regular-file path.
  - Assert the specific typed exception.

- `TestMultiSourceUtils.getFileToDownloadTo_validBaseDirectory_doesNotAskForFolder`
  - Use an existing-target handler that fails if called.
  - Assert a valid target file is returned.

- Manual smoke test:
  - Open Search Window.
  - Run a search that returns at least two files.
  - Select multiple rows.
  - Choose `Download To...`.
  - Confirm only one folder chooser appears and all selected downloads start using that folder.
  - Repeat with chooser cancellation and confirm no downloads start.
  - Repeat from `ClientWindow` if its handler was refactored.

## 11. Docs / Javadoc To Update

- Add Javadoc to `DownloadDirectoryChooser.chooseWritableDownloadDirectory(...)` explaining that it validates only the base directory and returns `Optional.empty()` on user cancellation.
- Add Javadoc to `DownloadDirectoryValidator.validateDownloadDirectory(...)` explaining that it is non-GUI validation for already selected/configured paths.
- Update `SearchResult.downloadTo(Path)` Javadoc/comment to clarify that callers provide a validated shared base directory.
- Add Javadoc to `DownloadStartException` and `DownloadTargetException` explaining they cover local download startup/preparation, not asynchronous network download failures.
- If `MSDownloadParams` Javadoc is touched, clarify that `targetDir` is the absolute base destination directory and `subDirectory` remains relative.
