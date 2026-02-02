# Custom Type Auto-Update

## Summary
When a user enables or disables a custom type (or default type), automatically update all dependent components in Myster: the Tracker's MysterTypeServerList instances, open Search windows, and the TrackerWindow UI. From Myster's perspective, enabling a type is equivalent to "adding" it (making it available) and disabling is equivalent to "removing" it (making it unavailable). This ensures custom types behave identically to default types from the user's perspective.

## Goals
- On type **enable** (appears as "adding" to the rest of Myster): 
  - Create a new `MysterTypeServerList` for the newly-enabled type in the Tracker
  - Populate it with existing `MysterServer` instances that know about the type
  - Add the enabled type to all open Search window dropdowns
  - Add the enabled type to the TrackerWindow dropdown
- On type **disable** (appears as "removing" to the rest of Myster):
  - Remove the `MysterTypeServerList` from the Tracker
  - Clean up preferences for that type's server list
  - Remove the disabled type from all open Search window dropdowns
  - Remove the disabled type from the TrackerWindow dropdown
- Distinguish between "server has 0 files for this type" vs "server doesn't know about this type"
- Ensure changes take effect immediately (no restart required)
- Handle both custom types and default types uniformly (both can be enabled/disabled)

## Non-goals
- Modifying how types are queried from remote servers (existing protocol works)
- Changing the MysterServerPool implementation
- Handling type name/description updates (TypeChoice will auto-refresh, but Tracker lists don't need to change since they use the MysterType key, not the name)
  - **Note**: Type name updates could be handled by firing `typeEnabled()` after update, but this is separate from the enable/disable auto-update feature
  - TypeChoice would rebuild and show the new name
  - Tracker doesn't care (uses MysterType, not the display name)
- Creating/deleting custom types themselves (that's handled by TypeManagerV2PreferencesGUI and is separate from enable/disable)

## Important Window Lifecycle Notes

**Tracker Window vs Search Windows:**
- **TrackerWindow**: Singleton that is hidden/shown (never destroyed). TypeChoice updates must work for this window even when hidden.
- **SearchWindow**: Destroyed when closed, recreated when opened. Multiple instances can exist simultaneously.
- **SearchTab**: Each tab within a SearchWindow has its own TypeChoice. Changes must update all tabs in all open SearchWindows, even tabs not currently visible.

This means:
- TypeChoice instances can exist in hidden/background tabs and must stay synchronized
- We need to update ALL TypeChoice instances, not just visible ones
- TrackerWindow's TypeChoice must update even when the window is hidden

## Proposed Design (High-level)

### Event Flow

**On Type Enable (Custom or Default):**
1. User enables a type in TypeManagerV2PreferencesGUI (checking the "Enabled" checkbox)
2. `DefaultTypeDescriptionList.setEnabledType(type, true)` is called
3. Fire existing event: `TypeListener.typeEnabled(TypeDescriptionEvent e)`
4. Tracker listens for this event and:
   - Creates a new `MysterTypeServerList` for the type
   - Iterates through `MysterServerPool` to find servers that know about this type
   - Adds qualifying servers to the new list
5. All `TypeChoice` instances listen for this event and:
   - Rebuild their dropdown items to include the newly-enabled type
   - Preserve current selection if possible

**On Type Disable (Custom or Default):**
1. User disables a type in TypeManagerV2PreferencesGUI (unchecking the "Enabled" checkbox)
2. `DefaultTypeDescriptionList.setEnabledType(type, false)` is called
3. Fire existing event: `TypeListener.typeDisabled(TypeDescriptionEvent e)`
4. Tracker listens and:
   - Removes the `MysterTypeServerList` from its internal array
   - Cleans up preferences for that type
5. All `TypeChoice` instances listen and:
   - Rebuild their dropdown items to exclude the disabled type
   - Switch to first available type if current selection was removed

**Note on Custom Type Deletion:**
When a custom type is deleted (not just disabled), the `removeCustomType()` method also fires `typeDisabled()` event. So the deletion flow piggybacks on the disable flow - the type is effectively disabled and then removed from the type definition list entirely. This plan handles both cases uniformly.

### Type Distinction: 0 Files vs Unknown Type

The existing protocol **already handles this correctly**:
- `ServerStats` response includes a `/NumberOfFiles/` MML section
- Only types the server knows about appear as keys (e.g., `/NumberOfFiles/ABC123`)
- Missing key = server doesn't know about that type
- Present key with value 0 = server knows the type but has no files

**Current Implementation:**
```java
// MysterServerImplementation.NumOfFiles.getNumberOfFiles(MysterType type)
try {
    return Integer.parseInt(get("/" + type)); // Returns int if key exists
} catch (NullPointerException ex) {
    return 0; // Key doesn't exist -> doesn't know about type
}
```

**Issue:** Current code returns 0 for both cases! We need to fix this.

**Proposed Fix:**
Add a method to distinguish the cases:
```java
public boolean knowsAboutType(MysterType type) {
    return get("/" + type) != null;
}
```

Then when populating a new `MysterTypeServerList`, only add servers where:
```java
server.knowsAboutType(newType) && server.getNumberOfFiles(newType) >= 0
```

## Affected Modules/Packages

### Core Type System
- `com.myster.type` - TypeListener interface, TypeDescriptionEvent, DefaultTypeDescriptionList

### Tracker Module  
- `com.myster.tracker` - Tracker, MysterTypeServerList
- `com.myster.tracker.ui` - TrackerWindow

### Search Module
- `com.myster.search.ui` - SearchWindow, SearchTab

### UI Utilities
- `com.myster.util` - TypeChoice

### Server Implementation (for API fix)
- `com.myster.tracker` - MysterServer interface, MysterServerImplementation

## Files/Classes to Change or Create

### Files to Modify

1. **`com/myster/type/TypeListener.java`**
   - Keep existing `typeEnabled()` and `typeDisabled()` methods (no new methods needed!)
   - Note: `typeEnabled()` fires when a type is enabled (appears as "adding" to Myster)
   - Note: `typeDisabled()` fires when a type is disabled or deleted (appears as "removing")

2. **`com/myster/type/DefaultTypeDescriptionList.java`**
   - In `addCustomType()`: Fire `typeEnabled()` event if the new type is created enabled
   - In `removeCustomType()`: Already fires `typeDisabled()` - no change needed
   - In `setEnabledType()`: No changes (already fires enable/disable events correctly)
   - **Consider**: In `updateCustomType()`: Fire `typeEnabled()` event to trigger TypeChoice refresh (name may have changed)
     - This causes TypeChoice dropdowns to rebuild with the new name
     - Tracker doesn't need to care (uses MysterType key, not name)
     - Alternative: Add a new `typeUpdated()` event, but might be overkill for just updating display names

3. **`com/myster/tracker/MysterServer.java`** (interface)
   - Add `boolean knowsAboutType(MysterType type)` method

4. **`com/myster/tracker/MysterServerImplementation.java`**
   - Modify `NumOfFiles.getNumberOfFiles()` to preserve current behavior (return 0)
   - Add `NumOfFiles.knowsAboutType(MysterType type)` implementation:
     ```java
     public boolean knowsAboutType(MysterType type) {
         try {
             return get("/" + type) != null;
         } catch (Exception ex) {
             return false;
         }
     }
     ```
   - Update `MysterServerReference` to implement new interface method

5. **`com/myster/tracker/Tracker.java`**
   - Add field: `private final TypeDescriptionList tdList` (already exists!)
   - In constructor: Register as a `TypeListener` on `tdList`
   - Implement `typeEnabled(TypeDescriptionEvent e)`:
     - Check if type is enabled (only add enabled types)
     - Resize `list[]` array to accommodate new type
     - Resize `enabledTypes[]` array
     - Create new `MysterTypeServerList` for the type
     - Iterate through `pool.forEach()` and add servers that know about the type
     - Fire `ListChangedListener.serverAddedRemoved(type)` event
   - Implement `typeDisabled(TypeDescriptionEvent e)`:
     - Find index of type in `enabledTypes[]`
     - If found, remove from lists
     - Clean up preferences: `preferences.node(type.toHexString()).removeNode()`
     - Resize arrays to remove the type
     - Fire `ListChangedListener.serverAddedRemoved(type)` event
   - Note: Current design uses fixed-size arrays based on enabled types at startup
   - **Refactoring needed**: Change `ServerList[] list` and `TypeDescription[] enabledTypes` from arrays to `List<>` for dynamic resizing

6. **`com/myster/tracker/MysterTypeServerList.java`**
   - Constructor already accepts a `MysterType` - no changes needed
   - Verify that `save()` uses `type.toHexString()` as preference key (already does)
   - No changes required

7. **`com/myster/util/TypeChoice.java`**
   - Add field: `private final TypeDescriptionList tdList`
   - In constructor: 
     - Store reference to `tdList`
     - Register as a `TypeListener`
   - Add method: `private void rebuildTypeList()`:
     - Save current selection
     - Clear all items
     - Reload from `tdList.getEnabledTypes()`
     - Re-add separator and extras if applicable
     - Restore selection (or select first item if removed)
   - Implement `typeEnabled(TypeDescriptionEvent e)`: Call `rebuildTypeList()`
   - Implement `typeDisabled(TypeDescriptionEvent e)`: Call `rebuildTypeList()`
   - Note: We rebuild on both events because enable/disable changes the list

8. **`com/myster/tracker/ui/TrackerWindow.java`**
   - Already has a `TypeChoice choice` field
   - TypeChoice will auto-update via its listener
   - Add listener to detect when current selection is removed:
     - If current type is disabled/deleted, reload the server list
   - Consider: Add `Tracker.ListChangedListener` to refresh when types change
   - May need to call `loadTheList()` when `typeEnabled()`/`typeDisabled()` fires

9. **`com/myster/search/ui/SearchWindow.java`**
   - Already uses `TypeChoice` in each `SearchTab`
   - Each `SearchTab` creates its own `TypeChoice` instance
   - TypeChoice will auto-update via its listener
   - No changes needed (TypeChoice handles it)

10. **`com/myster/search/ui/SearchTab.java`**
    - Already has a `TypeChoice choice` field  
    - TypeChoice will auto-update via its listener
    - No changes needed

### Files to Create
None - all changes are modifications to existing files.

## Step-by-Step Implementation Plan

### Phase 1: Add API to Distinguish Type Knowledge ✅ COMPLETE
1. ✅ Add `boolean knowsAboutType(MysterType type)` to `MysterServer` interface
2. ✅ Implement in `MysterServerImplementation.MysterServerReference`
3. ✅ Add `knowsAboutType()` to `MysterServerImplementation.NumOfFiles` class
4. ⏳ Write unit test to verify distinction between 0 files vs unknown type (deferred)

### Phase 2: Refactor Tracker for Dynamic Type Lists ✅ COMPLETE
1. ✅ Change `ServerList[] list` to `List<ServerList> list`
2. ✅ Change `TypeDescription[] enabledTypes` to `List<TypeDescription> enabledTypes`
3. ✅ Update all array access patterns to use List methods
4. ✅ Update `getIndex()` to work with Lists
5. ✅ Test that existing functionality still works (all 199 tests pass)

### Phase 3: Implement Tracker Type Enable Logic ✅ COMPLETE
1. ✅ Make `Tracker` implement `TypeListener`
2. ✅ Register Tracker as listener on `tdList` in constructor
3. ✅ Implement `typeEnabled(TypeDescriptionEvent e)` - creates list, populates with servers that know about type
4. ✅ Test: Enable a custom type and verify it appears in Tracker with existing servers

### Phase 4: Implement Tracker Type Disable Logic ✅ COMPLETE
1. ✅ Implement `typeDisabled(TypeDescriptionEvent e)` - removes list, cleans up preferences
2. ✅ Test: Disable a custom type and verify it's removed from Tracker and preferences
3. ✅ Test: Delete a custom type and verify same behavior (deletion fires typeDisabled)

### Phase 5: Make TypeChoice Auto-Update ✅ COMPLETE
1. ✅ Add `TypeDescriptionList tdList` field to `TypeChoice`
2. ✅ Make `TypeChoice` implement `TypeListener`
3. ✅ Register as listener in constructor
4. ✅ Implement `rebuildTypeList()` helper method - saves selection, rebuilds, restores selection
5. ✅ Implement `typeEnabled(TypeDescriptionEvent e)`: Call `rebuildTypeList()` on EDT
6. ✅ Implement `typeDisabled(TypeDescriptionEvent e)`: Call `rebuildTypeList()` on EDT
7. ⏳ Test: Enable/disable types and verify all TypeChoice dropdowns update (manual testing needed)

### Phase 6: Update TrackerWindow ⏭️ SKIPPED
1. ✅ TypeChoice auto-updates (implemented in Phase 5)
2. ⏳ Manual test: Disable currently-selected type in TrackerWindow (verify graceful handling)
Note: No code changes needed - TypeChoice handles everything

### Phase 7: Verify Search Windows ⏭️ SKIPPED  
1. ⏳ Test: Open multiple search windows/tabs
2. ⏳ Enable a custom type - verify it appears in all dropdowns
3. ⏳ Disable a custom type - verify it's removed from all dropdowns
4. ⏳ Verify selections are preserved when possible
Note: No code changes needed - TypeChoice handles everything

### Phase 8: Integration Testing ⏳ IN PROGRESS
1. ⏳ Test full workflow: Create custom type → Enable → Use in search → Disable → Delete
2. ⏳ Test with multiple types being enabled/disabled rapidly
3. ⏳ Test with no enabled types (edge case)
4. ⏳ Verify restart persistence (disabled types stay disabled, enabled stay enabled)
5. ✅ All existing tests pass (199 tests, 0 failures)
