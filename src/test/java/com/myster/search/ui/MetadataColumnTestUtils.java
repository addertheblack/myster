package com.myster.search.ui;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.util.Map;

import com.myster.mml.MessagePak;
import com.myster.search.SearchResult;

/** Builds typed wire fixtures and mocks only the search-row boundary. */
final class MetadataColumnTestUtils {
    private MetadataColumnTestUtils() {}

    static SearchResult searchResult(Map<String, Object> values) throws IOException {
        MessagePak metadata = MessagePak.newEmpty();
        values.forEach((key, value) -> {
            switch (value) {
                case Long number -> metadata.putLong(key, number);
                case String text -> metadata.putString(key, text);
                default -> throw new IllegalArgumentException("Unsupported test fixture: " + value);
            }
        });
        return searchResult(MessagePak.fromBytes(metadata.toBytes()));
    }

    static SearchResult searchResult(MessagePak metadata) {
        SearchResult result = mock(SearchResult.class);
        when(result.getName()).thenReturn("file");
        when(result.getMetaData()).thenReturn(metadata.asReadOnly());
        return result;
    }
}
