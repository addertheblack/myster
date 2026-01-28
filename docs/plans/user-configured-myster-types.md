# User Configured Myster Types

## Summary
Implement a UI and data model for users to configure custom MysterTypes (overlay networks). Users will be able to create, edit, delete, enable, and disable custom types alongside the built-in default types. Each custom type has a user-readable name, description, file extensions, archive search flag, and a public/private flag (private functionality deferred). This replaces the existing simple enable/disable preferences pane with a more comprehensive type management system.

## Goals
- Allow users to create, edit, and delete custom MysterTypes
- Provide UI for configuring custom type properties:
  - User-readable name (displayed in UI)
  - User-readable description
  - List of accepted file extensions
  - Flag to search inside .zip files
  - Public/private flag (UI present, but private functionality not yet implemented)
- Integrate custom types with existing TypeDescriptionList infrastructure
- Show both default (built-in) and custom types in the same UI
- Allow enable/disable for all types (default and custom)
- Prevent deletion of default types
- Replace the existing `TypeManagerPreferencesGUI` with new comprehensive type manager

## Non-goals
- Implementing private network functionality (crypto/authentication) - just add the flag placeholder
- Modifying the MysterType class itself (it already supports public keys)
- Changing how types are used in search/sharing (that infrastructure already works)
- Supporting multiple users or per-user type configurations
- Import/export of type configurations (future enhancement)

## Proposed Design (High-level)

### Core Concept
MysterTypes represent overlay networks. Currently, the system loads default types from `TypeDescriptionList.mml` resource file. We'll extend this to also load and persist user-created custom types from preferences, presenting both seamlessly to the user.

### Data Model

**TypeSource enum**: NEW
- `DEFAULT` - Built-in types from TypeDescriptionList.mml (non-deletable)
- `CUSTOM` - User-created types (deletable, editable)

**CustomTypeDefinition class**: NEW
- Stores user-created type definitions in preferences
- Fields:
  - `PublicKey publicKey` - The cryptographic key that defines this type
  - `String name` - User-readable name
  - `String description` - User-readable description
  - `String[] extensions` - File extensions (e.g., ["mp3", "flac"])
  - `boolean searchInArchives` - Whether to look inside ZIP files
  - `boolean isPublic` - Public/private flag (always true for now, private not implemented)
  - `boolean enabled` - Whether type is enabled
- Persisted to preferences as MessagePack or JSON

**Enhanced TypeDescription**: MODIFIED
- Add `TypeSource source` field to indicate if default or custom
- Add `boolean isDeletable()` method (returns source == CUSTOM)
- Add `boolean isEditable()` method (returns source == CUSTOM)

### UI Design

**New TypeManagerV2PreferencesGUI**: Replaces TypeManagerPreferencesGUI

Layout:
```
+--------------------------------------------------+
| Type Manager                                      |
|--------------------------------------------------|
| [Instructions/Help Text]                          |
|--------------------------------------------------|
|                               [+ Add Type] [Edit] |
| +--------------------------------------------+    |
| | Name         | Desc     | Enabled | Source |   |
| |--------------|----------|---------|--------|   |
| | Audio (MP3)  | MP3 file | [x]     | Built-in|  |
| | Video (MOV)  | Movies   | [x]     | Built-in|  |
| | My Network   | Custom   | [ ]     | Custom  |   |
| | ...          | ...      | ...     | ...    |   |
| +--------------------------------------------+    |
|                                      [Delete]     |
+--------------------------------------------------+
```

**Add/Edit Type Dialog**: NEW
- Modal dialog for creating or editing custom types
- Fields:
  - Name (required, text field)
  - Description (optional, text area)
  - Public Key (required, display as hex or allow import, for MVP: auto-generate)
  - File Extensions (list widget: add/remove extensions)
  - Search in archives (checkbox)
  - Public/Private (radio buttons, private disabled with tooltip)
- Validation:
  - Name must be non-empty and unique
  - At least one extension (or allow empty for "all files"?)
  - Public key must be valid

### Integration Points

