# Tree MCList: Arrow Keys for Multiple Selected Folders

## Design Section

### 1. Summary

Make Right Arrow open every selected folder in a tree MCList and Left Arrow close every selected folder. The shared keyboard handler currently acts on only the first selected row. Apply each key press to the selection as a single operation so changing the tree's visible rows cannot skip folders or affect unrelated items.

### 2. Non-goals

- Recursive expand/collapse of unselected descendants or an expand-all command.
- Changes to mouse chevrons, double-click behavior, selection modes, or other keyboard commands.
- New parent/child focus navigation or modifier-specific shortcuts.
- Changes to flat MCLists, tree sorting, public APIs, persistence, or network behavior.

### 3. Assumptions & open questions

- “Folder” means any tree container, including containers with custom icons.
- Operate on folders selected and visible when the key is pressed. Ignore selected leaf rows, even when a leaf is the first selected row.
- Preserve the existing selection behavior: visible selected items remain selected; descendants hidden by collapsing a folder become unselected.
- No blocking questions. The scope is the existing Left/Right handler, including its current handling of keyboard modifiers.

### 4. Proposed design

When Left or Right is pressed, capture the selected folder objects before changing anything. Right sets every captured folder to open; Left sets every captured folder to closed. Rebuild the visible tree once after all changes, and only if a folder changed state. This avoids using stale row positions after children appear or disappear.

Consume the arrow event whenever the captured selection contains a folder, including when all selected folders already have the requested state. This preserves the current suppression of the table's horizontal navigation. With no selected folders, retain the existing fallback behavior.

### 5. Architecture connections

The shared tree factory already attaches the keyboard listener to every tree MCList it creates. The listener reads selection through the existing JMCList selection model, updates folder objects, and asks the tree model to rebuild the visible rows. The model continues to own sorting and deselection of descendants hidden by collapsed folders. The client file browser receives the fix through its existing use of the factory, without window-specific handlers.

| New / changed thing | Owned / created by | Called / used by | Connects to (existing) |
|---|---|---|---|
| Multi-folder arrow operation | Shared TreeMCList keyboard listener | Tree lists created by the factory, including the client file browser | JMCList selection and existing tree item open state |
| One rebuild after the operation | Existing tree model | Keyboard listener after state changes | Visible-row reconstruction, sorting, and hidden-descendant deselection |

There are no new protocols, file formats, persisted settings, or public APIs. Tree lists that currently allow only one selected row retain that selection mode.

### 6. Key decisions & edge cases

- Set a requested state rather than toggle: mixed open/closed selections all converge to the key's requested state.
- Capture the initial selection by object identity before rebuilding. If a selected parent and its selected child folder are collapsed together, both receive the closed state, even though the child becomes hidden and unselected afterward.
- Leave unselected folders' open states alone. Collapsing a parent still hides its descendants, and reopening it retains unselected descendants' previous open states.
- Empty folders are containers and participate normally.
- Preserve selected visible folders and leaves through row shifts using the existing selection model; do not clear and reselect rows.

### 7. Acceptance criteria

- [x] Right Arrow opens all selected folders, including noncontiguous selections.
- [x] Left Arrow closes all selected folders, including noncontiguous selections.
- [x] Mixed open/closed folder selections end in the requested state; repeated presses are harmless.
- [x] Selected leaves are ignored without preventing selected folders from being processed.
- [x] Unselected folders retain their open state, and newly revealed descendants are not automatically opened or selected.
- [x] Selected parent and child folders both close; hidden descendants are unselected according to existing behavior.
- [x] Visible selected items remain selected after expansion/collapse changes row positions; a Right-then-Left sequence acts on the same folders.
- [x] Arrow events with selected folders are consumed, even for a no-op; empty and leaf-only selections retain existing fallback behavior.
- [ ] Single-folder behavior, other keys, mouse interactions, and flat MCLists retain their existing behavior.

Automated checks verify the first eight criteria plus single-folder behavior and unrelated-key pass-through. Interactive mouse and keyboard smoke checks remain pending because the configured X11 display is unreachable; see the implementation summary for verification results.

---
## ✦ IMPLEMENTATION DETAILS (for the implementation agent)
---

### 8. Affected files / classes

- `src/main/java/com/general/mclist/TreeMCList.java` — change `createTreeKeyboardNavigationHandler` to process the full selection in one operation.
- **New:** `src/test/java/com/general/mclist/TestTreeMCListKeyboardNavigation.java` — behavioral regression tests against a real tree list and its installed keyboard listener.
- `docs/conventions/myster-coding-conventions.md` — retain the selection/rebuild note recorded while planning.
- `docs/plans/README.md` — plan index entry added during planning.
- **New during implementation:** `docs/impl_summary/tree-mclist-multi-folder-arrows.md` — implementation results and verification.

