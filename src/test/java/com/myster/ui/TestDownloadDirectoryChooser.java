package com.myster.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class TestDownloadDirectoryChooser {
    @TempDir
    Path tempDir;

    @Test
    void chooseWritableDownloadDirectory_validSelection_returnsAbsolutePath() {
        DownloadDirectoryChooser.FolderDialog folderDialog = mockFolderDialog(tempDir);

        Optional<Path> result = DownloadDirectoryChooser.chooseWritableDownloadDirectory(
                null,
                "Choose",
                Optional.empty(),
                folderDialog);

        assertTrue(result.isPresent());
        assertEquals(tempDir.toAbsolutePath().normalize(), result.get());
        verify(folderDialog).askForFolder(any(), eq("Choose"));
        verify(folderDialog, never()).showAlert(any(), anyString(), any(String[].class));
    }

    @Test
    void chooseWritableDownloadDirectory_userCancels_returnsEmpty() {
        DownloadDirectoryChooser.FolderDialog folderDialog = mockFolderDialog((Path) null);

        Optional<Path> result = DownloadDirectoryChooser.chooseWritableDownloadDirectory(
                null,
                "Choose",
                Optional.empty(),
                folderDialog);

        assertFalse(result.isPresent());
        verify(folderDialog).askForFolder(any(), eq("Choose"));
        verify(folderDialog, never()).showAlert(any(), anyString(), any(String[].class));
    }

    @Test
    void chooseWritableDownloadDirectory_missingPath_allowsRetry() {
        Path missingPath = tempDir.resolve("missing");
        DownloadDirectoryChooser.FolderDialog folderDialog =
                mockFolderDialog(missingPath, tempDir);
        when(folderDialog.showAlert(any(), anyString(), any(String[].class))).thenReturn("OK");

        Optional<Path> result = DownloadDirectoryChooser.chooseWritableDownloadDirectory(
                null,
                "Choose",
                Optional.empty(),
                folderDialog);

        assertTrue(result.isPresent());
        assertEquals(tempDir.toAbsolutePath().normalize(), result.get());
        verify(folderDialog, times(2)).askForFolder(any(), eq("Choose"));
        verify(folderDialog).showAlert(any(), anyString(), any(String[].class));
    }

    @Test
    void chooseWritableDownloadDirectory_filePath_canCancel() throws Exception {
        Path file = tempDir.resolve("not-a-directory.txt");
        Files.writeString(file, "data");
        DownloadDirectoryChooser.FolderDialog folderDialog = mockFolderDialog(file);
        when(folderDialog.showAlert(any(), anyString(), any(String[].class)))
                .thenReturn("Cancel");

        Optional<Path> result = DownloadDirectoryChooser.chooseWritableDownloadDirectory(
                null,
                "Choose",
                Optional.empty(),
                folderDialog);

        assertFalse(result.isPresent());
        verify(folderDialog).askForFolder(any(), eq("Choose"));
        verify(folderDialog).showAlert(any(), anyString(), any(String[].class));
    }

    @Test
    void chooseWritableDownloadDirectory_validConfiguredPath_doesNotAsk() {
        DownloadDirectoryChooser.FolderDialog folderDialog =
                mock(DownloadDirectoryChooser.FolderDialog.class);

        Optional<Path> result = DownloadDirectoryChooser.chooseWritableDownloadDirectory(
                null,
                "Choose",
                Optional.of(tempDir),
                folderDialog);

        assertTrue(result.isPresent());
        assertEquals(tempDir.toAbsolutePath().normalize(), result.get());
        verifyNoInteractions(folderDialog);
    }

    @Test
    void chooseWritableDownloadDirectory_missingConfiguredPath_canCancel() {
        Path missingPath = tempDir.resolve("missing");
        DownloadDirectoryChooser.FolderDialog folderDialog =
                mock(DownloadDirectoryChooser.FolderDialog.class);
        when(folderDialog.showAlert(any(), anyString(), any(String[].class)))
                .thenReturn("Cancel");

        Optional<Path> result = DownloadDirectoryChooser.chooseWritableDownloadDirectory(
                null,
                "Choose",
                Optional.of(missingPath),
                folderDialog);

        assertFalse(result.isPresent());
        verify(folderDialog, never()).askForFolder(any(), anyString());
        verify(folderDialog).showAlert(any(), anyString(), any(String[].class));
    }

    @Test
    void chooseWritableDownloadDirectory_missingConfiguredPath_allowsRetry() {
        Path missingPath = tempDir.resolve("missing");
        DownloadDirectoryChooser.FolderDialog folderDialog = mockFolderDialog(tempDir);
        when(folderDialog.showAlert(any(), anyString(), any(String[].class))).thenReturn("OK");

        Optional<Path> result = DownloadDirectoryChooser.chooseWritableDownloadDirectory(
                null,
                "Choose",
                Optional.of(missingPath),
                folderDialog);

        assertTrue(result.isPresent());
        assertEquals(tempDir.toAbsolutePath().normalize(), result.get());
        verify(folderDialog).showAlert(any(), anyString(), any(String[].class));
        verify(folderDialog).askForFolder(any(), eq("Choose"));
    }

    @Test
    void chooseWritableDownloadDirectory_alwaysAsk_skipsConfiguredCollaborators() {
        DownloadDirectoryChooser.FolderDialog folderDialog = mockFolderDialog(tempDir);

        Optional<Path> result = DownloadDirectoryChooser.chooseWritableDownloadDirectory(
                null,
                "Choose",
                true,
                null,
                null,
                folderDialog);

        assertTrue(result.isPresent());
        assertEquals(tempDir.toAbsolutePath().normalize(), result.get());
        verify(folderDialog).askForFolder(any(), eq("Choose"));
    }

    @Test
    void chooseWritableDownloadDirectory_configuredLookupRequiresFileManager() {
        DownloadDirectoryChooser.FolderDialog folderDialog =
                mock(DownloadDirectoryChooser.FolderDialog.class);

        assertThrows(NullPointerException.class,
                     () -> DownloadDirectoryChooser.chooseWritableDownloadDirectory(
                             null,
                             "Choose",
                             false,
                             null,
                             null,
                             folderDialog));

        verifyNoInteractions(folderDialog);
    }

    private static DownloadDirectoryChooser.FolderDialog mockFolderDialog(Path... selections) {
        DownloadDirectoryChooser.FolderDialog folderDialog =
                mock(DownloadDirectoryChooser.FolderDialog.class);
        if (selections.length == 1) {
            when(folderDialog.askForFolder(any(), eq("Choose"))).thenReturn(selections[0]);
        } else {
            when(folderDialog.askForFolder(any(), eq("Choose")))
                    .thenReturn(selections[0],
                                Arrays.copyOfRange(selections, 1, selections.length));
        }
        return folderDialog;
    }
}