**TypeDescriptionList interface**: MINIMAL CHANGES
- `getAllTypes()` returns both default and custom types
- `getEnabledTypes()` filters by enabled flag (works for both)
- `get(MysterType)` looks up in both default and custom
- Add: `addCustomType(CustomTypeDefinition)` - NEW
- Add: `removeCustomType(MysterType)` - NEW
- Add: `updateCustomType(MysterType, CustomTypeDefinition)` - NEW

**DefaultTypeDescriptionList implementation**: MODIFIED
- Load default types from TypeDescriptionList.mml (existing)
- Load custom types from preferences (NEW)
- Merge both into unified type list
- Save custom types to preferences on changes
- Save enabled/disabled state for all types

### Persistence Strategy

**Custom Types Storage**: Preferences node `CustomTypes`

Use `MysterType.toShortBytes()` (MD5 hash of public key, 16 bytes) as the preference node key. This provides a short, unique identifier for each custom type without needing to store the full public key multiple times.

```
CustomTypes/
  <type-short-bytes-hex>/    # e.g., "a3f5c2d1e4b6..." (32 hex chars = 16 bytes)
    name = "My Custom Network"
    description = "Files for my project"
    publicKey = <base64>       # Store full key for reconstruction
    extensions = ["doc", "pdf"]
    searchInArchives = true
    isPublic = true
    enabled = true
  <another-type-short-bytes-hex>/
    ...
```

**Key Benefits of Using Short Bytes**:
- Compact: 32 hex characters vs. hundreds for base64-encoded public key
- Unique: MD5 hash ensures uniqueness
- Consistent: Same format used throughout Myster for type identification
- Efficient: Fast lookup without string parsing

**Enabled/Disabled State**: Existing mechanism in preferences
- Continue using existing `DefaultTypeDescriptionList saved defaults` key
- Works for both default and custom types
- Uses `MysterType.toString()` (which returns hex string of short bytes)

## Affected Modules/Packages

- `com.myster.type` - Core type system
- `com.myster.type.ui` - UI for type management
- `com.myster.pref` - Preferences persistence

## Files/Classes to Change or Create

### Files to Create

1. **`com/myster/type/TypeSource.java`**
   - Enum: `DEFAULT`, `CUSTOM`
   - Indicates source of a type definition

2. **`com/myster/type/CustomTypeDefinition.java`**
   - Data class for user-created type definitions
   - Includes all configurable properties
   - Methods for serialization/deserialization to/from preferences

3. **`com/myster/type/CustomTypeManager.java`**
   - Manages custom type persistence
   - CRUD operations for custom types
   - Integration with preferences

4. **`com/myster/type/ui/TypeManagerV2PreferencesGUI.java`**
   - Replacement for TypeManagerPreferencesGUI
   - Unified UI for default and custom types
   - Enable/disable, add, edit, delete actions

5. **`com/myster/type/ui/TypeEditorDialog.java`**
   - Modal dialog for creating/editing custom types
   - Form validation
   - Public key generation/import

### Files to Modify

6. **`com/myster/type/TypeDescription.java`**
   - Add `TypeSource source` field
   - Add `isDeletable()` method
   - Add `isEditable()` method
   - Update constructor to accept source

7. **`com/myster/type/TypeDescriptionList.java`** (interface)
   - Add `addCustomType(CustomTypeDefinition def)` method
   - Add `removeCustomType(MysterType type)` method
   - Add `updateCustomType(MysterType type, CustomTypeDefinition def)` method

8. **`com/myster/type/DefaultTypeDescriptionList.java`**
   - Load custom types from preferences in constructor
   - Implement new custom type CRUD methods
   - Merge default and custom types in getAllTypes()
   - Save custom types when modified

9. **`com/myster/Myster.java`**
   - Replace `TypeManagerPreferencesGUI` with `TypeManagerV2PreferencesGUI` in preferences registration

### Files to Deprecate (Eventually Delete)

10. **`com/myster/type/ui/TypeManagerPreferencesGUI.java`**
    - Mark as deprecated
    - Will be removed after TypeManagerV2 is stable

## Step-by-Step Implementation Plan

### Phase 1: Core Data Model

1. **Create TypeSource enum**
   - Simple enum: DEFAULT, CUSTOM
   - Javadoc explaining purpose

2. **Create CustomTypeDefinition class**
   - All fields for custom type configuration
   - Constructor, getters
   - `toPreferences()` - serialize to Preferences node
   - `fromPreferences(Preferences node)` - deserialize from Preferences node
   - Validation methods

