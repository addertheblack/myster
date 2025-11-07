package com.myster.net.stream.client;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executors;

import com.general.thread.AsyncContextList;
import com.general.thread.PromiseFutureList;
import com.myster.filemanager.FileTypeList;
import com.myster.hash.FileHash;
import com.myster.mml.MessagePack;
import com.myster.net.DisconnectException;
import com.myster.net.MysterAddress;
import com.myster.net.MysterSocket;
import com.myster.net.stream.client.msdownload.DownloadInitiator;
import com.myster.search.HashCrawlerManager;
import com.myster.search.MysterFileStub;
import com.myster.type.MysterType;
import com.myster.ui.MysterFrameContext;

/**
 * Contains many of the more common (simple) stream based connection sections. Most of these
 * connection sections have UDP transaction equivalents.
 */
public class StandardSuiteStream {
    public static List<String> getSearch(MysterSocket socket, MysterType searchType, String searchString)
            throws IOException {
        List<String> searchResults = new ArrayList<>();

        socket.out.writeInt(35);
        socket.out.flush();

        checkProtocol(socket.in);

        socket.out.writeType(searchType);
        socket.out.writeUTF(searchString);

        for (String temp = socket.in.readUTF(); !temp.equals(""); temp = socket.in.readUTF())
            searchResults.add(temp);

        return searchResults;
    }

    public static List<String> getTopServers(MysterSocket socket, MysterType searchType)
            throws IOException {
        List<String> ipList = new ArrayList<String>();

        socket.out.writeInt(10); // Get top ten the 10 is the command code...
        // not the length of the list!
        
        socket.out.flush();
        
        checkProtocol(socket.in);
        
        socket.out.writeType(searchType);

        for (String temp = socket.in.readUTF(); !temp.equals(""); temp = socket.in.readUTF()) {
            ipList.add(temp);
        }

        return ipList;
    }

    public static MysterType[] getTypes(MysterSocket socket) throws IOException {
        socket.out.writeInt(74);

        socket.out.flush();
        
        checkProtocol(socket.in);

        int numberOfTypes = socket.in.readInt();
        MysterType[] mysterTypes = new MysterType[numberOfTypes];

        for (int i = 0; i < numberOfTypes; i++) {
            mysterTypes[i] = socket.in.readType();
        }

        return mysterTypes;
    }

    
    public static MessagePack getServerStats(MysterSocket socket) throws IOException {
        socket.setSoTimeout(90000); // ? Probably important in some way or
        // other.

        socket.out.writeInt(101);
        socket.out.flush();

        checkProtocol(socket.in);

        return socket.in.readMessagePack();
    }

    /**
     * downloadFile downloads a file by starting up a MultiSourceDownload or
     * Regular old style download whichever is appropriate.
     * <p>
     * THIS ROUTINE IS ASYNCHRONOUS!
     */
    public static void downloadFile(MysterFrameContext c,
                                    final HashCrawlerManager crawlerManager,
                                    final MysterAddress ip,
                                    final MysterFileStub stub) {
        Executors.newVirtualThreadPerTaskExecutor().execute(new DownloadInitiator(c, crawlerManager, ip, stub));
    }

    public static MessagePack getFileStats(MysterAddress ip, MysterFileStub stub) throws IOException {
        try (MysterSocket socket = MysterSocketFactory.makeStreamConnection(ip)) {
            return getFileStats(socket, stub);
        }
    }

    /**
     * Gets the file stats for a particular file on a particular server.
     * Now uses MessagePack instead of MML
     */
    public static MessagePack getFileStats(MysterSocket socket, MysterFileStub stub)
            throws IOException {
        socket.out.writeInt(77);

        checkProtocol(socket.in);

        socket.out.writeType(stub.getType());
        socket.out.writeUTF(stub.getName());

        // Read the MessagePack data length and bytes
        return socket.in.readMessagePack();
    }
    
