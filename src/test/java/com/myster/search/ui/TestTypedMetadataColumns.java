package com.myster.search.ui;

import static com.myster.search.ui.MetadataColumnTestUtils.searchResult;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Stream;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import com.general.mclist.ColumnSortable;
import com.general.mclist.SortableLong;
import com.myster.client.ui.FileListerThread.FileRecord;
import com.myster.mml.MessagePak;
import com.myster.mml.ReadOnlyMessagePak;
import com.myster.search.SearchResult;

class TestTypedMetadataColumns {
    private static final List<String> NUMERIC_KEYS = List.of("/size", "/BitRate", "/Hz", "/LengthSec",
            "/ImageWidth", "/ImageHeight", "/ImageTakenAtMillis", "/ImageOrientation", "/ImageBitDepth",
            "/VideoLengthSec", "/VideoWidth", "/VideoHeight", "/VideoBitRate");
    private static final List<String> TEXT_KEYS = List.of("/ID3Name", "/Artist", "/Album",
            "/CameraMake", "/CameraModel", "/VideoCodec");

    static Stream<Arguments> handlers() {
        return Stream.of(
                Arguments.of(new ClientGenericHandleObject(), new int[] {1}),
                Arguments.of(new ClientMPG3HandleObject(), new int[] {1, 2, 3, 7}),
                Arguments.of(new ClientImageHandleObject(), new int[] {1, 2, 3, 5, 6}),
                Arguments.of(new ClientVideoHandleObject(), new int[] {1, 2, 3, 5}));
    }

    @ParameterizedTest
    @MethodSource("handlers")
    void searchAndBrowsingShareFormattingAndSortNumericallyWithoutStringConversion(
            FileTypeColumnHandler handler, int[] numericColumns) throws Exception {
        MessagePak smaller = typedValues(2);
        MessagePak larger = typedValues(10);
        ReadOnlyMessagePak view = spy(larger.asReadOnly());
        SearchResult result = searchResult(larger);
        when(result.getMetaData()).thenReturn(view);
        ColumnSortable<?> search = handler.getSearchItem(result);
        ColumnSortable<?> file = handler.getFileItem(new FileRecord("file", larger));
        ColumnSortable<?> small = handler.getSearchItem(searchResult(smaller));

        for (int column = 0; column < handler.getColumnCount(); column++) {
            assertEquals(file.getValueOfColumn(column).toString(), search.getValueOfColumn(column).toString());
            assertEquals(file.getValueOfColumn(column).getValue(), search.getValueOfColumn(column).getValue());
        }
        for (int column : numericColumns) {
            SortableLong low = assertInstanceOf(SortableLong.class, small.getValueOfColumn(column));
            SortableLong high = assertInstanceOf(SortableLong.class, search.getValueOfColumn(column));
            assertTrue(low.isLessThan(high), "numeric order at column " + column);
        }
        verify(result, never()).getMetaData(anyString());
        verify(view, never()).get(anyString());
    }

    @ParameterizedTest
    @MethodSource("handlers")
    void rowsReadMetadataInstalledAfterTheirCreationAndReplacement(
            FileTypeColumnHandler handler, int[] numericColumns) throws Exception {
        AtomicReference<ReadOnlyMessagePak> current =
                new AtomicReference<>(MessagePak.newEmpty().asReadOnly());
        SearchResult result = mock(SearchResult.class);
        when(result.getName()).thenReturn("file");
        when(result.getMetaData()).thenAnswer(_ -> current.get());
        ColumnSortable<?> row = handler.getSearchItem(result);
        for (int column : numericColumns) {
            assertEquals(-1L, row.getValueOfColumn(column).getValue());
        }

        for (long value : new long[] {2, 10}) {
            MessagePak pack = typedValues(value);
            current.set(pack.asReadOnly());
            ColumnSortable<?> expected = handler.getFileItem(new FileRecord("file", pack));
            for (int column : numericColumns) {
                assertEquals(expected.getValueOfColumn(column).getValue(), row.getValueOfColumn(column).getValue());
            }
        }
    }

    @ParameterizedTest
    @MethodSource("handlers")
    void malformedRemoteFieldsBehaveLikeMissingFields(
            FileTypeColumnHandler handler, int[] numericColumns) throws Exception {
        ColumnSortable<?> emptySearch = handler.getSearchItem(searchResult(MessagePak.newEmpty()));
        ColumnSortable<?> emptyFile = handler.getFileItem(new FileRecord("file", MessagePak.newEmpty()));
        for (int scenario = 0; scenario < 4; scenario++) {
            MessagePak writer = MessagePak.newEmpty();
            for (String key : NUMERIC_KEYS) {
                switch (scenario) {
                    case 0 -> writer.putString(key, "10");
                    case 1 -> writer.putBoolean(key, true);
                    case 2 -> writer.putLong(key + "/child", 10);
                    case 3 -> { /* absent */ }
                    default -> fail("Unexpected scenario");
                }
            }
            for (String key : TEXT_KEYS) {
                writer.putLong(key, 10);
            }
            MessagePak remote = MessagePak.fromBytes(writer.toBytes());
            ColumnSortable<?> search = handler.getSearchItem(searchResult(remote));
            ColumnSortable<?> file = handler.getFileItem(new FileRecord("file", remote));
            for (int column = 1; column < handler.getColumnCount(); column++) {
                assertEquals(emptySearch.getValueOfColumn(column).toString(), search.getValueOfColumn(column).toString());
                assertEquals(emptyFile.getValueOfColumn(column).toString(), file.getValueOfColumn(column).toString());
                assertEquals(emptySearch.getValueOfColumn(column).getValue(), search.getValueOfColumn(column).getValue());
                assertEquals(emptyFile.getValueOfColumn(column).getValue(), file.getValueOfColumn(column).getValue());
            }
        }
    }

    @ParameterizedTest
    @MethodSource("handlers")
    void blankTextAndInvalidNumericValuesPreserveBrowsingParity(
            FileTypeColumnHandler handler, int[] numericColumns) throws Exception {
        for (long invalid : new long[] {-1, 0}) {
            MessagePak writer = MessagePak.newEmpty();
            NUMERIC_KEYS.forEach(key -> writer.putLong(key, invalid));
            TEXT_KEYS.forEach(key -> writer.putString(key, " "));
            MessagePak remote = MessagePak.fromBytes(writer.toBytes());
            ColumnSortable<?> search = handler.getSearchItem(searchResult(remote));
            ColumnSortable<?> file = handler.getFileItem(new FileRecord("file", remote));
            for (int column : numericColumns) {
                assertEquals(file.getValueOfColumn(column).getValue(), search.getValueOfColumn(column).getValue());
                assertEquals(file.getValueOfColumn(column).toString(), search.getValueOfColumn(column).toString());
            }
            if (handler instanceof ClientMPG3HandleObject) {
                // Audio historically preserves blank search tags but normalizes browse tags.
                assertEquals(" ", search.getValueOfColumn(4).toString());
                assertEquals("-", file.getValueOfColumn(4).toString());
            } else {
                for (int column = 2; column < handler.getColumnCount(); column++) {
                    assertEquals(file.getValueOfColumn(column).toString(), search.getValueOfColumn(column).toString());
                }
            }
        }
    }

    private static MessagePak typedValues(long number) throws Exception {
        MessagePak writer = MessagePak.newEmpty();
        NUMERIC_KEYS.forEach(key -> writer.putLong(key, number));
        TEXT_KEYS.forEach(key -> writer.putString(key, "text"));
        return MessagePak.fromBytes(writer.toBytes());
    }
}
