package com.myster.net.stream;

/**
 * Shared TCP stream section identifiers and wire-protocol constants.
 */
public final class StreamConstants {
    private StreamConstants() {
    }

    /** Basic ping request. */
    public static final int PING_SECTION_NUMBER = 1;
    /** Acknowledges and closes the connection. */
    public static final int DISCONNECT_SECTION_NUMBER = 2;
    /** Requests the known-server list for a type. */
    public static final int TOP_TEN_SECTION_NUMBER = 10;
    /** Searches shared filenames. */
    public static final int SEARCH_SECTION_NUMBER = 35;
    /** Lists available file types. */
    public static final int TYPE_LIST_SECTION_NUMBER = 74;
    /** Requests metadata for one file. */
    public static final int FILE_STATS_SECTION_NUMBER = 77;
    /** Lists filenames for one type. */
    public static final int DIRECTORY_LIST_SECTION_NUMBER = 78;
    /** Requests a thumbnail. */
    public static final int THUMBNAIL_SECTION_NUMBER = 79;
    /** Requests a multi-source file transfer. */
    public static final int MULTI_SOURCE_DOWNLOAD_SECTION_NUMBER = 90;
    /** Requests server statistics. */
    public static final int SERVER_STATS_SECTION_NUMBER = 101;
    /** Fetches a type's access-list chain. */
    public static final int ACCESS_LIST_GET_SECTION_NUMBER = 125;
    /** Redeems an invitation to join a type. */
    public static final int TYPE_JOIN_SECTION_NUMBER = 126;
    /** Resolves file hashes to filenames. */
    public static final int FILE_BY_HASH_SECTION_NUMBER = 150;
    /** Requests metadata for a batch of files. */
    public static final int BATCH_FILE_STATS_SECTION_NUMBER = 177;
    /** Upgrades a plaintext connection to TLS. */
    public static final int START_TLS_SECTION_NUMBER = 0x53544C53;

    /** Maximum encoded request or response size accepted by the type-join section. */
    public static final int TYPE_JOIN_MAX_FRAME_BYTES = 4 * 1024;

    /** MessagePack field containing the multi-source transfer queue position. */
    public static final String MULTI_SOURCE_QUEUED_PATH = "/queued";
    /** MessagePack field containing a multi-source transfer status message. */
    public static final String MULTI_SOURCE_MESSAGE_PATH = "/message";
}
