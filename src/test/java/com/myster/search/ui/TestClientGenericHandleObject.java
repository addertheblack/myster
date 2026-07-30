package com.myster.search.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.nio.file.Path;
import java.util.Map;
import java.util.function.Consumer;

import org.junit.jupiter.api.Test;

import com.general.mclist.MCListItemInterface;
import com.myster.net.MysterAddress;
import com.myster.net.client.MysterProtocol;
import com.myster.net.stream.client.msdownload.DownloadStartException;
import com.myster.search.SearchResult;
import com.myster.tracker.MysterServer;

class TestClientGenericHandleObject {

    @Test
    void getSearchItem_fileSizeParsesLongValues() {
        long videoSize = 4_294_967_296L;

        MCListItemInterface<SearchResult> item = new ClientGenericHandleObject()
                .getSearchItem(new StubSearchResult(Map.of("/size", Long.toString(videoSize))));

        assertEquals(videoSize, item.getValueOfColumn(1).getValue());
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
            return "video.mp4";
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
