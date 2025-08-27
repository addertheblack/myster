package com.myster.mml;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Optional;
import java.util.logging.Logger;

/**
 * A robust version of MessagePackSerializer that handles errors gracefully and
 * never throws runtime exceptions that are caused by a disagreement between
 * expected data and actual data. Designed for consuming potentially corrupt
 * data from external sources. So if you getInt() and the value is a string or a
 * branch in the data, you will just get a empty optional and not a
 * BranchAsLeafException or ClassCastException. Uses composition to wrap a
 * MessagePackSerializer instance.
 */
class RobustMessagePackSerializer implements com.myster.mml.MessagePack {
    private static final Logger LOGGER = Logger.getLogger(RobustMessagePackSerializer.class.getName());
    private final MessagePackSerializer delegate;
    private boolean trace;

    RobustMessagePackSerializer() {
        this.delegate = new MessagePackSerializer();
    }

    public RobustMessagePackSerializer(byte[] data) throws IOException {
        this.delegate = new MessagePackSerializer(data);
    }

    public boolean isTrace() {
        return trace;
    }

    public void setTrace(boolean trace) {
        this.trace = trace;
    }

    @Override
    public synchronized void put(String path, String value) {
        clearAPath(path);
        delegate.put(path, value);
    }

    // Getter methods with robust error handling
    @Override
    public synchronized Optional<Boolean> getBoolean(String path) {
        try {
            return delegate.getBoolean(path);
        } catch (ClassCastException | BranchAsALeafException | LeafAsABranchException ex) {
            if (trace) {
                LOGGER.fine("Failed to get boolean at " + path + ": " + ex.getMessage());
                ex.printStackTrace();
            }
            return Optional.empty();
        }
    }

    @Override
    public synchronized Optional<Integer> getInt(String path) {
        try {
            return delegate.getInt(path);
        } catch (ClassCastException | BranchAsALeafException | LeafAsABranchException ex) {
            if (trace) {
                LOGGER.fine("Failed to get int at " + path + ": " + ex.getMessage());
                ex.printStackTrace();
            }
            return Optional.empty();
        }
    }

    @Override
    public synchronized Optional<Long> getLong(String path) {
        try {
            return delegate.getLong(path);
        } catch (ClassCastException | BranchAsALeafException | LeafAsABranchException ex) {
            if (trace) {
                LOGGER.fine("Failed to get long at " + path + ": " + ex.getMessage());
                ex.printStackTrace();
            }
            return Optional.empty();
        }
    }

    @Override
    public synchronized Optional<Short> getShort(String path) {
        try {
            return delegate.getShort(path);
        } catch (ClassCastException | BranchAsALeafException | LeafAsABranchException ex) {
            if (trace) {
                LOGGER.fine("Failed to get short at " + path + ": " + ex.getMessage());
                ex.printStackTrace();
            }
            return Optional.empty();
        }
    }

    @Override
    public synchronized Optional<Float> getFloat(String path) {
        try {
            return delegate.getFloat(path);
        } catch (ClassCastException | BranchAsALeafException | LeafAsABranchException ex) {
            if (trace) {
                LOGGER.fine("Failed to get float at " + path + ": " + ex.getMessage());
                ex.printStackTrace();
            }
            return Optional.empty();
        }
    }

    @Override
    public synchronized Optional<Double> getDouble(String path) {
        try {
            return delegate.getDouble(path);
        } catch (ClassCastException | BranchAsALeafException | LeafAsABranchException ex) {
            if (trace) {
                LOGGER.fine("Failed to get double at " + path + ": " + ex.getMessage());
                ex.printStackTrace();
            }
            return Optional.empty();
        }
    }

    @Override
    public synchronized Optional<Date> getDate(String path) {
        try {
            return delegate.getDate(path);
        } catch (ClassCastException | BranchAsALeafException | LeafAsABranchException ex) {
            if (trace) {
                LOGGER.fine("Failed to get date at " + path + ": " + ex.getMessage());
                ex.printStackTrace();
            }
            return Optional.empty();
        }
    }

    @Override
    public synchronized Optional<byte[]> getByteArray(String path) {
        try {
            return delegate.getByteArray(path);
        } catch (ClassCastException | BranchAsALeafException | LeafAsABranchException ex) {
            if (trace) {
                LOGGER.fine("Failed to get byte array at " + path + ": " + ex.getMessage());
                ex.printStackTrace();
            }
            return Optional.empty();
        }
    }

    @Override
    public synchronized Optional<int[]> getIntArray(String path) {
        try {
            return delegate.getIntArray(path);
        } catch (ClassCastException | BranchAsALeafException | LeafAsABranchException ex) {
            if (trace) {
                LOGGER.fine("Failed to get int array at " + path + ": " + ex.getMessage());
                ex.printStackTrace();
            }
            return Optional.empty();
        }
    }

    @Override
    public synchronized Optional<long[]> getLongArray(String path) {
        try {
            return delegate.getLongArray(path);
        } catch (ClassCastException | BranchAsALeafException | LeafAsABranchException ex) {
            if (trace) {
                LOGGER.fine("Failed to get long array at " + path + ": " + ex.getMessage());
                ex.printStackTrace();
            }
            return Optional.empty();
        }
    }

    @Override
    public synchronized Optional<short[]> getShortArray(String path) {
        try {
            return delegate.getShortArray(path);
        } catch (ClassCastException | BranchAsALeafException | LeafAsABranchException ex) {
            if (trace) {
                LOGGER.fine("Failed to get short array at " + path + ": " + ex.getMessage());
                ex.printStackTrace();
            }
            return Optional.empty();
        }
    }

