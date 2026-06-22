# Replace `mp3agic` with Apache Tika for MPG3 metadata

## 1. Summary
Replace the current `com.mpatric.mp3agic` dependency with Apache Tika for metadata extraction in `MPG3FileItem`, while keeping the on-wire/on-disk `MessagePak` contract exactly the same. This milestone is intentionally narrow: swap the extraction backend only, preserve the existing protocol keys and value types, remove `mp3agic` from the build since it is only used in one place, and apply only those small standing refactors from `docs/conventions/standing-refactors.md` that naturally arise in files already being touched for this work.

## 2. Non-goals
- Do not change any UI classes, columns, labels, sorting, or search-window behaviour.
- Do not change `MessagePak` key names, value types, omission rules, or general shape.
- Do not introduce a generic metadata framework for all Myster types in this milestone.
- Do not rename `MPG3FileItem`, `patchFunction2`, or other public/protocol-adjacent symbols unless required for compilation.
- Do not change how non-audio metadata is handled for `TEXT`, `PICT`, `MOOV`, or custom types yet.
- Do not clean up unrelated technical debt beyond scoped standing refactors that apply to files already being modified for this project.
- Do not touch UI/search presentation files just to perform a mechanical rename from the standing-refactors list; those refactors should wait until those files are being changed for another substantive reason.

## 3. Assumptions & open questions
- The protocol contract is fixed: these keys must continue to be populated exactly as today when values are available: `/BitRate`, `/Hz`, `/Vbr`, `/ID3Name`, `/Artist`, `/Album`, `/OriginalArtist`.
- Existing consumers already tolerate missing keys; absent metadata should still be omitted rather than represented as empty strings or sentinel values.
- Tika field names for some audio properties, especially original artist and VBR, may vary by parser/module and by source file. If no reliable value is exposed for a field, the implementation should omit that key rather than invent or approximate a value.
- The exact Maven module set for Tika 3.3.1 must be chosen during implementation. Prefer the smallest parser set that reliably extracts MP3 metadata and works with the existing shaded build.
- **Tika `Mp3Parser.parse()` does not write bitrate into the `Metadata` object at all.** Bitrate must come from `metadata-extractor` (`com.drewnoakes:metadata-extractor:2.20.0`), which is pulled in transitively by `tika-parser-audiovideo-module` and does not need a separate Maven dependency. Its `Mp3Directory.TAG_BITRATE` returns the value in **kbps** — the same unit as `mp3agic.getBitrate()`. The existing `* 1000L` conversion to produce bps for the protocol applies to this value too.
- **Tika does expose sample rate via `XMPDM.AUDIO_SAMPLE_RATE`** from `Mp3Parser.parse()`, as a string representation of Hz (e.g., `"44100"`). Parse with `Long.parseLong()`.
- **VBR flag (`/Vbr`) — owner decision: drop it.** Extracting VBR requires scanning the first MPEG audio frame for a Xing/Info/VBRI header, which neither Tika nor `metadata-extractor` expose. The field will no longer be emitted. For VBR files, `metadata-extractor` will return the first-frame bitrate via `TAG_BITRATE`; this is worth observing during smoke testing but does not change the protocol output.
- **OriginalArtist (`/OriginalArtist`) — owner decision: drop it.** The ID3v2 TOPE frame is not surfaced by Tika's standard `parse()` output. `/OriginalArtist` will no longer be populated after this migration. Low-risk: it was the only ID3v2-exclusive field in the old code and consumers tolerate its absence.
- `docs/conventions/standing-refactors.md` is in scope as an opportunistic cleanup rule: if this migration naturally modifies a listed file, apply the prescribed mechanical rename/refactor in the same change; do not expand the file set just to chase standing refactors.
- For this milestone, the standing-refactors rule is most likely to apply to touched file-local cleanup such as logging modernization in `MPG3FileItem`. The listed `ClientInfoFactoryUtilities` → `ClientInfoFactoryUtils` rename should only happen if that file becomes part of the required implementation, which is not currently expected.
- Tika 3.3.1 artifact choice is no longer fully open-ended for this milestone. The preferred immediate implementation is the minimal MP3-capable set: `org.apache.tika:tika-core:3.3.1` plus `org.apache.tika:tika-parser-audiovideo-module:3.3.1`, with direct use of `org.apache.tika.parser.mp3.Mp3Parser` rather than the broad `AutoDetectParser` convenience path.
- Because Tika uses SLF4J, the implementation should also decide whether to add an SLF4J binding. Since Myster already uses `java.util.logging`, the preferred integration is `org.slf4j:slf4j-jdk14` so Tika logging flows into the existing JUL configuration instead of emitting a one-time "no provider" warning.
- The broader artifact `org.apache.tika:tika-parsers-standard-package:3.3.1` is a valid fallback, but it should be treated as a conscious footprint trade-off rather than the default. It pulls in many parser modules that are unnecessary for an MP3-only milestone.