3. **Update TypeDescription**
   - Add `TypeSource source` field (default to DEFAULT for compatibility)
   - Add constructor parameter for source
   - Add `isDeletable()` method: `return source == TypeSource.CUSTOM;`
   - Add `isEditable()` method: `return source == TypeSource.CUSTOM;`

### Phase 2: Custom Type Persistence

4. **Create CustomTypeManager**
   - Manages loading/saving custom types from preferences
   - Uses `MysterType.toHexString()` (which calls `toShortBytes()`) for preference node names
   - Methods:
     - `List<CustomTypeDefinition> loadCustomTypes()`
       - Iterates through child nodes under "CustomTypes"
       - Each node name is the hex string of MysterType short bytes
       - Deserializes CustomTypeDefinition from each node
     - `void saveCustomType(CustomTypeDefinition def)`
       - Creates/updates node at `CustomTypes/<type.toHexString()>`
       - Stores all properties as preference values
     - `void deleteCustomType(MysterType type)`
       - Removes node at `CustomTypes/<type.toHexString()>`
     - `void updateCustomType(MysterType type, CustomTypeDefinition def)`
       - Updates existing node at `CustomTypes/<type.toHexString()>`
   - Uses Preferences API with simple key/value pairs (not MessagePack - simpler for debugging)
   - Example node structure:
     ```java
     Preferences customTypesRoot = prefs.node("CustomTypes");
     Preferences typeNode = customTypesRoot.node(mysterType.toHexString());
     typeNode.put("name", "My Network");
     typeNode.put("description", "Custom network for...");
     typeNode.put("publicKey", Base64.getEncoder().encodeToString(publicKey.getEncoded()));
     // extensions stored as comma-separated or as sub-nodes
     typeNode.putBoolean("searchInArchives", true);
     typeNode.putBoolean("isPublic", true);
     ```

5. **Update TypeDescriptionList interface**
   - Add methods for custom type management:
     ```java
     void addCustomType(CustomTypeDefinition def);
     void removeCustomType(MysterType type) throws IllegalArgumentException;
     void updateCustomType(MysterType type, CustomTypeDefinition def) throws IllegalArgumentException;
     ```

6. **Update DefaultTypeDescriptionList**
   - Add `CustomTypeManager customTypeManager` field
   - In constructor:
     - Load default types (existing code)
     - Load custom types via `customTypeManager.loadCustomTypes()`
     - Merge into unified types array
     - Mark each TypeDescription with appropriate TypeSource
   - Implement custom type CRUD methods
   - Update `saveEverythingToDisk()` to save custom types

### Phase 3: UI Components

7. **Create TypeEditorDialog**
   - Extends JDialog
   - Form fields for all custom type properties:
     - Name (JTextField)
     - Description (JTextArea)
     - Public Key (display hex, with "Generate New" button)
     - Extensions (JList with add/remove buttons)
     - Search in archives (JCheckBox)
     - Public/Private (JRadioButton group, private disabled)
   - Validation on OK:
     - Name non-empty and unique
     - Public key valid
   - Returns CustomTypeDefinition or null if cancelled

8. **Create TypeManagerV2PreferencesGUI**
   - Extends PreferencesPanel
   - Use MCList or JTable to show types
   - Columns:
     - Name (String)
     - Description (String, truncated)
     - Enabled (Boolean, checkbox)
     - Source (String: "Built-in" or "Custom")
   - Buttons:
     - "Add Type" - opens TypeEditorDialog in create mode
     - "Edit" - opens TypeEditorDialog in edit mode (disabled if DEFAULT selected)
     - "Delete" - removes type (disabled if DEFAULT selected)
   - Double-click on custom type to edit
   - Enable/disable checkbox works for all types
   - Save/Reset methods update TypeDescriptionList

### Phase 4: Integration and Polish

9. **Wire up TypeManagerV2 in Myster.java**
   - Replace:
     ```java
     preferencesGui.addPanel(new TypeManagerPreferencesGUI(tdList));
     ```
   - With:
     ```java
     preferencesGui.addPanel(new TypeManagerV2PreferencesGUI(tdList));
     ```

