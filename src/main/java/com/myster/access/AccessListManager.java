package com.myster.access;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;

import com.myster.application.MysterGlobals;
import com.myster.type.MysterType;

/**
 * Manages loading, saving, and caching of access lists.
 *
 * <p>Access lists are stored as binary files in the AccessLists directory:
 * {@code PrivateDataPath/AccessLists/{mysterType_hex}.accesslist}
 *
 * <p>Maintains an in-memory cache keyed by {@link MysterType} for performance.
 * Thread-safe via {@link ConcurrentHashMap}.
 */
public class AccessListManager implements AccessListReader {
    private static final Logger log = Logger.getLogger(AccessListManager.class.getName());

    private final Map<MysterType, AccessList> cache = new ConcurrentHashMap<>();

    /**
     * Loads an access list from disk. Returns the cached copy if available.
     *
     * @param mysterType the type to load
     * @return the AccessList if found
     */
    public Optional<AccessList> loadAccessList(MysterType mysterType) {
        AccessList cached = cache.get(mysterType);
        if (cached != null) {
            return Optional.of(cached);
        }

        File file = getAccessListFile(mysterType);
        if (!file.exists()) {
            return Optional.empty();
        }

        try (FileInputStream fis = new FileInputStream(file)) {
            AccessList accessList = AccessListStorageUtils.read(fis);

            if (!accessList.getMysterType().equals(mysterType)) {
                log.warning("MysterType mismatch in file " + file.getAbsolutePath());
                return Optional.empty();
            }

            cache.put(mysterType, accessList);
            log.info("Loaded access list for type: " + mysterType.toHexString());
            return Optional.of(accessList);

        } catch (IOException e) {
            log.severe("Failed to load access list from " + file.getAbsolutePath() + ": " + e.getMessage());
            return Optional.empty();
        }
    }

    /**
     * Saves an access list to disk and updates the cache.
     *
     * @param accessList the access list to save
     * @throws IOException if saving fails
     */
    public void saveAccessList(AccessList accessList) throws IOException {
        MysterType mysterType = accessList.getMysterType();
        File file = getAccessListFile(mysterType);
        file.getParentFile().mkdirs();

        File tempFile = new File(file.getAbsolutePath() + ".tmp");

        try (FileOutputStream fos = new FileOutputStream(tempFile)) {
            AccessListStorageUtils.write(accessList, fos);
        }

        if (!tempFile.renameTo(file)) {
            tempFile.delete();
            throw new IOException("Failed to rename temp file to " + file.getAbsolutePath());
        }

        cache.put(mysterType, accessList);
        log.info("Saved access list for type: " + mysterType.toHexString());
    }

    /**
     * Removes an access list from disk and cache.
     *
     * @param mysterType the type to remove
     * @return true if removed
     */
    public boolean removeAccessList(MysterType mysterType) {
        File file = getAccessListFile(mysterType);
        boolean deleted = file.delete();
        cache.remove(mysterType);
        if (deleted) {
            log.info("Removed access list for type: " + mysterType.toHexString());
        }
        return deleted;
    }

    /**
     * Checks if an access list exists (in cache or on disk).
     *
     * @param mysterType the type to check
     * @return true if it exists
     */
    public boolean hasAccessList(MysterType mysterType) {
        if (cache.containsKey(mysterType)) {
            return true;
        }
        return getAccessListFile(mysterType).exists();
    }

    /**
     * Clears the in-memory cache.
     */
    public void clearCache() {
        cache.clear();
    }

    private File getAccessListFile(MysterType mysterType) {
        String filename = mysterType.toHexString() + ".accesslist";
        return new File(MysterGlobals.getAccessListPath(), filename);
    }
}
