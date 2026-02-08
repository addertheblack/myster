# Myster Coding Conventions

This document captures Myster-specific coding conventions, preferred libraries, and architectural patterns. It serves as a reference for AI agents and developers working on the codebase.

## Table of Contents

- [UI Components](#ui-components)
- [Layout](#layout)
- [Data Persistence](#data-persistence)
- [Testing](#testing)
- [Preference Panels](#preference-panels)

**For architectural patterns** (Event System, Promise/Future, Listener Pattern, Dependency Injection, Threading), see:
- **[Important Patterns](myster-important-patterns.md)** - Key architectural and design patterns

---

## UI Components

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

---

*Last updated: February 2026*

