# Type Choice — Public/Private Grouping & File Manager Warning

## Design (for the owner/reviewer)

---

### 1. Summary

Every `TypeChoice` combo box in the UI currently lists all enabled types in a flat, unlabelled
list. Now that a type can be either public (anyone can list and download) or private (members
only), the dropdown should make that distinction visible. Additionally, the File Manager
preferences panel (`FmiChooser`) needs a persistent visual cue when the user is configuring a
public type — the risk of accidentally sharing files publicly is real and easy to miss.

---

### 2. Non-goals

- No change to how types are stored, enabled/disabled, or persisted.
- No change to `SearchWindow` or `TrackerWindow` beyond inheriting the new grouping from
  `TypeChoice`.
- No new warning for private types — private is the "safe" state and needs no cue.
- No colour change to the `TitledBorder` in `FmiChooser` (too subtle; a dedicated label is clearer).
- No per-type icon in the combo box (future consideration only).

---

### 3. Assumptions & open questions

- All built-in types (MP3, Text, ROMs, etc.) are public — `TypeDescription` currently carries
  no `isPublic` flag, so they will default to `true`.
- The warning banner is yellow (`"Actions.Yellow"`) not red — it is informational, not an error.
- If all enabled types happen to be the same visibility (all public or all private), the
  corresponding section header is still shown — the grouping is always present so the user
  always knows what they are looking at.

---

### 4. Proposed Design

**`TypeDescription.isPublic()`** — a new boolean field on `TypeDescription`. Built-in types
default to `true`. Custom types carry the value from `CustomTypeDefinition.isPublic()`, which
is already derived from `AccessListState.getPolicy().isListFilesPublic()`. The new 8-argument
constructor is the canonical one; the existing 6- and 7-argument constructors chain to it with
`true` so no existing call sites break.

**`TypeChoice` grouping** — `buildTypeList()` / `rebuildTypeList()` split enabled types into
public and private buckets before adding items. Public types are listed first under a
non-selectable "Public Types" header; private types follow under a "Private Types" header,
separated by a `Util.SEPARATOR`. The headers use a prefix sentinel string (`HEADER_PREFIX`)
that the renderer and model can recognise to render them in a smaller bold muted style and
block selection. The `types[]` array is reordered to match — public first, private second —
so the existing index-based `getType(int)` mapping remains correct. `setType()` is updated to
scan by `MysterType` equality rather than raw index, which it already does correctly.

`TypeChoice` also gains a `getSelectedTypeDescription()` accessor that returns the
`TypeDescription` for the current selection (empty for LAN/Bookmarks/headers), which
`FmiChooser` needs to query `isPublic()`.

**`FmiChooser` warning banner** — a `JLabel` (`publicWarningLabel`) is added as a new row
between the combo box row and the type-configuration panel. It uses the FlatLaf yellow
semantic colour (`UIManager.getColor("Actions.Yellow")`, fallback `#EDA200`) and a
`warning-icon.svg` sized to font height (same pattern as `MessageField.sayError`). It is
visible only when the selected type is public, hidden otherwise. The `ItemListener` on
`choice` already calls `restoreState()` on every selection change — `updateWarningBanner()`
is called from the same listener and from the constructor after initial state is set.

---

### 5. Architecture Connections

The public/private flag flows from the access list on disk through the model into the UI:

```
AccessListState.getPolicy().isListFilesPublic()
  → CustomTypeDefinition.isPublic()
  → DefaultTypeDescriptionList.buildTypeDescription()  [new 8-arg constructor]
  → TypeDescription.isPublic()                         [new field + getter]
  → TypeChoice.buildTypeList()                         [grouping logic]
  → TypeChoice.getSelectedTypeDescription()            [new accessor]
  → FmiChooser.updateWarningBanner()                   [new helper]
```

Built-in types are loaded from `typedescriptionlist.mml` via the existing 6-arg constructor,
which chains to `isPublic = true` — no change to the resource file or the loader.

