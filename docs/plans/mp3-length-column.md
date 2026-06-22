# MP3 Length Column in GUI

**Feature slug:** `mp3-length-column`  
**Date:** 2026-06-18  
**Status:** Ready for implementation

---

## 1. Summary

Add a "Length" column to the MP3 file-type view that displays track duration in
M:SS format (e.g. "3:24"). The `/LengthSec` protocol key is already emitted by
`MPG3FileItem` as of the Tika migration. This change is purely client-side display
and sorting plumbing — no server, protocol, or data-model changes are needed.

---

## 2. Non-goals

- No server-side changes. `/LengthSec` is already in the wire format.
- No changes to other file types (video, generic). Those may use Tika duration
  in a future milestone.
- No changes to `MPG3FileItem`, `MessagePak`, or any network code.
- No changes to `ClientInfoFactoryUtilities` (rename deferred to standing-refactor).
- No changes to the search protocol or `SearchResult` serialisation.

---

## 3. Assumptions & open questions

- `/LengthSec` values are `long` seconds, always non-negative when present.
  Absent means the server didn't provide duration (old server or non-MP3 file).
- `-` is the display sentinel for unknown/absent values (updated convention;
  `SortableBit` and `SortableHz` still use `??` but new code uses `-`).
  A stored value of -1 means "not available".
- Sorting by length when the value is absent: absent (-1) rows sort before
  zero-length rows (numeric ascending, same as other `SortableLong` subclasses).
- Column width: 70 px (matches File Size; narrow enough not to crowd the list).

---

## 4. Proposed design

A new `SortableLength` class (extending `SortableLong`, same pattern as `SortableBit`
and `SortableHz`) wraps a `long` (seconds) and formats it as `M:SS`
(no hours component — the longest MP3 albums are under 100 minutes so hours
are unnecessary and would look wrong for 3-minute songs). For missing values
it shows `-` (the current convention for absent data).

`ClientMPG3HandleObject` gains one new entry at the end of each of its three
parallel arrays (`headerarray`, `keyarray`, `headerSize`) and one new switch
case (`case 5`) in both code paths that map column indices to `Sortable` instances
(`getFileItem` lambda and `MPG3SearchItem.getValueOfColumn`).

---

## 5. Architecture connections

The existing column-dispatch pattern in `ClientMPG3HandleObject` is entirely
index-driven: `headerarray`, `keyarray`, and `headerSize` are parallel arrays
where `headerarray[i]` is the column title, `keyarray[i]` is the metadata key
read from `MessagePak`/`SearchResult`, and `headerSize[i]` is the pixel width.
`getColumnCount()` returns `super.getColumnCount() + headerarray.length`, so
adding one entry to each array automatically makes the new column visible in both
the Search window and the Client browse window without touching any layout code.

Data flow:

```
MPG3FileItem.patchFunction2()
  → writes "/LengthSec" (long, seconds) into MessagePak     [server side, done]
  → propagated over the wire unchanged

ClientMPG3HandleObject.MPG3SearchItem.getValueOfColumn(5)
  → reads result.getMetaData("/LengthSec")
  → wraps in new SortableLength(seconds)                    [new]

ClientMPG3HandleObject.getFileItem(record).getValueOfColumn(5)
  → reads record.metaData().get("/LengthSec")
  → wraps in new SortableLength(seconds)                    [new]

SortableLength.toString()
  → formats seconds → "M:SS" (or "-" if < 0)              [new]
```

| New / changed thing | Owned / created by | Called / used by | Connects to (existing) |
|---|---|---|---|
| `SortableLength` (new class) | `com.myster.search.ui` | `ClientMPG3HandleObject` (both code paths) | `SortableLong` (superclass); MCList rendering via `toString()` |
| `ClientMPG3HandleObject` — new array entries + case 5 | `com.myster.search.ui` | Search window MCList, Client browse window MCList | `SortableLength`; `/LengthSec` key already in protocol |

No new protocols, file formats, or interfaces are introduced.

---

## 6. Key decisions & edge cases

- **M:SS vs H:MM:SS** — M:SS chosen. Even 99:59 (a ~100 min album) fits without
  ambiguity. If seconds ≥ 3600 the display will wrap past 59 minutes gracefully
  (e.g. "63:07") rather than silently truncating.
- **Absent vs zero** — seconds == 0 is a legitimate (if odd) value. The sentinel
  for "not available" is -1, matching SortableBit/SortableHz convention.
- **Sort order** — `SortableLong` comparison is numeric ascending, so absent (-1)
  rows sort before zero-length rows. This is the existing convention for all
  numeric columns; no special handling needed.
- **Old servers** — will not emit `/LengthSec`; the column will show `-` for
  results from those servers. No compatibility issue.

---

## 7. Acceptance criteria

- [ ] A "Length" column appears in the MP3 search results table (Search window).
- [ ] The same "Length" column appears when browsing an MP3 share in the Client window.
- [ ] Duration is displayed as `M:SS` (e.g. "3:24" for 204 seconds).
- [ ] Files where `/LengthSec` is absent show `-` in the Length column.
- [ ] Clicking the Length column header sorts rows by duration (ascending then descending).
- [ ] Non-MP3 file-type views are unaffected (no extra column, no errors).
- [ ] Folder rows in the Client window show `-` in the Length column (consistent with other type-specific columns).

---

## ✦ IMPLEMENTATION DETAILS (for the implementation agent)

