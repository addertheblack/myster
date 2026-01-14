# Tabbed Search Window

## Summary

Evolve the existing `SearchWindow` to support multiple tabs, where each tab contains its own independent search interface and results. Also support multiple search windows (each with their own tabs).

## Goals

- Modify `SearchWindow` to contain a `JTabbedPane` with multiple search tabs
- Each tab has its own search field, type selector, search button, results list, and status message
- Users can add/remove/switch tabs freely
- Support multiple `SearchWindow` instances (each with tabs)
- Keyboard shortcuts:
  - `Ctrl+N` — Add new tab to current search window (or create window if none exists)
  - `Ctrl+Shift+N` — Create new search window
- Preserve existing search behavior (start/stop, results display, context menus, double-click to download)
- Persist open windows and their tabs, restore on restart
- If user closes all search windows, "New Search" recreates one

## Non-goals

- Changing the search engine (`SearchEngine`, `MysterSearch`) internals
- Changing how search results are rendered (`ClientHandleObject`, `MCList`)
- Adding "search across all tabs" or merged results functionality
- Drag-and-drop tab reordering (can be added later)

## Proposed design (high-level)

### Architecture

```
SearchWindow (MysterFrame) — can have multiple instances
├── JTabbedPane
│   ├── SearchTab (JPanel) — tab 0
│   │   ├── JTextField (search field)
│   │   ├── TypeChoice (type selector)
│   │   ├── JButton (Search/Stop)
│   │   ├── JMCList<SearchResult> (results list)
│   │   └── MessageField (status)
│   ├── SearchTab — tab 1
│   └── ... more tabs
└── "+" button or menu item to add new tab
```

### Key design decisions

1. **Extract `SearchTab` from `SearchWindow`**: Move all the per-search UI components and logic into a new `SearchTab extends JPanel` that implements `SearchResultListener` and `Sayable`.

2. **Evolve `SearchWindow`**: Keep `SearchWindow` as the `MysterFrame` subclass but change its content from direct components to a `JTabbedPane` holding `SearchTab` instances.

3. **Multiple windows supported**: Unlike `ServerStatsWindow` (singleton), `SearchWindow` allows multiple instances. Each window has its own tabs. Use `WindowPrefDataKeeper.MULTIPLE_WINDOWS`.

4. **Window recreation**: If all search windows are closed and user hits "New Search", create a fresh window. Track active windows via a static list or let the menu action always call `new SearchWindow(...)`.

5. **Tab titles**: Use search string as tab title (e.g., `"mp3"` or `"New Search"` if empty). Update title when search starts.

6. **Tab close buttons**: Each tab gets a close button. Closing a tab stops its search. Closing the last tab closes the window.

7. **Preferences persistence** (following `ClientWindow` pattern):
   - Register with `WindowPrefDataKeeper` using `addFrame()` with a lambda to save per-window state
   - Save: window bounds, tab count, per-tab data (search string, selected type)
   - Use `getLastLocs()` with a builder lambda to restore on startup
   - Call the returned `Runnable` from `addFrame()` to trigger manual saves when tabs change

### Keyboard shortcuts

| Shortcut | Action |
|----------|--------|
| `Ctrl+N` | Add new tab to front search window (or create window if none) |
| `Ctrl+Shift+N` | Create new search window with one empty tab |

**Note**: Remove `Ctrl+Shift+N` from `NewClientWindowAction` (keep the menu item, just no shortcut).

### Data flow

- `NewSearchWindowAction` (`Ctrl+N`) → If search window exists and is front, add tab; else create new window
- `NewSearchWindowAction` with shift (`Ctrl+Shift+N`) → Always create new window
- Each `SearchTab` owns its own `SearchEngine` instance
- `SearchTab` implements `SearchResultListener` to receive results
- Context menus, double-click download, etc. stay within `SearchTab`

## Affected modules/packages

- `com.myster.search.ui` — main changes
- `com.myster.ui.menubar.event` — update `NewSearchWindowAction`, modify `NewClientWindowAction`
- `com.myster.ui.menubar` — update shortcuts in `MysterMenuBar`

## Files/classes to change or create

### New files

| File | Purpose |
|------|---------|
| `com.myster.search.ui.SearchTab` | `JPanel` extracted from `SearchWindow`, implements `SearchResultListener`, `Sayable` |

### Files to modify

| File | Changes |
|------|---------|
| `SearchWindow.java` | Evolve to hold `JTabbedPane` of `SearchTab`s; add `addNewTab()`, tab close handling, prefs persistence |
| `SearchButtonHandler.java` | Update to work with `SearchTab` instead of `SearchWindow` (or inline it) |
| `NewSearchWindowAction.java` | Handle both new tab (`Ctrl+N`) and new window (`Ctrl+Shift+N`) |
| `NewClientWindowAction.java` | Remove keyboard shortcut (keep menu item) |
| `MysterMenuBar.java` | Update shortcuts: `Ctrl+N` for new search tab, `Ctrl+Shift+N` for new search window, remove shift from client window |

