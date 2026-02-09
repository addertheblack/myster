# Public/Private Data Path Separation

## Summary
Separate Myster's data storage into **public** (user-managed) and **private** (application-managed) directories. Public paths contain user content like downloads and images. Private paths contain application data like identity keys and temporary incoming files. This separation improves user experience by making user content easily accessible while keeping system files hidden.

## Goals
- Split `MysterGlobals.getAppDataPath()` into `getPublicDataPath()` and `getPrivateDataPath()`
- Move user-facing content (downloads, images) to public path
- Move application data (identity, incoming) to private path
- Use platform-appropriate conventions for each OS
- Ensure backward compatibility through migration or deprecation strategy

## Non-goals
- Automatic migration of existing data (can be done later as a separate feature)
- Changing the structure of data within each path
- Making paths user-configurable (still using standard OS conventions)
- Supporting custom path locations via preferences

## Proposed Design (High-level)

### Path Definitions by Platform

| Platform | Private Path | Public Path |
|----------|--------------|-------------|
| **Linux** | `~/.myster` | `~/myster` |
| **macOS** | `~/Library/Application Support/Myster` | `~/myster` |
| **Windows** | `%LOCALAPPDATA%\Myster` (existing) | `%USERPROFILE%\myster` |

### Directory Usage Classification

| Directory | Type | Current Location | New Location |
|-----------|------|------------------|--------------|
| `Downloads` (per file type) | **Public** | `getAppDataPath()` | `getPublicDataPath()` |
| `Images` | **Public** | `getAppDataPath()` | `getPublicDataPath()` |
| `Incoming` | **Private** | `getAppDataPath()` | `getPrivateDataPath()` |
| `identity` | **Private** | `getAppDataPath()` | `getPrivateDataPath()` |

**Rationale:**
- **Downloads:** User's downloaded files, should be easily accessible in their home directory
- **Images:** User-added banner images, public content for sharing
- **Incoming:** Temporary staging for incomplete downloads, should be hidden
- **identity:** Cryptographic keys and keystores, should be hidden and protected

### API Changes

Replace the single method:
```java
public static File getAppDataPath()
```

With two new methods:
```java
public static File getPublicDataPath()  // For downloads, images
public static File getPrivateDataPath() // For identity, incoming
```

Mark `getAppDataPath()` as `@Deprecated` initially to maintain backward compatibility.

## Affected Modules/Packages
- `com.myster.application` - `MysterGlobals` (core path methods)
- `com.myster.filemanager` - `FileTypeList` (downloads path)
- `com.myster.net.stream.client.msdownload` - `MultiSourceUtilities` (incoming path)
- `com.myster.net.server` - `BannersManager` (images path)
- `com.myster.identity` - `Identity` (identity path)

## Files/Classes to Change or Create

### 1. **MysterGlobals.java** - Core path infrastructure
- Add `getPublicDataPath()` method
- Add `getPrivateDataPath()` method
- Deprecate `getAppDataPath()` (keep for now, map to private path for safety)

### 2. **FileTypeList.java** - Downloads location
- Change `getDefaultDirectory()` to use `getPublicDataPath()`
- Downloads go to public user directory

### 3. **MultiSourceUtilities.java** - Incoming downloads
- Change `getIncomingDirectory()` to use `getPrivateDataPath()`
- Incomplete downloads stay hidden in private directory

### 4. **BannersManager.java** - Images location
- Change `getImagesDirectory()` to use `getPublicDataPath()`
- User banner images go to public directory

### 5. **Identity.java** - Keystore location
- Change `newIdentity()` to use `getPrivateDataPath()`
- Cryptographic identity stays in private directory

## Step-by-Step Implementation Plan

### Phase 1: Add New Path Methods
1. **Add `getPrivateDataPath()` to MysterGlobals**
   - Linux/macOS: `~/.myster`
   - Windows: `%LOCALAPPDATA%\Myster` (unchanged)
   - Create directory if it doesn't exist
   - Return `File` object

