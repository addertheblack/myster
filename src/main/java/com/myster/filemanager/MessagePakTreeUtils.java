package com.myster.filemanager;

import java.util.Date;
import java.util.List;

import com.myster.mml.MessagePak;

final class MessagePakTreeUtils {
    private MessagePakTreeUtils() {
    }

    static MessagePak copyAllowedKeys(MessagePak source, MetadataType metadataType) {
        MessagePak copy = MessagePak.newEmpty();
        copyAllowedKeys(source, copy, metadataType.cacheableKeys());
        return copy;
    }

    static void copyAllowedKeys(MessagePak source, MessagePak destination, MetadataType metadataType) {
        copyAllowedKeys(source, destination, metadataType.cacheableKeys());
    }

    static void copyDirectory(MessagePak source,
                              String sourceDirectory,
                              MessagePak destination,
                              String destinationDirectory) {
        if (!source.isADirectory(sourceDirectory)) {
            return;
        }

        for (String child : source.list(sourceDirectory)) {
            String sourcePath = childPath(sourceDirectory, child);
            String destinationPath = childPath(destinationDirectory, child);
            copyPath(source, sourcePath, destination, destinationPath);
        }
    }

    static boolean isEmpty(MessagePak messagePak) {
        return messagePak.list("/").isEmpty();
    }

    private static void copyAllowedKeys(MessagePak source,
                                        MessagePak destination,
                                        List<String> allowedKeys) {
        for (String key : allowedKeys) {
            copyPath(source, key, destination, key);
        }
    }

    private static void copyPath(MessagePak source,
                                 String sourcePath,
                                 MessagePak destination,
                                 String destinationPath) {
        if (source.isAValue(sourcePath)) {
            source.getValue(sourcePath).ifPresent(value -> putValue(destination, destinationPath, value));
        } else if (source.isADirectory(sourcePath)) {
            copyDirectory(source, sourcePath, destination, destinationPath);
        }
    }

    private static String childPath(String parent, String child) {
        if (parent.equals("/")) {
            return "/" + child;
        }

        return (parent.endsWith("/") ? parent : parent + "/") + child;
    }

    private static void putValue(MessagePak destination, String path, Object value) {
        switch (value) {
            case String string -> destination.putString(path, string);
            case Boolean bool -> destination.putBoolean(path, bool);
            case Integer integer -> destination.putInt(path, integer);
            case Long longValue -> destination.putLong(path, longValue);
            case Short shortValue -> destination.putShort(path, shortValue);
            case Float floatValue -> destination.putFloat(path, floatValue);
            case Double doubleValue -> destination.putDouble(path, doubleValue);
            case Date date -> destination.putDate(path, date);
            case byte[] bytes -> destination.putByteArray(path, bytes.clone());
            case int[] ints -> destination.putIntArray(path, ints.clone());
            case long[] longs -> destination.putLongArray(path, longs.clone());
            case double[] doubles -> destination.putDoubleArray(path, doubles.clone());
            case short[] shorts -> destination.putShortArray(path, shorts.clone());
            case String[] strings -> destination.putStringArray(path, strings.clone());
            case Object[] objects -> destination.putObjectArray(path, objects.clone());
            default -> {
                // Ignore unsupported values rather than making cache corruption fatal.
            }
        }
    }
}
