package com.myster.search.ui;

import static com.myster.search.ui.MetadataColumnTestUtils.searchResult;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Map;

import com.general.mclist.ColumnSortable;
import com.general.mclist.MCListItemInterface;
import com.general.mclist.Sortable;
import com.myster.client.ui.FileListerThread.FileRecord;
import com.myster.mml.MessagePak;
import com.myster.search.SearchResult;
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
    void getSearchItem_formatsVideoColumnsFromTypedMetadata() throws Exception {
        MCListItemInterface<SearchResult> item = new ClientVideoHandleObject()
                .getSearchItem(searchResult(Map.of(
                        "/VideoLengthSec", 125L,
                        "/VideoWidth", 1280L,
                        "/VideoHeight", 720L,
                        "/VideoCodec", "HEVC",
                        "/VideoBitRate", 900000L)));

        assertEquals("2:05", item.getValueOfColumn(2).toString());
        assertEquals("1280x720", item.getValueOfColumn(3).toString());
        assertEquals("HEVC", item.getValueOfColumn(4).toString());
        assertEquals("900 kbps", item.getValueOfColumn(5).toString());
    }

    @Test
    void missingAndMalformedValuesShowUnknown() throws Exception {
        MessagePak metadata = MessagePak.newEmpty();
        metadata.putString("/VideoCodec", " ");
        ColumnSortable<String> fileItem = new ClientVideoHandleObject()
                .getFileItem(new FileRecord("film.mkv", metadata));
        MCListItemInterface<SearchResult> searchItem = new ClientVideoHandleObject()
                .getSearchItem(searchResult(Map.of(
                        "/VideoLengthSec", "bad",
                        "/VideoWidth", 1920L,
                        "/VideoBitRate", -1L)));

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
}
