# Split Type Columns from Search-Only Columns

**Feature slug:** `split-type-columns-from-search-columns`  
**Date:** 2026-06-14

---

## ✦ DESIGN (for the owner/reviewer)

---

### 1. Summary

`ClientGenericHandleObject` currently bundles four columns — "File Name", "File Size", "Server", and "Ping" — into every MCList configured through `ClientInfoFactoryUtilities`. "Server" and "Ping" are search-context concepts that make no sense in the `ClientWindow` file list. This change splits the column definition into two layers: a per-type layer ("File Name", "File Size", and any type-specific extras like MPG3 fields), and a search-context layer ("Server", "Ping") that `SearchTab` appends itself. `ClientWindow` then uses the per-type layer to drive its `fileList` column headers and cell values, eliminating the hardcoded `new String[]{"Name", "Size"}` and gaining type-specific columns (e.g. MPG3 metadata) for free.

### 2. Non-goals

- No changes to the server-side protocol or metadata keys.
- No changes to `FolderMCListItem` beyond tolerating variable column counts (it already delegates correctly for columns > 1).
- No changes to how the stats panel or `FileInfoListerThread` work.
- No changes to `SearchEngine`, search result serialisation, or ping/server-stats logic.
- No UI layout changes.

### 3. Assumptions & open questions

- `JMCList.setNumberOfColumns()` / `setColumnName()` / `setColumnWidth()` work correctly on a TreeMCList-backed list after it has been created. The `TreeMCListTableModel.setColumnIdentifiers()` already calls `fireTableStructureChanged()`, and the test suite confirms it. **Assumed safe.**
- `FileRecord.metaData()` returns a `MessagePak` that exposes the same metadata keys (e.g. `/BitRate`, `/Hz`, etc.) that `SearchResult.getMetaData()` does, because both come from the same server response. **Assumed true; verify during implementation.**
- `ClientHandleObject` has no callers outside `com.myster.search.ui` (confirmed by search). It can be deleted.

### 4. Proposed design

A new interface, `FileTypeColumnHandler`, replaces `ClientHandleObject`. It carries three responsibilities:

1. **Column metadata** — count, header name, width — covering only the type-relevant columns ("File Name", "File Size", and any type-specific extras). "Server" and "Ping" are not included.
2. **SearchResult item factory** — produces `MCListItemInterface<SearchResult>` items whose `getValueOfColumn(i)` covers the same type-relevant indices. Used by `SearchTab`.
3. **FileRecord item factories** — `getFileItem(FileRecord)` and `getFolderItem(String)` produce `ColumnSortable<String>` objects covering the same column indices. Used by `ClientWindow`.

`SearchTab` wraps the handler in a new `SearchColumnDecorator` before configuring its MCList. The decorator transparently prepends the type columns and appends "Server" and "Ping" as the last two columns. It also wraps each `MCListItemInterface<SearchResult>` to handle those last two indices by reading the server name and ping from the `SearchResult`.

`ClientWindow` stores the handler in a field, calls a new `recolumnizeFileList()` method at the start of each file listing, and delegates item construction to the handler instead of building inline `ColumnSortable` lambdas.

### 5. Architecture connections

The new code introduces a clean boundary between what a Myster type *knows about its own files* (the handler) and what a particular UI context *adds on top* (the decorator, or nothing in the client window case).

| New / changed thing | Owned / created by | Called / used by | Connects to (existing) |
|---|---|---|---|
| `FileTypeColumnHandler` (new interface) | `com.myster.search.ui` | `SearchTab`, `ClientWindow`, `ClientInfoFactoryUtilities` | Replaces `ClientHandleObject` |
| `SearchColumnDecorator` (new class) | `com.myster.search.ui` | `SearchTab.recolumnize()` | Wraps `FileTypeColumnHandler`; uses `SortablePing`, `MysterServer` |
| `ClientGenericHandleObject` (changed) | `com.myster.search.ui` | `ClientInfoFactoryUtilities` | Implements `FileTypeColumnHandler`; drops Server/Ping columns |
| `ClientMPG3HandleObject` (changed) | `com.myster.search.ui` | `ClientInfoFactoryUtilities` | Extends `ClientGenericHandleObject`; gains `getFileItem`/`getFolderItem` |
| `ClientInfoFactoryUtilities` (changed) | `com.myster.search.ui` | `SearchTab`, `ClientWindow` | Return type widens to `FileTypeColumnHandler` |
| `ClientWindow.recolumnizeFileList()` (new method) | `com.myster.client.ui` | `ClientWindow.startFileList()` | Reads from `currentTypeHandler`; drives `fileList` column setup |

