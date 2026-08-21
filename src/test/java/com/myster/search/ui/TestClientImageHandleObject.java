package com.myster.search.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Map;
import java.util.function.Consumer;

import com.general.mclist.ColumnSortable;
import com.general.mclist.MCListItemInterface;
import com.myster.client.ui.FileListerThread.FileRecord;
import com.myster.mml.MessagePak;
import com.myster.net.MysterAddress;
import com.myster.net.client.MysterProtocol;
import com.myster.net.stream.client.msdownload.DownloadStartException;
import com.myster.search.SearchResult;
import com.myster.tracker.MysterServer;
import org.junit.jupiter.api.Test;

class TestClientImageHandleObject {
    @Test
    void getColumnCount_returns7() {
        assertEquals(7, new ClientImageHandleObject().getColumnCount());
    }

    @Test
    void getHeader_imageColumns() {
        ClientImageHandleObject handler = new ClientImageHandleObject();

        assertEquals("Resolution", handler.getHeader(2));
        assertEquals("Taken", handler.getHeader(3));
        assertEquals("Camera", handler.getHeader(4));
        assertEquals("Orientation", handler.getHeader(5));
        assertEquals("Bit Depth", handler.getHeader(6));
    }

    @Test
    void getFileItem_formatsImageColumns() {
        MessagePak metadata = MessagePak.newEmpty();
        metadata.putLong("/ImageWidth", 4032);
        metadata.putLong("/ImageHeight", 3024);
        metadata.putLong("/ImageTakenAtMillis", epochMillis(2026, 1, 2, 3, 4));
        metadata.putString("/CameraMake", "Canon");
        metadata.putString("/CameraModel", "EOS 80D");
        metadata.putLong("/ImageOrientation", 6);
        metadata.putLong("/ImageBitDepth", 24);

        ColumnSortable<String> item = new ClientImageHandleObject()
                .getFileItem(new FileRecord("photo.jpg", metadata));

        assertEquals("4032 x 3024", item.getValueOfColumn(2).toString());
        assertEquals("2026-01-02 03:04", item.getValueOfColumn(3).toString());
        assertEquals("Canon EOS 80D", item.getValueOfColumn(4).toString());
        assertEquals("90 CW", item.getValueOfColumn(5).toString());
        assertEquals("24-bit", item.getValueOfColumn(6).toString());
    }

    @Test
    void getFileItem_missingValuesShowUnknown() {
        ColumnSortable<String> item = new ClientImageHandleObject()
                .getFileItem(new FileRecord("photo.jpg", MessagePak.newEmpty()));

        assertEquals("-", item.getValueOfColumn(2).toString());
        assertEquals("-", item.getValueOfColumn(3).toString());
        assertEquals("-", item.getValueOfColumn(4).toString());
        assertEquals("-", item.getValueOfColumn(5).toString());
        assertEquals("-", item.getValueOfColumn(6).toString());
    }

    @Test
    void getFolderItem_imageColumnsShowUnknown() {
        ColumnSortable<String> item = new ClientImageHandleObject().getFolderItem("Photos");

        assertEquals("-", item.getValueOfColumn(2).toString());
        assertEquals("-", item.getValueOfColumn(3).toString());
        assertEquals("-", item.getValueOfColumn(4).toString());
        assertEquals("-", item.getValueOfColumn(5).toString());
        assertEquals("-", item.getValueOfColumn(6).toString());
    }

    @Test
    void getSearchItem_formatsImageColumnsFromStrings() {
        long taken = epochMillis(2026, 1, 2, 3, 4);
        MCListItemInterface<SearchResult> item = new ClientImageHandleObject()
                .getSearchItem(new StubSearchResult(Map.of(
                        "/ImageWidth", "1024",
                        "/ImageHeight", "768",
                        "/ImageTakenAtMillis", Long.toString(taken),
                        "/CameraMake", "Apple",
                        "/CameraModel", "iPhone 12",
                        "/ImageOrientation", "1",
                        "/ImageBitDepth", "24")));

        assertEquals("1024 x 768", item.getValueOfColumn(2).toString());
        assertEquals("2026-01-02 03:04", item.getValueOfColumn(3).toString());
        assertEquals("Apple iPhone 12", item.getValueOfColumn(4).toString());
        assertEquals("Normal", item.getValueOfColumn(5).toString());
        assertEquals("24-bit", item.getValueOfColumn(6).toString());
    }

    @Test
    void getSearchItem_missingValuesShowUnknown() {
        MCListItemInterface<SearchResult> item = new ClientImageHandleObject()
                .getSearchItem(new StubSearchResult(Map.of()));

        assertEquals("-", item.getValueOfColumn(2).toString());
        assertEquals("-", item.getValueOfColumn(3).toString());
        assertEquals("-", item.getValueOfColumn(4).toString());
        assertEquals("-", item.getValueOfColumn(5).toString());
        assertEquals("-", item.getValueOfColumn(6).toString());
    }

    private static long epochMillis(int year, int month, int day, int hour, int minute) {
        return LocalDateTime.of(year, month, day, hour, minute)
                .atZone(ZoneId.systemDefault())
                .toInstant()
                .toEpochMilli();
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
            return "photo.jpg";
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
