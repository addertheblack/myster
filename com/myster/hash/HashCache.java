package com.myster.hash;

import java.io.File;
import java.io.IOException;
import java.io.Serializable;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.util.Vector;
import java.util.Hashtable;
import java.util.Enumeration;

/**
*	This class is yet another manager. In this case, this manager should only be used
*	by the file hashing sub system and not by random bits of code. It is made public because
*	it is safe for use by third party code although I can't think of a reasone why it would be used.
*
* 	It's purpose is to cache all know file hashes and to save and restore these hashes to disk.
*	All save opperations are automatic although there might be a delay between the time the
*	change is made and the time the change is written to disk.
*
*	The usage is to get the DefaulthashCache and to use the functions expressed in the HashCache
*	interface.
*/

public abstract class HashCache {
	private static HashCache defaultHashCache;

	/**
	*	Gets Myster's (default) FileHash Cache.
	*/
	public synchronized static HashCache getDefault() {
		if (defaultHashCache ==  null) defaultHashCache = new DefaultHashCache();
		
		return defaultHashCache;
	}
	
	/**
	*	Gets the File Hashes for a files if the information is contained in the cache
	*
	*	@param		The java.io.File you wish to get the Hashes for
	*	@returns	The known FileHashes[] for this file
	*/
	public abstract FileHash[] getHashesForFile(File file) ;
	
	/**
	*	Gets the File Hash for a files if the information is contained in the cache
	*
	*	@param		The java.io.File you wish to get the Hashes for
	*	@param		String, the type of hash you wish to extract.
	*	@returns	The known FileHash for this file
	*/
	public abstract FileHash getHashFromFile(File file, String hashType);
	
	/**
	*	Adds and updates the file hashes for a file. If a Hash of that
	*	type already exists then it is updated. If it doesn't it is added
	*	to the cache.
	*
	*	@param		The java.io.File you wish to add the Hashes for
	*/
	public abstract void putHashes(File file, FileHash[] hashes);
	
	/**
	*	Same behavior as putHashes only for one Hash
	*
	*	@param		The java.io.File you wish to add the Hass for
	*/
	public abstract void putHash(File file, FileHash hash);
	
	/**
	*	Deletes all cached hashes for this File.
	*
	*	@param		The java.io.File you wish to delete all cached hashes
	*/
	public abstract void clearHashes(File file);
	
	/**
	*	Deletes cached hash for this File given the type. If the type is not
	*	found this function has no effect.
	*
	*	@param		The java.io.File you wish to delete the hash for.
	*	@param		String, the type of hahs you want to clear from the cache.
	*/
	public abstract void clearHash(File file, String hashType);
}


/**
*	The default Hash Cache implementation
*/
class DefaultHashCache extends HashCache {
	private Hashtable hashtable = new Hashtable();
	
	private static final String FILE_NAME = "hashCache";
	
	public DefaultHashCache() {
		ObjectInputStream in = null;
		
		try {
			File file = new File("HashCache");
			
			in = new ObjectInputStream(new FileInputStream(file));
			
			for (;;) {
				CachedFileHashEntry entry = (CachedFileHashEntry)(in.readObject());
				
				if (entry.file.exists() && entry.isForThisFile(entry.file)) {
					hashtable.put(entry.getFile(), entry);
				}
			}
		} catch (IOException ex) {
			ex.printStackTrace();
		} catch (Exception ex) {
			ex.printStackTrace();
		} finally {
			try {in.close();} catch (Exception ex) {}
		}
	}

	public synchronized FileHash[] getHashesForFile(File file) {
		CachedFileHashEntry temp = ((CachedFileHashEntry)(hashtable.get(file)));
		
		if (temp == null || !temp.isForThisFile(file)) return null;
		
		return temp.getHashes();
	}
	
	public synchronized FileHash getHashFromFile(File file, String hashType) {	
		CachedFileHashEntry temp = ((CachedFileHashEntry)(hashtable.get(file)));
		
		if (temp == null || !temp.isForThisFile(file)) return null;
		
		return temp.getHash(hashType);
	}
	
	public synchronized void putHashes(File file, FileHash[] sourceHashes) {
		CachedFileHashEntry destinationHashes = ((CachedFileHashEntry)(hashtable.get(file)));
		
		if (destinationHashes == null) {
			hashtable.put(file, new CachedFileHashEntry(file, sourceHashes));
		} else {
			hashtable.put(file, destinationHashes.addHashes(sourceHashes));
		}
		
		save();
	}
	
	public synchronized void putHash(File file, FileHash hash) {
		putHashes(file, new FileHash[]{hash});
	}
	