10. **Add help text and tooltips**
    - Explain custom types concept
    - Tooltip on Private radio button: "Private networks not yet implemented. Coming soon!"
    - Help text explaining file extensions and archive search

11. **Handle edge cases**
    - Duplicate type names (validation)
    - Deleting type that's currently in use (warning dialog?)
    - Public key generation failures
    - Type with no extensions (allow as "all files"?)

### Phase 5: Testing and Documentation

12. **Write unit tests**
    - `TestCustomTypeDefinition` - serialization/deserialization
    - `TestCustomTypeManager` - CRUD operations
    - `TestDefaultTypeDescriptionList` - custom type integration
    - `TestTypeManagerV2PreferencesGUI` - UI behavior

13. **Update Javadoc**
    - Document all new classes and methods
    - Explain custom types concept in package-info

14. **Test integration**
    - Create custom type
    - Enable/disable it
    - Search for files with custom type
    - Verify file manager uses custom extensions
    - Restart Myster, verify persistence

## Tests/Verification

### Unit Tests Required

**`TestCustomTypeDefinition.java`:**
- `testSerializeToPreferences()` - verify all fields saved
- `testDeserializeFromPreferences()` - verify all fields loaded
- `testRoundTripSerialization()` - save then load, compare
- `testValidation()` - verify validation logic

**`TestCustomTypeManager.java`:**
- `testLoadCustomTypes()` - load from preferences
- `testSaveCustomType()` - save to preferences
- `testDeleteCustomType()` - remove from preferences
- `testUpdateCustomType()` - modify existing type

**`TestDefaultTypeDescriptionList.java`** (additions):
- `testLoadDefaultAndCustomTypes()` - both loaded
- `testAddCustomType()` - adds and persists
- `testRemoveCustomType()` - removes custom type
- `testCannotRemoveDefaultType()` - throws exception
- `testUpdateCustomType()` - updates custom type

**`TestTypeManagerV2PreferencesGUI.java`:**
- `testShowsDefaultAndCustomTypes()` - both displayed
- `testEditButtonDisabledForDefault()` - can't edit built-in
- `testDeleteButtonDisabledForDefault()` - can't delete built-in
- `testAddCustomType()` - opens dialog, adds type
- `testEnableDisableWorks()` - checkbox toggles enabled

### Integration Tests

**Manual Testing Checklist:**
- Create a custom type with name "Test Network"
- Add extensions: ["test", "dat"]
- Enable "search in archives"
- Mark as "Public"
- Save and verify type appears in list
- Disable the type
- Restart Myster
- Verify type still exists and is disabled
- Re-enable type
- Create files with .test extension
- Verify they appear in file manager for that type
- Edit the custom type, change name to "Test Network 2"
- Delete the custom type
- Verify it's gone
- Try to delete a built-in type (should be disabled/error)
- Try to edit a built-in type (should be disabled/error)

### Edge Case Verification

- Create type with empty name (should fail validation)
- Create type with duplicate name (should fail validation)
- Create type with no extensions (decide if allowed)
- Create type with invalid public key (should fail validation)
- Delete type while search is using it (graceful handling?)
- 100 custom types (performance test)

## Docs/Comments to Update

1. **Package Javadoc** (`com/myster/type/package-info.java`)
   - Explain custom types feature
   - Explain TypeSource concept
   - Link to type overlay networks documentation

2. **README.md**
   - Update with custom types feature description
   - Note this enables custom private networks (foundation)

3. **Class-level Javadoc**
   - All new classes: comprehensive documentation
   - TypeDescription: explain source field
   - DefaultTypeDescriptionList: explain custom type loading

4. **Method-level Javadoc**
   - All new methods documented
   - Existing methods updated if behavior changes

5. **User-facing documentation** (if exists)
   - How to create custom types
   - Use cases for custom types
   - Limitations (private not yet implemented)

## Acceptance Criteria

1. ✅ User can create custom MysterType via UI
2. ✅ Custom type has name, description, extensions, archive flag, public flag
3. ✅ Custom types persist across restarts
4. ✅ User can enable/disable both default and custom types
5. ✅ User can edit custom types (but not default types)
6. ✅ User can delete custom types (but not default types)
7. ✅ Default and custom types shown together in unified UI
8. ✅ File manager uses custom type extensions
9. ✅ Private flag is visible but disabled with tooltip
10. ✅ TypeManagerPreferencesGUI is replaced by TypeManagerV2
11. ✅ All unit tests pass
12. ✅ Integration testing successful
13. ✅ Code is properly documented

