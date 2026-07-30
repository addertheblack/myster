
package com.myster.net.stream.client.msdownload;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.*;

public class TestMultiSourceUtils {
    // JUnit 5 will create and clean up a temporary directory for us
    @TempDir
    File tempDir;

    /**
     * Test the simple case where no conflicting target exists. The file
     * "example.txt.i" should be renamed to "example.txt".
     */
    @Test
    public void testValidRenameNoConflict() throws Exception {
        // Create the source file "example.txt.i" with some content.
        File sourceFile = new File(tempDir, "example.txt.i");
        Files.writeString(sourceFile.toPath(), "Hello, world!");

        // Confirm that the target file "example.txt" does not exist.
        File targetFile = new File(tempDir, "example.txt");
        assertFalse(targetFile.exists(), "Target file should not exist before rename.");

        // Attempt the rename.
        MultiSourceUtils.moveFileToFinalDestination(sourceFile, _ -> {});

        // After renaming, the original file should be gone and the target
        // should exist.
        assertFalse(sourceFile.exists(),
                    "Source file should have been renamed (and no longer exist).");
        assertTrue(targetFile.exists(), "Target file should exist after rename.");

        // Verify that the content was preserved.
        String content = Files.readString(targetFile.toPath());
        assertEquals("Hello, world!", content);
    }

    /**
     * Test the case where a file with the intended name already exists. Given
     * that "example.txt" exists, the code should rename "example.txt.i" to
     * "example-2.txt".
     */
    @Test
    public void testRenameWithConflict() throws Exception {
        // Create the source file "example.txt.i".
        File sourceFile = new File(tempDir, "example.txt.i");
        Files.writeString(sourceFile.toPath(), "New content");

        // Create a conflicting file "example.txt".
        File conflictFile = new File(tempDir, "example.txt");
        Files.writeString(conflictFile.toPath(), "Existing content");

        // The expected target is "example-2.txt".
        File expectedFile = new File(tempDir, "example-2.txt");

        // Attempt the rename.
        MultiSourceUtils.moveFileToFinalDestination(sourceFile, _ -> {});

        // The source file should be renamed to "example-2.txt".
        assertFalse(sourceFile.exists(), "Source file should have been renamed.");
        assertTrue(expectedFile.exists(), "Expected renamed file should exist.");

        // Verify that the content of the renamed file is the one from the
        // source.
        String content = Files.readString(expectedFile.toPath());
        assertEquals("New content", content);
        
        // Verify no accidental delete
        String conflictFileContent = Files.readString(conflictFile.toPath());
        assertEquals("Existing content", conflictFileContent);
    }

    /**
     * Test that if the source file does not have the expected suffix, nothing
     * happens.
     */
    @Test
    public void testInvalidSuffix() throws Exception {
        // Create a file with an invalid suffix (missing ".i").
        File sourceFile = new File(tempDir, "example.txt");
        Files.writeString(sourceFile.toPath(), "Data");

        // Attempt the rename.
        MultiSourceUtils.moveFileToFinalDestination(sourceFile, _ -> {});

        // Since the file name is invalid, it should not be renamed.
        assertTrue(sourceFile.exists(), "File should remain unchanged if suffix is invalid.");
    }

    /**
     * Test the scenario where all candidate names (from the base name and "-2"
     * through "-100") already exist. In this case the method should not rename
     * the file.
     */
    @Test
    public void testRenameConflictExhausted() throws Exception {
        // Create the source file "example.txt.i".
        File sourceFile = new File(tempDir, "example.txt.i");
        Files.writeString(sourceFile.toPath(), "Final content");

        // Create a conflict file for the intended base name "example.txt".
        File baseConflict = new File(tempDir, "example.txt");
        Files.writeString(baseConflict.toPath(), "Conflict");

        // Create conflicting files for candidates "example-2.txt" to
        // "example-100.txt".
        for (int counter = 2; counter <= 100; counter++) {
            File conflict = new File(tempDir, "example-" + counter + ".txt");
            Files.writeString(conflict.toPath(), "Conflict");
        }

        // Attempt the rename.
        MultiSourceUtils.moveFileToFinalDestination(sourceFile, _ -> {});

        // Since no available candidate could be found, the source file should
        // remain.
        assertTrue(sourceFile.exists(),
                   "Source file should remain since rename failed due to name exhaustion.");
    }
    
