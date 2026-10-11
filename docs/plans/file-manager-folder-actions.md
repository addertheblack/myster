# File manager folder actions

## 1. Goal

Make the File Manager preferences folder path copyable, expose the complete path in a tooltip,
open the displayed folder in the desktop file manager, and rescan for new files on demand.

## 2. Scope

Keep the existing folder chooser and Apply/Reset preferences behavior. No changes to file
filtering, metadata extraction, or sharing permissions.

## 3. Assumptions

Open Folder acts on the displayed path, including a pending folder choice. Refresh acts on
the saved path for the selected type and is disabled until pending folder changes are applied.

## 4. Design

Replace the shortened label with a borderless, read-only JTextField storing the complete path.
Left-align the Shared Folder label, right-align path text, and align Choose Folder/Open Folder to the
panel's right edge. Use standard text selection and keyboard copy. Open the directory through Desktop
on a background promise, reporting failures on the EDT. Add Refresh beside the shared-file
heading and reuse the existing indexing progress timer.

## 5. Architecture connections

FmiChooser uses FileTypeList's existing background indexer through getFileTypeList(). A new
refresh() entry point bypasses the hourly cache interval, reuses an ongoing scan, and preserves
disabled sharing. Folder opening uses PromiseFutures for EDT-safe completion.

## 6. Decisions

Use the platform-neutral label Open Folder. Retain old indexed files until the rescan completes.
Place Open Folder before Choose Folder in the right-aligned button group.
Disable Refresh while indexing, while sharing is disabled, or when the displayed folder differs
from the saved folder. Do not apply pending preferences implicitly.

## 7. Acceptance criteria

- Complete paths remain selectable and copyable, including long paths; tooltips show the full path.
- The path has no visible border, its text is right-aligned, Shared Folder is left-aligned, and folder action buttons are right-aligned.
- Open Folder opens the displayed directory and reports unsupported desktop actions or I/O errors.
- Refresh detects added and removed files immediately without changing saved preferences.
- Disabled sharing remains disabled and ongoing indexing keeps the UI responsive.

---
✦ IMPLEMENTATION DETAILS

## 8. Files

- src/main/java/com/myster/filemanager/ui/FmiChooser.java
- src/main/java/com/myster/filemanager/FileTypeList.java
- src/test/java/com/myster/filemanager/TestFileTypeList.java
- docs/impl_summary/file-manager-folder-actions.md

## 9. Steps

1. Add a synchronized refresh entry point to FileTypeList.
2. Replace path truncation with the full-path text field supporting selection and keyboard copy.
3. Add asynchronous Open Folder and Refresh actions, updating enabled states with the existing timer.

## 10. Verification

Add regression tests for immediate added/removed-file discovery and disabled sharing. Run
file-manager tests and the existing Maven test suite. Review panel layout and clipboard wiring;
actual desktop opening and clipboard integration require an interactive desktop smoke test.

## 11. Documentation

Document refresh semantics in Javadoc and folder actions in the panel class Javadoc. Review
existing design docs for stale contracts and write the implementation summary after verification.
