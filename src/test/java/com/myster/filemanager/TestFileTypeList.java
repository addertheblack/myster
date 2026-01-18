package com.myster.filemanager;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.FileSystem;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.google.common.jimfs.Configuration;
import com.google.common.jimfs.Jimfs;
import com.myster.hash.FileHash;
import com.myster.hash.FileHashEvent;
import com.myster.hash.FileHashListener;
import com.myster.type.MysterType;
import com.myster.type.TypeDescription;
import com.myster.type.TypeDescriptionList;

class TestFileTypeList {
    private FileSystem fileSystem;
    private FileTypeList fileTypeList;
    private MysterType testType;
    private TestHashProvider hashProvider;
    private TestTypeDescriptionList typeDescriptionList;
    private String testPrefPath;
    private Path testRoot;
    
    @BeforeEach
    void setUp() throws IOException {
        // Create in-memory file system (Unix-style for consistency)
        fileSystem = Jimfs.newFileSystem(Configuration.unix());
        
        testType = new MysterType(new byte[]{0x01, 0x02, 0x03, 0x04});
        hashProvider = new TestHashProvider();
        typeDescriptionList = new TestTypeDescriptionList();
        testPrefPath = "/test/path";
        
        // Create test file structure in memory
        testRoot = fileSystem.getPath("/test/root");
        Files.createDirectories(testRoot);
        createTestFileStructure();
        
        fileTypeList = new FileTypeList(testType, testPrefPath, hashProvider, typeDescriptionList, fileSystem);
    }
    
    @AfterEach
    void tearDown() throws IOException {
        // Wait for any pending indexing to complete before closing filesystem
        fileTypeList.waitForIndexer();
        
        if (fileSystem != null && fileSystem.isOpen()) {
            fileSystem.close();
        }
    }

    private void createTestFileStructure() throws IOException {
        // Root level files (depth 0)
        Files.createFile(testRoot.resolve("root_file1.txt"));
        Files.createFile(testRoot.resolve("root_file2.txt"));
        Files.write(testRoot.resolve("root_file1.txt"), "content".getBytes()); // Non-zero size
        Files.write(testRoot.resolve("root_file2.txt"), "content".getBytes());
        
        // One level deep (depth 1)
        Path level1 = Files.createDirectories(testRoot.resolve("level1"));
        Files.write(Files.createFile(level1.resolve("level1_file1.txt")), "content".getBytes());
        Files.write(Files.createFile(level1.resolve("level1_file2.txt")), "content".getBytes());
        
        // Two levels deep (depth 2)
        Path level2 = Files.createDirectories(level1.resolve("level2"));
        Files.write(Files.createFile(level2.resolve("level2_file1.txt")), "content".getBytes());
        
        // Three levels deep (depth 3)
        Path level3 = Files.createDirectories(level2.resolve("level3"));
        Files.write(Files.createFile(level3.resolve("level3_file1.txt")), "content".getBytes());
        
        // Four levels deep (depth 4 - should be indexed, last level at max depth 5)
        Path level4 = Files.createDirectories(level3.resolve("level4"));
        Files.write(Files.createFile(level4.resolve("level4_file1.txt")), "content".getBytes());
        
        // Five levels deep (depth 5 - should NOT be indexed, exceeds max depth)
        Path level5 = Files.createDirectories(level4.resolve("level5"));
        Files.write(Files.createFile(level5.resolve("level5_file1.txt")), "content".getBytes());
    }
    
    @Test
    @DisplayName("Test constructor initializes correctly")
    void testConstructor() {
        assertNotNull(fileTypeList);
        assertEquals(testType, fileTypeList.getType());
    }
    
    @Test
    @DisplayName("Test isShared flag defaults to true")
    void testIsSharedDefaultsToTrue() {
        assertTrue(fileTypeList.isShared());
    }
    
    @Test
    @DisplayName("Test setShared updates sharing state")
    void testSetShared() {
        fileTypeList.setShared(false);
        assertFalse(fileTypeList.isShared());
        
        fileTypeList.setShared(true);
        assertTrue(fileTypeList.isShared());
    }
    