---

## 8. Affected files / classes

| Package | Class | Change |
|---|---|---|
| `com.myster.search.ui` | `SortableLength` | **NEW** — `SortableLong` subclass; formats seconds as M:SS |
| `com.myster.search.ui` | `ClientMPG3HandleObject` | Add "Length"/"/LengthSec" to arrays; add `case 5` in two `getValueOfColumn` paths |

No other files need touching.

---

## 9. Step-by-step implementation

### Step 1 — Create `SortableLength`

New file: `src/main/java/com/myster/search/ui/SortableLength.java`

```java
package com.myster.search.ui;

import com.general.mclist.SortableLong;

/**
 * Sortable column value for audio track duration.
 * Wraps a duration in whole seconds; displays as "M:SS" (e.g. "3:24").
 * A value of -1 means the duration is not available and displays as "-".
 */
public class SortableLength extends SortableLong {

    public SortableLength(long seconds) {
        super(seconds);
    }

    @Override
    public String toString() {
        if (number < 0) return "-";
        long minutes = number / 60;
        long secs    = number % 60;
        return minutes + ":" + String.format("%02d", secs);
    }
}
```

### Step 2 — Update `ClientMPG3HandleObject`

File: `src/main/java/com/myster/search/ui/ClientMPG3HandleObject.java`

**2a.** Extend the three parallel arrays:

```java
protected String[] headerarray = { "Bit Rate", "Hz", "Song Title",
        "Artist", "Album", "Length" };

protected String[] keyarray = { "/BitRate", "/Hz", "/ID3Name", "/Artist",
        "/Album", "/LengthSec" };

protected int[] headerSize = { 100, 100, 100, 100, 100, 70 };
```

**2b.** In `getFileItem(FileRecord record)`, add a `case 5` to the switch expression
(after the existing `case 2, 3, 4` arm):

```java
case 5 -> {
    try { yield new SortableLength(Long.parseLong(record.metaData().get(keyarray[extra]).orElse("-1"))); }
    catch (Exception e) { yield new SortableLength(-1); }
}
```

Update `default` from `case 2, 3, 4` to accommodate — the `default` arm already
throws `RuntimeException` so no extra change needed there.

**2c.** In `MPG3SearchItem.getValueOfColumn(int index)`, add `case 5` before the
`default` in the existing `switch`:

```java
case 5:
    try {
        return new SortableLength(Long.parseLong(result.getMetaData(keyarray[newIndex])));
    } catch (Exception ex) {
        return new SortableLength(-1);
    }
```

**2d.** Add import at the top of the file:

No additional import needed — `SortableLength` is in the same package.

---

## 10. Tests to write

### Unit — `TestSortableLength` (new)

`src/test/java/com/myster/search/ui/TestSortableLength.java`

| Test | Verifies |
|---|---|
| `toString_negativeMeansUnknown` | `new SortableLength(-1).toString()` → `"-"` |
| `toString_zero` | `new SortableLength(0).toString()` → `"0:00"` |
| `toString_underOneMinute` | `new SortableLength(34).toString()` → `"0:34"` |
| `toString_exactMinutes` | `new SortableLength(180).toString()` → `"3:00"` |
| `toString_typical` | `new SortableLength(204).toString()` → `"3:24"` |
| `toString_longTrack` | `new SortableLength(3787).toString()` → `"63:07"` (album edge-case) |
| `sortOrder_shorterLessThanLonger` | `new SortableLength(60).isLessThan(new SortableLength(120))` → `true` |
| `sortOrder_unknownLessThanZero` | `new SortableLength(-1).isLessThan(new SortableLength(0))` → `true` |

### Unit — `TestClientMPG3HandleObject` (new, or extend if it exists)

`src/test/java/com/myster/search/ui/TestClientMPG3HandleObject.java`

| Test | Verifies |
|---|---|
| `getColumnCount_returns8` | `new ClientMPG3HandleObject().getColumnCount()` → `8` (2 base + 6 type-specific) |
| `getHeader_lengthColumn` | `getHeader(7)` → `"Length"` |
| `getFileItem_lengthColumn_present` | `getValueOfColumn(7)` with `/LengthSec`=`"204"` returns `SortableLength` with `toString()` `"3:24"` |
| `getFileItem_lengthColumn_absent` | `getValueOfColumn(7)` with no `/LengthSec` returns `SortableLength(-1)` → `"-"` |
| `getFolderItem_lengthColumn` | `getFolderItem("x").getValueOfColumn(7)` → `SortableString("-")` |

### Manual smoke test

1. Start Myster server with at least one MP3 file that has `/LengthSec` (any file indexed after the Tika migration).
2. Open Search window, type ``, select MPG3. Confirm 9 columns: File Name, File Size, Bit Rate, Hz, Song Title, Artist, Album, **Length**, Server, Ping.
3. Confirm "Length" shows formatted durations (e.g. "3:24") for matching files.
4. Click "Length" header — list sorts by duration.
5. Open a server's MP3 share in the Client window. Confirm "Length" column present and populated.
6. Verify folder rows show "-" in the Length column.
7. Switch to a non-MP3 type. Confirm no "Length" column.

---

## 11. Docs / Javadoc to update

- Add class-level Javadoc to `SortableLength` (included in Step 1 sketch above).
- No other Javadoc changes required; `ClientMPG3HandleObject` has no per-method Javadoc to update.