**Data flow — SearchTab:** On search start, `recolumnize()` calls the factory, wraps the result in `SearchColumnDecorator`, and calls `setNumberOfColumns` / `setColumnName` / `setColumnWidth` on the MCList using the decorator. Each incoming `SearchResult` is wrapped by the decorator's item factory, which delegates the first N columns to the type handler's item and handles the last two (Server, Ping) itself.

**Data flow — ClientWindow:** On type selection, `startFileList()` calls the factory, stores the handler, calls `recolumnizeFileList()` to configure the TreeMCList column headers, then starts `FileListerThread`. As `FileRecord` batches arrive, `addItemsToFileList()` delegates to `handler.getFileItem(record)` and `handler.getFolderItem(name)` to build the `ColumnSortable` objects for each row — instead of the current hardcoded two-column lambdas.

### 6. Key decisions & edge cases

- **Decorator over inheritance**: `SearchColumnDecorator` is a wrapper, not a subclass of the handler implementations. This avoids a fragile diamond and keeps search-context knowledge out of the type handlers.
- **Server/Ping logic moves to the decorator**: `SortablePing` construction and server-name lookup (currently in `GenericSearchItem`) move into `SearchColumnDecorator.SearchContextItem`. The type handler items handle only file data.
- **Folder rows with extra columns**: `FolderMCListItem.getValueOfColumn(1)` already overrides size; for indices ≥ 2 it falls through to the delegate. `getFolderItem(String name)` must therefore return `new SortableString("-")` for every extra column, which is safe.
- **FileRecord metaData access**: `MessagePak.getLong("/size")` is already used in `ClientWindow`. The MPG3 handler will use the same API for its keys. If a key is absent, the handler falls back to a sentinel value (same pattern as the search item).
- **Column count at init time**: `fileList` is still created with `new String[]{"Name", "Size"}` in `init()` as a safe default. `recolumnizeFileList()` reconfigures it on each type selection, replacing the initial placeholder.

### 7. Acceptance criteria

- [ ] The SearchWindow file list still shows "File Name", "File Size", "Server", "Ping" for generic types and those four plus the five MPG3 columns for the MPG3 type.
- [ ] The ClientWindow file list shows "File Name" and "File Size" for generic types.
- [ ] The ClientWindow file list shows "File Name", "File Size", "Bit Rate", "Hz", "Song Title", "Artist", "Album" when the selected type is MPG3, with values populated from file metadata.
- [ ] Switching types in ClientWindow reconfigures the file list columns correctly (clears and resets).
- [ ] No regression in sort behaviour in either window.
- [ ] `ClientHandleObject.java` is deleted; no compile errors.

---

## ✦ IMPLEMENTATION DETAILS (for the implementation agent)

---

### 8. Affected files / classes

| File | Change |
|---|---|
| `com/myster/search/ui/FileTypeColumnHandler.java` | **NEW** — interface replacing `ClientHandleObject` |
| `com/myster/search/ui/SearchColumnDecorator.java` | **NEW** — wraps handler, appends Server + Ping columns |
| `com/myster/search/ui/ClientHandleObject.java` | **DELETE** |
| `com/myster/search/ui/ClientGenericHandleObject.java` | Remove Server/Ping entries; implement `FileTypeColumnHandler`; add `getFileItem` and `getFolderItem` |
| `com/myster/search/ui/ClientMPG3HandleObject.java` | Override `getFileItem` and `getFolderItem` for MPG3 metadata; implements via inheritance |
| `com/myster/search/ui/ClientInfoFactoryUtilities.java` | Return type → `FileTypeColumnHandler` |
| `com/myster/search/ui/SearchTab.java` | `metaDateHandler` type → `FileTypeColumnHandler`; wrap in `SearchColumnDecorator` in `recolumnize()` |
| `com/myster/client/ui/ClientWindow.java` | Add `currentTypeHandler` field; add `recolumnizeFileList()`; update `startFileList()`, `extractFileRecordElement()`, `extractDirElement()`, `mkdir()` |

