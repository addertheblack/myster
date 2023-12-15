package com.myster.hash;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.EOFException;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.OutputStream;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.Hashtable;
import java.util.List;

import com.myster.application.MysterGlobals;

/**
 * This class is yet another manager. In this case, this manager should only be
 * used by the file hashing sub system and not by random bits of code. It is
 * made public because it is safe for use by third party code although I can't
 * think of a reason why it would be used.
 * 
 * It's purpose is to cache all know file hashes and to save and restore these
 * hashes to disk. All save operations are automatic although there might be a
 * delay between the time the change is made and the time the change is written
 * to disk.
 * 
 * The usage is to get the DefaulthashCache and to use the functions expressed
 * in the HashCache interface.
 */

public interface HashCache {
    /**
     * Gets the File Hashes for a files if the information is contained in the
     * cache
     * 
     * @param file
     *            The java.io.File you wish to get the Hashes for
     * @return The known FileHashes[] for this file
     */
    public abstract FileHash[] getHashesForFile(File file);

    /**
     * Gets the File Hash for a files if the information is contained in the
     * cache
     * 
     * @param file
     *            The java.io.File you wish to get the Hashes for
     * @param hashType
     *            String, the type of hash you wish to extract.
     * @return The known FileHash for this file
     */
    public abstract FileHash getHashFromFile(File file, String hashType);

    /**
     * Adds and updates the file hashes for a file. If a Hash of that type
     * already exists then it is updated. If it doesn't it is added to the
     * cache.
     * 
     * @param file
     *            The java.io.File you wish to add the Hashes for
     * @param hashes
     *            The hashes you wish to add
     */
    public abstract void putHashes(File file, FileHash[] hashes);

    /**
     * Same behavior as putHashes only for one Hash
     * 
     * @param file
     *            The java.io.File you wish to add the Hashes for
     * @param hash
     *            The hashes you wish to add
     */
    public abstract void putHash(File file, FileHash hash);

    /**
     * Deletes all cached hashes for this File.
     * 
     * @param file
     *            The java.io.File you wish to delete all cached hashes
     */
    public abstract void clearHashes(File file);

    /**
     * Deletes cached hash for this File given the type. If the type is not
     * found this function has no effect.
     * 
     * @param file
     *            The java.io.File you wish to delete the hash for.
     * @param hashType
     *            String, the type of hahs you want to clear from the cache.
     */
    public abstract void clearHash(File file, String hashType);
}
