package com.myster.client.stream.msdownload;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.InetAddress;
import java.net.SocketException;
import java.nio.file.Path;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mockito;
import org.mockito.stubbing.Answer;

import com.myster.client.stream.MysterDataInputStream;
import com.myster.client.stream.MysterDataOutputStream;
import com.myster.client.stream.msdownload.InternalSegmentDownloader.SocketFactory;
import com.myster.identity.Identity;
import com.myster.mml.RobustMML;
import com.myster.net.MysterAddress;
import com.myster.net.MysterSocket;
import com.myster.search.MysterFileStub;
import com.myster.type.MysterType;

public class TestInternalSegmentDownloader {
    private static final int SEGMENT_SIZE = 1024 * 1024 * 2;

    private byte[] data;
    
    
    // JUnit 5 will automatically create and clean up this temporary directory
    @TempDir
    static Path tempDir;
    static Identity identity; 
    
    @BeforeAll
    static void beforeAll() {
        identity = new Identity("TestMultiSourceDownload", tempDir.toFile());
    }


    @BeforeEach
    public void setUp() throws IOException {
        var bout = new ByteArrayOutputStream();
        try (var out = new MysterDataOutputStream(bout)) {
            out.write(1); // protocol is understood
            out.write(1); // yes we have that file


            // Your queue position is - 1
            RobustMML mml = new RobustMML();
            mml.put(com.myster.server.stream.MultiSourceSender.QUEUED_PATH,"1");
            mml.put(com.myster.server.stream.MultiSourceSender.MESSAGE_PATH, "You are queued");
            out.writeUTF(mml.toString());

            // Your queue position is - 0
            RobustMML mml2 = new RobustMML();
            mml2.put(com.myster.server.stream.MultiSourceSender.QUEUED_PATH,"0");
            mml2.put(com.myster.server.stream.MultiSourceSender.MESSAGE_PATH, "You are queued");
            out.writeUTF(mml2.toString());

            // check for sync
            out.writeInt(6669);
            out.write('d');

            out.writeLong(SEGMENT_SIZE);

            // setup data block here
            byte[] dataToLoad = new byte[SEGMENT_SIZE];
            for (int i = 0; i < SEGMENT_SIZE; i++) {
                dataToLoad[i] = (byte) (i % 256);
            }
            out.write(dataToLoad);

            out.writeUTF(mml2.toString());
            
            // check for sync
            out.writeInt(6669);
            out.write('d');

            out.writeLong(3);
            out.write(dataToLoad, 0, 3);
        }
        data = bout.toByteArray();
    }

    @Test
    public void testBasic() throws IOException {
        Controller controller = mock(Controller.class);
        WorkSegment[] workSegments =
                new WorkSegment[] { new WorkSegment(0, SEGMENT_SIZE), new WorkSegment(SEGMENT_SIZE, 3) };
                int[] workSegmentCounter = new int[1];
                when(controller.getNextWorkSegment(anyInt()))
                        .thenAnswer((Answer<WorkSegment>) _ -> {
                            if (workSegmentCounter[0] > 1) {
                                return new WorkSegment(0, 0);
                            }
                            // Custom logic based on the argument
                            // Create appropriate WorkSegment based on the
                            // argument
                            return workSegments[workSegmentCounter[0]++];
                        });
        Mockito.doNothing().when(controller).receiveExtraSegments(any(WorkSegment[].class));
        Mockito.doNothing().when(controller).receiveDataBlock(any(DataBlock.class));
        when(controller.isOkToQueue()).thenReturn(true);
        when(controller.removeDownload(any(SegmentDownloader.class))).thenReturn(true);
        // stuff missing

        ByteArrayOutputStream bout = new ByteArrayOutputStream();

        SocketFactory socketFactory = new SocketFactory() {
            public MysterSocket makeStreamConnection(MysterAddress ip) throws IOException {
                return new FakeMysterSocket(new MysterDataInputStream(new ByteArrayInputStream(data)),
                                            new MysterDataOutputStream(bout));
            }
        };


        final MysterType mysterType = new MysterType(identity.getMainIdentity().get().getPublic());
        final String TEST_FILENAME = "Filename";
        InternalSegmentDownloader internalSegmentDownloader =
                new InternalSegmentDownloader(controller,
                                              socketFactory,
                                              new MysterFileStub(MysterAddress.createMysterAddress("127.0.0.1"),
                                                                 mysterType,
                                                                 TEST_FILENAME),
                                              2 * 2014);

        internalSegmentDownloader.run();

        byte[] byteArray = bout.toByteArray();

        MysterDataInputStream dataSendToServer = new MysterDataInputStream(new ByteArrayInputStream(byteArray));

        assertEquals(dataSendToServer.readInt(), com.myster.server.stream.MultiSourceSender.SECTION_NUMBER);

        assertEquals(dataSendToServer.readType(), mysterType);
        assertEquals(dataSendToServer.readUTF(), TEST_FILENAME);

        assertEquals(dataSendToServer.readLong(), 0);
        assertEquals(dataSendToServer.readLong(), 2097152);

        assertEquals(dataSendToServer.readLong(), 2097152);
        assertEquals(dataSendToServer.readLong(), 3);

        assertEquals(-1, dataSendToServer.read());
        
        dataSendToServer.close();
    }
};

class FakeMysterSocket extends MysterSocket {
    public FakeMysterSocket(MysterDataInputStream i, MysterDataOutputStream o) {
        super(i, o);
    }

    public InetAddress getInetAddress() {
        throw new RuntimeException("Not implemented");
    }

    public InetAddress getLocalAddress() {
        throw new RuntimeException("Not implemented");
    }

    public int getPort() {
        throw new RuntimeException("Not implemented");
    }

    public int getLocalPort() {
        throw new RuntimeException("Not implemented");
    }

    public MysterDataInputStream getInputStream() throws IOException {
        return in;
    }

    public MysterDataOutputStream getOutputStream() throws IOException {
        return out;
    }

    public void setSoLinger(boolean on, int val) throws SocketException {
        throw new RuntimeException("Not implemented");
    }

    public int getSoLinger() throws SocketException {
        throw new RuntimeException("Not implemented");
    }

    public void setSoTimeout(int timeout) throws SocketException {
        throw new RuntimeException("Not implemented");
    }

    public int getSoTimeout() throws SocketException {
        throw new RuntimeException("Not implemented");
    }

    public void close() throws IOException {
    }

    public String toString() {
        return "FakeMysterSocket";
    }
}