### 9. Step-by-step implementation

#### Step 1 — Create `FileTypeColumnHandler`

Create `src/main/java/com/myster/search/ui/FileTypeColumnHandler.java`:

```java
package com.myster.search.ui;

import com.general.mclist.ColumnSortable;
import com.general.mclist.MCListItemInterface;
import com.myster.client.ui.FileListerThread.FileRecord;
import com.myster.search.SearchResult;

/**
 * Describes the per-type columns for a Myster file list.
 * Does NOT include search-context columns such as "Server" or "Ping";
 * those are added by SearchColumnDecorator.
 */
public interface FileTypeColumnHandler {
    int getColumnCount();
    String getHeader(int index);
    int getHeaderSize(int index);

    /** Item factory for the SearchTab MCList. */
    MCListItemInterface<SearchResult> getSearchItem(SearchResult s);

    /** Item factory for a ClientWindow file row. */
    ColumnSortable<String> getFileItem(FileRecord record);

    /**
     * Item factory for a ClientWindow folder row.
     * Column 1 (size) is a placeholder; FolderMCListItem overrides it with the
     * accumulated folder size. Extra columns (index ≥ 2) should return
     * {@code new SortableString("-")}.
     */
    ColumnSortable<String> getFolderItem(String folderName);
}
```

#### Step 2 — Refactor `ClientGenericHandleObject`

- Change `implements ClientHandleObject` → `implements FileTypeColumnHandler`
- Remove "Server" and "Ping" from `headerarray` and `headerSize`; remove the corresponding `keyarray` entries. After change:
  - `headerarray = { "File Name", "File Size" }`
  - `headerSize = { 300, 70 }`
  - `keyarray = { "n/a", "/size" }` (or simplify if only 2 entries are used)
- Rename `getMCListItem(SearchResult s)` → `getSearchItem(SearchResult s)` (implements the new interface method name).
- Remove `serverString` and `ping` fields from `GenericSearchItem` (they move to `SearchColumnDecorator`).
- Update `GenericSearchItem.getValueOfColumn(int index)` to only handle indices 0 and 1 (name and size). The `case 2` / `case 3` (Server, Ping) are removed.
- Add `getFileItem(FileRecord record)`:
  ```java
  public ColumnSortable<String> getFileItem(FileRecord record) {
      return new ColumnSortable<String>() {
          public Sortable getValueOfColumn(int column) {
              return switch (column) {
                  case 0 -> new SortableString(record.file());
                  case 1 -> new SortableByte(record.metaData().getLong("/size").orElse(0L));
                  default -> throw new RuntimeException("Column " + column + " doesn't exist");
              };
          }
          public String getObject() { return record.file(); }
      };
  }
  ```
- Add `getFolderItem(String folderName)`:
  ```java
  public ColumnSortable<String> getFolderItem(String folderName) {
      return new ColumnSortable<String>() {
          public Sortable getValueOfColumn(int column) {
              return switch (column) {
                  case 0 -> new SortableString(folderName);
                  case 1 -> new SortableByte(-2); // placeholder; overridden by FolderMCListItem
                  default -> throw new RuntimeException("Column " + column + " doesn't exist");
              };
          }
          public String getObject() { return folderName; }
      };
  }
  ```

#### Step 3 — Refactor `ClientMPG3HandleObject`

