# Standing Refactors

Mechanical improvements to be applied **as you touch an affected file** — not all at once.
Doing them opportunistically keeps each diff focused and reviewable.

**Rule**: before committing changes to a file, check whether any directive below applies.
If it does, apply it in the same commit. Once every instance of a directive is done, remove
it from this file.

---

## Rename `*Util` / `*Utilities` → `*Utils`

Standard suffix for static-only helper classes is `*Utils`. The names below are legacy.
Rename the class and update all references when you touch the file.

| Current name | Rename to |
|---|---|
| `com.myster.identity.Util` | `Utils` |
| `com.general.tab.TabUtilities` | `TabUtils` |
| `com.myster.net.datagram.DatagramEncryptUtil` | `DatagramEncryptUtils` |
| `com.myster.net.datagram.client.DatagramUtilities` | `DatagramUtils` |
| `com.myster.net.stream.client.msdownload.MultiSourceUtilities` | `MultiSourceUtils` |
| `com.myster.search.ui.ClientInfoFactoryUtilities` | `ClientInfoFactoryUtils` |
| `com.myster.util.ThemeUtil` | `ThemeUtils` |

> `com.general.util.Util` is a general-purpose catch-all referenced almost everywhere.
> Rename it only if there is already another reason to touch it broadly.