    // ========== Tests for getFileToDownloadTo ==========
    
    @Test
    public void testGetFileToDownloadTo_WithValidBasePath() throws Exception {
        // Setup
        Path baseDir = tempDir.toPath();
        Path relativePath = Path.of("music/albums");
        String fileName = "song";
        
        // Execute
        File result = MultiSourceUtils.getFileToDownloadTo(
            fileName, 
            baseDir,
            relativePath,
            failIfAsked()
        );
        
        // Verify
        assertNotNull(result);
        assertTrue(result.getName().endsWith(".i"));
        assertTrue(Files.exists(baseDir.resolve(relativePath))); // Subdirectories were created
        assertEquals(baseDir.resolve(relativePath).resolve(fileName + ".i").toFile(), result);
    }
    
    @Test
    public void testGetFileToDownloadTo_WithExistingFile_Overwrite() throws Exception {
        // Setup
        Path baseDir = tempDir.toPath();
        Path relativePath = Path.of("music");
        String fileName = "song";
        
        // Create the file that already exists
        Files.createDirectories(baseDir.resolve(relativePath));
        Path existingFile = baseDir.resolve(relativePath).resolve(fileName + ".i");
        Files.createFile(existingFile);
        
        // Execute
        File result = MultiSourceUtils.getFileToDownloadTo(
            fileName,
            baseDir,
            relativePath,
            (partialFile, finalFile) -> ExistingDownloadTargetHandler.Decision.OVERWRITE
        );
        
        // Verify
        assertNotNull(result);
        assertFalse(Files.exists(existingFile)); // Old file was deleted
    }
    
    @Test
    public void testGetFileToDownloadTo_WithExistingFile_Cancel() throws Exception {
        // Setup
        Path baseDir = tempDir.toPath();
        Path relativePath = Path.of("music");
        String fileName = "song";
        
        // Create the file that already exists
        Files.createDirectories(baseDir.resolve(relativePath));
        Files.createFile(baseDir.resolve(relativePath).resolve(fileName + ".i"));
        
        // Execute
        File result = MultiSourceUtils.getFileToDownloadTo(
            fileName,
            baseDir,
            relativePath,
            (partialFile, finalFile) -> ExistingDownloadTargetHandler.Decision.CANCEL
        );
        
        // Verify
        assertNull(result); // Cancelled
    }
    
    @Test
    public void testGetFileToDownloadTo_InvalidBasePath_ThrowsDownloadTargetException()
            throws Exception {
        // Setup
        Path relativePath = Path.of("downloads");
        String fileName = "file";
        Path missingDirectory = tempDir.toPath().resolve("missing").toAbsolutePath();
        
        assertThrows(InvalidDownloadDirectoryException.class, () -> MultiSourceUtils.getFileToDownloadTo(
            fileName,
            missingDirectory,
            relativePath,
            failIfAsked()
        ));
    }
    
    @Test
    public void testGetFileToDownloadTo_BasePathValidation() {
        // Setup - try to pass a relative path as base path
        Path relativePath = Path.of("relative/path");

        // Execute & Verify
        assertThrows(IllegalArgumentException.class, () -> {
            MultiSourceUtils.getFileToDownloadTo(
                "file",
                relativePath,
                Path.of("subdir"),
                null
            );
        });
    }

    private static ExistingDownloadTargetHandler failIfAsked() {
        return (partialFile, finalFile) -> {
            fail("Should not ask about existing download targets");
            return ExistingDownloadTargetHandler.Decision.CANCEL;
        };
    }
}
