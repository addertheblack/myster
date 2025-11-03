package com.myster.mml;

import java.io.IOException;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.msgpack.core.MessageBufferPacker;
import org.msgpack.core.MessagePack;
import org.msgpack.core.MessagePackException;
import org.msgpack.core.MessagePacker;
import org.msgpack.core.MessageUnpacker;
import org.msgpack.value.ValueType;

import com.general.util.ProtectedForUnitTests;

/**
 * A MessagePack-based serialization utility that provides an interface similar to MML
 * for easier migration. This class uses a more efficient binary format while maintaining
 * a similar tree-like structure for data storage.
 * 
 * Use this class when you are writing a data structure.
 */
public class MessagePackSerializer implements com.myster.mml.MessagePack {
    private Map<String, Object> root;
    
    /**
     * Use {@link com.myster.mml.MessagePack#newEmpty()}
     */
    MessagePackSerializer() {
        root = new HashMap<>();
    }
    
    /**
     * Do not use this method. Use {@link com.myster.mml.MessagePack#fromBytes(byte[])}
     */
    MessagePackSerializer(byte[] data) throws IOException {
        try (MessageUnpacker unpacker = MessagePack.newDefaultUnpacker(data)) {
            root = unpackMap(unpacker);
        } catch (MessagePackException ex) {
            // MessagePackException is a damn RuntimeException!
            // How is file corruption an unrecoverable error you asshat!
            throw new IOException("Parsing exception", ex);
        }
    }
    
    /**
     * Package Protected for unit tests
     */
    @ProtectedForUnitTests
    Map<String, Object> navigateToParent(String path) throws NonExistantPathException {
        if (path == null) {
            throw new NonExistantPathException("Path cannot be null");
        }
        
        String[] parts = parsePath(path);
        Map<String, Object> current = root;
        
        // Navigate to parent
        for (int i = 0; i < parts.length - 1; i++) {
            Object next = current.get(parts[i]);
            if (next == null) {
                throw new NonExistantPathException("Path does not exist: " + path);
            }
            if (!(next instanceof Map)) {
                throw new LeafAsABranchException("Came across a leaf \"" + parts[i] + "\" in " + path);
            }
            
            current = (Map<String, Object>) next;
        }
        
        return current;
    }

    /**
     * Navigate to parent, creating intermediate directories as needed
     */
    private Map<String, Object> navigateToParentCreatingAsNeeded(String path) {
        if (path == null) {
            throw new NullPointerException("Path cannot be null");
        }
        
        String[] parts = parsePath(path);
        Map<String, Object> current = root;
        
        // Navigate to parent, creating directories as needed
        for (int i = 0; i < parts.length - 1; i++) {
            Object next = current.get(parts[i]);
            if (next == null) {
                // Create new directory
                Map<String, Object> newDir = new HashMap<>();
                current.put(parts[i], newDir);
                current = newDir;
            } else if (next instanceof Map) {
                current = (Map<String, Object>) next;
            } else {
                throw new LeafAsABranchException("Came across a leaf \"" + parts[i] + "\" in " + path);
            }
        }
        
        return current;
    }

    private void assertIsBranchPath(String path) {
        if (path == null)
            throw new NullPointerException();
        if (path.charAt(path.length() - 1) != '/')
            throw new MMLPathException("\"" + path + "\" is not a branch path, it's a leaf.");
    }

    public synchronized void putString(String path, String value) {
        putValue(path, value);
    }

    public synchronized Optional<String> getString(String path) {
        return getValue(path).map(i -> (String) i);
    }
    
    public synchronized void putBoolean(String path, boolean value) {
        putValue(path, value);
    }

    public synchronized void putInt(String path, int value) {
        putValue(path, (long) value);
    }

    public synchronized void putLong(String path, long value) {
        putValue(path, value);
    }

    public synchronized void putShort(String path, short value) {
          putValue(path, (long) value);
    }

    public synchronized void putFloat(String path, float value) {
        putValue(path, (double) value);
    }

    public synchronized void putDouble(String path, double value) {
        putValue(path, value);
    }

    public synchronized void putDate(String path, Date value) {
        putValue(path, value);
    }

    public synchronized void putByteArray(String path, byte[] value) {
        putValue(path, value);
    }

    public synchronized void putIntArray(String path, int[] value) {
        if (value == null) {
            putValue(path, null);
            
            return;
        }
        Object[] objectArray = new Object[value.length];
        for (int i = 0; i < value.length; i++) {
            objectArray[i] = (long) value[i]; // Store as Long for consistency
                                              // with MessagePack
        }
        putValue(path, objectArray);
    }