## 4. Proposed design
Keep `MPG3FileItem` as the single special-case metadata enrichment point for the `MPG3` type, but replace its internal parser from `mp3agic` to Apache Tika. The surrounding file-indexing flow, `MessagePak` creation, and all downstream readers stay unchanged.

The implementation uses two sources from the Tika dependency set — both come from the two declared Maven deps with no additional artifacts needed:

1. **`Mp3Parser.parse()` → `Metadata`** (from `tika-parser-audiovideo-module`): supplies title, artist, album, and sample rate via the `TikaCoreProperties.TITLE`, `XMPDM.ARTIST`, `XMPDM.ALBUM`, and `XMPDM.AUDIO_SAMPLE_RATE` properties.
2. **`metadata-extractor` `Mp3Directory.TAG_BITRATE`** (transitively pulled by `tika-parser-audiovideo-module`, no extra Maven dep): supplies bitrate in **kbps** — the same unit as `mp3agic.getBitrate()` — so the existing `* 1000L` conversion to bps still applies exactly.

**Two fields that cannot be sourced from this library set:**
- `/Vbr` — requires scanning the first MPEG audio frame for a Xing/Info/VBRI header. Neither Tika nor metadata-extractor exposes this. See §6 for the owner decision.
- `/OriginalArtist` — the ID3v2 TOPE frame is not surfaced by Tika's standard `parse()` output. See §6.

This milestone should also preserve existing behaviour quirks that are now part of protocol compatibility, especially the current `.mp3` filename gate for `/BitRate`, `/Hz`, and `/Vbr`. Even though the built-in `MPG3` type covers many audio extensions, today only files whose names end with `.mp3` receive those numeric audio fields. Preserve that behaviour in this milestone.

Because `MPG3FileItem` will necessarily be edited, the implementation may also apply small code-health cleanups in that file if they are directly covered by the repo conventions and do not alter behaviour. The intended example here is logging cleanup: replace ad-hoc `System.err.println(...)` style error reporting with the codebase’s normal logger-based style if that can be done without changing control flow or protocol output.

## 5. Architecture connections
`FileTypeList.FileListIndexCall` creates `MPG3FileItem` only for the built-in `StandardTypes.MPG3` branch. `MPG3FileItem.getMessagePackRepresentation()` first delegates to `FileItem` for generic metadata like `/size` and `/path`, then enriches the same `MessagePak` with audio-specific metadata. That enriched `MessagePak` is what travels through Myster’s existing file metadata flow and is later read by search/file-list consumers.

Apache Tika plugs in entirely inside `MPG3FileItem`; no protocol, indexing, or UI layer needs to know that the extraction backend changed. The compatibility boundary is therefore the `MessagePak` payload, not the parser API.

### Connections table

| New / changed thing | Owned / created by | Called / used by | Connects to (existing) |
|---|---|---|---|
| Tika-backed metadata extraction inside `com/myster/filemanager/MPG3FileItem.java` | `MPG3FileItem.getMessagePackRepresentation()` / `patchFunction2(...)` | `FileTypeList.FileListIndexCall.createFileItem(...)` for `StandardTypes.MPG3` | Existing `FileItem` base metadata, `MessagePak`, search/file-list metadata consumers |
| MP3 metadata-to-protocol mapping layer in `MPG3FileItem` | `MPG3FileItem` private/static helpers | Same class only | Existing protocol keys `/BitRate`, `/Hz`, `/Vbr`, `/ID3Name`, `/Artist`, `/Album`, `/OriginalArtist` |
| Maven dependency swap in `pom.xml` | Maven build | `MPG3FileItem` compile/runtime path | Existing shade/package flow in `pom.xml` |
| New metadata regression tests | JUnit tests under `src/test/java/com/myster/filemanager/` | CI / local test runs | Existing `MessagePak` serialization and current MPG3 compatibility expectations |