2. **Add `getPublicDataPath()` to MysterGlobals**
   - Linux/macOS: `~/myster`
   - Windows: `%USERPROFILE%\myster`
   - Create directory if it doesn't exist
   - Return `File` object

3. **Deprecate `getAppDataPath()`**
   - Mark with `@Deprecated` annotation
   - Add Javadoc explaining migration to new methods
   - Keep implementation pointing to private path (safest default)

### Phase 2: Update Call Sites
4. **Update FileTypeList.java**
   - In `getDefaultDirectory()`, change:
     - FROM: `MysterGlobals.getAppDataPath().getAbsolutePath()`
     - TO: `MysterGlobals.getPublicDataPath().getAbsolutePath()`

5. **Update BannersManager.java**
   - In `getImagesDirectory()`, change:
     - FROM: `new File(MysterGlobals.getAppDataPath(), IMAGE_DIRECTORY)`
     - TO: `new File(MysterGlobals.getPublicDataPath(), IMAGE_DIRECTORY)`

6. **Update MultiSourceUtilities.java**
   - In `getIncomingDirectory()`, change:
     - FROM: `new File(com.myster.application.MysterGlobals.getAppDataPath(), "Incoming")`
     - TO: `new File(com.myster.application.MysterGlobals.getPrivateDataPath(), "Incoming")`

7. **Update Identity.java**
   - In `newIdentity()`, change:
     - FROM: `new File(MysterGlobals.getAppDataPath(), "identity")`
     - TO: `new File(MysterGlobals.getPrivateDataPath(), "identity")`

### Phase 3: Testing & Verification
8. **Manual testing on each platform**
   - Verify paths are created correctly
   - Verify directories are created with proper permissions
   - Test fresh installs
   - Test with existing data (no migration yet, just verify correct paths)

9. **Code review and documentation**
   - Update Javadocs for all modified methods
   - Document the new path structure

## Tests/Verification

### Unit Tests
- **Test `getPublicDataPath()` on each platform**
  - Mock `System.getProperty("os.name")` for Linux, Mac, Windows
  - Mock `System.getProperty("user.home")` and `System.getenv("USERPROFILE")`
  - Verify correct path construction
  - Verify directory creation

- **Test `getPrivateDataPath()` on each platform**
  - Similar to above
  - Ensure Windows path matches old behavior

### Integration Tests
- **Test FileTypeList downloads path**
  - Verify downloads directory created in public path
  - Verify file operations work correctly

- **Test BannersManager images path**
  - Verify images directory created in public path
  - Verify image loading/saving works

- **Test MultiSourceUtilities incoming path**
  - Verify incoming directory created in private path
  - Verify download staging works

- **Test Identity keystore path**
  - Verify identity directory created in private path
  - Verify key generation and storage works

### Manual Verification Checklist
- [ ] Linux: Public path is `~/myster`, Private is `~/.myster`
- [ ] macOS: Public path is `~/myster`, Private is `~/Library/Application Support/Myster`
- [ ] Windows: Public path is `%USERPROFILE%\myster`, Private is `%LOCALAPPDATA%\Myster`
- [ ] Downloads appear in public path
- [ ] Images appear in public path
- [ ] Incoming directory in private path
- [ ] Identity keystore in private path
- [ ] Fresh install creates both directories
- [ ] No exceptions on startup

## Docs/Comments to Update

### Code Documentation
1. **MysterGlobals.java**
   - Add comprehensive Javadoc for `getPublicDataPath()`
   - Add comprehensive Javadoc for `getPrivateDataPath()`
   - Update deprecation notice for `getAppDataPath()`

2. **FileTypeList.java**
   - Update comment for `getDefaultDirectory()` explaining it uses public path

3. **MultiSourceUtilities.java**
   - Update comment for `getIncomingDirectory()` explaining it uses private path

4. **BannersManager.java**
   - Update comment for `getImagesDirectory()` explaining it uses public path

5. **Identity.java**
   - Update comment for `newIdentity()` explaining it uses private path

### External Documentation
6. **Update `docs/codebase-structure.md`**
   - Add section explaining public vs private data paths
   - Document path conventions per platform
   - Explain which data goes where