    public synchronized void putLongArray(String path, long[] value) {
        if (value == null) {
            putValue(path, null);
            return;
        }
        Object[] objectArray = new Object[value.length];
        for (int i = 0; i < value.length; i++) {
            objectArray[i] = value[i]; // Already Long
        }
        putValue(path, objectArray);
    }

    public synchronized void putDoubleArray(String path, double[] value) {
        if (value == null) {
            putValue(path, null);
            return;
        }
        Object[] objectArray = new Object[value.length];
        for (int i = 0; i < value.length; i++) {
            objectArray[i] = value[i]; // Already Double
        }
        putValue(path, objectArray);
    }

    public synchronized void putShortArray(String path, short[] value) {
        if (value == null) {
            putValue(path, null);
            return;
        }
        Object[] objectArray = new Object[value.length];
        for (int i = 0; i < value.length; i++) {
            objectArray[i] = (long) value[i]; // Store as Long for consistency
                                              // with MessagePack
        }
        putValue(path, objectArray);
    }

    public synchronized void putStringArray(String path, String... value) {
        if (value == null) {
            putValue(path, null);
            return;
        }
        Object[] objectArray = new Object[value.length];
        for (int i = 0; i < value.length; i++) {
            objectArray[i] = value[i]; // Already String
        }
        putValue(path, objectArray);
    }

    public synchronized void putObjectArray(String path, Object[] value) {
          putValue(path, value);
    }

    public synchronized Optional<Boolean> getBoolean(String path) {
        return getValue(path).map(i -> (Boolean) i);
    }

    public synchronized Optional<Integer> getInt(String path) {
        return getValue(path).map(value -> {
            if (!(value instanceof Long)) {
                throw new ClassCastException("Value at path '" + path + "' is not an integer, it's a " + value.getClass().getSimpleName());
            }
            Long longValue = (Long) value;
            if (longValue < Integer.MIN_VALUE || longValue > Integer.MAX_VALUE) {
                throw new ClassCastException("Value at path '" + path + "' (" + longValue + ") does not fit in an Integer");
            }
            return longValue.intValue();
        });
    }

    public synchronized Optional<Long> getLong(String path) {
        return getValue(path).map(i -> (Long) i);
    }

    public synchronized Optional<Short> getShort(String path) {
        return getValue(path).map(value -> {
            if (!(value instanceof Long)) {
                throw new ClassCastException("Value at path '" + path + "' is not an integer, it's a " + value.getClass().getSimpleName());
            }
            Long longValue = (Long) value;
            if (longValue < Short.MIN_VALUE || longValue > Short.MAX_VALUE) {
                throw new ClassCastException("Value at path '" + path + "' (" + longValue + ") does not fit in a Short");
            }
            return longValue.shortValue();
        });
    }

    public synchronized Optional<Float> getFloat(String path) {
        return getValue(path).map(i -> (float)(double)(Double) i);
    }

    public synchronized Optional<Double> getDouble(String path) {
        return getValue(path).map(i -> (Double) i);
    }

    public synchronized Optional<Date> getDate(String path) {
        return getValue(path).map(i -> (Date) i);
    }

    public synchronized Optional<byte[]> getByteArray(String path) {
        return getValue(path).map(i -> (byte[]) i);
    }

    public synchronized Optional<int[]> getIntArray(String path) {
        return getValue(path).map(value -> {
            if (!(value instanceof Object[])) {
                throw new ClassCastException("Value at path '" + path + "' is not an array, it's a " + value.getClass().getSimpleName());
            }
            
            Object[] objectArray = (Object[]) value;
            int[] intArray = new int[objectArray.length];
            
            for (int i = 0; i < objectArray.length; i++) {
                if (!(objectArray[i] instanceof Long)) {
                    throw new ClassCastException("Array element at index " + i + " is not an integer, it's a " + objectArray[i].getClass().getSimpleName());
                }
                Long longValue = (Long) objectArray[i];
                if (longValue < Integer.MIN_VALUE || longValue > Integer.MAX_VALUE) {
                    throw new ClassCastException("Array element at index " + i + " (" + longValue + ") does not fit in an Integer");
                }
                intArray[i] = longValue.intValue();
            }
            
            return intArray;
        });
    }

