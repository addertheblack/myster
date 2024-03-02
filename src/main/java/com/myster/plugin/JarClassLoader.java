/*
 * Main.java
 * 
 * Title: Jar Class Loader Author: Andrew Trumper Description: Loads class files
 * insider jars.
 */

package com.myster.plugin;

import com.myster.client.stream.MysterDataInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.Hashtable;
import java.util.List;
import java.util.logging.Logger;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

public class JarClassLoader extends ClassLoader {
    private static final Logger LOGGER = Logger.getLogger(JarClassLoader.class.getName());
    
    private final Hashtable<String, Class> cache = new Hashtable<>();
    private final ZipFile zip;

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
            LOGGER.info(entry.getName());
        }
    }

    public List<String> getEntries() {
        List<String> entries = new ArrayList<>();
        Enumeration t = zip.entries();

        while (t.hasMoreElements()) {
            ZipEntry entry = ((ZipEntry) (t.nextElement()));
            entries.add(entry.getName());
        }
        return entries;
    }

    private byte[] loadClassData(String p_name) throws ClassNotFoundException {
        try {
            String  name = p_name.replace('.', '/') + ".class";
            ZipEntry entry = zip.getEntry(name);
            InputStream in = zip.getInputStream(entry);
            long size = entry.getSize();
            if (size == -1) {
                LOGGER.info("This file is broken");
                throw new ClassNotFoundException("Fuck");
            }
            
            try (MysterDataInputStream dataIn = new MysterDataInputStream(in)) {
                byte[] b = new byte[(int) size];
                dataIn.readFully(b);
                return b;
            }
        } catch (IOException ex) {
            throw new ClassNotFoundException("I hate shit.");
        }
    }

    public synchronized Class loadClass(String name, boolean resolve)
            throws ClassNotFoundException {
        try {
            try {
                return findSystemClass(name);
            } catch (NoClassDefFoundError ex) {
                LOGGER.info("Here.");
                return null;
            } catch (Error ex) {
                LOGGER.info("" + ex);
                throw ex;
            }
        } catch (ClassNotFoundException ex) {
            Class c = cache.get(name);
            if (c == null) {
                LOGGER.info("Loading class : " + name);
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
            URL url = new URI(pathName).toURL();
            return url;
        } catch (java.net.MalformedURLException | URISyntaxException ex) {
            ex.printStackTrace();
            return null;
        }
    }
}