7. **Create or update user documentation**
   - Explain where to find downloaded files
   - Explain where to put custom banner images
   - Note that identity and temporary files are stored separately

## Acceptance Criteria

### Functional Requirements
- ✅ `getPublicDataPath()` returns correct path for each OS
- ✅ `getPrivateDataPath()` returns correct path for each OS
- ✅ Both directories are created automatically if they don't exist
- ✅ Downloads go to public path
- ✅ Images go to public path
- ✅ Incoming files go to private path
- ✅ Identity keystore goes to private path
- ✅ No regression in existing functionality

### Code Quality
- ✅ All modified methods have clear Javadoc
- ✅ Platform detection uses existing constants (`ON_LINUX`, `ON_MAC`, `ON_WINDOWS`)
- ✅ No hardcoded paths outside of path getter methods
- ✅ Consistent use of `File.separator` or path construction methods

### Testing
- ✅ Unit tests pass for path construction on all platforms
- ✅ Integration tests pass for all affected components
- ✅ Manual testing completed on at least one platform per OS family

## Risks/Edge Cases/Rollout Notes

### Risks
1. **Existing user data not migrated**
   - Users upgrading will have data in old location
   - New data will go to new location
   - **Mitigation:** This is Phase 1, data migration is a separate feature
   - **Short term:** Document where data is located for upgraded users

2. **Permission issues on directory creation**
   - Public/private directories might fail to create
   - **Mitigation:** Same risk as current `getAppDataPath()`, no new exposure
   - Log errors clearly when directory creation fails

3. **Windows path differences**
   - Private path stays same, public path is new
   - **Mitigation:** Test thoroughly on Windows
   - Verify `%USERPROFILE%` is always available

### Edge Cases
1. **User manually deletes directories**
   - Directories recreated on next access
   - Same behavior as current implementation

2. **Concurrent access to path methods**
   - Multiple calls during startup
   - **Handled:** Directory creation is idempotent

3. **Read-only filesystems or quota issues**
   - Directory creation fails
   - **Handled:** Exception propagates to caller (same as current behavior)

4. **Symbolic links or junction points**
   - User might have created custom links
   - **Handled:** File API handles this transparently

### Rollout Notes
1. **Backward Compatibility**
   - Keep `getAppDataPath()` as deprecated but functional
   - Map to private path (conservative choice for system data)
   - Future version can remove after migration feature is complete

2. **Communication**
   - Update release notes explaining new path structure
   - Document where users can find their files
   - Explain that old data stays in place (no automatic migration)

3. **Future Work**
   - Phase 2: Implement data migration utility
   - Phase 2: Add UI preference to show data locations
   - Phase 2: Add "Reveal in Finder/Explorer" buttons for data directories

### Platform-Specific Notes

#### Linux
- Hidden directory convention: `.myster` for private data ✅
- User directory: `myster` (no dot) for public data ✅
- Both should be easily accessible via `~`

#### macOS
- Same convention as Linux (Unix-based)
- Consider future support for `~/Library/Application Support/Myster` for private
- Current plan uses `~/.myster` for simplicity and consistency

#### Windows
- Private: Keep existing `%LOCALAPPDATA%\Myster` for compatibility
- Public: New `%USERPROFILE%\myster` for user files
- Ensure backslash vs forward slash handled correctly

## Implementation Assumptions

1. **No automatic data migration**
   - This plan only changes where NEW data is stored
   - Existing data remains in old location
   - Users must manually move files if desired
   - Automated migration is a separate future feature

2. **Standard environment variables are available**
   - `user.home` system property always available
   - `LOCALAPPDATA` environment variable on Windows
   - `USERPROFILE` environment variable on Windows

3. **Directory creation permissions**
   - User has permission to create directories in home folder
   - User has permission to create directories in app data folder

4. **No conflicts with existing file structure**
   - If directories already exist (from previous versions), reuse them
   - No renaming or restructuring of existing content

5. **Single-user context**
   - Each OS user account has separate data paths
   - No shared multi-user data directories

## Open Questions

None - requirements are clear from user request.