    public synchronized Optional<long[]> getLongArray(String path) {
        return getValue(path).map(value -> {
            if (!(value instanceof Object[])) {
                throw new ClassCastException("Value at path '" + path + "' is not an array, it's a " + value.getClass().getSimpleName());
            }
            
            Object[] objectArray = (Object[]) value;
            long[] longArray = new long[objectArray.length];
            
            for (int i = 0; i < objectArray.length; i++) {
                if (!(objectArray[i] instanceof Long)) {
                    throw new ClassCastException("Array element at index " + i + " is not an integer, it's a " + objectArray[i].getClass().getSimpleName());
                }
                longArray[i] = (Long) objectArray[i];
            }
            
            return longArray;
        });
    }

    public synchronized Optional<short[]> getShortArray(String path) {
        return getValue(path).map(value -> {
            if (!(value instanceof Object[])) {
                throw new ClassCastException("Value at path '" + path + "' is not an array, it's a " + value.getClass().getSimpleName());
            }
            
            Object[] objectArray = (Object[]) value;
            short[] shortArray = new short[objectArray.length];
            
            for (int i = 0; i < objectArray.length; i++) {
                if (!(objectArray[i] instanceof Long)) {
                    throw new ClassCastException("Array element at index " + i + " is not an integer, it's a " + objectArray[i].getClass().getSimpleName());
                }
                Long longValue = (Long) objectArray[i];
                if (longValue < Short.MIN_VALUE || longValue > Short.MAX_VALUE) {
                    throw new ClassCastException("Array element at index " + i + " (" + longValue + ") does not fit in a Short");
                }
                shortArray[i] = longValue.shortValue();
            }
            
            return shortArray;
        });
    }

    public synchronized Optional<double[]> getDoubleArray(String path) {
        return getValue(path).map(value -> {
            if (!(value instanceof Object[])) {
                throw new ClassCastException("Value at path '" + path + "' is not an array, it's a " + value.getClass().getSimpleName());
            }
            
            Object[] objectArray = (Object[]) value;
            double[] doubleArray = new double[objectArray.length];
            
            for (int i = 0; i < objectArray.length; i++) {
                if (!(objectArray[i] instanceof Double)) {
                    throw new ClassCastException("Array element at index " + i + " is not a float, it's a " + objectArray[i].getClass().getSimpleName());
                }
                doubleArray[i] = ((Double) objectArray[i]).doubleValue();
            }
            
            return doubleArray;
        });
    }

    public synchronized Optional<String[]> getStringArray(String path) {
        return getValue(path).map(value -> {
            if (!(value instanceof Object[])) {
                throw new ClassCastException("Value at path '" + path + "' is not an array, it's a " + value.getClass().getSimpleName());
            }
            
            Object[] objectArray = (Object[]) value;
            String[] stringArray = new String[objectArray.length];
            
            for (int i = 0; i < objectArray.length; i++) {
                if (objectArray[i] != null && !(objectArray[i] instanceof String)) {
                    throw new ClassCastException("Array element at index " + i + " is not a string, it's a " + objectArray[i].getClass().getSimpleName());
                }
                stringArray[i] = (String) objectArray[i];
            }
            
            return stringArray;
        });
    }

    public synchronized Optional<Object[]> getObjectArray(String path) {
        return getValue(path).map(i -> (Object[]) i);
    }

    private void putValue(String path, Object value) {
        assertLeafPath(path);
        
        String[] parts = parsePath(path);
        Map<String, Object> parent = navigateToParentCreatingAsNeeded(path);
        parent.put(parts[parts.length - 1], value);
    }

    public Optional<Object> getValue(String path) {
        assertLeafPath(path);
        
        try {
            String[] parts = parsePath(path);
            if (parts.length == 0) {
                return Optional.empty();
            }
            
            Map<String, Object> parent = navigateToParent(path);
            Object value = parent.get(parts[parts.length - 1]);
            if (value instanceof Map) {
                throw new BranchAsALeafException("Trying to get value from a branch at: " + path);
            }
            return Optional.ofNullable(value);
        } catch (NonExistantPathException ex) {
            return Optional.empty();
        }
    }
    
    public synchronized List<String> list(String path) {
        String[] parts = parsePath(path);
        Map<String, Object> current = root;

        // Navigate to target directory
        for (String part : parts) {
            Object next = current.get(part);
            if (next == null) {
                throw new MMLPathException("path does not exist: \""+path+"\"");
            }
            if (!(next instanceof Map)) {
                throw new LeafAsABranchException("Came across a leaf \"" + part + "\" in " + path);
            }
            current = (Map<String, Object>) next;
        }

        return current.entrySet()
                .stream()
                .map(e -> e.getKey())
                .toList();
    }

