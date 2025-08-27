package com.myster.mml;

import java.io.IOException;
import java.util.Date;
import java.util.List;
import java.util.Optional;

/**
 * Interface for MessagePack-based serialization utilities that provide an interface similar to MML
 * for easier migration. Implementations use a more efficient binary format while maintaining
 * a similar tree-like structure for data storage.
 */
public interface MessagePack {
    
    public static MessagePack newEmpty() {
        return new MessagePackSerializer();
    }
    
    public static MessagePack fromBytes(byte[] b) throws IOException {
        return new RobustMessagePackSerializer(b);
    }
    
    // String operations
    void put(String path, String value);
    Optional<String> get(String path);
    
    // Primitive types
    void putBoolean(String path, boolean value);
    void putInt(String path, int value);
    void putLong(String path, long value);
    void putShort(String path, short value);
    void putFloat(String path, float value);
    void putDouble(String path, double value);
    void putDate(String path, Date value);
    
    Optional<Boolean> getBoolean(String path);
    Optional<Integer> getInt(String path);
    Optional<Long> getLong(String path);
    Optional<Short> getShort(String path);
    Optional<Float> getFloat(String path);
    Optional<Double> getDouble(String path);
    Optional<Date> getDate(String path);
    
    // Array types
    void putByteArray(String path, byte[] value);
    void putIntArray(String path, int[] value);
    void putLongArray(String path, long[] value);
    void putDoubleArray(String path, double[] value);
    void putShortArray(String path, short[] value);
    void putObjectArray(String path, Object[] value);
    
    Optional<byte[]> getByteArray(String path);
    Optional<int[]> getIntArray(String path);
    Optional<long[]> getLongArray(String path);
    Optional<double[]> getDoubleArray(String path);
    Optional<short[]> getShortArray(String path);
    Optional<Object[]> getObjectArray(String path);
    
    // Directory operations
    List<String> list(String path);
    boolean isAValue(String path);
    boolean isADirectory(String path);
    
    // Removal operations
    boolean remove(String path);
    boolean removeDir(String path);
    
    // Serialization
    byte[] toBytes() throws IOException;
}