| New / changed thing | Owned / created by | Called / used by | Connects to (existing) |
|---|---|---|---|
| `TypeDescription.isPublic()` | `com.myster.type` | `TypeChoice`, `FmiChooser` | `CustomTypeDefinition.isPublic()`, `DefaultTypeDescriptionList.buildTypeDescription()` |
| `TypeChoice` grouping + headers | `com.myster.util` | `SearchTab`, `TrackerWindow`, `FmiChooser` | `TypeDescriptionList.getEnabledTypes()`, `Util.addSeparatorSupport` |
| `TypeChoice.getSelectedTypeDescription()` | `com.myster.util` | `FmiChooser` | `TypeDescription.isPublic()` |
| `FmiChooser.publicWarningLabel` + `updateWarningBanner()` | `com.myster.filemanager.ui` | constructed inline | `TypeChoice.getSelectedTypeDescription()`, `IconLoader.loadSvg`, `UIManager` |

---

### 6. Key decisions & edge cases

- **Header items must be non-selectable.** `Util.addSeparatorSupport` already blocks
  `SEPARATOR` via a model override; the same model override is extended to also block any item
  whose string starts with the `HEADER_PREFIX` sentinel (`"\u0000HEADER:"`). The null-byte
  prefix makes accidental collision with a real type name impossible.
- **Index mapping after grouping.** `TypeChoice` maintains `types[]` as an ordered array that
  parallels the selectable items in the combo box. The reorder (public-first, private-second)
  must happen before items are added to the model so the array and the model stay in sync.
  The existing `getType(int)` caller in `FmiChooser`'s "Set all paths" button iterates over
  `getItemCount()` — it already calls `choice.getType(i).get()` wrapped in an Optional so a
  header/separator returning `empty` is handled correctly.
- **Warning banner SVG.** Reuse `warning-icon.svg` from `com.general.util` (already exists,
  used by `MessageField`). `IconLoader.loadSvg(MessageField.class, "warning-icon", h)` is the
  exact call pattern.
- **No reflow.** The warning label is always in the layout (row between the choice row and the
  panel); it is shown/hidden via `setVisible(boolean)`. This avoids GridBag reflow jank on
  every type change.

---

### 7. Acceptance criteria

- [ ] All `TypeChoice` instances (Search, Tracker, File Manager) show "Public Types" and
  "Private Types" section headers in the dropdown when both kinds of type are enabled.
- [ ] Header items cannot be selected; arrow-key navigation skips them.
- [ ] If only public (or only private) types are enabled, only that section header appears —
  no empty opposing section.
- [ ] The "Local Network" / "Bookmarks" extras (Tracker window only) still appear after the
  private types section, separated as before.
- [ ] Selecting a public type in `FmiChooser` shows the yellow warning banner.
- [ ] Selecting a private type in `FmiChooser` hides the banner.
- [ ] The warning banner icon uses the FlatLaf yellow semantic colour and scales with font
  height without causing layout reflow.
- [ ] No existing tests break; all 288 tests pass.

---

## ✦ IMPLEMENTATION DETAILS (for the implementation agent)

---

### 8. Affected files / classes

Modified:
- `com.myster.type.TypeDescription` — add `isPublic` field; new 8-arg constructor; `isPublic()` getter; existing constructors chain to it with `true`
- `com.myster.type.DefaultTypeDescriptionList` — `buildTypeDescription()` passes `def.isPublic()` as 8th arg
- `com.myster.util.TypeChoice` — grouped `buildTypeList()`; extended model override; `getSelectedTypeDescription()`; `HEADER_PREFIX` constant
- `com.myster.filemanager.ui.FmiChooser` — `publicWarningLabel` field; `updateWarningBanner()` helper; label added to layout; listener wired

No new files (reuses `com/general/util/warning-icon.svg` already on disk).

---

### 9. Step-by-step implementation

#### Step 1 — `TypeDescription.isPublic()`

Add `private final boolean isPublic` field.

Add new 8-arg constructor:
```java
public TypeDescription(MysterType type, String internalName, String description,
        String[] extensions, boolean isArchived, boolean isEnabledByDefault,
        TypeSource source, boolean isPublic) { ... }
```

