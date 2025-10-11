package com.myster.mml;

import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.Optional;

import com.general.util.Util;

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
    
    Optional<Object> getValue(String path);
    
    /**
     * @return a string representation of whatever is in that path
     */
    default Optional<String> getToString(String path) {
        return getValue(path).map(i -> convertToString(i));
    }

    private static String convertToString(Object i) {
        return switch (i) {
            case String s -> s;
            case Long l -> Long.toString(l);
            case Double d -> Double.toString(d);
            case Integer n -> Integer.toString(n);
            case Short s -> Short.toString(s);
            case Float f -> Float.toString(f);
            case Boolean b -> Boolean.toString(b);
            case Date date -> new SimpleDateFormat().format(date);
            case byte[] bytes -> Util.asHex(bytes);
            case Object[] array -> "["
                    + String.join(", ", Util.map(Arrays.asList(array), e -> convertToString(e)))
                    + "]";
            default -> "?";
        };
    }
    
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