    public static PromiseFutureList<MessagePack> getFileStatsBatch(MysterSocket socket, MysterFileStub[] stubs)
            throws IOException {
        return PromiseFutureList.newPromiseFutureList(l -> {
            Executors.newVirtualThreadPerTaskExecutor().execute(() -> {
                try {
                    if (stubs.length == 0) {
                        l.done();
                        return;
                    }
                    
                    // All stubs must be of the same type for batch request
                    MysterType type = stubs[0].getType();
                    
                    // Send batch command (177)
                    socket.out.writeInt(177);
                    
                    // Send type
                    socket.out.writeType(type);
                    
                    // Send count
                    socket.out.writeInt(stubs.length);
                    
                    // Send all filenames
                    for (MysterFileStub stub : stubs) {
                        checkCancelled(l);
                        socket.out.writeUTF(stub.getName());
                    }
                    
                    socket.out.flush();
                    
                    // Read protocol check
                    checkProtocol(socket.in);
                    
                    // Read all responses
                    for (int i = 0; i < stubs.length; i++) {
                        checkCancelled(l);
                        l.addResult(socket.in.readMessagePack());
                    }
                } catch (Exception ex) {
                    l.setException(ex);
                }

                l.done();
            });
        });
    }

    private static void checkCancelled(AsyncContextList<MessagePack> l) throws DisconnectException {
        if (l.isCancelled()) {
            throw new DisconnectException();
        }
    }

    public static String getFileFromHash(MysterAddress ip, MysterType type, FileHash hash)
            throws IOException {
        return getFileFromHash(ip, type, new FileHash[] { hash });
    }

    public static String getFileFromHash(MysterAddress ip, MysterType type, FileHash[] hashes)
            throws IOException {
        MysterSocket socket = null;
        try {
            socket = MysterSocketFactory.makeStreamConnection(ip);
            return getFileFromHash(socket, type, hashes);
        } finally {
            disconnectWithoutException(socket);
        }
    }

    public static String getFileFromHash(MysterSocket socket, MysterType type, FileHash hash)
            throws IOException {
        return getFileFromHash(socket, type, new FileHash[] { hash });
    }

    // Returns "" if file is not found or name of file if file is found.
    public static String getFileFromHash(MysterSocket socket, MysterType type, FileHash[] hashes)
            throws IOException {
        socket.out.writeInt(150);

        checkProtocol(socket.in);

        socket.out.writeType(type);

        for (int i = 0; i < hashes.length; i++) {
            socket.out.writeUTF(hashes[i].getHashName());

            socket.out.writeShort(hashes[i].getHashLength());

            byte[] byteArray = hashes[i].getBytes();

            socket.out.write(byteArray, 0, byteArray.length);
        }

        socket.out.writeUTF("");

        return FileTypeList.mergePunctuation(socket.in.readUTF());
    }

    public static void checkProtocol(MysterDataInputStream in) throws IOException,
            UnknownProtocolException { // this should have its own exception
        // type
        int err = in.read();

        if (err == -1)
            throw new IOException("Server disconnected");

        if (err != 1) {
            throw new UnknownProtocolException(err, "Protocol is not understood. (none 1 response)");
        }
    }

    public static void disconnect(MysterSocket socket) throws IOException {
        socket.out.writeInt(2);
        socket.in.read();

        try {
            socket.close();
        } catch (Exception _) {
            // nothing
        }
    }

    public static String[] getFileList(MysterSocket socket, MysterType type) throws IOException {
        socket.out.writeInt(78);
        checkProtocol(socket.in);

        socket.out.writeType(type);

        String[] fileList = new String[socket.in.readInt()];
        for (int i = 0; i < fileList.length; i++) {
            fileList[i] = socket.in.readUTF();
        }
        return fileList;
    }

    public static void disconnectWithoutException(MysterSocket socket) {
        try {
            socket.out.writeInt(2);
            socket.in.read();
        } catch (Exception _) {
            // nothing
        }

        try {
            socket.close();
        } catch (Exception _) {
            // nothing
        }
    }

}