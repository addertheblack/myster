package com.myster.search.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.util.Map;
import java.util.function.Consumer;

import com.general.mclist.ColumnSortable;
import com.general.mclist.MCListItemInterface;
import com.general.mclist.Sortable;
import com.myster.client.ui.FileListerThread.FileRecord;
import com.myster.mml.MessagePak;
import com.myster.net.MysterAddress;
import com.myster.net.client.MysterProtocol;
import com.myster.net.stream.client.msdownload.DownloadStartException;
import com.myster.search.SearchResult;
import com.myster.tracker.MysterServer;
import org.junit.jupiter.api.Test;

class TestClientVideoHandleObject {
    @Test
    void getColumnCountAndHeaders_includeVideoColumns() {
        ClientVideoHandleObject handler = new ClientVideoHandleObject();

        assertEquals(6, handler.getColumnCount());
        assertEquals("Duration", handler.getHeader(2));
        assertEquals("Resolution", handler.getHeader(3));
        assertEquals("Codec", handler.getHeader(4));
        assertEquals("Bit Rate", handler.getHeader(5));
    }

    @Test
    void getFileItem_formatsVideoColumns() {
        MessagePak metadata = videoMetadata();

        ColumnSortable<String> item = new ClientVideoHandleObject()
                .getFileItem(new FileRecord("film.mp4", metadata));

        assertEquals("1:42:18", item.getValueOfColumn(2).toString());
        assertEquals("1920x1080", item.getValueOfColumn(3).toString());
        assertEquals("H.264", item.getValueOfColumn(4).toString());
        assertEquals("4.5 Mbps", item.getValueOfColumn(5).toString());
    }

    @Test
    void getSearchItem_formatsVideoColumnsFromStrings() {
        MCListItemInterface<SearchResult> item = new ClientVideoHandleObject()
                .getSearchItem(new StubSearchResult(Map.of(
                        "/VideoLengthSec", "125",
                        "/VideoWidth", "1280",
                        "/VideoHeight", "720",
                        "/VideoCodec", "HEVC",
                        "/VideoBitRate", "900000")));

        assertEquals("2:05", item.getValueOfColumn(2).toString());
        assertEquals("1280x720", item.getValueOfColumn(3).toString());
        assertEquals("HEVC", item.getValueOfColumn(4).toString());
        assertEquals("900 kbps", item.getValueOfColumn(5).toString());
    }

    @Test
    void missingAndMalformedValuesShowUnknown() {
        MessagePak metadata = MessagePak.newEmpty();
        metadata.putString("/VideoCodec", " ");
        ColumnSortable<String> fileItem = new ClientVideoHandleObject()
                .getFileItem(new FileRecord("film.mkv", metadata));
        MCListItemInterface<SearchResult> searchItem = new ClientVideoHandleObject()
                .getSearchItem(new StubSearchResult(Map.of(
                        "/VideoLengthSec", "bad",
                        "/VideoWidth", "1920",
                        "/VideoBitRate", "-1")));

        for (int column = 2; column < 6; column++) {
            assertEquals("-", fileItem.getValueOfColumn(column).toString());
            assertEquals("-", searchItem.getValueOfColumn(column).toString());
        }
    }

    @Test
    void folderVideoColumnsShowUnknown() {
        ColumnSortable<String> item = new ClientVideoHandleObject().getFolderItem("Films");

        for (int column = 2; column < 6; column++) {
            assertEquals("-", item.getValueOfColumn(column).toString());
        }
    }

    @Test
    @SuppressWarnings({ "rawtypes", "unchecked" })
    void numericColumnsSortByRawValues() {
        ColumnSortable<String> smaller = new ClientVideoHandleObject()
                .getFileItem(new FileRecord("small.mp4", metadata(60, 640, 480, 500_000)));
        ColumnSortable<String> larger = new ClientVideoHandleObject()
                .getFileItem(new FileRecord("large.mp4", metadata(120, 1920, 1080, 4_000_000)));

        for (int column : new int[] { 2, 3, 5 }) {
            Sortable smallerValue = smaller.getValueOfColumn(column);
            Sortable largerValue = larger.getValueOfColumn(column);
            assertTrue(smallerValue.isLessThan(largerValue));
        }
    }

    private static MessagePak videoMetadata() {
        MessagePak metadata = metadata(6_138, 1920, 1080, 4_500_000);
        metadata.putString("/VideoCodec", "H.264");
        return metadata;
    }

    private static MessagePak metadata(long duration, long width, long height, long bitRate) {
        MessagePak metadata = MessagePak.newEmpty();
        metadata.putLong("/VideoLengthSec", duration);
        metadata.putLong("/VideoWidth", width);
        metadata.putLong("/VideoHeight", height);
        metadata.putLong("/VideoBitRate", bitRate);
        return metadata;
    }

    private record StubSearchResult(Map<String, String> metadata) implements SearchResult {
        @Override
        public void downloadTo(Path baseDirectory,
                               Consumer<DownloadStartException> startFailureHandler) {}

        @Override
        public String getNetwork() {
            return "test";
        }

        @Override
        public String getMetaData(String key) {
            return metadata.get(key);
        }

        @Override
        public String[] getKeyList() {
            return metadata.keySet().toArray(new String[] {});
        }

        @Override
        public String getName() {
            return "film.mp4";
        }

        @Override
        public MysterAddress getHostAddress() {
            return null;
        }

        @Override
        public MysterServer getServer() {
            return null;
        }

        @Override
        public MysterProtocol getProtocol() {
            return null;
        }
    }
}
