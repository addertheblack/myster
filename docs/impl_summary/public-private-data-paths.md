# Public/Private Data Path Separation - Implementation Summary

## Status: ✅ COMPLETE

Implementation completed on February 8, 2026.

## Changes Made

### 1. MysterGlobals.java
- **Added** `getPublicDataPath()` method for user-managed content (downloads, images)
  - Linux/macOS: `~/myster`
  - Windows: `%USERPROFILE%\myster`
  
- **Added** `getPrivateDataPath()` method for application-managed content (identity, incoming)
  - Linux: `~/.myster`
  - macOS: `~/Library/Application Support/Myster`
  - Windows: `%LOCALAPPDATA%\Myster`

- **Removed** `getAppDataPath()` method (replaced by the two new methods)

### 2. FileTypeList.java
- Updated `getDefaultDirectory()` to use `getPublicDataPath()`
- Downloads now go to the public user directory
- Updated comment to reflect usage of public data path

### 3. BannersManager.java
- Updated `getImagesDirectory()` to use `getPublicDataPath()`
- Banner images now stored in public directory
- Updated method documentation

### 4. MultiSourceUtilities.java
- Updated `getIncomingDirectory()` to use `getPrivateDataPath()`
- Incomplete downloads now stored in private directory
- Updated method documentation

### 5. Identity.java
- Updated `newIdentity()` to use `getPrivateDataPath()`
- Keystore now stored in private directory
- Updated `main()` test method to use `getPrivateDataPath()`
- Updated method documentation

## Implementation Notes

### Platform Conventions Followed
- **macOS**: Uses `~/Library/Application Support/Myster` for private data, following Apple's guidelines
- **Linux**: Uses `~/.myster` (hidden directory) for private data, following Unix conventions
- **Windows**: Uses `%LOCALAPPDATA%\Myster` for private data (unchanged from before)

### Data Classification
| Data Type | Storage | Path Type | Rationale |
|-----------|---------|-----------|-----------|
| Downloads | Public | User-accessible | Users need easy access to downloaded files |
| Images | Public | User-accessible | Users manage banner images |
| Incoming | Private | Application-managed | Temporary/incomplete downloads |
| Identity | Private | Application-managed | Security-sensitive keystores |

## Testing Recommendations

### Manual Testing Checklist
- [ ] Fresh install creates both public and private directories
- [ ] Downloads appear in correct public path
- [ ] Images directory created in public path
- [ ] Incoming directory created in private path
- [ ] Identity keystore created in private path
- [ ] Test on Linux (verify `~/myster` and `~/.myster`)
- [ ] Test on macOS (verify `~/myster` and `~/Library/Application Support/Myster`)
- [ ] Test on Windows (verify paths in user profile and app data)

### Migration Considerations
- **No automatic migration implemented** - This is intentional (Phase 1)
- Existing users will have data in old locations
- New data will use new paths
- Consider implementing data migration in Phase 2

## API Changes Summary

### Removed
```java
public static File getAppDataPath()
```

### Added
```java
public static File getPublicDataPath()  // For downloads, images
public static File getPrivateDataPath() // For identity, incoming
```

## Files Modified
1. `/src/main/java/com/myster/application/MysterGlobals.java`
2. `/src/main/java/com/myster/filemanager/FileTypeList.java`
3. `/src/main/java/com/myster/net/server/BannersManager.java`
4. `/src/main/java/com/myster/net/stream/client/msdownload/MultiSourceUtilities.java`
5. `/src/main/java/com/myster/identity/Identity.java`
6. `/docs/plans/public-private-data-paths.md` (updated with macOS correction)

## Next Steps (Future Work)
- Implement data migration utility to move existing user data to new locations
- Add UI to show data directory locations
- Add "Reveal in Finder/Explorer" buttons for data directories
- Update user-facing documentation explaining new directory structure