- The superclass (`ClientGenericHandleObject`) now has columns 0 and 1 only; MPG3 adds 5 more (indices 2–6).
- `numOfColumns` will be 2 (was 4 — now correct after removing Server/Ping from super).
- Rename `getMCListItem` → `getSearchItem` (override).
- Override `getFileItem(FileRecord record)`:
  ```java
  @Override
  public ColumnSortable<String> getFileItem(FileRecord record) {
      ColumnSortable<String> base = super.getFileItem(record);
      return new ColumnSortable<String>() {
          public Sortable getValueOfColumn(int column) {
              if (column < numOfColumns) return base.getValueOfColumn(column);
              int extra = column - numOfColumns;
              return switch (extra) {
                  case 0 -> { try { yield new SortableBit(Long.parseLong(record.metaData().getString(keyarray[extra]).orElse("-1"))); } catch (Exception e) { yield new SortableBit(-1); } }
                  case 1 -> { try { yield new SortableHz(Long.parseLong(record.metaData().getString(keyarray[extra]).orElse("-1"))); } catch (Exception e) { yield new SortableHz(-1); } }
                  case 2, 3, 4 -> { String v = record.metaData().getString(keyarray[extra]).orElse("-"); yield new SortableString(v.isBlank() ? "-" : v); }
                  default -> throw new RuntimeException("Column " + column + " doesn't exist");
              };
          }
          public String getObject() { return record.file(); }
      };
  }
  ```
  > Note: verify the exact `MessagePak` API for reading string values (it may be `getString(key)` returning `Optional<String>` or similar — check `MessagePak` source and adapt accordingly).
- Override `getFolderItem(String folderName)`:
  ```java
  @Override
  public ColumnSortable<String> getFolderItem(String folderName) {
      ColumnSortable<String> base = super.getFolderItem(folderName);
      return new ColumnSortable<String>() {
          public Sortable getValueOfColumn(int column) {
              if (column < numOfColumns) return base.getValueOfColumn(column);
              return new SortableString("-"); // extra columns are empty for folders
          }
          public String getObject() { return folderName; }
      };
  }
  ```

#### Step 4 — Create `SearchColumnDecorator`

Create `src/main/java/com/myster/search/ui/SearchColumnDecorator.java`:

```java
package com.myster.search.ui;

import com.general.mclist.AbstractMCListItemInterface;
import com.general.mclist.ColumnSortable;
import com.general.mclist.MCListItemInterface;
import com.general.mclist.Sortable;
import com.general.mclist.SortableString;
import com.myster.client.ui.FileListerThread.FileRecord;
import com.myster.search.SearchResult;
import com.myster.tracker.MysterServer;

/**
 * Wraps a FileTypeColumnHandler and appends two search-context columns:
 * "Server" (index N) and "Ping" (index N+1), where N = wrapped.getColumnCount().
 */
public class SearchColumnDecorator implements FileTypeColumnHandler {
    private static final String[] SEARCH_HEADERS  = { "Server", "Ping" };
    private static final int[]    SEARCH_WIDTHS   = { 150, 70 };

    private final FileTypeColumnHandler wrapped;
    private final int typeColumnCount;

    public SearchColumnDecorator(FileTypeColumnHandler wrapped) {
        this.wrapped = wrapped;
        this.typeColumnCount = wrapped.getColumnCount();
    }

    @Override public int getColumnCount()          { return typeColumnCount + SEARCH_HEADERS.length; }
    @Override public String getHeader(int i)       { return i < typeColumnCount ? wrapped.getHeader(i)      : SEARCH_HEADERS[i - typeColumnCount]; }
    @Override public int getHeaderSize(int i)      { return i < typeColumnCount ? wrapped.getHeaderSize(i)  : SEARCH_WIDTHS[i  - typeColumnCount]; }

    @Override
    public MCListItemInterface<SearchResult> getSearchItem(SearchResult s) {
        MCListItemInterface<SearchResult> typeItem = wrapped.getSearchItem(s);
        SortableString serverString = new SortableString(
                s.getServer() == null ? "N/A" : s.getServer().getServerName());
        SortablePing ping = new SortablePing(s.getProtocol(), s.getHostAddress());

        return new AbstractMCListItemInterface<SearchResult>() {
            @Override
            public Sortable getValueOfColumn(int index) {
                if (index < typeColumnCount) return typeItem.getValueOfColumn(index);
                return switch (index - typeColumnCount) {
                    case 0 -> serverString;
                    case 1 -> ping;
                    default -> throw new RuntimeException("Column " + index + " doesn't exist");
                };
            }
            @Override public SearchResult getObject() { return s; }
        };
    }

    /** Not called from SearchTab; delegates to the wrapped handler. */
    @Override public ColumnSortable<String> getFileItem(FileRecord record)     { return wrapped.getFileItem(record); }
    /** Not called from SearchTab; delegates to the wrapped handler. */
    @Override public ColumnSortable<String> getFolderItem(String folderName)   { return wrapped.getFolderItem(folderName); }
}
```

