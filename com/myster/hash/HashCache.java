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

public abstract class HashCache {
	private static HashCache defaultHashCache;

	public synchronized static HashCache getDefault() {
		if (defaultHashCache ==  null) defaultHashCache = new DefaultHashCache();
		
		return defaultHashCache;
	}
	
	public abstract FileHash[] getHashesForFile(File file) ;
	public abstract FileHash getHashFromFile(File file, String hashType);
	public abstract void putHashes(File file, FileHash[] hashes);
	public abstract void putHash(File file, FileHash hash);
	public abstract void clearHashes(File file);
	public abstract void clearHash(File file, String hashType);
}

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
				
				hashtable.put(entry.getFile(), entry);
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
		
		if (temp == null) return null;
		
		return temp.getHashes();
	}
	
	public synchronized FileHash getHashFromFile(File file, String hashType) {	
		CachedFileHashEntry temp = ((CachedFileHashEntry)(hashtable.get(file)));
		
		if (temp == null) return null;
		
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
class CachedFileHashEntry implements Serializable {
	//public static CachedFileHashEntry newCachedFileHashEntry(InputStream in) throws IOException {
	//	
	//}
	
	File file;
	FileHash[] hashes;
	
	public CachedFileHashEntry(File file, FileHash[] hashes) {
		this.file = file;
		this.hashes = hashes;
	}
	
	public FileHash[] getHashes() {
		return (FileHash[])hashes.clone();
	}
	
	public FileHash getHash(String type) {
		for (int i = 0; i < hashes.length ; i++) {
			if (hashes[i].getHashName().equals(type)) return hashes[i];
		}
		
		return null;
	}
	
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
	
	
	private static final int getIndex(FileHash[] hashes, String type) {
		for (int i = 0; i < hashes.length; i++) {
			if (hashes[i].getHashName().equals(type)) return i;
		}
		
		return -1;
	}
	
	public File getFile() {
		return file;
	}
	
	public void save(ObjectOutputStream out) throws IOException {
		out.writeObject(this);
	}
}