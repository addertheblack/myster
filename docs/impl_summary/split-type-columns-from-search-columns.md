# Implementation Summary: Split Type Columns from Search-Only Columns

**Feature slug:** `split-type-columns-from-search-columns`  
**Date:** 2026-06-14  
**Plan:** `docs/plans/split-type-columns-from-search-columns.md`

---

## What was implemented

"Server" and "Ping" columns are now exclusively a search-window concern. All per-type column knowledge (headers, widths, cell values) lives in a new `FileTypeColumnHandler` interface whose implementations (`ClientGenericHandleObject`, `ClientMPG3HandleObject`) are reusable from both `SearchTab` and `ClientWindow`. The `SearchColumnDecorator` appends Server+Ping on top before the search MCList is configured. `ClientWindow` now configures its file-list columns dynamically on each type selection, and MPG3 metadata columns appear there too.

---

## Files changed

| File | Change |
|---|---|
| `com/myster/search/ui/FileTypeColumnHandler.java` | **NEW** — interface replacing `ClientHandleObject` |
| `com/myster/search/ui/SearchColumnDecorator.java` | **NEW** — wraps any handler, appends "Server"+"Ping" |
| `com/myster/search/ui/ClientHandleObject.java` | **DELETED** |
| `com/myster/search/ui/ClientGenericHandleObject.java` | Dropped Server/Ping; implements `FileTypeColumnHandler`; added `getFileItem`/`getFolderItem`; renamed `getMCListItem`→`getSearchItem`; removed `serverString`/`ping` from `GenericSearchItem` |
| `com/myster/search/ui/ClientMPG3HandleObject.java` | Renamed `getMCListItem`→`getSearchItem`; fixed `getColumnCount`/`getHeader`/`getHeaderSize` to call `getColumnCount()` (was `getNumberOfColumns()`); added `getFileItem` and `getFolderItem` overrides |
| `com/myster/search/ui/ClientInfoFactoryUtilities.java` | Return type updated to `FileTypeColumnHandler`; Javadoc added |
| `com/myster/search/ui/SearchTab.java` | `metaDateHandler` type changed; `recolumnize()` now wraps in `SearchColumnDecorator`; `addSearchResults()` uses `getSearchItem` |
| `com/general/mclist/JMCList.java` | `tableChanged()` now saves/restores per-column cell renderers around structure rebuilds (same pattern as existing selection-model save/restore) |

---

## Key design decisions made during implementation

- **`extractDirElement` kept static** with a `FileTypeColumnHandler` parameter rather than converted to an instance method. This keeps the static utility chain intact and makes the dependency explicit at each call site.
- **`MessagePak.get(key)` used for MPG3 `getFileItem`** rather than `getString(key)`. `get()` returns a string representation of any stored type, mirroring exactly how `SearchResult.getMetaData()` works (`mml.get(key).orElse(null)`), so the two item factories behave identically regardless of how the server stored the value.
- **`currentTypeHandler` initialised to `ClientGenericHandleObject`** in the field declaration. This guarantees the field is never null even if `extractFileRecordElement()` is somehow called before a type is selected.

---

## Deviations from the plan

- **`extractDirElement` kept static** (plan said to make it non-static/instance). Kept static with explicit handler parameter instead — cleaner, less risk of accidentally using stale `this.currentTypeHandler` on a different code path.
- **No separate `getNumberOfColumns` → `getColumnCount` migration on old callers**: The old `getNumberOfColumns()` name was only on `ClientHandleObject`/`ClientGenericHandleObject`. The new interface only has `getColumnCount()`. No migration needed.

---

## Javadoc / design docs updated

- `FileTypeColumnHandler` — full interface and method Javadoc written.
- `SearchColumnDecorator` — class and method Javadoc written.
- `ClientGenericHandleObject` — class Javadoc updated (Server/Ping removed from description).
- `ClientInfoFactoryUtilities.getHandler()` — Javadoc added explaining decorator pattern.
- `ClientWindow.recolumnizeFileList()` — Javadoc written.

---

## Known issues / follow-up work

- **No unit tests added yet.** The plan called for `ClientGenericHandleObjectTest`, `SearchColumnDecoratorTest`, and `ClientMPG3HandleObjectTest`. These should be added.
- **FolderMCListItem with extra columns**: For MPG3 type in `ClientWindow`, folder rows correctly show "-" for columns 2–6 (returned by `ClientMPG3HandleObject.getFolderItem()`). However, `FolderMCListItem.getValueOfColumn(1)` overrides size as before. Columns 2+ fall through to the delegate which returns `-`. This is correct behaviour but should be verified in a live MPG3 test.
- **MPG3 metadata key types**: `/BitRate` and `/Hz` may be stored as `Long` in MessagePak (not String). The implementation uses `record.metaData().get(key)` which converts any type to String via `MessagePak.convertToString()`. If they are stored as `Long`, the string will be a plain decimal number, which `Long.parseLong()` can parse. Should work correctly; verify in smoke test.

---

## Tests that should be added later

- `ClientGenericHandleObjectTest`: verify column count = 2, headers, `getSearchItem` cell values, `getFileItem` name/size cells, `getFolderItem` name cell.
- `SearchColumnDecoratorTest`: verify column count = type count + 2, Server/Ping headers at the right indices, `getSearchItem` delegates type columns and adds server/ping.
- `ClientMPG3HandleObjectTest`: verify column count = 7, `getFileItem` BitRate/Hz parsing, `getFolderItem` returns "-" for extra columns.

---

## Anything else to know

The `ClientHandleObject` interface is gone. Any external plugins or forks referencing it will need to migrate to `FileTypeColumnHandler` and rename `getMCListItem` → `getSearchItem`. The method signatures are otherwise identical.