#### Step 5 — Update `ClientInfoFactoryUtilities`

Change the return type to `FileTypeColumnHandler`:

```java
public static FileTypeColumnHandler getHandler(TypeDescriptionList tdList, MysterType type) {
    if (tdList.getType(StandardTypes.MPG3).equals(type))
        return new ClientMPG3HandleObject();
    else
        return new ClientGenericHandleObject();
}
```

#### Step 6 — Update `SearchTab`

- Change field type: `private ClientHandleObject metaDateHandler;` → `private FileTypeColumnHandler metaDateHandler;`
- In `recolumnize()`:
  ```java
  private void recolumnize() {
      FileTypeColumnHandler typeHandler = ClientInfoFactoryUtilities.getHandler(tdList, getMysterType());
      metaDateHandler = new SearchColumnDecorator(typeHandler);
      int max = metaDateHandler.getColumnCount();
      fileList.setNumberOfColumns(max);
      for (int i = 0; i < max; i++) {
          fileList.setColumnName(i, metaDateHandler.getHeader(i));
          fileList.setColumnWidth(i, metaDateHandler.getHeaderSize(i));
      }
  }
  ```
- In `addSearchResults()`, change the stream to use `getSearchItem` instead of `getMCListItem`:
  ```java
  MCListItemInterface<SearchResult>[] m = Arrays.stream(resultArray)
      .map(metaDateHandler::getSearchItem)
      .toArray(MCListItemInterface[]::new);
  ```

#### Step 7 — Update `ClientWindow`

**a) Add field and import:**
```java
import com.myster.search.ui.ClientInfoFactoryUtilities;
import com.myster.search.ui.FileTypeColumnHandler;
// ...
private FileTypeColumnHandler currentTypeHandler = new com.myster.search.ui.ClientGenericHandleObject();
```

**b) Add `recolumnizeFileList()`:**
```java
private void recolumnizeFileList() {
    MysterType type = getCurrentType();
    currentTypeHandler = ClientInfoFactoryUtilities.getHandler(typeDescriptionList, type);
    int count = currentTypeHandler.getColumnCount();
    fileList.setNumberOfColumns(count);
    for (int i = 0; i < count; i++) {
        fileList.setColumnName(i, currentTypeHandler.getHeader(i));
        fileList.setColumnWidth(i, currentTypeHandler.getHeaderSize(i));
    }
    fileList.sortBy(0);
}
```

**c) Call `recolumnizeFileList()` in `startFileList()`**, right after `stopFileListing()` and before constructing `FileListerThread`:
```java
public void startFileList() {
    stopFileListing();
    recolumnizeFileList(); // <-- add this
    fileListThread = new FileListerThread(...);
    ...
}
```

**d) Make `extractFileRecordElement` non-static (it needs `currentTypeHandler`):**
```java
private ColumnSortable<String> extractFileRecordElement(final FileRecord file) {
    return currentTypeHandler.getFileItem(file);
}
```

**e) Make `extractDirElement` non-static (it needs `currentTypeHandler`):**
```java
private ColumnSortable<String> extractDirElement(final String name) {
    return currentTypeHandler.getFolderItem(name);
}
```
Remove the `static` modifier. Update the call site in `mkdir()` — since `mkdir()` calls `extractDirElement()`, `mkdir()` must also become a non-static (instance) method, or accept `currentTypeHandler` as a parameter. The simplest approach: make `mkdir()` and `loopBackThroughParents()` instance methods (they currently have no `this` references, so just removing `static` is sufficient). Update `addItemsToFileList()` call sites accordingly — since they are already called on `this`, no other changes are needed.