No changes to `JMCList`, `TreeMCListTableModel`, or individual window classes are expected.

### 9. Step-by-step implementation

1. In `TreeMCList.createTreeKeyboardNavigationHandler(JMCList<E>)`, return immediately for key codes other than `KeyEvent.VK_RIGHT` and `KeyEvent.VK_LEFT`.
2. Use `list.getSelectedIndexes()` and `treeModel.getRow(index)` to materialize a snapshot of selected `TreeMCListItem<E>` objects filtered by `isContainer()`. These are model/rendered-row indexes from the custom selection model; do not retain indexes for use after rebuilding.
3. If the snapshot is empty, return without consuming the event. Otherwise derive the requested open state from the arrow key and set it on every folder whose state differs, tracking whether anything changed.
4. After the complete state update, call `treeModel.resortAndRebuild()` exactly once if anything changed. Consume the event whenever the folder snapshot was nonempty, including a no-op. Keep execution synchronous on the Swing EDT in the current listener.
5. Preserve existing infrastructure:
   - `JMCList.tableChanged` shields the item-based selection model from JTable clearing selection during model updates.
   - `TreeMCListTableModel.buildRenderedListRecursively` deselects descendants of closed folders via `unselectRecursively`.
   - `TreeMCListItem.setOpen` does not rebuild; batching these setters is sufficient. Do not introduce another selection store or a public batch API.
6. Add the tests below, with list creation, selection, key handling, and assertions inside `SwingUtilities.invokeAndWait`. Build trees using `TreePathString`, `TreeMCListItem`, and delegates following `TestTreeMCListTableModel`; enable multiple selection explicitly because the tree factory defaults to single selection.
7. Exercise the installed key listeners with a `KEY_PRESSED` event targeted at the list, stopping once consumed, without exposing the private handler solely for testing. Direct listener invocation tests the handler without requiring a focused window; verify real event dispatch in the manual smoke test.
8. Run focused regression coverage with `mvn -Djava.awt.headless=true -Dtest=TestTreeMCListKeyboardNavigation,TestTreeMCListTableModel,TestJMCListSelectionModel,TestTreeMCListIcons test`, using the project's Java 26 toolchain. Run `mvn test` after the focused checks pass. Record any environmental blockers separately from test failures.
9. Complete the manual smoke test and write the implementation summary with changes, verification, and any deviations.

### 10. Tests to write

Use real item objects and assert their state and selected identities, plus visible children, rather than relying only on row numbers. Attach a `TableModelListener` after fixture setup to verify one data-change notification for a changed operation and none for an already-satisfied operation.

- **Open multiple folders:** select separated closed siblings with children and leave an unselected folder between them. Right opens every selected folder, reveals their children, preserves selection by identity, and leaves the unselected folder closed. Include an unselected closed subfolder to verify expansion is not recursive.
- **Close multiple folders:** select open siblings with visible children. Left closes them all despite changing row positions and rebuilds once. Verify an unselected open sibling retains its state.
- **Repeated key sequence:** Right followed by Left operates on the same selected folder objects after row indexes shift.
- **Mixed states / no-op:** exercise both arrows with a mixture of open and closed selected folders, including an empty folder. Verify convergence, then repeat the same arrow and assert no rebuild and a consumed event.
- **Mixed leaves and folders:** place a selected leaf before selected folders and verify both arrows still act on all selected folders. A visible selected leaf outside collapsing folders retains selection.
- **Selected ancestor and descendant:** select an open parent and its open child folder, then press Left. Both become closed, the child and other hidden descendants become unselected, and the parent stays selected. Reopen the parent and verify the child remains closed.
- **Fallback and unaffected keys:** no selection and leaf-only selection leave both arrows unconsumed by the tree handler with no model changes. An unrelated key leaves a selected folder unchanged and is not consumed by the tree handler. A single-folder selection still opens/closes normally.

Manual smoke test:

1. In the client file browser, select several folders with Shift and with Ctrl/Cmd. Press Right and confirm all open while their selections remain visible.
2. Press Left and confirm all selected folders close. Repeat both keys with mixed states and a leaf as the first selected row.
3. Select a visible parent and child folder, close both, then reopen the parent and confirm the child is closed.
4. Verify arrow keys do not move to another column or discard the folder selection, and verify mouse chevrons, double-click, and Up/Down navigation still work.

### 11. Docs / Javadoc to update

- Update `TreeMCList.createTreeKeyboardNavigationHandler` documentation to describe all selected containers, the initial-selection snapshot, and one rebuild per changed operation.
- Keep the JMCList selection/rebuild convention consistent with the implementation. No broader design document currently describes this keyboard behavior, so none needs updating.
- Write `docs/impl_summary/tree-mclist-multi-folder-arrows.md` during implementation; do not mark acceptance criteria complete until verified.
