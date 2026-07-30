package com.myster.net.stream.client.msdownload;

import java.nio.file.Path;

/**
 * Decides what to do when a per-file download target already exists.
 */
@FunctionalInterface
public interface ExistingDownloadTargetHandler {
    enum Decision {
        OVERWRITE,
        CANCEL
    }

    ExistingDownloadTargetHandler CANCEL_DOWNLOAD = (partialFile, finalFile) -> Decision.CANCEL;

    /**
     * Chooses whether Myster should overwrite existing target files.
     *
     * @param partialFile path to the partial-download file ending in {@code .i}
     * @param finalFile path to the final file name without the partial suffix
     * @return overwrite to delete existing targets, or cancel to skip this file
     */
    Decision chooseForExistingTarget(Path partialFile, Path finalFile);
}
