# File manager folder actions

Implemented a complete, read-only folder-path field with a full-path tooltip and keyboard
copying, an asynchronous Open Folder action, and a Refresh button for the selected type's folder.
Shared Folder is left-aligned and path text is right-aligned; the path field is borderless with the panel
background, and Choose Folder/Open Folder align to the right edge.
Open Folder appears to the left of Choose Folder.
The path spans the space freed by removing Copy Path; the label column stays compact, and
Refresh also aligns with the right edge of the folder controls.

## Files changed

- `src/main/java/com/myster/filemanager/ui/FmiChooser.java`: folder controls, selectable path,
  full path display, desktop error reporting, and refresh availability/progress.
- `src/main/java/com/myster/filemanager/FileTypeList.java`: synchronized asynchronous `refresh()`.
- `src/test/java/com/myster/filemanager/TestFileTypeList.java`: added/removed-file refresh and
  disabled-sharing regression tests.
- `docs/plans/file-manager-folder-actions.md`: recorded the implementation plan for this request.

## Decisions

Open Folder opens the displayed directory, including an unapplied folder choice. Refresh uses
the saved directory and is disabled while a folder change awaits Apply, while indexing, or while
saved sharing is disabled. An existing scan is reused; completed file lists remain available
until a replacement scan publishes its result. Path text is never shortened internally, and
normal keyboard selection/copy works in the borderless field spanning both remaining columns.
Existing progress polling also updates
the Refresh button; both use the same indexing-state snapshot to avoid a completion race.

## Validation

- `mvn -o -Dtest=TestFileTypeList test`: 29 tests passed.
- Full Maven suite with `java.awt.headless=true`, temporary `user.home`, and temporary Java
  preferences: 783 tests ran; 778 passed and five encountered sandbox restrictions on sockets.
- The five socket tests (`TestApplicationContext`, `TestAsyncDatagramSocket`) passed when rerun
  outside the sandbox with the same temporary application data and preferences.
- Temporary headless Swing smoke harness verified the complete path, tooltip, text selection,
  Refresh action wiring, and unclipped button dimensions at 450 × 300. Inspected a rendered panel.
- `git diff --check` passed.
- Layout follow-up: `mvn -o -DskipTests compile` passed. The headless Swing smoke check
  confirmed left alignment of Shared Folder, right alignment of path text and Open Folder,
  and the path's empty border/transparent background. Inspected the updated 450 × 300 rendering.

Initial test runs needed correction: the package wildcard selected no tests; the default full
suite required a display; headless execution initially still used restricted preferences and
application directories. The final isolated run resolved these environment issues.

## Documentation and follow-up

Updated panel and refresh Javadoc. Reviewed existing design docs; none describes these folder
controls or needs updating. No implementation deviations or additional automated tests are
needed for this change. Actual desktop opening and system clipboard interaction still require
an interactive desktop smoke test; headless validation cannot exercise them.