### Files unchanged

- `SearchEngine.java` — no changes needed
- `MysterSearch.java` — no changes needed
- `SearchResultListener.java` — no changes needed
- `ClientHandleObject.java`, `ClientInfoFactoryUtilities.java` — no changes needed

## Step-by-step implementation plan

### Phase 1: Extract `SearchTab` from `SearchWindow`

1. Create `SearchTab extends JPanel implements SearchResultListener, Sayable`
2. Move all UI components from `SearchWindow` constructor into `SearchTab`:
   - `textEntry`, `choice`, `searchButton`, `fileList`, `msg`
   - Layout code (`GridBagLayout` setup)
   - Event listeners (search button, text field enter, double-click, context menus)
3. Move search lifecycle methods:
   - `startSearch()`, `stopSearch()`, `searchStart()`, `searchOver()`
   - `addSearchResults()`, `searchStats()`, `recolumnize()`
4. Add constructor: `SearchTab(MysterFrameContext c, Consumer<SearchTab> onTitleChange)`
   - `onTitleChange` callback notifies parent when search string changes (for tab title)
5. Add `getTabTitle()` method returning search string or "New Search"
6. Add `dispose()` method to stop search and clean up
7. Keep static fields (`protocol`, `hashManager`, `manager`) in `SearchWindow` and pass to `SearchTab` constructor

### Phase 2: Evolve `SearchWindow` to tabbed

1. Replace direct component layout with `JTabbedPane` as main content
2. Add instance field: `JTabbedPane tabbedPane`
3. Modify constructor to:
   - Create `JTabbedPane` with `SCROLL_TAB_LAYOUT` policy
   - Add one initial `SearchTab`
   - Set up tab close buttons
4. Add `addNewTab()` method:
   - Creates new `SearchTab`
   - Adds to `JTabbedPane` with close button
   - Selects the new tab
   - Triggers prefs save
5. Add `removeTab(int index)` method:
   - Stops search on that tab
   - Removes from pane
   - If last tab, close window
   - Triggers prefs save
6. Wire up `SearchTab`'s title change callback to update tab title via `tabbedPane.setTitleAt()`
7. Add tab close button component using `tabbedPane.setTabComponentAt()` with label + close button

### Phase 3: Preferences persistence (following ClientWindow pattern)

1. Create `SearchWindowData` record:
   ```java
   public record SearchWindowData(List<TabData> tabs, int selectedTabIndex) {}
   public record TabData(String searchString, Optional<MysterType> type) {}
   ```

2. In `SearchWindow` constructor, register with `WindowPrefDataKeeper`:
   ```java
   savePrefs = context.keeper().addFrame(this, (p) -> {
       // Save number of tabs
       p.putInt(TAB_COUNT_KEY, tabbedPane.getTabCount());
       p.putInt(SELECTED_TAB_KEY, tabbedPane.getSelectedIndex());
       
       // Save per-tab data
       for (int i = 0; i < tabbedPane.getTabCount(); i++) {
           SearchTab tab = (SearchTab) tabbedPane.getComponentAt(i);
           p.put(TAB_SEARCH_KEY + i, tab.getSearchString());
           if (tab.getMysterType() != null) {
               p.put(TAB_TYPE_KEY + i, tab.getMysterType().toHexString());
           }
       }
   }, WINDOW_KEEPER_KEY, WindowPrefDataKeeper.MULTIPLE_WINDOWS);
   ```

3. Call `savePrefs.run()` whenever tabs are added/removed/changed

4. Add static `initWindowLocations(MysterFrameContext c)`:
   ```java
   public static int initWindowLocations(MysterFrameContext c) {
       List<PrefData<SearchWindowData>> lastLocs = c.keeper().getLastLocs(WINDOW_KEEPER_KEY, (p) -> {
           int tabCount = p.getInt(TAB_COUNT_KEY, 1);
           int selectedTab = p.getInt(SELECTED_TAB_KEY, 0);
           List<TabData> tabs = new ArrayList<>();
           for (int i = 0; i < tabCount; i++) {
               String search = p.get(TAB_SEARCH_KEY + i, "");
               Optional<MysterType> type = getTypeFromPrefs(p, TAB_TYPE_KEY + i);
               tabs.add(new TabData(search, type));
           }
           return new SearchWindowData(tabs, selectedTab);
       });
       
       for (PrefData<SearchWindowData> prefData : lastLocs) {
           SearchWindow window = new SearchWindow(c, prefData.data());
           window.setBounds(prefData.location().bounds());
           window.setVisible(true);
       }
       
       return lastLocs.size();
   }
   ```

5. Add constructor overload that accepts `SearchWindowData` for restoration

### Phase 4: Update menu actions and shortcuts

1. **Modify `NewSearchWindowAction`**:
   - Check if event has shift modifier
   - If shift (`Ctrl+Shift+N`): always create new `SearchWindow`
   - If no shift (`Ctrl+N`): 
     - If a `SearchWindow` is the front window, add a new tab to it
     - Else create new `SearchWindow`

