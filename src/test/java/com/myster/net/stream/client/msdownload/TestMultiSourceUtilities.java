
package com.myster.net.stream.client.msdownload;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import java.awt.Frame;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.myster.net.stream.client.msdownload.MultiSourceUtilities.DialogProvider;

public class TestMultiSourceUtilities {
    // JUnit 5 will create and clean up a temporary directory for us
    @TempDir
    File tempDir;

    /**
     * Test the simple case where no conflicting target exists. The file
     * "example.txt.i" should be renamed to "example.txt".
     */
    @Test
    public void testValidRenameNoConflict() throws IOException {
        // Create the source file "example.txt.i" with some content.
        File sourceFile = new File(tempDir, "example.txt.i");
        Files.writeString(sourceFile.toPath(), "Hello, world!");

        // Confirm that the target file "example.txt" does not exist.
        File targetFile = new File(tempDir, "example.txt");
        assertFalse(targetFile.exists(), "Target file should not exist before rename.");

        // Attempt the rename.
        MultiSourceUtilities.moveFileToFinalDestination(sourceFile, _ -> {});

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
    public void testRenameWithConflict() throws IOException {
        // Create the source file "example.txt.i".
        File sourceFile = new File(tempDir, "example.txt.i");
        Files.writeString(sourceFile.toPath(), "New content");

        // Create a conflicting file "example.txt".
        File conflictFile = new File(tempDir, "example.txt");
        Files.writeString(conflictFile.toPath(), "Existing content");

        // The expected target is "example-2.txt".
        File expectedFile = new File(tempDir, "example-2.txt");

        // Attempt the rename.
        MultiSourceUtilities.moveFileToFinalDestination(sourceFile, _ -> {});

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
    public void testInvalidSuffix() throws IOException {
        // Create a file with an invalid suffix (missing ".i").
        File sourceFile = new File(tempDir, "example.txt");
        Files.writeString(sourceFile.toPath(), "Data");

        // Attempt the rename.
        MultiSourceUtilities.moveFileToFinalDestination(sourceFile, _ -> {});

        // Since the file name is invalid, it should not be renamed.
        assertTrue(sourceFile.exists(), "File should remain unchanged if suffix is invalid.");
    }

    /**
     * Test the scenario where all candidate names (from the base name and "-2"
     * through "-100") already exist. In this case the method should not rename
     * the file.
     */
    @Test
    public void testRenameConflictExhausted() throws IOException {
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
        MultiSourceUtilities.moveFileToFinalDestination(sourceFile, _ -> {});

        // Since no available candidate could be found, the source file should
        // remain.
        assertTrue(sourceFile.exists(),
                   "Source file should remain since rename failed due to name exhaustion.");
    }
    
    // ========== Tests for getFileToDownloadTo ==========
    
    @Test
    public void testGetFileToDownloadTo_WithValidBasePath() throws IOException {
        // Setup
        Path baseDir = tempDir.toPath();
        Path relativePath = Path.of("music/albums");
        String fileName = "song";
        
        // Create a mock dialog provider that doesn't show any dialogs
        DialogProvider mockProvider = new DialogProvider() {
            @Override
            public String showAlert(Frame parentFrame, String message, String[] buttons) {
                fail("Should not show any dialogs when base path is valid");
                return null;
            }
            
            @Override
            public Path askForFolder(String title) {
                fail("Should not ask for folder when base path is provided");
                return null;
            }
        };
        
        // Execute
        File result = MultiSourceUtilities.getFileToDownloadTo(
            fileName, 
            null, 
            Optional.of(baseDir), 
            relativePath,
            mockProvider
        );
        
        // Verify
        assertNotNull(result);
        assertTrue(result.getName().endsWith(".i"));
        assertTrue(Files.exists(baseDir.resolve(relativePath))); // Subdirectories were created
        assertEquals(baseDir.resolve(relativePath).resolve(fileName + ".i").toFile(), result);
    }
    
    @Test
    public void testGetFileToDownloadTo_WithExistingFile_Overwrite() throws IOException {
        // Setup
        Path baseDir = tempDir.toPath();
        Path relativePath = Path.of("music");
        String fileName = "song";
        
        // Create the file that already exists
        Files.createDirectories(baseDir.resolve(relativePath));
        Path existingFile = baseDir.resolve(relativePath).resolve(fileName + ".i");
        Files.createFile(existingFile);
        
        // Mock provider that chooses to overwrite
        DialogProvider mockProvider = new DialogProvider() {
            @Override
            public String showAlert(Frame parentFrame, String message, String[] buttons) {
                if (message.contains("already exists")) {
                    return "Write-Over";
                }
                fail("Unexpected dialog: " + message);
                return null;
            }
            
            @Override
            public Path askForFolder(String title) {
                fail("Should not ask for folder");
                return null;
            }
        };
        
        // Execute
        File result = MultiSourceUtilities.getFileToDownloadTo(
            fileName,
            null,
            Optional.of(baseDir),
            relativePath,
            mockProvider
        );
        
        // Verify
        assertNotNull(result);
        assertFalse(Files.exists(existingFile)); // Old file was deleted
    }
    
    @Test
    public void testGetFileToDownloadTo_WithExistingFile_Cancel() throws IOException {
        // Setup
        Path baseDir = tempDir.toPath();
        Path relativePath = Path.of("music");
        String fileName = "song";
        
        // Create the file that already exists
        Files.createDirectories(baseDir.resolve(relativePath));
        Files.createFile(baseDir.resolve(relativePath).resolve(fileName + ".i"));
        
        // Mock provider that cancels
        DialogProvider mockProvider = new DialogProvider() {
            @Override
            public String showAlert(Frame parentFrame, String message, String[] buttons) {
                if (message.contains("already exists")) {
                    return "Cancel";
                }
                fail("Unexpected dialog: " + message);
                return null;
            }
            
            @Override
            public Path askForFolder(String title) {
                fail("Should not ask for folder");
                return null;
            }
        };
        
        // Execute
        File result = MultiSourceUtilities.getFileToDownloadTo(
            fileName,
            null,
            Optional.of(baseDir),
            relativePath,
            mockProvider
        );
        
        // Verify
        assertNull(result); // Cancelled
    }
    
    @Test
    public void testGetFileToDownloadTo_NoBasePath_UserSelectsFolder() throws IOException {
        // Setup
        Path selectedDir = tempDir.toPath();
        Path relativePath = Path.of("downloads");
        String fileName = "file";
        
        // Mock provider that returns a selected folder
        DialogProvider mockProvider = new DialogProvider() {
            @Override
            public String showAlert(Frame parentFrame, String message, String[] buttons) {
                fail("Should not show any dialogs");
                return null;
            }
            
            @Override
            public Path askForFolder(String title) {
                return selectedDir;
            }
        };
        
        // Execute
        File result = MultiSourceUtilities.getFileToDownloadTo(
            fileName,
            null,
            Optional.empty(), // No base path provided
            relativePath,
            mockProvider
        );
        
        // Verify
        assertNotNull(result);
        assertEquals(selectedDir.resolve(relativePath).resolve(fileName + ".i").toFile(), result);
    }
    
    @Test
    public void testGetFileToDownloadTo_NoBasePath_UserCancels() {
        // Setup
        Path relativePath = Path.of("downloads");
        String fileName = "file";
        
        // Mock provider that returns null (user cancelled)
        DialogProvider mockProvider = new DialogProvider() {
            @Override
            public String showAlert(Frame parentFrame, String message, String[] buttons) {
                fail("Should not show any dialogs");
                return null;
            }
            
            @Override
            public Path askForFolder(String title) {
                return null; // User cancelled
            }
        };
        
        // Execute
        File result = MultiSourceUtilities.getFileToDownloadTo(
            fileName,
            null,
            Optional.empty(),
            relativePath,
            mockProvider
        );
        
        // Verify
        assertNull(result); // Cancelled
    }
    
    @Test
    public void testGetFileToDownloadTo_RelativePathValidation() {
        // Setup - try to pass an absolute path as relativePath
        // Use tempDir to get a valid absolute path on any platform (Windows, Linux, macOS)
        Path absolutePath = tempDir.toPath().toAbsolutePath();
        
        // Execute & Verify
        assertThrows(IllegalArgumentException.class, () -> {
            MultiSourceUtilities.getFileToDownloadTo(
                "file",
                null,
                Optional.empty(),
                absolutePath,
                null
            );
        });
    }
    
    @Test
    public void testGetFileToDownloadTo_BasePathValidation() {
        // Setup - try to pass a relative path as base path
        Path relativePath = Path.of("relative/path");
        
        // Execute & Verify
        assertThrows(IllegalArgumentException.class, () -> {
            MultiSourceUtilities.getFileToDownloadTo(
                "file",
                null,
                Optional.of(relativePath),
                Path.of("subdir"),
                null
            );
        });
    }
}