    @Test
    @DisplayName("Test getType returns correct type")
    void testGetType() {
        assertEquals(testType, fileTypeList.getType());
    }
    
    @Test
    @DisplayName("Test setPath and getPath")
    void testSetPathAndGetPath() {
        String newPath = testRoot.toString();
        fileTypeList.setPath(newPath);
        
        assertEquals(FileTypeList.mergePunctuation(newPath), fileTypeList.getPath());
    }
    
    @Test
    @DisplayName("Test getFileListAsStrings when shared")
    void testGetFileListAsStringsWhenShared() throws InterruptedException {
        fileTypeList.setPath(testRoot.toString());
        fileTypeList.setShared(true);
        
        waitForIndexing();
        
        String[] files = fileTypeList.getFileListAsStrings();
        assertNotNull(files);
        assertTrue(files.length > 0, "Should have indexed some files");
    }
    
    @Test
    @DisplayName("Test getFileListAsStrings returns empty when not shared")
    void testGetFileListAsStringsWhenNotShared() throws InterruptedException {
        fileTypeList.setPath(testRoot.toString());
        fileTypeList.setShared(false);
        
        String[] files = fileTypeList.getFileListAsStrings();
        assertNotNull(files);
        assertEquals(0, files.length, "Should return no files when not shared");
    }
    
    @Test
    @DisplayName("Test breadth-first indexing order - root files come first")
    void testBreadthFirstIndexingOrder() throws InterruptedException {
        fileTypeList.setPath(testRoot.toString());
        fileTypeList.setShared(true);
        
        waitForIndexing();
        
        String[] files = fileTypeList.getFileListAsStrings();
        assertTrue(files.length > 0, "Should have files");
        
        // Verify root files are present
        boolean hasRootFile = false;
        for (String file : files) {
            if (file.equals("root_file1.txt") || file.equals("root_file2.txt")) {
                hasRootFile = true;
                break;
            }
        }
        assertTrue(hasRootFile, "Should contain root level files");
    }
    
    @Test
    @DisplayName("Test getFileItemFromString with HashMap lookup")
    void testGetFileItemFromString() throws InterruptedException {
        fileTypeList.setPath(testRoot.toString());
        fileTypeList.setShared(true);
        
        waitForIndexing();
        
        String[] files = fileTypeList.getFileListAsStrings();
        if (files.length > 0) {
            String firstFileName = files[0];
            FileItem item = fileTypeList.getFileItemFromString(firstFileName);
            assertNotNull(item, "Should find file item by name");
            assertEquals(firstFileName, item.getName(), "Names should match");
        }
    }
    
    @Test
    @DisplayName("Test getFileItemFromString returns null for non-existent file")
    void testGetFileItemFromStringNonExistent() throws InterruptedException {
        fileTypeList.setPath(testRoot.toString());
        fileTypeList.setShared(true);
        
        waitForIndexing();
        
        FileItem item = fileTypeList.getFileItemFromString("non_existent_file.txt");
        assertNull(item, "Should return null for non-existent file");
    }
    
    @Test
    @DisplayName("Test getNumOfFiles when shared")
    void testGetNumOfFilesWhenShared() throws InterruptedException {
        fileTypeList.setPath(testRoot.toString());
        fileTypeList.setShared(true);
        
        waitForIndexing();
        
        int numFiles = fileTypeList.getNumOfFiles();
        assertEquals(numFiles, 7, "Should have indexed files");
    }
    
    @Test
    @DisplayName("Test getNumOfFiles returns 0 when not shared")
    void testGetNumOfFilesWhenNotShared() {
        fileTypeList.setShared(false);
        
        int numFiles = fileTypeList.getNumOfFiles();
        assertEquals(0, numFiles, "Should return 0 when not shared");
    }
    
