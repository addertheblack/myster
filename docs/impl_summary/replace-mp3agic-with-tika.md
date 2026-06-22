# Implementation Summary: replace-mp3agic-with-tika

## What was implemented

Replaced the `com.mpatric:mp3agic` library with Apache Tika (`tika-core` +
`tika-parser-audiovideo-module`) for MP3 metadata extraction in `MPG3FileItem`.
The `MessagePak` protocol keys, value types, and omission semantics are preserved
for all fields that the new library set can source. `mp3agic` is fully removed from
the build.

After initial rollout and empirical testing on VBR files, added a **bitrate fallback**:
when explicit MP3 bitrate metadata is missing, `MPG3FileItem` now computes average
bitrate from file size and duration, then clamps to the nearest common MP3 bitrate
bucket (e.g. 160 kbps instead of 168.5 kbps). Also added `/LengthSec` when duration
is available.

---

## Files changed

| File | Change |
|---|---|
| `pom.xml` | Removed `com.mpatric:mp3agic:0.9.1`; added `tika-core:3.3.1`, `tika-parser-audiovideo-module:3.3.1`, `slf4j-jdk14:2.0.18` |
| `src/main/java/com/myster/filemanager/MPG3FileItem.java` | Full rewrite; Tika extraction, VBR fallback estimation + clamping, duration key, logging cleanup, helper methods |
| `src/test/java/com/myster/filemanager/TestMPG3FileItem.java` | New; 25 tests covering helpers, failure handling, clamping, and protocol constraints |
| `docs/plans/replace-mp3agic-with-tika.md` | Plan file (pre-existed; implementation executed from it) |

---

## Key design decisions made during implementation

### Two-source extraction
Tika's `Mp3Parser.parse()` does not write bitrate to the `Metadata` object at all
(confirmed by bytecode inspection before implementation). The extraction is therefore
split:
- **`Mp3Parser.parse()` → Tika `Metadata`**: title → `/ID3Name`, artist → `/Artist`,
  album → `/Album`, sample rate → `/Hz` via `XMPDM.AUDIO_SAMPLE_RATE`, duration →
  `/LengthSec` via `XMPDM.DURATION`.
- **`metadata-extractor` `Mp3Directory.TAG_BITRATE`**: bitrate in kbps, multiplied by
  1000L for bps. `metadata-extractor` is already a transitive dependency of
  `tika-parser-audiovideo-module` — no extra Maven dep needed.

### VBR fallback from average bitrate
If explicit bitrate metadata is missing (observed on some VBR files), bitrate is
estimated as:

`avgBps = (fileSizeBytes * 8) / durationSeconds`

The result is converted to kbps, clamped to nearest common MP3 buckets
`{32,40,48,56,64,80,96,112,128,160,192,224,256,320}`, then stored back as bps
(e.g. 168.5 kbps -> 160000 bps).

### `tika-core` declared explicitly
`tika-parser-audiovideo-module` carries `tika-core` as `provided` in its parent POM.
`tika-core` is therefore declared as an explicit `compile`-scope dependency to ensure
it is always on the runtime/shaded classpath.

### Logging cleanup
Replaced `System.err.println(...)` with `Logger.getLogger(...)` based reporting,
consistent with neighboring file-manager classes. This is the standing-refactor
scoped cleanup the plan authorized for touched files.

### Helper methods are package-private
`putIfNotBlank`, `parseBitrateKbpsToBps`, `parseSampleRateHz`,
`parseDurationSeconds`, `estimateAverageBitrateBps`, and
`clampToLikelyBitrateKbps` are `static` package-private, so they can be tested
without reflection.

### `DefaultHandler` for SAX content handler
`Mp3Parser.parse()` requires a `ContentHandler`. A `DefaultHandler` (SAX no-op
implementation, part of the JDK) is used since the body text output is not needed.

---

## Deviations from the plan

- **Added `/LengthSec` metadata key** (`long` seconds) when Tika duration is available.
  This was requested during implementation follow-up and was not in the initial
  acceptance criteria.
- **Changed bitrate behavior for VBR files from "possibly missing" to "best-effort
  present"** by adding average bitrate estimation + clamping fallback.

These are protocol additive/compatibility-safe changes: no existing key types changed,
and consumers that ignore unknown keys remain unaffected.

---

## Protocol fields that are no longer emitted (owner-approved)

| Key | Reason | Owner decision |
|---|---|---|
| `/Vbr` | Neither Tika nor `metadata-extractor` expose VBR detection | Drop it (option A) |
| `/OriginalArtist` | ID3v2 TOPE frame not surfaced by Tika `Mp3Parser` | Drop it |

Both fields were optional; consumers tolerate their absence.

---

## Javadoc / design docs updated

- `MPG3FileItem` class Javadoc: updated to describe extraction strategy and list
  protocol keys, including `/LengthSec`.
- `patchFunction2` Javadoc: documents `.mp3` filename gate and non-fatal failure
  contract.
- Helper Javadocs added/updated: `parseBitrateKbpsToBps`, `parseSampleRateHz`,
  `parseDurationSeconds`, `estimateAverageBitrateBps`, `clampToLikelyBitrateKbps`,
  `putIfNotBlank`.

---

## Known issues / follow-up work

- **No real-file integration test.** All 25 tests run without an actual MP3 fixture.
  A follow-up test with a small tagged `.mp3` file under `src/test/resources` would
  provide end-to-end confidence that Tika and `metadata-extractor` both parse real
  files correctly and produce the expected `MessagePak` payload.
- **Non-`.mp3` MPG3 files (`.ogg`, `.wav`, `.mid`, etc.)** will fail `Mp3Parser.parse()`
  and return only generic `FileItem` metadata. This matches pre-migration behavior.
- **Average fallback accuracy:** bitrate fallback uses duration from Tika metadata.
  If duration is absent or inaccurate, `/BitRate` may still be missing or imperfect.

---

## Tests that should be added later

1. **Real MP3 fixture test** — parse a small tagged `.mp3` and assert `/ID3Name`,
   `/Artist`, `/Album`, `/BitRate`, `/Hz`, and `/LengthSec` with correct types.
2. **Known VBR fixture test** — verify computed/clamped fallback behavior against a
   known file and document expected clamped bucket.

---

## Anything else the maintainer should know

- `slf4j-jdk14:2.0.18` matches the exact SLF4J version declared in Tika's parent BOM.
  Without it, SLF4J 2.x uses a NOP logger and silently drops all Tika log output.
- The `metadata-extractor` transitive version pulled is `2.20.0` (locked by Tika's
  parent BOM). If Tika is upgraded, check whether this version changes.