2. **Modify `NewClientWindowAction`**:
   - Remove the keyboard shortcut parameter (keep menu item text)

3. **Update `MysterMenuBar.initMenuBar()`**:
   ```java
   // Change from:
   file.add(new MysterMenuItemFactory("New Search",
                                      new NewSearchWindowAction(context),
                                      java.awt.event.KeyEvent.VK_N));
   file.add(new MysterMenuItemFactory("New Peer-to-Peer Connection...",
                                      new NewClientWindowAction(context),
                                      java.awt.event.KeyEvent.VK_N,
                                      true));
   
   // To:
   file.add(new MysterMenuItemFactory("New Search Tab",
                                      new NewSearchWindowAction(context, false),
                                      java.awt.event.KeyEvent.VK_N));
   file.add(new MysterMenuItemFactory("New Search Window",
                                      new NewSearchWindowAction(context, true),
                                      java.awt.event.KeyEvent.VK_N,
                                      true));
   file.add(new MysterMenuItemFactory("New Peer-to-Peer Connection...",
                                      new NewClientWindowAction(context)));
   // No shortcut for client window
   ```

### Phase 5: Window tracking and recreation

1. Add static tracking in `SearchWindow`:
   ```java
   private static final List<SearchWindow> activeWindows = new ArrayList<>();
   ```

2. In constructor, add `this` to `activeWindows`

3. Override `dispose()` to remove from `activeWindows`

4. In `NewSearchWindowAction`, if `activeWindows.isEmpty()`, create new window

5. Alternative simpler approach: just always allow creating new windows; `NewSearchWindowAction` creates/finds windows as needed

### Phase 6: Cleanup

1. Update any other references to `SearchWindow` (search for usages)
2. Ensure startup code calls `SearchWindow.initWindowLocations()`
3. Remove old single-search window logic

## Tests/verification

### Manual testing

1. **Basic tabbing**:
   - `Ctrl+N` → window appears with one tab
   - `Ctrl+N` again → adds second tab to same window
   - `Ctrl+Shift+N` → creates new window
   - Close a tab → tab removed, search stopped
   - Close last tab → window closes

2. **Multiple windows**:
   - Create two search windows
   - Each has independent tabs
   - Close one window, other remains

3. **Search functionality per tab**:
   - Start search in tab 1, switch to tab 2, start different search
   - Both searches run independently
   - Results appear in correct tabs
   - Stop button works per-tab

4. **Context menus**: Right-click in results list, verify all menu items work

5. **Double-click download**: Works as before

6. **Persistence**:
   - Open 2 windows with 3 tabs each, do searches, quit
   - Restart → windows restored with correct tabs

7. **Window recreation**:
   - Close all search windows
   - `Ctrl+N` → new window created

8. **Keyboard shortcuts**:
   - `Ctrl+N` adds tab
   - `Ctrl+Shift+N` creates window
   - Client window menu item works but has no shortcut

### Unit tests (optional)

- `SearchTab` construction and disposal
- Tab title updates on search start

## Docs/comments to update

- Add Javadoc to new `SearchTab` class
- Update Javadoc on `SearchWindow` to describe tabbed behavior
- Update any references in existing docs

## Acceptance criteria

- [ ] `SearchWindow` contains a `JTabbedPane` with tabs
- [ ] Each tab has independent search field, type, button, results, status
- [ ] `Ctrl+N` adds a tab (or creates window if none)
- [ ] `Ctrl+Shift+N` creates a new search window
- [ ] Tabs can be closed; closing stops the search
- [ ] Closing last tab closes the window
- [ ] Tab title shows search string (or "New Search")
- [ ] Window position and tabs persist across restarts
- [ ] All existing search functionality works (results, stats, download, context menus)
- [ ] `NewClientWindowAction` has no keyboard shortcut
- [ ] No regressions in search behavior

## Risks/edge cases

1. **Memory**: Many tabs with large result sets could use significant memory. Consider lazy cleanup of closed tab data.

2. **Static state in `SearchWindow`**: Current `SearchWindow` has static fields (`protocol`, `hashManager`, `manager`). Keep using `SearchWindow.init()` pattern; pass to `SearchTab` constructor.

3. **`SearchButtonHandler` coupling**: Currently takes `SearchWindow` in constructor. Update to take `SearchTab` or inline the logic.

4. **Tab overflow**: If user opens many tabs, ensure `JTabbedPane` scroll policy is set (use `SCROLL_TAB_LAYOUT`).

5. **Focus handling**: When adding a new tab, focus should go to the search text field in that tab.

6. **Prefs key collisions**: If persisting per-tab data, use indexed keys (`TAB_0_SEARCH`, `TAB_1_SEARCH`, etc.) and clean up old keys when tab count decreases.

## Open questions

None — design is clear based on user feedback.