    public synchronized boolean isAValue(String path) {
        if (path == null) {
            return false;
        }
        
        String[] parts = parsePath(path);
        Map<String, Object> current = root;
        
        // Navigate to parent
        for (int i = 0; i < parts.length - 1; i++) {
            Object next = current.get(parts[i]);
            if (!(next instanceof Map)) {
                return false;
            }
            current = (Map<String, Object>) next;
        }
        
        return current.containsKey(parts[parts.length - 1]) && !(current.get(parts[parts.length - 1]) instanceof Map);
    }
    
    public synchronized boolean isADirectory(String path) {
        if (path == null) {
            return false;
        }
        if (path.equals("/")) {
            return true;
        }
        
        String[] parts = parsePath(path);
        Map<String, Object> current = root;
        
        // Navigate to target
        for (String part : parts) {
            if (part.isEmpty()) continue;
            Object next = current.get(part);
            if (!(next instanceof Map)) {
                return false;
            }
            current = (Map<String, Object>) next;
        }
        
        return true;
    }
    
    public synchronized boolean remove(String path) {
        if (path == null) {
            return false;
        }
        assertLeafPath(path);
        
        try {
            String[] parts = parsePath(path);
            if (parts.length == 0) {
                return false;
            }
            
            Map<String, Object> parent = navigateToParent(path);
            String key = parts[parts.length - 1];
            
            // Check if the key exists and is not a Map (directory)
            Object value = parent.get(key);
            if (value == null) {
                return false; // Key doesn't exist
            }
            if (value instanceof Map) {
                return false; // Can't remove a directory with remove(), use removeDir()
            }
            
            parent.remove(key);
            return true;
        } catch (NonExistantPathException ex) {
            return false;
        }
    }

    public synchronized boolean removeDir(String path) {
        if (path == null) {
            return false;
        }
        if (path.equals("/")) {
            return false;
        }
        
        // Only accept branch paths (ending with /)
        assertIsBranchPath(path);
        
        try {
            Map<String, Object> parent = navigateToParent(path);
            
            String[] parts = parsePath(path);
            String keyToRemove = parts[parts.length-1];
            
            if (!parent.containsKey(keyToRemove)) {
                return false; // Directory doesn't exist
            }
            
            parent.remove(keyToRemove);
            return true;
        } catch (NonExistantPathException | MMLPathException ex) {
            return false;
        }
    }
    
    public byte[] toBytes() throws IOException {
        try (MessageBufferPacker packer = MessagePack.newDefaultBufferPacker()) {
            packMap(packer, root);
            return packer.toByteArray();
        }
    }
    
    static String[] parsePath(String path) {
        if (path == null)
            throw new NullPointerException();
        if (path.length() < 1)
            throw new MMLPathException("Path is too short: " + path);
        if (path.charAt(0) != '/')
            throw new NoStartingSlashException(path);
        if (path.indexOf("//") != -1)
            throw new DoubleSlashException(path);
        if (path.equals("/")) 
            return new String[]{};

        return (path.endsWith("/") ? path.substring(1, path.length() - 1) : path.substring(1) ).split("/");
    }
    
    private void assertLeafPath(String path) {
        if (path == null)
            throw new NullPointerException();
        if (path.isEmpty())
            throw new MMLPathException("Path is too short: " + path);
        if (!path.startsWith("/"))
            throw new NoStartingSlashException(path);
        if (path.charAt(path.length()-1) == '/')
            throw new BranchAsALeafException("\"" + path + "\" is not a leaf path, it's a branch,.");
    }
    
    private void packMap(MessagePacker packer, Map<String, Object> map) throws IOException {
        packer.packMapHeader(map.size());
        for (Map.Entry<String, Object> entry : map.entrySet()) {
            packer.packString(entry.getKey());
            packValue(packer, entry.getValue());
        }
    }
    