Chain existing 7-arg constructor: `this(..., true)`.

Add getter:
```java
/** @return true if non-members may list and download files; false for private types */
public boolean isPublic() { return isPublic; }
```

#### Step 2 — `DefaultTypeDescriptionList.buildTypeDescription()`

Change the `new TypeDescription(...)` call to the 8-arg constructor, passing `def.isPublic()`
as the final argument. One-line change.

#### Step 3 — `TypeChoice` grouping

**3a. `HEADER_PREFIX` constant:**
```java
static final String HEADER_PREFIX = "\u0000HEADER:";
```

**3b. Extend `Util.addSeparatorSupport`'s model — or install an overriding model immediately
after the `addSeparatorSupport` call** that blocks both `SEPARATOR` and `HEADER_PREFIX` items:
```java
// called in constructor, after Util.addSeparatorSupport(this)
setModel(new DefaultComboBoxModel<String>() {
    @Override public void setSelectedItem(Object o) {
        if (isNonSelectable(o)) return;
        super.setSelectedItem(o);
    }
});
```

**3c. Install renderer** (in constructor, after `setModel`):
```java
// capture existing renderer before replacing
final ListCellRenderer<String> base = (ListCellRenderer<String>) getRenderer();
setRenderer((list, value, index, isSelected, hasFocus) -> {
    if (value != null && value.startsWith(HEADER_PREFIX)) {
        JLabel lbl = new JLabel(value.substring(HEADER_PREFIX.length()));
        lbl.setFont(lbl.getFont().deriveFont(Font.BOLD, lbl.getFont().getSize2D() - 1f));
        lbl.setForeground(UIManager.getColor("Label.disabledForeground"));
        lbl.setBorder(new EmptyBorder(3, 6, 1, 4));
        return lbl;
    }
    if (Util.SEPARATOR.equals(value)) { JSeparator s = new JSeparator(); s.setEnabled(false); return s; }
    return base.getListCellRendererComponent(list, value, index, isSelected, hasFocus);
});
```

**3d. `isNonSelectable` helper:**
```java
private static boolean isNonSelectable(Object item) {
    if (item == null) return false;
    String s = item.toString();
    return s.startsWith(HEADER_PREFIX) || Util.SEPARATOR.equals(s);
}
```

**3e. `buildTypeList()` — replace flat loop with grouped logic:**
```java
TypeDescription[] pub  = Arrays.stream(types).filter(TypeDescription::isPublic).toArray(TypeDescription[]::new);
TypeDescription[] priv = Arrays.stream(types).filter(t -> !t.isPublic()).toArray(TypeDescription[]::new);

// reorder types[] to match combo order: public first, then private
System.arraycopy(pub,  0, types, 0,            pub.length);
System.arraycopy(priv, 0, types, pub.length,   priv.length);

if (pub.length > 0) {
    addItem(HEADER_PREFIX + "Public Types");
    for (TypeDescription td : pub) addItem(td.getDescription());
}
if (priv.length > 0) {
    if (pub.length > 0) addItem(Util.SEPARATOR);
    addItem(HEADER_PREFIX + "Private Types");
    for (TypeDescription td : priv) addItem(td.getDescription());
}
if (addExtras) { /* existing SEPARATOR / LOCAL_NETWORK / SEPARATOR / BOOKMARKS */ }
```

**3f. `rebuildTypeList()`** — call `buildTypeList()` after `removeAllItems()` and
`types = tdList.getEnabledTypes()`, same as today but delegating to the new `buildTypeList()`.

**3g. `getType(int comboIndex)`** — must skip non-selectable items when mapping combo index
to `types[]` index:
```java
public Optional<MysterType> getType(int comboIndex) {
    int typeIdx = 0;
    for (int i = 0; i < getItemCount(); i++) {
        if (isNonSelectable(getItemAt(i))) continue;
        String item = getItemAt(i);
        if (LOCAL_NETWORK.equals(item) || BOOKMARKS.equals(item)) continue;
        if (i == comboIndex) return typeIdx < types.length ? Optional.of(types[typeIdx].getType()) : Optional.empty();
        typeIdx++;
    }
    return Optional.empty();
}
```