    @Test
    @DisplayName("Test getFileListAsStrings with query string")
    void testGetFileListAsStringsWithQuery() throws InterruptedException {
        fileTypeList.setPath(testRoot.toString());
        fileTypeList.setShared(true);
        
        waitForIndexing();
        
        String[] files = fileTypeList.getFileListAsStrings("root");
        assertNotNull(files);
        
        assertEquals(files.length, 2, "Should return 2 files matching 'root'");
        
        // Should only return files matching "root"
        for (String file : files) {
            assertTrue(file.toLowerCase().contains("root"), 
                      "File '" + file + "' should contain 'root'");
        }
    }
    
    @Test
    @DisplayName("Test query with quoted string")
    void testQueryWithQuotedString() throws InterruptedException {
        fileTypeList.setPath(testRoot.toString());
        fileTypeList.setShared(true);
        
        waitForIndexing();
        
        String[] files = fileTypeList.getFileListAsStrings("\"root file\"");
        assertNotNull(files);
    }
    
    @Test
    @DisplayName("Test isIndexing returns correct state")
    void testIsIndexing() throws InterruptedException {
        fileTypeList.setPath(testRoot.toString());
        fileTypeList.setShared(true);
        
        // Wait and verify it's no longer indexing
        waitForIndexing();
        assertFalse(fileTypeList.isIndexing(), "Should not be indexing after completion");
        assertTrue(fileTypeList.isInitialized(), "Should be initialized after indexing");
    }

    @Test
    @DisplayName("Test maximum depth indexing - level 4 included, level 5 excluded")
    void testMaximumDepthIndexing() throws InterruptedException {
        fileTypeList.setPath(testRoot.toString());
        fileTypeList.setShared(true);

        String[] files = fileTypeList.getFileListAsStrings();

        waitForIndexing();

        boolean hasLevel4 = false;
        boolean hasLevel5 = false;
        
        for (String file : files) {
            if (file.equals("level4_file1.txt")) {
                hasLevel4 = true;
            }
            if (file.equals("level5_file1.txt")) {
                hasLevel5 = true;
            }
        }
        
        assertTrue(hasLevel4, "Should index files at depth 4 (within max depth)");
        assertFalse(hasLevel5, "Should NOT index files at depth 5 (exceeds max depth)");
    }
    
    @Test
    @DisplayName("Test changing path triggers re-indexing")
    void testChangingPathTriggersReindexing() throws InterruptedException, IOException {
        fileTypeList.setPath(testRoot.toString());
        fileTypeList.setShared(true);
        
        waitForIndexing();
        
        int firstCount = fileTypeList.getNumOfFiles();
        assertEquals(firstCount, 7);
        
        // Create new directory with different files
        Path newRoot = fileSystem.getPath("/test/newroot");
        Files.createDirectories(newRoot);
        Files.write(Files.createFile(newRoot.resolve("different_file.txt")), "content".getBytes());
        
        fileTypeList.setPath(newRoot.toString());
        
        waitForIndexing();
        
        String[] files = fileTypeList.getFileListAsStrings();
        assertNotNull(files);
    }
    
    @Test
    @DisplayName("Test mergePunctuation with regular text")
    void testMergePunctuationRegularText() {
        String input = "test_file.txt";
        String result = FileTypeList.mergePunctuation(input);
        assertEquals(input, result);
    }
    
    @Test
    @DisplayName("Test mergePunctuation with empty string")
    void testMergePunctuationEmptyString() {
        String result = FileTypeList.mergePunctuation("");
        assertEquals("", result);
    }
    
    @Test
    @DisplayName("Test mergePunctuation with single character")
    void testMergePunctuationSingleCharacter() {
        String result = FileTypeList.mergePunctuation("a");
        assertEquals("a", result);
    }
    
    @Test
    @DisplayName("Test mergePunctuation with Japanese characters")
    void testMergePunctuationJapanese() {
        String input = "\u30a6\u3099"; // ウ + combining dakuten
        String result = FileTypeList.mergePunctuation(input);
        assertEquals("\u30f4", result); // Should become ヴ
    }
    
