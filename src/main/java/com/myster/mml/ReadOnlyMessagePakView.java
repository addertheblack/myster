package com.myster.mml;

import java.io.IOException;
import java.util.Arrays;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Live, protected read access to a MessagePak. Delegates type/error handling and display
 * conversion to its owner, while detaching mutable return values. Only supported MessagePak
 * value trees are accepted: arbitrary objects and cyclic object graphs are outside the contract.
 * No independent thread-safety guarantee is added to the owner's contract.
 */
final class ReadOnlyMessagePakView implements ReadOnlyMessagePak {
    private final MessagePak delegate;

    ReadOnlyMessagePakView(MessagePak delegate) {
        this.delegate = delegate;
    }

    @Override
    public Optional<String> getString(String path) {
        return delegate.getString(path);
    }

    @Override
    public Optional<Boolean> getBoolean(String path) {
        return delegate.getBoolean(path);
    }

    @Override
    public Optional<Integer> getInt(String path) {
        return delegate.getInt(path);
    }

    @Override
    public Optional<Long> getLong(String path) {
        return delegate.getLong(path);
    }

    @Override
    public Optional<Short> getShort(String path) {
        return delegate.getShort(path);
    }

    @Override
    public Optional<Float> getFloat(String path) {
        return delegate.getFloat(path);
    }

    @Override
    public Optional<Double> getDouble(String path) {
        return delegate.getDouble(path);
    }

    @Override
    public Optional<Date> getDate(String path) {
        return delegate.getDate(path).map(value -> new Date(value.getTime()));
    }

    @Override
    public Optional<Object> getValue(String path) {
        return delegate.getValue(path).map(ReadOnlyMessagePakView::copyValue);
    }

    @Override
    public Optional<String> get(String path) {
        return delegate.get(path);
    }

    @Override
    public Optional<byte[]> getByteArray(String path) {
        return delegate.getByteArray(path).map(byte[]::clone);
    }

    @Override
    public Optional<int[]> getIntArray(String path) {
        return delegate.getIntArray(path).map(int[]::clone);
    }

    @Override
    public Optional<long[]> getLongArray(String path) {
        return delegate.getLongArray(path).map(long[]::clone);
    }

    @Override
    public Optional<double[]> getDoubleArray(String path) {
        return delegate.getDoubleArray(path).map(double[]::clone);
    }

    @Override
    public Optional<short[]> getShortArray(String path) {
        return delegate.getShortArray(path).map(short[]::clone);
    }

    @Override
    public Optional<String[]> getStringArray(String path) {
        return delegate.getStringArray(path).map(String[]::clone);
    }

    @Override
    public Optional<Object[]> getObjectArray(String path) {
        return delegate.getObjectArray(path).map(value -> (Object[]) copyValue(value));
    }

    @Override
    public List<String> list(String path) {
        return List.copyOf(delegate.list(path));
    }

    @Override
    public boolean isAValue(String path) {
        return delegate.isAValue(path);
    }

    @Override
    public boolean isADirectory(String path) {
        return delegate.isADirectory(path);
    }

    @Override
    public byte[] toBytes() throws IOException {
        return delegate.toBytes().clone();
    }

    /**
     * Detaches every mutable descendant, including maps inside decoded object arrays.
     * Unknown object types are rejected rather than accidentally exposing a mutable reference.
     *
     * @throws IllegalArgumentException if a value is outside the supported MessagePak tree types
     */
    private static Object copyValue(Object value) {
        return switch (value) {
            case null -> null;
            case String _, Boolean _, Long _, Integer _, Short _, Float _, Double _ -> value;
            case Date date -> new Date(date.getTime());
            case byte[] array -> array.clone();
            case int[] array -> array.clone();
            case long[] array -> array.clone();
            case double[] array -> array.clone();
            case short[] array -> array.clone();
            case Object[] array -> {
                Object[] copy = array.clone();
                for (int i = 0; i < copy.length; i++) {
                    Object detached = copyValue(array[i]);
                    // A detached Date/map need not share the original concrete subclass.
                    if (detached != null && !copy.getClass().getComponentType().isInstance(detached)) {
                        copy = Arrays.copyOf(copy, copy.length, Object[].class);
                    }
                    copy[i] = detached;
                }
                yield copy;
            }
            case Map<?, ?> map -> {
                Map<String, Object> copy = new HashMap<>();
                for (var entry : map.entrySet()) {
                    if (!(entry.getKey() instanceof String key)) {
                        throw new IllegalArgumentException("MessagePak map keys must be strings");
                    }
                    copy.put(key, copyValue(entry.getValue()));
                }
                yield copy;
            }
            default -> throw new IllegalArgumentException(
                    "Unsupported MessagePak value type: " + value.getClass().getName());
        };
    }
}