**3h. `setType(MysterType)`** — already scans `types[]` by `equals`; only the
`setSelectedIndex(i)` must now translate `i` (types[] index) to the correct combo-box index
by counting non-header, non-extra items. Adjust accordingly.

**3i. `getSelectedTypeDescription()`** — new accessor:
```java
public Optional<TypeDescription> getSelectedTypeDescription() {
    return getType(getSelectedIndex())
            .flatMap(t -> Arrays.stream(types).filter(td -> td.getType().equals(t)).findFirst());
}
```

**3j. `getSelectedDescription()`** — update to use `getSelectedTypeDescription()`:
```java
public String getSelectedDescription() {
    return getSelectedTypeDescription().map(TypeDescription::getDescription).orElse("");
}
```

#### Step 4 — `FmiChooser` warning banner

**4a.** Add field:
```java
private final JLabel publicWarningLabel;
```

**4b.** Construct it after `choice` is created:
```java
publicWarningLabel = new JLabel();
publicWarningLabel.setVisible(false); // hidden until updateWarningBanner() is called
```

**4c.** Add to layout between the combo-box row (row 0) and the panel (row 1). Shift the
panel down to row 2 (`withGridLoc(0, 2)`). Add the label at row 1:
```java
add(publicWarningLabel,
    outterGbc.withGridLoc(0, 1).withSize(2, 1).withWeight(1, 0)
             .withFill(GridBagConstraints.HORIZONTAL)
             .withInsets(new Insets(0, 4, 4, 4)));
```

**4d.** `updateWarningBanner()` helper:
```java
private void updateWarningBanner() {
    boolean isPublic = choice.getSelectedTypeDescription()
                             .map(TypeDescription::isPublic)
                             .orElse(false);
    if (isPublic) {
        publicWarningLabel.setForeground(
            Optional.ofNullable(UIManager.getColor("Actions.Yellow"))
                    .orElse(new Color(0xED, 0xA2, 0x00)));
        int h = publicWarningLabel.getFontMetrics(publicWarningLabel.getFont()).getHeight();
        try { publicWarningLabel.setIcon(IconLoader.loadSvg(MessageField.class, "warning-icon", h)); }
        catch (Exception ignored) { publicWarningLabel.setIcon(null); }
        publicWarningLabel.setText("Public type — files in this folder are visible to everyone on the network.");
        publicWarningLabel.setVisible(true);
    } else {
        publicWarningLabel.setVisible(false);
    }
}
```

**4e.** Call `updateWarningBanner()` from:
- The `ItemListener` on `choice` (same place `restoreState()` is called)
- Once at the end of the constructor, after `restoreState()`

**4f.** Add required imports: `TypeDescription`, `IconLoader`, `MessageField`, `UIManager`,
`Optional`, `Color`.

---

### 10. Tests to write

- `TypeChoice` index mapping: unit test that with 2 public + 1 private type, `getType(0)`
  returns empty (header), `getType(1)` returns the first public type, etc.
- `TypeDescription.isPublic()`: already covered implicitly by `TestAccessEnforcementUtils`
  and `TestDefaultTypeDescriptionListImport`; add a one-line check in
  `TestDefaultTypeDescriptionListImport` that a private-policy custom type loaded from an
  access list produces a `TypeDescription` with `isPublic() == false`.
- Manual smoke: open File Manager prefs with a mix of public and private types enabled;
  verify headers appear and the warning banner toggles correctly.

---

### 11. Docs / Javadoc to update

- `TypeDescription` — class Javadoc note that `isPublic()` reflects the access-list policy
  for custom types and defaults to `true` for built-in types.
- `TypeChoice` — class Javadoc: describe grouping behaviour and `HEADER_PREFIX` sentinel.
- `FmiChooser` — class Javadoc: mention the public-type warning banner.