	public synchronized void clearHashes(File file) {
		hashtable.remove(file);
		save();
	}
	
	public synchronized void clearHash(File file, String hashType) {
		CachedFileHashEntry cacheEntry = (CachedFileHashEntry)hashtable.get(file);
		
		if (cacheEntry == null) return;
		
		hashtable.put(file, cacheEntry.removeHash(hashType));
		save();
	}
	
	private synchronized void save() {
		ObjectOutputStream out = null;
		
		try {
			File file = new File("HashCache");
			
			out = new ObjectOutputStream(new FileOutputStream(file));
			
			Enumeration enum = hashtable.elements();
			
			while (enum.hasMoreElements()) {
				CachedFileHashEntry entry = (CachedFileHashEntry)(enum.nextElement());
				
				entry.save(out);
			}
		} catch (IOException ex) {
			ex.printStackTrace();
		} catch (Exception ex) {
			ex.printStackTrace();
		} finally {
			try {out.close();} catch (Exception ex) {}
		}
	}
}

//Immutable
/**
*	Represents a cached FileHash entry.
*/
class CachedFileHashEntry implements Serializable {
	//public static CachedFileHashEntry newCachedFileHashEntry(InputStream in) throws IOException {
	//	
	//}
	
	File file; //here only for it's path
	long lastModifiedDate;
	long fileLength;
	FileHash[] hashes;
	
	public CachedFileHashEntry(File file, FileHash[] hashes) {
		this(file, hashes, file.lastModified(), file.length());
	}
	
	public CachedFileHashEntry(File file, FileHash[] hashes, long lastModifiedDate, long fileLength) {
		this.file = file;
		this.hashes = hashes;
		this.lastModifiedDate = lastModifiedDate;
		this.fileLength = fileLength;
	}
	
	/** Returns all known File Hashes for this file*/
	public FileHash[] getHashes() {
		return (FileHash[])hashes.clone();
	}
	
	/** 
	*	Returns the File Hash of the requested type. Mostly here for speed.
	*	Since this object is immutable the FileHash Array must be duplicated before
	*	being sent. This allows the user to access a specific element without needing
	*	to get a copy of the whole array.
	*/
	public FileHash getHash(String type) {
		for (int i = 0; i < hashes.length ; i++) {
			if (hashes[i].getHashName().equals(type)) return hashes[i];
		}
		
		return null;
	}
	
	/**
	*	Adds a FileHash to the file hash of this object then returns a new
	*	CachedFileHashEntry representing this new entry. Since this object is
	*	immutable a new object must be created if any change is to be made.
	*
	*	If a FileHash of the same type already exists this function will
	*	over-ride that value with the new value.
	*/
	public CachedFileHashEntry addHashes(FileHash[] sourceHashes) {
		Vector v_temp = new Vector(sourceHashes.length + hashes.length);
		
		for (int i = 0; i < sourceHashes.length; i++) {
			v_temp.addElement(sourceHashes[i]);
		}
		
		for (int i = 0; i < hashes.length; i++) {
			if (! v_temp.contains(hashes[i])) {
				v_temp.addElement(hashes[i]);
			}
		}
		
		FileHash[] newHashes = new FileHash[v_temp.size()];
		v_temp.copyInto(newHashes);
		
		return new CachedFileHashEntry(file, newHashes);
	}
	
	/**
	*	Removes a FileHash to the file hash of this object then returns a new
	*	CachedFileHashEntry representing this new entry. Since this object is
	*	immutable a new object must be created if any change is to be made.
	*
	*	If the type is not found, no changes are made and the new object is a copy.
	*/
	public CachedFileHashEntry removeHash(String type) {
		int index = getIndex(hashes, type);
		
		if (index == -1) return null;
		
		FileHash[] newHash = new FileHash[hashes.length];
		
		for (int i = 0, j = 0; i < hashes.length; i++) {
			if (i != index) {
				newHash[j] = hashes[i];
				j++;
			}
		}
		
		return new CachedFileHashEntry(file, newHash);
	}
	
	/** private code reuse method. Gets the index of the FileHash of the type or -1*/
	private static final int getIndex(FileHash[] hashes, String type) {
		for (int i = 0; i < hashes.length; i++) {
			if (hashes[i].getHashName().equals(type)) return i;
		}
		
		return -1;
	}
	
	/** returns the file associated with this hash */
	public File getFile() {
		return file;
	}
	
	/** Going to be redone soon. */
	public void save(ObjectOutputStream out) throws IOException {
		out.writeObject(this);
	}
	
	public boolean isForThisFile(File file) {
		return (file.equals(file) && (file.lastModified() == lastModifiedDate) && (file.length() == fileLength));
	}
	
}