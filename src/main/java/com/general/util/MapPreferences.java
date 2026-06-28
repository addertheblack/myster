package com.general.util;

import java.io.IOException;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.prefs.BackingStoreException;
import java.util.prefs.NodeChangeListener;
import java.util.prefs.PreferenceChangeListener;
import java.util.prefs.Preferences;


/** This is an in-mem impl of a java pref node. At some point I should make this a util */
public class MapPreferences extends Preferences {
    private final MapPreferences root;
    private final Optional<MapPreferences> parent;
    private final Map<String, MapPreferences> children = new HashMap<>();
    
    private final Map<String, Object> values = new HashMap<>();
    private String name;
    
    public MapPreferences(MapPreferences parent, MapPreferences root, String name) {
        this.root = root == null ? this : root;
        this.parent = Optional.ofNullable(parent);
        this.name = name == null ? "" : name;
    }

    public MapPreferences() {
        this(null, null, null);
    }
        
    @Override
    public void put(String key, String value) {
        values.put(key, value);
    }

    @Override
    public String get(String key, String def) {
        return (String) getWhatever(key, def);
    }
    
    private Object getWhatever(String key, Object def) {
        Object whatever =  values.get(key);
        return whatever == null ? def : whatever;
    }

    @Override
    public void remove(String key) {
        values.remove(key);
    }

    @Override
    public void clear() throws BackingStoreException {
        values.clear();
    }

    @Override
    public void putInt(String key, int value) {
        values.put(key, value);
    }

    @Override
    public int getInt(String key, int def) {
        return (int) getWhatever(key, def);
    }

    @Override
    public void putLong(String key, long value) {
        values.put(key, value);
    }

    @Override
    public long getLong(String key, long def) {
        return (long) getWhatever(key, def);
    }

    @Override
    public void putBoolean(String key, boolean value) {
        values.put(key, value);
    }

    @Override
    public boolean getBoolean(String key, boolean def) {
        return (boolean) getWhatever(key, def);
    }

    @Override
    public void putFloat(String key, float value) {
        values.put(key, value);        
    }

    @Override
    public float getFloat(String key, float def) {
        return (float) getWhatever(key, def);
    }

    @Override
    public void putDouble(String key, double value) {
        values.put(key, value);
    }

    @Override
    public double getDouble(String key, double def) {
        return (double) getWhatever(key, def);
    }

    @Override
    public void putByteArray(String key, byte[] value) {
        values.put(key, value);
    }

    @Override
    public byte[] getByteArray(String key, byte[] def) {
        return (byte[]) getWhatever(key, def);
    }

    @Override
    public String[] keys() throws BackingStoreException {
        return new ArrayList<>(values.keySet()).toArray(new String[values.keySet().size()]);
    }

    @Override
    public String[] childrenNames() throws BackingStoreException {
        return new ArrayList<>(children.keySet()).toArray(new String[children.keySet().size()]);
    }

    @Override
    public Preferences parent() {
        return parent.orElse(null);
    }

    @Override
    public Preferences node(String pathName) {
        return findNode(pathName, true).orElseThrow();
    }

    @Override
    public boolean nodeExists(String pathName) throws BackingStoreException {
        return findNode(pathName, false).isPresent();
    }

    private Optional<MapPreferences> findNode(String pathName, boolean createMissing) {
        if (pathName.equals("")) {
            return Optional.of(this);
        }
        if (pathName.equals("/")) {
            return Optional.of(root);
        }
        if (pathName.startsWith("/")) {
            return root.findNode(pathName.substring(1), createMissing);
        }

        int indexOfSlash = pathName.indexOf("/");
        String nameOfNode = indexOfSlash == -1 ? pathName : pathName.substring(0, indexOfSlash);
        String remainingPath = indexOfSlash == -1 ? "" : pathName.substring(indexOfSlash + 1);

        MapPreferences child = children.get(nameOfNode);
        if (child == null) {
            if (!createMissing) {
                return Optional.empty();
            }
            child = new MapPreferences(this, root, nameOfNode);
            children.put(nameOfNode, child);
        }

        return remainingPath.equals("") ? Optional.of(child) : child.findNode(remainingPath, createMissing);
    }

    @Override
    public void removeNode() throws BackingStoreException {
        if (parent.isEmpty()) {
            throw new UnsupportedOperationException("Cannot remove root node");
        }
        parent.get().children.remove(name);
        values.clear();
        children.clear();
    }

    @Override
    public String name() {
        return name;
    }

    @Override
    public String absolutePath() {
        if (parent.isEmpty()) {
            return "/";
        }
        String parentPath = parent.get().absolutePath();
        return parentPath.equals("/") ? parentPath + name : parentPath + "/" + name;
    }

    @Override
    public boolean isUserNode() {
        return true;
    }

    @Override
    public String toString() {
        return "";
    }

    @Override
    public void flush() throws BackingStoreException {
        
    }

    @Override
    public void sync() throws BackingStoreException {
        
    }

    @Override
    public void addPreferenceChangeListener(PreferenceChangeListener pcl) {
        
    }

    @Override
    public void removePreferenceChangeListener(PreferenceChangeListener pcl) {
        
    }

    @Override
    public void addNodeChangeListener(NodeChangeListener ncl) {
        
    }

    @Override
    public void removeNodeChangeListener(NodeChangeListener ncl) {
        // TODO Auto-generated method stub
        
    }

    @Override
    public void exportNode(OutputStream os) throws IOException, BackingStoreException {
        
    }

    @Override
    public void exportSubtree(OutputStream os) throws IOException, BackingStoreException {
        
    }
}