### Protocol / file-format contract to preserve
All existing keys remain the same type and omission semantics. Two optional fields are expected to become unpopulated by this migration:

| Key | Type | Source after migration | Notes |
|---|---|---|---|
| `/BitRate` | `long` (bps) | `metadata-extractor` `Mp3Directory.TAG_BITRATE` × 1000 | kbps→bps conversion preserved |
| `/Hz` | `long` | `XMPDM.AUDIO_SAMPLE_RATE` string → `Long.parseLong()` | |
| `/ID3Name` | `String` | `TikaCoreProperties.TITLE` | |
| `/Artist` | `String` | `XMPDM.ARTIST` | |
| `/Album` | `String` | `XMPDM.ALBUM` | |
| `/Vbr` | `boolean` | **No source — will no longer be emitted** | Owner decision required (see §6) |
| `/OriginalArtist` | `String` | **No source — will no longer be emitted** | ID3v2 TOPE frame not in Tika output |

Generic keys created by `FileItem` such as `/size`, `/path`, and `/hash/...` are unchanged.

## 6. Key decisions & edge cases
- **Tika `Mp3Parser.parse()` does not write bitrate.** Confirmed by bytecode inspection: `Mp3Parser` does not call `getBitRate()` anywhere in its `parse()` method. Bitrate must come from `metadata-extractor` `Mp3Directory.TAG_BITRATE` (kbps × 1000 = bps). This library is pulled in transitively by `tika-parser-audiovideo-module`; no extra Maven dep is needed.
- **Bitrate unit chain is preserved.** `metadata-extractor` TAG_BITRATE → kbps (e.g., 128) × 1000L → 128000 bps. Identical to the old `mp3agic.getBitrate() * 1000L` path.
- **`/Vbr` — owner decision: drop it (option A).** VBR detection requires scanning the first MPEG audio frame for a Xing/Info/VBRI marker, which neither Tika nor `metadata-extractor` expose. The field will no longer be emitted; consumers tolerate its absence. Note: for VBR files, `metadata-extractor` will return the bitrate of the first audio frame (typically the nominal or header bitrate, not a computed average). Whether this value is meaningful for VBR files should be verified empirically during smoke testing — log what you observe but do not alter protocol output based on it in this milestone.
- **`/OriginalArtist` — owner decision: drop it.** The ID3v2 TOPE frame ("original performer") is not exposed by Tika's standard `parse()` output. This was the only ID3v2-exclusive field in the old code and is low-risk to lose. The field will no longer be emitted.
- **`XMPDM.ALBUM_ARTIST` is available but maps to nothing.** It is distinct from `/OriginalArtist`. Do not introduce new protocol keys in this milestone.
- **Compatibility boundary is `MessagePak`, not library API names.** The parser can change freely as long as the emitted keys and value types do not.
- **Preserve the `.mp3` gate exactly.** Current code only writes `/BitRate`, `/Hz`, and `/Vbr` when `path.toFile().getName().endsWith(".mp3")` is true. Keep this behaviour, including case-sensitivity.
- **Omit fields that are null, absent, or unparseable.** Same semantics as the current implementation.
- **Do not let metadata parsing break indexing.** If either parser throws, `FileItem`'s generic metadata should still be returned and audio enrichment skipped.
- **Keep this milestone scoped to MPG3.**
- **Codebase-specific surprise to account for later:** `typedescriptionlist.mml` contains built-in types like `TEXT`, `PICT`, and `ROMS`, but `StandardTypes` currently only exposes `MPG3` and `MOOV`. A future all-types Tika rollout should not assume the current enum is a complete list of metadata-bearing built-ins.
- **Standing refactors are opportunistic, not scope-expanding.** Follow `docs/conventions/standing-refactors.md` only in files already touched by the migration.
- **Logging cleanup is allowed in `MPG3FileItem`.** Replace `System.err.println(...)` with logger-based reporting.
- **Do not force the `ClientInfoFactoryUtilities` rename in this milestone.**

