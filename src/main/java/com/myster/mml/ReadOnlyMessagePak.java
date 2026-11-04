package com.myster.mml;

import java.io.IOException;
import java.util.Date;
import java.util.List;
import java.util.Optional;

/**
 * Read capability for MessagePak trees, with the same paths, types and error semantics as
 * {@link MessagePak}. Numeric getters do not parse strings. Missing values return empty
 * Optionals; type/branch mismatches may throw for a strict writer but return empty for a
 * robust decoded reader. Integer/short writes are stored as longs, and floats as doubles.
 *
 * <p>This interface alone does not guarantee immutability: a MessagePak also implements it.
 * Use {@link MessagePak#asReadOnly()} when passing data across a boundary that must prevent
 * mutation, including mutation through returned dates, arrays and nested values.
 * A protected view is live and inherits its owner's threading requirements; it is not a snapshot.
 */
public interface ReadOnlyMessagePak {
    Optional<String> getString(String path);

    Optional<Boolean> getBoolean(String path);
    Optional<Integer> getInt(String path);
    Optional<Long> getLong(String path);
    Optional<Short> getShort(String path);
    Optional<Float> getFloat(String path);
    Optional<Double> getDouble(String path);

    /**
     * Reads a stored Date. Date serialization currently produces an epoch-millis long,
     * so decoding does not restore a Date; use getLong for published timestamp fields.
     */
    Optional<Date> getDate(String path);

    /**
     * Reads a leaf value in its stored type. Protected views recursively detach dates, arrays
     * and maps nested in arrays; null array elements are preserved. Supported trees contain
     * strings, boxed numeric/boolean values, dates, byte/int/long/short/double arrays,
     * object arrays and string-keyed maps. Cyclic arbitrary Java graphs are unsupported.
     *
     * @throws IllegalArgumentException for unsupported value forms read through a protected view
     */
    Optional<Object> getValue(String path);

    /** Returns the existing MessagePak display conversion of any value at the path. */
    Optional<String> get(String path);

    Optional<byte[]> getByteArray(String path);
    Optional<int[]> getIntArray(String path);
    Optional<long[]> getLongArray(String path);
    Optional<double[]> getDoubleArray(String path);
    Optional<short[]> getShortArray(String path);
    Optional<String[]> getStringArray(String path);
    /**
     * Reads an object array, applying the same protected-view copy policy as {@link #getValue}.
     *
     * @throws IllegalArgumentException for unsupported value forms read through a protected view
     */
    Optional<Object[]> getObjectArray(String path);

    List<String> list(String path);
    boolean isAValue(String path);
    boolean isADirectory(String path);

    byte[] toBytes() throws IOException;
}
