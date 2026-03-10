# Myster Coding Conventions

This document captures Myster-specific coding conventions, preferred libraries, and architectural patterns. It serves as a reference for AI agents and developers working on the codebase.

**Quick index** — what lives here:
- **AnswerDialog** — never use `JOptionPane`; use `AnswerDialog` instead
- **JMCList** — prefer over raw `JTable` for multi-column lists
- **Modal Dialogs** — must extend `JDialog`, not `JFrame`
- **SVG Icons & FlatLaf colors** — `IconLoader.loadSvg`, magic hex colors, `#6E6E6E` / `#DB5860` etc.
- **GridBagLayout** — use `GridBagBuilder`; never `setLayout(null)`
- **Preferences** — Java `Preferences` API; `MysterType.toShortBytes()` as key
- **Testing** — add `main()` to UI panels for standalone testing
- **Preference Panel save semantics** — add = immediate; edit/delete = on save
- **Naming** — no banner comments; `Utils` suffix for static-only classes
- **Extensible Enums** — `final class` + `static final` constants, not Java `enum`
- **Serialization** — use `MessagePak` for forward-compatible binary formats
- **Access Lists** — single source of truth; `AccessListManager` singleton; key-file edit gate
- **Prefs enabled/disabled** — store only identifier + `enabled` boolean
- **Code commenting style** — see [`Code Comments.md`](Code%20Comments.md)
- **Standing Refactors** — see [`standing-refactors.md`](standing-refactors.md); apply when you touch an affected file

**For architectural patterns**, see **[myster-important-patterns.md](myster-important-patterns.md)**:
- Event System, Promise/Future, Listener Pattern, Dependency Injection, Threading
- **FlatLaf Theming** — `UIManager.getColor("Actions.Red")` etc.; never hardcode `new Color(...)`

---

## Table of Contents