    private void packValue(MessagePacker packer, Object value) throws IOException {
        if (value instanceof String) {
            packer.packString((String) value);
        } else if (value instanceof Map) {
            packMap(packer, (Map<String, Object>) value);
        } else if (value instanceof Integer) {
            packer.packInt((Integer) value);
        } else if (value instanceof Long) {
            packer.packLong((Long) value);
        } else if (value instanceof Short) {
            packer.packShort((Short) value);
        } else if (value instanceof Boolean) {
            packer.packBoolean((Boolean) value);
        } else if (value instanceof Float) {
            packer.packFloat((Float) value);
        } else if (value instanceof Double) {
            packer.packDouble((Double) value);
        } else if (value instanceof Date) {
            packer.packLong(((Date) value).getTime());
        } else if (value instanceof byte[]) {
            byte[] bytes = (byte[]) value;
            packer.packBinaryHeader(bytes.length);
            packer.writePayload(bytes);
        } else if (value instanceof int[]) {
            int[] array = (int[]) value;
            packer.packArrayHeader(array.length);
            for (int item : array) {
                packer.packInt(item);
            }
        } else if (value instanceof long[]) {
            long[] array = (long[]) value;
            packer.packArrayHeader(array.length);
            for (long item : array) {
                packer.packLong(item);
            }
        } else if (value instanceof short[]) {
            short[] array = (short[]) value;
            packer.packArrayHeader(array.length);
            for (short item : array) {
                packer.packShort(item);
            }
        } else if (value instanceof double[]) {
            double[] array = (double[]) value;
            packer.packArrayHeader(array.length);
            for (double item : array) {
                packer.packDouble(item);
            }
        } else if (value instanceof Object[]) {
            Object[] array = (Object[]) value;
            packer.packArrayHeader(array.length);
            for (Object item : array) {
                packValue(packer, item);
            }
        } else if (value == null) {
            packer.packNil();
        } else {
            // For unsupported types, pack as nil
            packer.packNil();
        }
    }
    
    private Map<String, Object> unpackMap(MessageUnpacker unpacker) throws IOException {
        Map<String, Object> result = new HashMap<>();
        int size = unpacker.unpackMapHeader();
        
        for (int i = 0; i < size; i++) {
            String key = unpacker.unpackString();
            ValueType valueType = unpacker.getNextFormat().getValueType();
            
            switch (valueType) {
                case MAP:
                    result.put(key, unpackMap(unpacker));
                    break;
                case STRING:
                    result.put(key, unpacker.unpackString());
                    break;
                case INTEGER:
                    // Always unpack integers as Long - MessagePack handles optimal packing automatically
                    result.put(key, unpacker.unpackLong());
                    break;
                case BINARY:
                    int binarySize = unpacker.unpackBinaryHeader();
                    result.put(key, binarySize > 0 ? unpacker.readPayload(binarySize) : new byte[0]);
                    break;
                case ARRAY:
                    result.put(key, unpackArray(unpacker));
                    break;
                case BOOLEAN:
                    result.put(key, unpacker.unpackBoolean());
                    break;
                case FLOAT:
                    result.put(key, unpacker.unpackDouble());
                    break;
                case NIL:
                    unpacker.unpackNil();
                    result.put(key, null);
                    break;
                default:
                    // For unsupported types, skip the value
                    unpacker.skipValue();
                    break;
            }
        }
        
        return result;
    }
    
    private Object unpackArray(MessageUnpacker unpacker) throws IOException {
        int arraySize = unpacker.unpackArrayHeader();
        if (arraySize == 0) {
            return new Object[0];
        }
        
        // For arrays, we'll always return Object[] and let the specific getters handle conversion
        // This is because MessagePack doesn't preserve the original array type information
        Object[] objectArray = new Object[arraySize];
        
        for (int i = 0; i < arraySize; i++) {
            ValueType elementType = unpacker.getNextFormat().getValueType();
            switch (elementType) {
                case STRING:
                    objectArray[i] = unpacker.unpackString();
                    break;
                case INTEGER:
                    // Always store integers as Long for consistency
                    objectArray[i] = unpacker.unpackLong();
                    break;
                case BINARY:
                    int binarySize = unpacker.unpackBinaryHeader();
                    objectArray[i] = unpacker.readPayload(binarySize);
                    break;
                case BOOLEAN:
                    objectArray[i] = unpacker.unpackBoolean();
                    break;
                case FLOAT:
                    objectArray[i] = unpacker.unpackDouble();
                    break;
                case NIL:
                    unpacker.unpackNil();
                    objectArray[i] = null;
                    break;
                case MAP:
                    objectArray[i] = unpackMap(unpacker);
                    break;
                case ARRAY:
                    objectArray[i] = unpackArray(unpacker);
                    break;
                default:
                    unpacker.skipValue();
                    objectArray[i] = null;
                    break;
            }
        }
        
        return objectArray;
    }
}