## 7. Acceptance criteria
- [ ] `mp3agic` is removed from `pom.xml` and no production code imports `com.mpatric.mp3agic.*`.
- [ ] `MPG3FileItem` emits `/BitRate`, `/Hz`, `/ID3Name`, `/Artist`, and `/Album` with the same types and units as before.
- [ ] `/BitRate` and `/Hz` are still only emitted for filenames ending with `.mp3`.
- [ ] Files whose metadata cannot be parsed still produce the generic `FileItem` metadata payload and do not fail indexing.
- [ ] No UI files are modified as part of this milestone.
- [ ] Any standing refactor applied as part of this work is limited to files already touched for the migration.
- [ ] Owner has acknowledged that `/Vbr` and `/OriginalArtist` will no longer be populated. ✅ (Option A chosen: drop both; empirically verify `/BitRate` values on VBR files during smoke testing.)

---
## ✦ IMPLEMENTATION DETAILS (for the implementation agent)
---

## 8. Affected files / classes
- `pom.xml` — remove `mp3agic`; add `tika-core`, `tika-parser-audiovideo-module`, and the chosen SLF4J binding (preferred: `slf4j-jdk14`) for MP3 metadata extraction.
- `src/main/java/com/myster/filemanager/MPG3FileItem.java` — replace `mp3agic` parsing with Tika-backed extraction while preserving `patchFunction2(MessagePak, Path)` and emitted keys/types.
- `src/test/java/com/myster/filemanager/TestMPG3FileItem.java` — new; regression tests for protocol-key mapping and failure behaviour.
- `src/test/resources/com/myster/filemanager/` — optional new MP3 fixture(s) only if needed for one end-to-end parse test.
- `docs/conventions/standing-refactors.md` — reference only; no edit expected unless the standing-refactor entry itself becomes fully completed by this project, which is not currently planned.

## 9. Step-by-step implementation
1. **Build dependency swap in `pom.xml`.**
   - Remove the `com.mpatric:mp3agic` block entirely.
   - Add the following three dependencies (exact block to paste):

   ```xml
   <!-- Apache Tika: metadata extraction (replaces mp3agic) -->
   <!-- tika-core must be explicit; tika-parser-audiovideo-module carries it as `provided` in its parent -->
   <dependency>
     <groupId>org.apache.tika</groupId>
     <artifactId>tika-core</artifactId>
     <version>3.3.1</version>
   </dependency>
   <!-- Contains org.apache.tika.parser.mp3.Mp3Parser and all audio/video parsers -->
   <dependency>
     <groupId>org.apache.tika</groupId>
     <artifactId>tika-parser-audiovideo-module</artifactId>
     <version>3.3.1</version>
   </dependency>
   <!-- Routes Tika/SLF4J log output into Myster's existing java.util.logging (JUL) setup.
        Without a binding SLF4J 2.x silently drops all Tika log output (NOP logger). -->
   <dependency>
     <groupId>org.slf4j</groupId>
     <artifactId>slf4j-jdk14</artifactId>
     <version>2.0.18</version>
   </dependency>
   ```

   - Fallback only if direct `Mp3Parser` use proves insufficient in testing:
     replace `tika-parser-audiovideo-module` with `org.apache.tika:tika-parsers-standard-package:3.3.1`
     (larger footprint; same migration path; no code changes required).
   - Do **not** depend on `org.apache.tika:tika-parser-audio-module`; that artifact does not exist as a consumable jar in Tika 3.3.1.
   - Confirm the new dependencies are compatible with the existing shade configuration.

2. **Keep the public shape of `MPG3FileItem` stable while allowing local cleanup.**
   - Do not change:
     - class name `MPG3FileItem`
     - field `messagePackRepresentation`
     - method signatures `getMessagePackRepresentation()` and `patchFunction2(MessagePak, Path)`
   - `getMessagePackRepresentation()` should keep the current flow:
     1. reuse cached `messagePackRepresentation` when present
     2. call `super.getMessagePackRepresentation()`
     3. enrich the same `MessagePak`
     4. return it
   - Allowed local cleanup in this file:
     - replace `System.err.println(...)` parse-failure reporting with logger-based reporting consistent with neighboring file-manager code
     - rename private helpers/locals if that improves clarity without changing any exposed/protocol-relevant names