- [UI Components](#ui-components)
  - [Dialogs — AnswerDialog not JOptionPane](#dialogs--use-answerdialog-not-joptionpane)
  - [JMCList](#jmclist-preferences)
  - [Modal Dialogs](#modal-dialogs)
  - [Icon Loading & SVG Colors](#icon-loading-convention)
- [Layout](#layout)
- [Data Persistence](#data-persistence)
- [Testing](#testing)
- [Preference Panels](#preference-panels)
- [Naming Conventions](#naming-conventions)
  - [No Banner Comments](#no-section-divider-banner-comments)
  - [Utils Classes](#utils-classes)
- [Extensible Enums](#extensible-enums)
- [Serialization Extensibility](#serialization-extensibility)
- [Access Lists as Canonical Metadata](#access-lists-as-canonical-metadata)
- [Prefs-Based Enabled/Disabled Index](#prefs-based-enableddisabled-index)

---

## UI Components

### Dialogs — use AnswerDialog, not JOptionPane

**Rule**: Never use `JOptionPane` in Myster UI code. It ignores the application theme and looks wrong. Use `com.general.util.AnswerDialog` instead.

```java
// Simple alert
AnswerDialog.simpleAlert("Something went wrong.");

// Confirmation with custom buttons — returns the button label that was clicked
String answer = AnswerDialog.simpleAlert(
        AnswerDialog.getCenteredFrame(),
        "No extensions specified. Continue?",
        new String[] { "Continue", "Cancel" });
if (!"Continue".equals(answer)) return;
```

### JMCList Preferences

**Pattern**: Prefer JMCList over raw JTable for displaying lists of items with multiple columns.

**When to use**: Lists with sortable columns, selection, and custom rendering needs.

**Checkbox Columns**:
For boolean columns, use a custom TableCellRenderer and TableCellEditor to provide checkbox widgets:

```java
// Set up checkbox for a boolean column (e.g., column 2)
JTable table = (JTable) mcList;
table.getColumnModel().getColumn(2).setCellRenderer(new CheckBoxRenderer());
table.getColumnModel().getColumn(2).setCellEditor(new CheckBoxEditor());

// Renderer
private class CheckBoxRenderer extends JCheckBox implements TableCellRenderer {
    public CheckBoxRenderer() {
        setHorizontalAlignment(JCheckBox.CENTER);
    }

    @Override
    public Component getTableCellRendererComponent(JTable table, Object value,
            boolean isSelected, boolean hasFocus, int row, int column) {
        if (value instanceof SortableBoolean) {
            setSelected(((SortableBoolean) value).getBooleanValue());
        }
        setBackground(isSelected ? table.getSelectionBackground() : table.getBackground());
        setForeground(isSelected ? table.getSelectionForeground() : table.getForeground());
        return this;
    }
}

// Editor
private class CheckBoxEditor extends DefaultCellEditor {
    private final JCheckBox checkBox;

    public CheckBoxEditor() {
        super(new JCheckBox());
        checkBox = (JCheckBox) getComponent();
        checkBox.setHorizontalAlignment(JCheckBox.CENTER);
    }

    @Override
    public Object getCellEditorValue() {
        // Update underlying data when checkbox is toggled
        int row = ((JTable) mcList).getEditingRow();
        if (row >= 0) {
            MyItem item = (MyItem) mcList.getMCListItem(row);
            item.setEnabled(checkBox.isSelected());
        }
        return new SortableBoolean(checkBox.isSelected());
    }
}
```

### Modal Dialogs

**Pattern**: Modal dialogs must extend `JDialog` (not `JFrame`) with `modal=true` parameter.

**Critical**: If you extend `JFrame` for a modal dialog, the dialog won't block and `showDialog()` will return immediately with the wrong result!

**Example**:
```java
public class MyDialog extends JDialog {
    public MyDialog(JFrame parent) {
        super(parent, "Dialog Title", true); // true = modal
        // ... setup components ...
    }
    
    public Result showDialog() {
        setVisible(true);  // This blocks until dialog is closed (because modal=true)
        return result;
    }
}
```

### Icon Loading Convention

**Pattern**: Icons are loaded using `IconLoader.loadSvg()` and stored in the resources directory matching the package structure of the class that uses them.

**Location**: SVG icon files are placed in `src/main/resources` matching the package path of the Java class that loads them (e.g., `src/main/resources/com/myster/type/ui/add-icon.svg` for a class in `com.myster.type.ui` package).

**CRITICAL - SVG Color Theming**: FlatLaf automatically substitutes specific hex colors with theme-appropriate colors. You **CANNOT** use `currentColor` or `ColorFilter` for stroke-based SVGs.

**Magic Colors** (auto-substituted by FlatLaf):
- `#6E6E6E` - Standard foreground color (changes with theme)
- `#DB5860` - Error / red action color (maps to `UIManager.getColor("Actions.Red")`)
- `#EDA200` - Warning / yellow action color (maps to `UIManager.getColor("Actions.Yellow")`)
- `#59A869` - Success / green action color (maps to `UIManager.getColor("Actions.Green")`)
- See [FlatIconColors.java](https://github.com/JFormDesigner/FlatLaf/blob/main/flatlaf-core/src/main/java/com/formdev/flatlaf/FlatIconColors.java) for full list

**SVG Example**:
```xml
<!-- CORRECT: Use magic hex color -->
<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24" 
     fill="none" stroke="#6E6E6E" stroke-width="2">
  <circle cx="12" cy="12" r="10"/>
</svg>

<!-- WRONG: currentColor doesn't work with FlatLaf's substitution -->
<svg stroke="currentColor">...</svg>
```

**Loading**:
```java
// Load icon from resources matching the current class's package
FlatSVGIcon icon = IconLoader.loadSvg(MyClass.class, "icon-name", 16);

// ColorFilter is optional and only needed for special cases (e.g., menu item selection states)
// For most icons, FlatLaf's automatic color substitution is sufficient
```

**Toolbar Icons**:
- Use 16x16 size for toolbar buttons
- Create `Action` objects instead of direct `JButton` instances
- Set `Action.SMALL_ICON` for the icon
- Set `Action.SHORT_DESCRIPTION` for tooltip text
- The toolbar will automatically display icon-only buttons with tooltips
- **Use `#6E6E6E` in SVG files** - FlatLaf automatically substitutes this with theme colors
- ColorFilter is NOT needed for toolbar icons (FlatLaf handles it automatically)

**Example** (from ProgressManagerWindow):
```java
pauseAction = new AbstractAction("Pause") {
    @Override
    public void actionPerformed(ActionEvent e) {
        // action logic
    }
};
pauseAction.putValue(Action.SHORT_DESCRIPTION, "Pause download");
pauseAction.setEnabled(false);

// Load icon - FlatLaf automatically applies theme colors
FlatSVGIcon pauseIcon = IconLoader.loadSvg(ProgressManagerWindow.class, "pause-icon", 16);
pauseAction.putValue(Action.SMALL_ICON, pauseIcon);

toolbar.add(pauseAction); // Adds icon-only button with tooltip
```

**Note**: FlatLaf's automatic color substitution handles theme changes for icons that use the magic hex colors like `#6E6E6E`. You only need `ColorFilter` for special cases like menu item selection states.

> **Using these colours in Java code** (not SVGs): use `UIManager.getColor("Actions.Red")` etc.
> rather than hardcoding `new Color(...)`. See the **FlatLaf Theming** section in
> [`myster-important-patterns.md`](myster-important-patterns.md) for the full pattern and rationale.

## Layout

### GridBagLayout with GridBagBuilder

**Pattern**: Use `GridBagLayout` with `GridBagBuilder` for resizable panels, especially preference panels.

**Avoid**: Using `setLayout(null)` or fixed layouts that don't resize properly.

**Example**:
```java
setLayout(new GridBagLayout());
GridBagBuilder gbc = new GridBagBuilder().withInsets(new Insets(5, 5, 5, 5));

add(component, gbc.withGridLoc(0, 0).withWeight(1.0, 0.0).withFill(GridBagConstraints.HORIZONTAL));
```

## Data Persistence

### Preferences Storage

**Pattern**: Use Java `Preferences` API for all persistent configuration and state.

**Example**:
```java
Preferences prefs = Preferences.userRoot().node("MysterTypes");
prefs.put("key", "value");
prefs.getInt("key", defaultValue);
```

**Location**: Preference nodes follow a hierarchical naming pattern based on feature area.

### Preferences for Custom Types

**Pattern**: Use `MysterType.toShortBytes()` as a key/identifier when storing custom types in preferences.

**Purpose**: Creates a shorter identifier for a type than the full public key.

## Testing

### Standalone UI Testing

**Pattern**: Add a `main()` method to UI panels for standalone testing without launching the full application.

**Example**:
```java
public static void main(String[] args) {
    SwingUtilities.invokeLater(() -> {
        JFrame testFrame = new JFrame("Component Test");
        testFrame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        
        MyComponent component = new MyComponent();
        testFrame.add(component);
        
        testFrame.setSize(600, 500);
        testFrame.setLocationRelativeTo(null);
        testFrame.setVisible(true);
    });
}
```

## Preference Panels

**Save Semantics**:
- **Add operations**: Saved immediately (cannot be "canceled" - deletion is the undo)
- **Edit/Delete operations**: Applied when the panel is saved (can be canceled)
- **Rationale**: Prevents loss of user data entry effort (add dialogs have their own OK/Cancel)


## Naming Conventions

### No Section-Divider Banner Comments

Do not use banner-style section dividers in Java source files:

```java
// Don't do this:
// ---------------------------------------------------------------------------
// Private helpers
// ---------------------------------------------------------------------------
```

Methods and fields speak for themselves. If a class needs section headers to be readable, it's a sign the class is too large and should be split.

### Utils Classes

**Pattern**: Classes that contain only static methods (no instances) should have `Utils` appended to the class name.

**Examples**: `AccessListStorageUtils`, `FooUtils`, `MultiSourceUtilities`

**Note**: The codebase has some historical inconsistency between `Util`, `Utils`, and `Utilities` suffixes, but the convention going forward is `Utils`. The intent is the same in all cases: the class is a collection of static methods, not something you instantiate.

---

## Extensible Enums

**Pattern**: For enum-like values that appear in serialized formats (wire protocol, file formats), use a `final class` with `static final` constants instead of a Java `enum`. This supports forward compatibility: unknown values from future versions can be preserved without crashing.

**Key characteristics**:
- String-based identifiers (not numeric) — self-describing and unlikely to conflict
- `fromString(String)` factory method returns the known constant or creates a non-canonical instance
- `isCanonical()` distinguishes known from unknown values
- Known values are interned via a `Map<String, T>` for identity comparison

**When to use**: Any enumeration that is serialized to disk or sent over the wire and may grow over time (e.g., `OpType`, `Role`).

**Example** (from `com.myster.access.OpType`):
```java
public final class OpType {
    public static final OpType SET_POLICY = new OpType("SET_POLICY", true);
    public static final OpType ADD_MEMBER = new OpType("ADD_MEMBER", true);
    // ...more canonical constants...

    private static final Map<String, OpType> KNOWN_TYPES = new ConcurrentHashMap<>();
    static { KNOWN_TYPES.put(SET_POLICY.identifier, SET_POLICY); /* ... */ }

    private final String identifier;
    private final boolean canonical;

    public static OpType fromString(String identifier) {
        OpType known = KNOWN_TYPES.get(identifier);
        return known != null ? known : new OpType(identifier, false);
    }

    public boolean isCanonical() { return canonical; }
}
```

## Serialization Extensibility

**Pattern**: For data structures that may gain new fields in future versions, use `MessagePak` (tree-structured binary format) instead of fixed-field binary layouts. Old nodes silently ignore unknown fields; new nodes can read old data with defaults.

**Example** (from `com.myster.access.Policy`):
```java
public byte[] toMessagePakBytes() throws IOException {
    MessagePak pak = MessagePak.newEmpty();
    pak.putBoolean("/listFilesPublic", listFilesPublic);
    return pak.toBytes();
}

public static Policy fromMessagePakBytes(byte[] bytes) throws IOException {
    MessagePak pak = MessagePak.fromBytes(bytes);
    boolean listFilesPublic = pak.getBoolean("/listFilesPublic").orElse(false);
    // unknown fields silently ignored — forward-compatible by design
    return new Policy(listFilesPublic);
}
```

---

## Access Lists as Canonical Metadata

**Pattern**: For types that have access control, the `AccessList` file on disk is the single
authoritative source of truth for all metadata (name, description, extensions, policy, public key).
Java `Preferences` stores **only** the enabled/disabled flag per type — nothing else.

**Rule**: Never duplicate access list metadata into `Preferences`. If you need type metadata at
runtime, load it from `AccessListManager`.

**Ownership / edit gate**: Whether the current machine can edit a type is determined solely by
the presence of an admin key file at `{PrivateDataPath}/AccessListKeys/{mysterType_hex}.key`.
There is no separate "isOwner" flag anywhere else. `AccessListKeyUtils.hasKeyPair(type)` is the
check to use.

**Singleton**: There is exactly one `AccessListManager` instance in the application, created in
`Myster.java` before `DefaultTypeDescriptionList`. Never `new AccessListManager()` anywhere else.

---

## Prefs-Based Enabled/Disabled Index

When a subsystem has a collection of items that can be enabled/disabled, the `Preferences`
store should contain only the item identifier (as the node name) and the `enabled` boolean.
All other metadata lives elsewhere (e.g. access list file, MML resource). This keeps the prefs
store minimal and avoids stale data problems.

```
CustomTypes/
  {mysterType_hex}/    ← node name is the unique identifier
    enabled = true     ← the ONLY key written here
```

Old prefs nodes that contain extra keys from a previous implementation are silently ignored
on read. Missing access list → delete the stale prefs node; log a WARNING; skip.

---

## Collection Helpers

Prefer `com.general.util.Util.filter` and `Util.map` over the Java stream API for simple
filter and map operations. They are more concise and faster for typical Myster collection sizes.

```java
// Preferred
List<ServerPickerItem> filtered = Util.filter(allItems,
        item -> term.isEmpty() || item.getServerName().contains(term));

// Avoid for simple cases — too verbose
List<ServerPickerItem> filtered = allItems.stream()
        .filter(item -> term.isEmpty() || item.getServerName().contains(term))
        .toList();
```

Use streams only when you need operations not covered by `Util` (e.g. `flatMap`, `reduce`,
`collect` to a non-list type).

---

*Last updated: March 2026*