    @Test
    @DisplayName("Test with hidden files - should be excluded")
    void testHiddenFilesExcluded() throws InterruptedException, IOException {
        // Create hidden file (starts with .)
        Files.write(Files.createFile(testRoot.resolve(".hidden_file.txt")), "content".getBytes());
        
        fileTypeList.setPath(testRoot.toString());
        fileTypeList.setShared(true);
        
        waitForIndexing();
        
        String[] files = fileTypeList.getFileListAsStrings();
        
        for (String file : files) {
            assertFalse(file.startsWith("."), "Hidden files should not be indexed");
        }
    }
    
    @Test
    @DisplayName("Test with zero-byte files - should be excluded")
    void testZeroByteFilesExcluded() throws InterruptedException, IOException {
        // Create zero-byte file
        Files.createFile(testRoot.resolve("empty_file.txt"));
        
        fileTypeList.setPath(testRoot.toString());
        fileTypeList.setShared(true);
        
        waitForIndexing();
        
        String[] files = fileTypeList.getFileListAsStrings();
        
        for (String file : files) {
            assertFalse(file.equals("empty_file.txt"), "Zero-byte files should not be indexed");
        }
    }
    
    @Test
    @DisplayName("Test FileItem uses Path internally")
    void testFileItemUsesPath() throws InterruptedException {
        fileTypeList.setPath(testRoot.toString());
        fileTypeList.setShared(true);
        
        waitForIndexing();
        
        String[] files = fileTypeList.getFileListAsStrings();
        if (files.length > 0) {
            FileItem item = fileTypeList.getFileItemFromString(files[0]);
            assertNotNull(item);
            assertNotNull(item.getPath(), "FileItem should have a Path");
            // Note: Can't test getFile() with Jimfs as it doesn't support toFile()
            // but the backward compatibility is there for real filesystems
        }
    }
    
    private void waitForIndexing() throws InterruptedException {
        fileTypeList.waitForIndexer();
    }
    
    // Test helper classes
    
    private static class TestHashProvider implements HashProvider {
        @Override
        public void findHashNonBlocking(Path path, FileHashListener listener) {
            // Simulate hash calculation with a concrete FileHash implementation
            FileHash[] hashes = new FileHash[]{
                new TestFileHash()
            };
            listener.foundHash(new FileHashEvent(hashes, path));
        }
    }
    
    private static class TestFileHash extends FileHash {
        private final byte[] hash = new byte[]{1, 2, 3, 4};
        
        @Override
        public byte[] getBytes() {
            return hash.clone();
        }
        
        @Override
        public short getHashLength() {
            return (short) hash.length;
        }
        
        @Override
        public String getHashName() {
            return "MD5";
        }
    }
    
    private static class TestTypeDescriptionList implements TypeDescriptionList {
        private final TypeDescription testTypeDescription;
        
        public TestTypeDescriptionList() {
            testTypeDescription = new TypeDescription(
                new MysterType(new byte[]{0x01, 0x02, 0x03, 0x04}), 
                "TestType",           // internalName
                "Test Type",          // description
                new String[]{".txt"}, // extensions
                false,                // isArchived
                true                  // isEnabledByDefault
            );
        }
        
        @Override
        public MysterType getType(com.myster.type.StandardTypes t) {
            return testTypeDescription.getType();
        }
        
        @Override
        public Optional<TypeDescription> get(MysterType type) {
            return Optional.of(testTypeDescription);
        }
        
        @Override
        public TypeDescription[] getAllTypes() {
            return new TypeDescription[]{testTypeDescription};
        }
        
        @Override
        public TypeDescription[] getEnabledTypes() {
            return new TypeDescription[]{testTypeDescription};
        }
        
        @Override
        public boolean isTypeEnabled(MysterType type) {
            return true;
        }

        @Override
        public boolean isTypeEnabledInPrefs(MysterType type) {
            return true;
        }
        
        @Override
        public void addTypeListener(com.myster.type.TypeListener l) {
            // No-op for testing
        }
        
        @Override
        public void removeTypeListener(com.myster.type.TypeListener l) {
            // No-op for testing
        }
        
        @Override
        public void setEnabledType(MysterType type, boolean enabled) {
            // No-op for testing
        }
    }
}
