/* 
	Main.java

	Title:			Jar Class Loader
	Author:			Andrew Trumper
	Description:	Loads class files insider jars.
*/

package com.myster.plugin;


import java.util.*;
import java.io.*;
import java.util.zip.*;

public class JarClassLoader extends ClassLoader {
	ZipFile zip;
	Hashtable cache = new Hashtable();
	
	public JarClassLoader(String jar) throws IOException {
		zip=new ZipFile(jar);
	}
	
	public JarClassLoader(File jar) throws IOException {
		zip=new ZipFile(jar);
	}
	
	public void printObjects() {
		Enumeration t=zip.entries();
		
		while (t.hasMoreElements()) {
			ZipEntry entry=((ZipEntry)(t.nextElement()));
			System.out.println(entry.getName());
		}
	}
	
	public Vector getEntries() {
		Vector entries=new Vector(10,10);
		Enumeration t=zip.entries();
		
		while (t.hasMoreElements()) {
			ZipEntry entry=((ZipEntry)(t.nextElement()));
			entries.addElement(entry.getName());
		}
		return entries;
	}

	private byte[] loadClassData(String name) throws ClassNotFoundException {
		try {
			name=name.replace('.','/');
			name=name+".class";
			ZipEntry entry=zip.getEntry(name);
			InputStream in=zip.getInputStream(entry);
			long size=entry.getSize();
			if (size==-1) {
				System.out.println("This file is broken");
				throw new ClassNotFoundException ("Fuck");
			}
			byte[] b=new byte[(int)size];
			in.read(b);
			return b;
		} catch (Exception ex) {
			throw new ClassNotFoundException("I hate shit.");
		}
	}
	
	public synchronized Class loadClass(String name, boolean resolve) throws ClassNotFoundException{
	     try {
	    	 try {
	    	 	return findSystemClass(name);
	    	 } catch (NoClassDefFoundError ex) {
	    	 	System.out.println("Here.");
	    	 	return null;
	    	 } catch (Error ex) {
	    	 	System.out.println(""+ex);
	    	 	throw ex;
	    	 }
	     } catch (ClassNotFoundException ex) {
			 Class c = (Class)(cache.get(name));
			 if (c == null) {
			     byte data[] = loadClassData(name);
			     c = defineClass(data, 0, data.length);
			     cache.put(name, c);
			 }
			 if (resolve)
			     resolveClass(c);
			 return c;
		}
	}	
}