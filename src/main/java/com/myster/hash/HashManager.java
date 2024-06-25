package com.myster.hash;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.logging.Logger;
import java.util.prefs.Preferences;

import com.general.events.NewGenericDispatcher;
import com.general.thread.Invoker;
import com.general.util.BlockingQueue;

/**
 * Facilities for passively determining the hash values of files.
 */

public class HashManager implements Runnable {
    private static final Logger LOGGER = Logger.getLogger(HashManager.class.getName());
    
    public static final String MD5 = "md5";
    public static final String SHA1 = "sha1";

    private static final String[] hashTypes = { MD5 };
    
    private static final String HASHING_ENABLED_PREF_KEY = "Hash Manager Is Enabled";
    private static final String HASH_CACHE_NODE = "Hash Manager Cache";
    
    private volatile boolean hashingIsEnabled = true;
    
    private final NewGenericDispatcher<HashManagerListener> hashManagerDispatcher;
    private final BlockingQueue<WorkingQueueItem> workQueue;
    private final HashCache oldHashes;

    public HashManager() {
        hashManagerDispatcher = new NewGenericDispatcher<>(HashManagerListener.class, Invoker.EDT);
        
        workQueue = new BlockingQueue<WorkingQueueItem>();
        workQueue.setRejectDuplicates(true);

        oldHashes = new PreferencesHashCache(getHashManagerRoot().node(HASH_CACHE_NODE));

        hashingIsEnabled = getHashManagerRoot().getBoolean(HASHING_ENABLED_PREF_KEY, true);
    }
    
    private static Preferences getHashManagerRoot() {
        return Preferences.userNodeForPackage(HashManager.class);
    }

    /**
     * Starts up the HashManager. Used to allow plugins to register themselves
     * before the Hashing begins.
     */
    public void start() {
        (new Thread(this)).start();
    }

    /**
     * Setting this to false means that no new hashes will be done.
     * <p>
     * (Synchronized because prefs and cached value should be in agreement and
     * events should exec in right order)
     */
    public synchronized void setHashingEnabled(boolean enableHashing) {
        hashingIsEnabled = enableHashing;

        getHashManagerRoot().putBoolean(HASHING_ENABLED_PREF_KEY, enableHashing);

        hashManagerDispatcher.fire().enabledStateChanged(new HashManagerEvent(enableHashing));
    }

    /**
     * If hashing is enabling this function returns true, false otherwise.
     */
    public boolean getHashingEnabled() {
        return hashingIsEnabled;
    }

    /**
     * Adds a listener for common hash manager events. This routine cannot be
     * synchronized.
     */
    public void addHashManagerListener(HashManagerListener listener) {
        hashManagerDispatcher.addListener(listener);
    }

//    /**
//     * Removes a listener from common hash manager events. This routine cannot
//     * be synchronized.
//     */
//    public static void removeHashManagerListener(HashManagerListener listener) {
//        hashManagerDispatcher.removeListener(listener);
//    }


    /**
     * This routine could take a long time or not. If the file is cached the
     * information is returned to the listener from the current thread.. Else
     * the information is returned from the internal hashing thread. (Yipes,
     * thread management in java is <->to memory alloc free in C++ in pointless
     * complexity)...
     */
    public void findHash(File file, FileHashListener listener) {
        FileHash[] hashes = oldHashes.getHashesForFile(file);

        if (hashes == null) {
            if (file.exists()) {
                workQueue.add(new WorkingQueueItem(file, listener));
            } else {
                dispatchHashFoundEvent(listener, new FileHash[] {}, file); // file
                // doens't
                // EXIST!
            }
        } else {
            dispatchHashFoundEvent(listener, hashes, file);
        }
    }

    private static void dispatchHashFoundEvent(FileHashListener listener, FileHash[] hashes, File file) {
        listener.foundHash(new FileHashEvent(hashes, file));
    }

    public void run() {
        MessageDigest[] digestArray = new MessageDigest[hashTypes.length];

        Thread.currentThread().setPriority(Thread.MIN_PRIORITY);

        for (;;) {
            try {
                WorkingQueueItem item = workQueue.get();

                if (!hashingIsEnabled)
                    continue;

                FileHash[] hashes = oldHashes.getHashesForFile(item.file);

                if (hashes !=null) { //Humm we have already hashed this.
                    dispatchHashFoundEvent(item.listener, hashes, item.file);
                    continue;
                }
                
                for (int i = 0; i < digestArray.length; i++) {
                    digestArray[i] = MessageDigest.getInstance(hashTypes[i].toUpperCase());
                }

                calcHash(item.file, digestArray);

                hashes = new FileHash[digestArray.length];
                for (int i = 0; i < hashes.length; i++) {
                    hashes[i] = new SimpleFileHash(hashTypes[i], digestArray[i].digest());
                }

                oldHashes.putHashes(item.file, hashes);

                dispatchHashFoundEvent(item.listener, hashes, item.file);
            } catch (NoSuchAlgorithmException ex) {
                ex.printStackTrace();
                // Should never happen
            } catch (InterruptedException ex) {
                ex.printStackTrace();
                // Should never happen.
            } catch (Exception ex) {
                ex.printStackTrace();
                // Should REALLY never happen
            }
        }
    }

    public int getWorkQueueLength() {
        return workQueue.getSize();
    }

    private final void calcHash(File file, MessageDigest[] digests) {

        InputStream in = null;

        try {
            in = new FileInputStream(file); //read only

            long currentByte = 0;

            byte[] buffer = new byte[64 * 1024]; //64k buffer

            hashManagerDispatcher.fire()
                    .fileHashStart(new HashManagerEvent(hashingIsEnabled, file, 0));

            long timeOfLastUpdate = 0;
            for (int bytesRead = in.read(buffer); bytesRead != -1; bytesRead = in.read(buffer)) {
                currentByte += bytesRead;

                addBytesToDigests(buffer, bytesRead, digests);

                if ((System.currentTimeMillis() - timeOfLastUpdate) > 100) { // blarg!
                                                                             // too
                                                                             // many
                                                                             // events!
                    hashManagerDispatcher.fire()
                            .fileHashProgress(new HashManagerEvent(hashingIsEnabled,
                                                                   file,
                                                                   currentByte));
                    timeOfLastUpdate = System.currentTimeMillis();
                }
            }
        } catch (IOException ex) {
            LOGGER.warning("Could not read a file.");
        } finally {
            try {
                in.close();
            } catch (Exception ex) {
                // ignore
            } // don't care

            hashManagerDispatcher.fire().fileHashEnd(new HashManagerEvent(
                    hashingIsEnabled, file, file.length()));
        }
    }

    private final static void addBytesToDigests(byte[] bytes, int endByte, MessageDigest[] digests) {

        for (int i = 0; i < digests.length; i++) {
            digests[i].update(bytes, 0, endByte);
            Thread.yield();
        }
    }

    private static class WorkingQueueItem {
        public final File file;

        public final FileHashListener listener;

        public WorkingQueueItem(File file, FileHashListener listener) {
            this.file = file;
            this.listener = listener;
        }
    }

    //private static class
}