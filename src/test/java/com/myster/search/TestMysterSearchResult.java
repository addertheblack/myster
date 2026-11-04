package com.myster.search;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;

import java.net.InetAddress;
import java.util.Arrays;
import java.util.Date;
import java.util.Set;

import javax.swing.SwingUtilities;

import org.junit.jupiter.api.Test;

import com.general.mclist.MCListItemInterface;
import com.myster.mml.MessagePak;
import com.myster.mml.ReadOnlyMessagePak;
import com.myster.net.MysterAddress;
import com.myster.net.client.MysterProtocol;
import com.myster.search.ui.ClientGenericHandleObject;
import com.myster.search.ui.ServerStatsFromCache;
import com.myster.type.MysterType;
import com.myster.ui.MysterFrameContext;

class TestMysterSearchResult {
    @Test
    void publishesAndReplacesTypedStatsForAnExistingRowAndClearsToEmpty() throws Exception {
        MysterSearchResult result = result();
        MCListItemInterface<?>[] row = new MCListItemInterface<?>[1];
        ReadOnlyMessagePak[] retained = new ReadOnlyMessagePak[1];
        SwingUtilities.invokeAndWait(() -> {
            assertTrue(result.getMetaData().getLong("/size").isEmpty());
            assertNull(result.getMetaData("/size"));
            assertArrayEquals(new String[0], result.getKeyList());
            assertFalse(result.getMetaData() instanceof MessagePak);
            row[0] = new ClientGenericHandleObject().getSearchItem(result);
            assertEquals(-1L, row[0].getValueOfColumn(1).getValue());
        });

        MessagePak first = MessagePak.newEmpty();
        first.putLong("/size", 4_294_967_296L);
        result.setFileStats(first);
        SwingUtilities.invokeAndWait(() -> {
            retained[0] = result.getMetaData();
            assertEquals(4_294_967_296L, row[0].getValueOfColumn(1).getValue());
        });

        MessagePak second = MessagePak.newEmpty();
        second.putLong("/size", 10);
        SwingUtilities.invokeAndWait(() -> {
            result.setFileStats(second);
            assertEquals(10L, row[0].getValueOfColumn(1).getValue());
            assertEquals(4_294_967_296L, retained[0].getLong("/size").orElseThrow());
            result.setFileStats(null);
            assertEquals(-1L, row[0].getValueOfColumn(1).getValue());
            assertArrayEquals(new String[0], result.getKeyList());
        });
    }

    @Test
    void preservesDisplayConversionAndRootLeafKeysWithProtectedTypedValues() throws Exception {
        MessagePak pack = MessagePak.newEmpty();
        pack.putLong("/size", 123);
        pack.putString("/title", "hello");
        pack.putDate("/date", new Date(456));
        pack.putBoolean("/boolean", true);
        pack.putDouble("/double", 1.5);
        pack.putByteArray("/bytes", (byte) 1);
        pack.putObjectArray("/objects", new Object[] {"hello", 123L});
        pack.putString("/directory/child", "not a root leaf");
        MysterSearchResult result = result();
        result.setFileStats(pack);

        SwingUtilities.invokeAndWait(() -> {
            Set<String> keys = Set.of("/size", "/title", "/date", "/boolean", "/double", "/bytes", "/objects");
            assertEquals(keys, Set.copyOf(Arrays.asList(result.getKeyList())));
            for (String key : keys) {
                assertEquals(pack.get(key).orElseThrow(), result.getMetaData(key));
            }
            assertNull(result.getMetaData("/absent"));
            assertFalse(result.getMetaData() instanceof MessagePak);
            assertEquals(123L, result.getMetaData().getLong("/size").orElseThrow());
            result.getMetaData().getByteArray("/bytes").orElseThrow()[0] = 99;
            result.getMetaData().getDate("/date").orElseThrow().setTime(99);
            assertEquals(1, pack.getByteArray("/bytes").orElseThrow()[0]);
            assertEquals(456, pack.getDate("/date").orElseThrow().getTime());
        });
    }

    private static MysterSearchResult result() {
        return new MysterSearchResult(mock(MysterProtocol.class), mock(HashCrawlerManager.class),
                mock(MysterFrameContext.class), new MysterFileStub(
                        new MysterAddress(InetAddress.getLoopbackAddress()),
                        new MysterType(new byte[16]), "file"), mock(ServerStatsFromCache.class));
    }
}
