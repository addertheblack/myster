package com.myster.type.ui;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for extension normalization behavior.
 *
 * Tests the following scenarios:
 * - exe, avi, mp3          → "exe, avi, mp3"
 * - exe avi mp3            → "exe, avi, mp3"
 * - .exe .avi .mp3         → "exe, avi, mp3"
 * - exe, .avi mp3 .tf      → "exe, avi, mp3, tf"
 */
public class TestExtensionNormalization {

    /**
     * Test that comma-separated extensions remain unchanged.
     */
    @Test
    public void testAlreadyNormalizedExtensions() {
        String input = "exe, avi, mp3";
        String expected = "exe, avi, mp3";
        assertEquals(expected, ExtensionNormalizer.normalize(input));
    }

    /**
     * Test that space-separated extensions get normalized to comma-separated.
     */
    @Test
    public void testSpaceSeparatedExtensions() {
        String input = "exe avi mp3";
        String expected = "exe, avi, mp3";
        assertEquals(expected, ExtensionNormalizer.normalize(input));
    }

    /**
     * Test that leading dots are removed from extensions.
     */
    @Test
    public void testExtensionsWithLeadingDots() {
        String input = ".exe .avi .mp3";
        String expected = "exe, avi, mp3";
        assertEquals(expected, ExtensionNormalizer.normalize(input));
    }

    /**
     * Test mixed format with commas, spaces, and dots.
     */
    @Test
    public void testMixedFormatExtensions() {
        String input = "exe, .avi mp3 .tf";
        String expected = "exe, avi, mp3, tf";
        assertEquals(expected, ExtensionNormalizer.normalize(input));
    }

    /**
     * Test empty input.
     */
    @Test
    public void testEmptyInput() {
        String input = "";
        String expected = "";
        assertEquals(expected, ExtensionNormalizer.normalize(input));
    }

    /**
     * Test null input.
     */
    @Test
    public void testNullInput() {
        String input = null;
        String expected = "";
        assertEquals(expected, ExtensionNormalizer.normalize(input));
    }

    /**
     * Test whitespace-only input.
     */
    @Test
    public void testWhitespaceOnlyInput() {
        String input = "   ";
        String expected = "";
        assertEquals(expected, ExtensionNormalizer.normalize(input));
    }

    /**
     * Test single extension.
     */
    @Test
    public void testSingleExtension() {
        String input = "exe";
        String expected = "exe";
        assertEquals(expected, ExtensionNormalizer.normalize(input));
    }

    /**
     * Test single extension with leading dot.
     */
    @Test
    public void testSingleExtensionWithDot() {
        String input = ".exe";
        String expected = "exe";
        assertEquals(expected, ExtensionNormalizer.normalize(input));
    }

    /**
     * Test extensions with extra spaces.
     */
    @Test
    public void testExtensionsWithExtraSpaces() {
        String input = "exe,  avi  ,   mp3";
        String expected = "exe, avi, mp3";
        assertEquals(expected, ExtensionNormalizer.normalize(input));
    }

    /**
     * Test extensions with tabs.
     */
    @Test
    public void testExtensionsWithTabs() {
        String input = "exe\tavi\tmp3";
        String expected = "exe, avi, mp3";
        assertEquals(expected, ExtensionNormalizer.normalize(input));
    }

    /**
     * Test extensions with mixed separators (commas, spaces, tabs).
     */
    @Test
    public void testExtensionsWithMixedSeparators() {
        String input = "exe,\tavi mp3, .tf";
        String expected = "exe, avi, mp3, tf";
        assertEquals(expected, ExtensionNormalizer.normalize(input));
    }
}

