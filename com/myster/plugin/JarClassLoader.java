/*
 * Main.java
 * 
 * Title: Jar Class Loader Author: Andrew Trumper Description: Loads class files
 * insider jars.
 */

package com.myster.plugin;

import java.io.DataInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.util.Enumeration;
import java.util.Hashtable;
import java.util.Vector;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

public class JarClassLoader extends ClassLoader {
    ZipFile zip;

    Hashtable cache = new Hashtable();

    public JarClassLoader(String jar) throws IOException {
        zip = new ZipFile(jar);
    }

    public JarClassLoader(File jar) throws IOException {
        zip = new ZipFile(jar);
    }

    public void printObjects() {
        Enumeration t = zip.entries();

        while (t.hasMoreElements()) {
            ZipEntry entry = ((ZipEntry) (t.nextElement()));
            System.out.println(entry.getName());
        }
    }

    public Vector getEntries() {
        Vector entries = new Vector(10, 10);
        Enumeration t = zip.entries();

        while (t.hasMoreElements()) {
            ZipEntry entry = ((ZipEntry) (t.nextElement()));
            entries.addElement(entry.getName());
        }
        return entries;
    }

    private byte[] loadClassData(String name) throws ClassNotFoundException {
        try {
            name = name.replace('.', '/');
            name = name + ".class";
            ZipEntry entry = zip.getEntry(name);
            InputStream in = zip.getInputStream(entry);
            long size = entry.getSize();
            if (size == -1) {
                System.out.println("This file is broken");
                throw new ClassNotFoundException("Fuck");
            }
            DataInputStream dataIn = new DataInputStream(in);

            byte[] b = new byte[(int) size];
            dataIn.readFully(b);
            return b;
        } catch (Exception ex) {
            throw new ClassNotFoundException("I hate shit.");
        }
    }

    public synchronized Class loadClass(String name, boolean resolve)
            throws ClassNotFoundException {
        try {
            try {
                return findSystemClass(name);
            } catch (NoClassDefFoundError ex) {
                System.out.println("Here.");
                return null;
            } catch (Error ex) {
                System.out.println("" + ex);
                throw ex;
            }
        } catch (ClassNotFoundException ex) {
            Class c = (Class) (cache.get(name));
            if (c == null) {
                System.out.println("Loading class : " + name);
                byte data[] = loadClassData(name);
                c = defineClass(name, data, 0, data.length);
                cache.put(name, c);
            }
            if (resolve)
                resolveClass(c);
            return c;
        }
    }

    public URL getResource(String name) {
        String pathName = "jar:file:" + zip.getName() + "!/" + name;
        try {
            URL url = new URL(pathName);
            return url;
        } catch (java.net.MalformedURLException ex) {
            ex.printStackTrace();
            return null;
        }
    }

}