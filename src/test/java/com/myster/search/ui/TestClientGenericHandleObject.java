package com.myster.search.ui;

import static com.myster.search.ui.MetadataColumnTestUtils.searchResult;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.Map;

import org.junit.jupiter.api.Test;

import com.general.mclist.MCListItemInterface;
import com.myster.search.SearchResult;

class TestClientGenericHandleObject {

    @Test
    void getSearchItem_fileSizeReadsLongValues() throws Exception {
        long videoSize = 4_294_967_296L;

        MCListItemInterface<SearchResult> item = new ClientGenericHandleObject()
                .getSearchItem(searchResult(Map.of("/size", videoSize)));

        assertEquals(videoSize, item.getValueOfColumn(1).getValue());
    }
}