    @Override
    public synchronized Optional<double[]> getDoubleArray(String path) {
        try {
            return delegate.getDoubleArray(path);
        } catch (ClassCastException | BranchAsALeafException | LeafAsABranchException ex) {
            if (trace) {
                LOGGER.fine("Failed to get double array at " + path + ": " + ex.getMessage());
                ex.printStackTrace();
            }
            return Optional.empty();
        }
    }

    @Override
    public synchronized Optional<Object[]> getObjectArray(String path) {
        try {
            return delegate.getObjectArray(path);
        } catch (ClassCastException | BranchAsALeafException | LeafAsABranchException ex) {
            if (trace) {
                LOGGER.fine("Failed to get object array at " + path + ": " + ex.getMessage());
                ex.printStackTrace();
            }
            return Optional.empty();
        }
    }

    @Override
    public synchronized Optional<String> get(String path) {
        try {
            return delegate.get(path);
        } catch (ClassCastException | BranchAsALeafException | LeafAsABranchException ex) {
            if (trace) {
                LOGGER.fine("Failed to get value at " + path + ": " + ex.getMessage());
                ex.printStackTrace();
            }
            return Optional.empty();
        }
    }

    @Override
    public synchronized void putBoolean(String path, boolean value) {
        clearAPath(path);

        delegate.putBoolean(path, value);
    }

    @Override
    public synchronized void putInt(String path, int value) {
        clearAPath(path);

        delegate.putInt(path, value);
    }

    @Override
    public synchronized void putLong(String path, long value) {
        clearAPath(path);

        delegate.putLong(path, value);
    }

    @Override
    public synchronized void putShort(String path, short value) {
        clearAPath(path);

        delegate.putShort(path, value);
    }

    @Override
    public synchronized void putFloat(String path, float value) {
        clearAPath(path);

        delegate.putFloat(path, value);
    }

    @Override
    public synchronized void putDouble(String path, double value) {
        clearAPath(path);

        delegate.putDouble(path, value);
    }

    @Override
    public synchronized void putDate(String path, Date value) {
        clearAPath(path);

        delegate.putDate(path, value);
    }

    @Override
    public synchronized void putByteArray(String path, byte[] value) {
        clearAPath(path);

        delegate.putByteArray(path, value);
    }

    @Override
    public synchronized void putIntArray(String path, int[] value) {
        clearAPath(path);

        delegate.putIntArray(path, value);
    }

    @Override
    public synchronized void putLongArray(String path, long[] value) {
        clearAPath(path);

        delegate.putLongArray(path, value);
    }

    @Override
    public synchronized void putDoubleArray(String path, double[] value) {
        clearAPath(path);

        delegate.putDoubleArray(path, value);
    }

    @Override
    public synchronized void putShortArray(String path, short[] value) {
        clearAPath(path);

        delegate.putShortArray(path, value);
    }

    @Override
    public synchronized void putObjectArray(String path, Object[] value) {
        clearAPath(path);

        delegate.putObjectArray(path, value);
    }

    private void clearAPath(String path) {
        if (path.equals("/")) {
            return;
        }

        if (delegate.isADirectory(path)) {
            delegate.removeDir(path + (path.endsWith("/") ? "" : "/"));
        }

        String[] parts = MessagePackSerializer.parsePath(path);
        
        String pathToCheck = "";
        for (int i = 0; i < parts.length; i++) {
            pathToCheck = "/" + parts[i];

            if (delegate.isAValue(pathToCheck)) {
                // This parent path is a value - remove it so we can create
                // directories
                delegate.remove(pathToCheck);
            }
        }
    }

    @Override
    public synchronized List<String> list(String path) {
        try {
            return delegate.list(path);
        } catch (ClassCastException | BranchAsALeafException | LeafAsABranchException ex) {
            if (trace) {
                LOGGER.fine("Failed to list path " + path + ": " + ex.getMessage());
                ex.printStackTrace();
            }
            return new ArrayList<>();
        }
    }

    @Override
    public synchronized boolean isAValue(String path) {
        return delegate.isAValue(path);
    }

    @Override
    public synchronized boolean isADirectory(String path) {
        return delegate.isADirectory(path);
    }

    @Override
    public synchronized boolean remove(String path) {
        try {
            return delegate.remove(path);
        } catch (BranchAsALeafException | LeafAsABranchException ex) {
            if (trace) {
                LOGGER.fine("Failed to remove value at " + path + ": " + ex.getMessage());
                ex.printStackTrace();
            }
            return false;
        }
    }

    @Override
    public synchronized boolean removeDir(String path) {
        try {
            return delegate.removeDir(path);
        } catch (BranchAsALeafException | LeafAsABranchException ex) {
            if (trace) {
                LOGGER.fine("Failed to remove directory at " + path + ": " + ex.getMessage());
                ex.printStackTrace();
            }
            return false;
        }
    }

    @Override
    public byte[] toBytes() throws IOException {
        try {
            return delegate.toBytes();
        } catch (BranchAsALeafException | LeafAsABranchException ex) {
            if (trace) {
                LOGGER.fine("Failed to serialize to bytes: " + ex.getMessage());
                ex.printStackTrace();
            }
            throw new IOException("Failed to serialize data", ex);
        }
    }

    /**
     * Get access to the underlying delegate for advanced operations.
     * Use with caution as operations on the delegate may throw exceptions.
     */
    public MessagePackSerializer getDelegate() {
        return delegate;
    }
}
