package com.myster.search.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.Map;

import org.junit.jupiter.api.Test;

import com.general.mclist.ColumnSortable;
import com.general.mclist.MCListItemInterface;
import com.myster.client.ui.FileListerThread.FileRecord;
import com.myster.mml.MessagePak;
import com.myster.net.MysterAddress;
import com.myster.net.client.MysterProtocol;
import com.myster.search.SearchResult;
import com.myster.tracker.MysterServer;

class TestClientMPG3HandleObject {

    @Test
    void getColumnCount_returns8() {
        assertEquals(8, new ClientMPG3HandleObject().getColumnCount());
    }

    @Test
    void getHeader_lengthColumn() {
        assertEquals("Length", new ClientMPG3HandleObject().getHeader(7));
    }

    @Test
    void getFileItem_lengthColumnFormatsSeconds() {
        MessagePak metadata = MessagePak.newEmpty();
        metadata.putLong("/LengthSec", 204);

        ColumnSortable<String> item = new ClientMPG3HandleObject()
                .getFileItem(new FileRecord("song.mp3", metadata));

        assertEquals("3:24", item.getValueOfColumn(7).toString());
    }

    @Test
    void getFileItem_missingLengthShowsUnknown() {
        ColumnSortable<String> item = new ClientMPG3HandleObject()
                .getFileItem(new FileRecord("song.mp3", MessagePak.newEmpty()));

        assertEquals("-", item.getValueOfColumn(7).toString());
    }

    @Test
    void getFolderItem_lengthColumnShowsUnknown() {
        ColumnSortable<String> item = new ClientMPG3HandleObject().getFolderItem("Album");

        assertEquals("-", item.getValueOfColumn(7).toString());
    }

    @Test
    void getSearchItem_lengthColumnFormatsSeconds() {
        MCListItemInterface<SearchResult> item = new ClientMPG3HandleObject()
                .getSearchItem(new StubSearchResult(Map.of("/LengthSec", "204")));

        assertEquals("3:24", item.getValueOfColumn(7).toString());
    }

    @Test
    void getSearchItem_missingLengthShowsUnknown() {
        MCListItemInterface<SearchResult> item = new ClientMPG3HandleObject()
                .getSearchItem(new StubSearchResult(Map.of()));

        assertEquals("-", item.getValueOfColumn(7).toString());
    }

    private record StubSearchResult(Map<String, String> metadata) implements SearchResult {
        @Override
        public void download() {}

        @Override
        public void downloadTo() {}

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
            return "song.mp3";
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