**f) `FolderMCListItem` — no change needed.** Its `getValueOfColumn(int i)` already delegates to the base item for `i != 1`. For columns ≥ 2 the delegate (`getFolderItem()` result) returns `new SortableString("-")`, which is correct.

**g) Remove the hardcoded column setup from `init()`:**  
Change the `fileList` creation line to use a single generic placeholder:  
```java
fileList = TreeMCList.create(new String[]{"Name", "Size"}, new TreePathString(new String[] {}));
```
This line stays as-is for the initial state. `recolumnizeFileList()` will override it on first type selection. (No change needed here; this is already correct.)

**h) Remove the now-redundant column 0 width call** later in `init()`:
```java
fileList.setColumnWidth(0, 300);
```
This becomes redundant once `recolumnizeFileList()` sets all widths; it can be removed or left as a harmless default. Recommended: remove to avoid confusion.

#### Step 8 — Delete `ClientHandleObject.java`

Delete `src/main/java/com/myster/search/ui/ClientHandleObject.java`.

#### Step 9 — Verify `MessagePak` API for MPG3 string keys

Before implementing Step 3, read `com/myster/mml/MessagePak.java` and confirm the method to retrieve a `String` value by key (e.g. `getString(key)` returning `Optional<String>`, or `get(key)` returning `String` or `null`). Adjust the `getFileItem` implementation accordingly. The existing `ClientMPG3HandleObject.MPG3SearchItem` calls `result.getMetaData(keyarray[newIndex])` which returns `String` (nullable) — confirm the equivalent on `FileRecord.metaData()`.

### 10. Tests to write

- **`ClientGenericHandleObjectTest`** (new unit test):
  - `getColumnCount()` returns 2.
  - `getHeader(0)` = "File Name", `getHeader(1)` = "File Size".
  - `getSearchItem(result).getValueOfColumn(0)` returns a `SortableString` matching the result name.
  - `getFileItem(record).getValueOfColumn(1)` returns a `SortableByte` matching the metadata size.
  - `getFolderItem("Foo").getValueOfColumn(0)` returns `SortableString("Foo")`.

- **`SearchColumnDecoratorTest`** (new unit test):
  - `getColumnCount()` = wrapped count + 2.
  - `getHeader(wrappedCount)` = "Server", `getHeader(wrappedCount + 1)` = "Ping".
  - `getSearchItem(result).getValueOfColumn(wrappedCount)` returns the server-name string.

- **`ClientMPG3HandleObjectTest`** (new unit test):
  - `getColumnCount()` returns 7.
  - `getFileItem(record).getValueOfColumn(2)` returns a `SortableBit` for a record with a `/BitRate` metadata entry.
  - `getFolderItem("Albums").getValueOfColumn(3)` returns `SortableString("-")`.

- **Manual smoke test — SearchTab:**
  1. Open Search window, select MPG3 type, run a search. Confirm 7 columns visible: File Name, File Size, Bit Rate, Hz, Song Title, Artist, Album, Server, Ping.
  2. Switch to a generic type. Confirm 4 columns: File Name, File Size, Server, Ping.

- **Manual smoke test — ClientWindow:**
  1. Connect to a server, select a generic type. Confirm 2 columns: File Name, File Size.
  2. Select the MPG3 type. Confirm 7 columns with metadata values where available.
  3. Switch back to a generic type. Confirm columns reset to 2.

### 11. Docs / Javadoc to update

- **`FileTypeColumnHandler`** — full Javadoc on the interface and each method (especially clarifying that Server/Ping are out of scope).
- **`SearchColumnDecorator`** — class-level Javadoc explaining what it adds and why it is a decorator rather than a subclass.
- **`ClientGenericHandleObject`** — update class Javadoc to remove the mention of Server/Ping columns.
- **`ClientInfoFactoryUtilities.getHandler()`** — update return-type Javadoc to reference `FileTypeColumnHandler`.
- **`ClientWindow.recolumnizeFileList()`** — brief Javadoc explaining it is called on every type selection.