## Risks/Edge Cases/Rollout Notes

### Risks

1. **Breaking Existing Type System**
   - Modifying DefaultTypeDescriptionList could break existing code
   - **Mitigation**: Maintain backwards compatibility, default source for existing types

2. **Data Migration**
   - Existing enabled/disabled preferences might not work with new system
   - **Mitigation**: Keep same preference key for enabled state

3. **Public Key Management**
   - Users might not understand public keys
   - **Mitigation**: Auto-generate with "Generate New" button, hide complexity

4. **Performance**
   - Loading many custom types could slow startup
   - **Mitigation**: Lazy loading, efficient serialization

### Edge Cases

1. **Duplicate Type Names**
   - Validation prevents creation
   - Case-insensitive comparison

2. **Type in Use When Deleted**
   - Show warning dialog
   - Or: prevent deletion if type has shared files

3. **Invalid Public Key**
   - Validation on save
   - Provide clear error message

4. **No Extensions**
   - Decide: allow (means "all files") or require at least one
   - Document behavior

5. **Very Long Names/Descriptions**
   - UI truncation in table
   - Tooltip shows full text

### Rollout Notes

1. **Phased Rollout**
   - Phase 1: Data model and persistence (no UI changes)
   - Phase 2: New UI alongside old (both registered in preferences)
   - Phase 3: Remove old UI after testing

2. **User Communication**
   - Announce custom types feature
   - Explain private networks coming later
   - Provide examples/templates for common use cases

3. **Backwards Compatibility**
   - Old preferences still work
   - No data loss on upgrade
   - Can revert to old UI if needed

4. **Performance Monitoring**
   - Monitor startup time with custom types
   - Monitor preference file size
   - Watch for UI lag with many types

## Assumptions

1. **MysterType Already Supports Public Keys**: ✅ Confirmed - MysterType constructor takes PublicKey
2. **Preferences API Available**: ✅ Already used throughout codebase
3. **File Manager Uses TypeDescription**: ✅ Confirmed - file manager filters by extensions from TypeDescription
4. **UI Framework**: Swing (confirmed from existing code)
5. **Serialization**: MessagePack or Preferences API (both available)
6. **Public Key Generation**: Use existing Identity class infrastructure
7. **Single User**: No multi-user support needed
8. **Type Uniqueness**: Enforced by public key (hex string) as identifier

## Open Questions

1. **Should types with no extensions be allowed?**
   - Option A: Allow, means "all files"
   - Option B: Require at least one extension
   - **Recommendation**: Require at least one for clarity

2. **How to handle type in use when deleting?**
   - Option A: Block deletion with error message
   - Option B: Allow but warn user
   - Option C: Automatically disable instead of delete
   - **Recommendation**: Option B - warn but allow

3. **Public key input method?**
   - Option A: Auto-generate only (simplest)
   - Option B: Allow paste/import (more flexible)
   - **Recommendation**: Option A for MVP, Option B later

4. **Extension format?**
   - With or without leading dot? ("mp3" vs ".mp3")
   - **Recommendation**: Without dot, add programmatically

## Implementation Notes

### Why This Design

The design builds on the existing TypeDescriptionList infrastructure rather than creating a parallel system. This ensures:
- Compatibility with all existing code that uses TypeDescriptionList
- Minimal changes to core type handling
- Reuse of enable/disable mechanisms
- Seamless integration of default and custom types

### Custom Type Identification

Custom types are identified by their MysterType (public key hash). This means:
- No name collisions (names are UI-only)
- Types are globally unique across all Myster instances
- Same type definition can be shared between users (future feature)

### Storage Strategy

Using Preferences API with one node per custom type allows:
- Easy CRUD operations
- Atomic updates
- Standard Java serialization
- Easy backup/restore

### UI Philosophy

The unified UI (showing default and custom together) provides:
- Consistent user experience
- Clear distinction via "Source" column
- Disabled actions for built-in types
- Single place to manage all types

This is better than separate tabs/windows for default and custom types.