3. **Replace `mp3agic` imports and parse flow in `MPG3FileItem`.**
   - Remove `ID3v1`, `ID3v2`, and `Mp3File` imports.
   - Add Tika imports for metadata extraction.
   - Preferred parser path for this milestone:
     - instantiate `org.apache.tika.parser.mp3.Mp3Parser`
     - parse into a `Metadata` instance using a lightweight handler such as `DefaultHandler`
   - Avoid broad parser bootstrap unless fallback to the standard package becomes necessary.
   - Keep parse failure non-fatal: if Tika throws during parse/open, log/emit a failure message and return without mutating protocol keys further.

4. **Introduce private helper methods inside `MPG3FileItem` for compatibility mapping.**
   Recommended helpers:
   - `private static void populateFromTikaMetadata(MessagePak messagePack, Path path, Metadata metadata)`
   - `private static void putIfNotBlank(MessagePak messagePack, String key, String value)`
   - `private static Optional<String> firstNonBlank(Metadata metadata, String... candidateKeys)` or equivalent using Tika constants where available
   - `private static OptionalLong parseBitrateToBitsPerSecond(String raw)`
   - `private static OptionalLong parseSampleRateHz(String raw)`
   - `private static Optional<Boolean> parseBooleanFlag(String raw)`

   These helpers exist to make the compatibility mapping testable without involving the full parser every time.

5. **Map Tika metadata into the existing protocol keys.**
   - Title-like field → `/ID3Name`
   - Artist-like field → `/Artist`
   - Album-like field → `/Album`
   - Original-artist-like field → `/OriginalArtist`
   - MP3-only numeric/boolean audio fields:
     - bitrate → `/BitRate` as `long` bits/sec
     - sample rate → `/Hz` as `long`
     - VBR marker → `/Vbr` as `boolean`
   - Probe multiple Tika metadata keys where necessary, because Tika often exposes both generic keys and format-specific/raw names.
   - Use omission semantics identical to current code:
     - blank string => do not write key
     - missing field => do not write key
     - unparsable numeric/boolean field => do not write key

6. **Preserve the current `.mp3` filename gate exactly.**
   - Before writing `/BitRate`, `/Hz`, and `/Vbr`, keep the current check based on the actual filename ending with `.mp3`.
   - Do not broaden this to other `MPG3` extensions in this milestone.
   - Do not normalize case in this milestone.

7. **Apply standing refactors only where the migration already touches a file.**
   - Check `docs/conventions/standing-refactors.md` before finalizing each modified file.
   - If a touched file has a prescribed mechanical rename or equivalent cleanup, apply it in the same change.
   - Do **not** add `ClientInfoFactoryUtilities.java` or other UI/search files to this project solely to satisfy the standing-refactors list.

8. **Do not change downstream consumers.**
   - Leave `ClientMPG3HandleObject.java`, `ClientInfoFactoryUtilities.java`, `FileTypeList.java`, and protocol/search code untouched unless a dependency or compile issue makes a change strictly necessary.
   - The whole point of this milestone is that these readers keep working unchanged because `MessagePak` remains stable.

9. **Remove the last `mp3agic` usage from the codebase.**
   - After updating `MPG3FileItem`, verify no remaining production imports/usages of `com.mpatric.mp3agic.*` remain.
   - The build should no longer need the dependency.

## 11. Docs / Javadoc to update
- `src/main/java/com/myster/filemanager/MPG3FileItem.java`
  - Update the class comment if it still reads as though it is specifically ID3/`mp3agic` based rather than a generic MPG3 metadata adapter.
  - Add/refresh method comments around `patchFunction2(...)` and any new mapping helpers to document the protocol-compatibility requirement.
- `pom.xml`
  - Keep dependency comments accurate if a comment is added for the new Tika dependency block.
  - If the implementation uses the fallback `tika-parsers-standard-package` instead of the preferred narrow module, document that in a dependency comment or implementation summary as a deliberate footprint trade-off.









