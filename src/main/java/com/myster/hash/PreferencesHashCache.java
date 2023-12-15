
package com.myster.hash;

import java.io.File;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;
import java.util.prefs.BackingStoreException;
import java.util.prefs.Preferences;
import java.util.stream.Collectors;

public class PreferencesHashCache implements HashCache {
    private Preferences node;

    public PreferencesHashCache(Preferences node) {
        this.node = node;
    }
    
    private static String hashedFilename(String filename) {
        try {
            MessageDigest md = MessageDigest.getInstance("MD5");
            md.update(filename.getBytes());
            byte[] digest = md.digest();
            return SimpleFileHash.asHex(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("Failed to hash the filename", e);
        }
    }


    @Override
    public FileHash[] getHashesForFile(File file) {
        Preferences hashesForFile = node.node(hashedFilename(file.getAbsolutePath()));
        
        String[] keys;
        try {
            keys = hashesForFile.keys();
        } catch (BackingStoreException exception) {
            // it's fine.. probably a transient error
            
            return null;
        }

        FileHash[] hashes = Arrays.asList(keys).stream()
                .filter(k -> hashesForFile.getByteArray(k, null) != null)
                .map(k -> SimpleFileHash.buildFileHash(k, hashesForFile.getByteArray(k, new byte[0])))
                .collect(Collectors.toUnmodifiableList()).toArray(new FileHash[0]);
        
        if ( hashes.length == 0) {
            return null;
        }
        return hashes;

    }

    @Override
    public FileHash getHashFromFile(File file, String hashType) {
        byte[] b=  node.node(hashedFilename(file.getAbsolutePath())).getByteArray(hashType, null);
        
        if(b == null) {
            return null;
        }
        
        return SimpleFileHash.buildFileHash(hashType, b);
    }

    @Override
    public void putHashes(File file, FileHash[] hashes) {
        Preferences hashesForFile = node.node(hashedFilename(file.getAbsolutePath()));
        
        for (FileHash fileHash : hashes) {
            hashesForFile.putByteArray(fileHash.getHashName(), fileHash.getBytes());
        }
    }

    @Override
    public void putHash(File file, FileHash hash) {
        Preferences hashesForFile = node.node(hashedFilename(file.getAbsolutePath()));

        hashesForFile.putByteArray(hash.getHashName(), hash.getBytes());
    }

    @Override
    public void clearHashes(File file) {
        try {
            node.node(hashedFilename(file.getAbsolutePath())).clear();
        } catch (BackingStoreException exception) {
            // ignore
        }
    }

    @Override
    public void clearHash(File file, String hashType) {
        node.node(hashedFilename(file.getAbsolutePath())).remove(hashType);
    }
}
