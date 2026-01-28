package com.myster.type.ui;

import java.util.ArrayList;
import java.util.List;

/**
 * Utility for normalizing file extension formats.
 *
 * <p>Accepts various input formats and normalizes to standard "ext1, ext2, ext3" format:
 * <ul>
 *   <li>"exe, avi, mp3" → "exe, avi, mp3" (already correct)</li>
 *   <li>"exe avi mp3" → "exe, avi, mp3" (space-separated)</li>
 *   <li>".exe .avi .mp3" → "exe, avi, mp3" (removes leading dots)</li>
 *   <li>"exe, .avi mp3 .tf" → "exe, avi, mp3, tf" (mixed format)</li>
 * </ul>
 */
public class ExtensionNormalizer {

    /**
     * Normalizes extension string to standard "ext1, ext2, ext3" format.
     *
     * @param input the input string with extensions in various formats
     * @return normalized comma-separated extension string, or empty string if input is null/empty
     */
    public static String normalize(String input) {
        if (input == null || input.trim().isEmpty()) {
            return "";
        }

        // Split by comma or whitespace
        String[] parts = input.split("[,\\s]+");
        StringBuilder normalized = new StringBuilder();

        for (String part : parts) {
            String cleaned = part.trim();

            // Remove leading dot if present
            if (cleaned.startsWith(".")) {
                cleaned = cleaned.substring(1);
            }

            // Only add non-empty extensions
            if (!cleaned.isEmpty()) {
                if (!normalized.isEmpty()) {
                    normalized.append(", ");
                }
                normalized.append(cleaned);
            }
        }

        return normalized.toString();
    }

    /**
     * Parses extensions from user input, handling various formats.
     * Returns a list of individual extensions.
     *
     * @param input the input string with extensions in various formats
     * @return list of individual extensions
     */
    public static List<String> parseToList(String input) {
        if (input == null || input.trim().isEmpty()) {
            return new ArrayList<>();
        }

        List<String> result = new ArrayList<>();
        String[] parts = input.split("[,\\s]+");

        for (String part : parts) {
            String cleaned = part.trim();

            // Remove leading dot if present
            if (cleaned.startsWith(".")) {
                cleaned = cleaned.substring(1);
            }

            // Only add non-empty extensions
            if (!cleaned.isEmpty()) {
                result.add(cleaned);
            }
        }

        return result;
    }
}

