package com.myster.client.stream;

import java.io.DataInputStream;
import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Future;

import com.general.thread.CallListener;
import com.general.util.AnswerDialog;
import com.general.util.Util;
import com.myster.filemanager.FileTypeList;
import com.myster.hash.FileHash;
import com.myster.mml.MMLException;
import com.myster.mml.RobustMML;
import com.myster.net.MysterAddress;
import com.myster.net.MysterSocket;
import com.myster.net.MysterSocketFactory;
import com.myster.net.MysterClientSocketPool;
import com.myster.search.HashCrawlerManager;
import com.myster.search.MysterFileStub;
import com.myster.type.MysterType;
import com.myster.ui.MysterFrameContext;
import com.myster.util.FileProgressWindow;

/**
 * Contains many of the more common (simple) stream based connection sections.
 */
class StandardSuite {
    // Vector of strings
    public static List<String> getSearch(MysterSocket socket, MysterType searchType, String searchString)
            throws IOException {
        List<String> searchResults = new ArrayList<>();

        socket.out.writeInt(35);
        socket.out.flush();

        checkProtocol(socket.in);

        socket.out.write(searchType.getBytes());
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
        
        socket.out.write(searchType.getBytes());

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
            mysterTypes[i] = new MysterType(socket.in.readInt());
        }

        return mysterTypes;
    }

    
    public static RobustMML getServerStats(MysterSocket socket) throws IOException {
        socket.setSoTimeout(90000); // ? Probably important in some way or
        // other.

        socket.out.writeInt(101);
        socket.out.flush();

        checkProtocol(socket.in);

        try {
            return new RobustMML(socket.in.readUTF());
        } catch (MMLException ex) {
            throw new ProtocolException("Server sent a corrupt MML String");
        }
    }

    /**
     * downloadFile downloads a file by starting up a MultiSourceDownload or
     * Regular old style download whichever is appropriate.
     * <p>
     * THIS ROUTINE IS ASYNCHRONOUS!
     */
    public static void downloadFile(MysterFrameContext c, final HashCrawlerManager crawlerManager, final MysterAddress ip, final MysterFileStub stub) {
        (new DownloadThread(c, crawlerManager, ip, stub)).start();
    }

    private static class DownloadThread extends com.myster.util.MysterThread {
        private final MysterAddress ip;
        private final MysterFileStub stub;
        private final HashCrawlerManager crawlerManager;
        private final MysterFrameContext context;

        public DownloadThread(MysterFrameContext c, HashCrawlerManager crawlerManager, MysterAddress ip, MysterFileStub stub) {
            this.context = c;
            this.ip = ip;
            this.stub = stub;
            this.crawlerManager = crawlerManager;
        }

        public void run() {
            final FileProgressWindow[] progressArray = new FileProgressWindow[1];

            try {
                Util.invokeAndWait(new Runnable() {
                    public void run() {
                        progressArray[0] = new com.myster.util.FileProgressWindow(context, "Connecting..");
                        FileProgressWindow progress = progressArray[0];
                        progress.setTitle("Downloading " + stub.getName());
                        progress.setText("Starting...");

                        progress.show();
                    }
                });
            } catch (InterruptedException e1) {
                Util.invokeLater(new Runnable() {
                    public void run() {
                        progressArray[0].close();
                    }
                });
                return;
            }

            progressArray[0].addWindowListener(new java.awt.event.WindowAdapter() {
                public void windowClosing(java.awt.event.WindowEvent e) {
                    if ((msDownload != null)
                            && (!MultiSourceUtilities.confirmCancel(progressArray[0], msDownload)))
                        return;

                    StandardSuite.DownloadThread.this.flagToEnd();

                    progressArray[0].setVisible(false);
                }
            });

            MysterSocket socket = null;
            try {
                socket = MysterSocketFactory.makeStreamConnection(ip);
            } catch (Exception ex) {
                com.general.util.AnswerDialog.simpleAlert("Could not connect to server.");
                return;
            }

            try {
                downloadFile(socket, crawlerManager, stub, progressArray[0]);
            } catch (IOException ex) {
                // ..
            } finally {
                disconnectWithoutException(socket);
            }

        }

        // should not be public
        private void downloadFile(final MysterSocket socket,
                                  final HashCrawlerManager crawlerManager,
                                  final MysterFileStub stub,
                                  final FileProgressWindow progress)
                throws IOException {

            try {
                progressSetTextThreadSafe(progress, "Getting File Statistics...");

                if (endFlag)
                    return;
                RobustMML mml = getFileStats(socket, stub);

                progressSetTextThreadSafe(progress, "Trying to use multi-source download...");

                final boolean DONT_USE_MULTISOURCE = false;
                if (DONT_USE_MULTISOURCE)
                    throw new IOException("Toss and catch: Multisource download disabled");

                if (endFlag)
                    return;

                final File theFile = MultiSourceUtilities.getFileToDownloadTo(stub, progress);
                if (theFile == null) {
                    progressSetTextThreadSafe(progress, "User cancelled...");
                    return;
                }
                if (endFlag)
                    return;

                if (!tryMultiSourceDownload(stub, crawlerManager, progress, mml, theFile))
                    throw new IOException("Toss and catch");
            } catch (IOException ex) {
                ex.printStackTrace();

                try {
                    progressSetTextThreadSafe(progress, "Trying to use normal download...");

                    synchronized (StandardSuite.DownloadThread.this) {
                        if (endFlag)
                            return;

                        secondDownload = new DownloaderThread(MysterSocketFactory
                                .makeStreamConnection(stub.getMysterAddress()), stub, progress);
                    }
                    secondDownload.run();
                } catch (IOException exp) {
                    progressSetTextThreadSafe(progress,
                            "Could not download file by either method...");
                }
            }
        }

        @SuppressWarnings("resource")
        private synchronized boolean tryMultiSourceDownload(final MysterFileStub stub,
                                                            HashCrawlerManager crawlerManager,
                                                            final FileProgressWindow progress,
                                                            RobustMML mml,
                                                            final File theFile)
                throws IOException {
            FileHash hash = MultiSourceUtilities.getHashFromStats(mml);
            if (hash == null)
                return false;

            long fileLengthFromStats = MultiSourceUtilities.getLengthFromStats(mml);
            MSPartialFile partialFile;
            try {
                partialFile = MSPartialFile.create(stub.getName(), new File(theFile.getParent()),
                        stub.getType(), MultiSourceDownload.DEFAULT_CHUNK_SIZE,
                        new FileHash[] { hash }, fileLengthFromStats);
            } catch (IOException ex) {
                AnswerDialog
                        .simpleAlert(
                                progress,
                                "I can't create a partial file because of: \n\n"
                                        + ex.getMessage()
                                        + "\n\nIf I can't make this partial file I can't use multi-source download.");
                throw ex;
            }

            msDownload = new MultiSourceDownload(new RandomAccessFile(theFile, "rw"), crawlerManager,
                    new MSDownloadHandler(progress, theFile, partialFile), partialFile);
            msDownload.setInitialServers(new MysterFileStub[] { stub });
            msDownload.start();
            
            return true;
        }

        private static void progressSetTextThreadSafe(final FileProgressWindow progress,
                                               final String string) {
            Util.invokeLater(new Runnable() {
                public void run() {
                    progress.setText(string);
                }
            });
        }

        MultiSourceDownload msDownload;

        DownloaderThread secondDownload;

        boolean endFlag;

        public synchronized void flagToEnd() {
            endFlag = true;

            if (msDownload != null)
                msDownload.cancel();

            if (secondDownload != null)
                secondDownload.end();
        }

        public void end() {
            flagToEnd();

            // try {
            // join();
            // } catch (InterruptedException ex) {}
        }
    }

    public static RobustMML getFileStats(MysterAddress ip, MysterFileStub stub) throws IOException {
        try (MysterSocket socket = MysterSocketFactory.makeStreamConnection(ip)) {
            return getFileStats(socket, stub);
        }
    }

    public static RobustMML getFileStats(MysterSocket socket, MysterFileStub stub)
            throws IOException {
        socket.out.writeInt(77);

        checkProtocol(socket.in);

        socket.out.writeInt(stub.getType().getAsInt()); // this protocol sucks
        socket.out.writeUTF(stub.getName());

        try {
            return new RobustMML(socket.in.readUTF());
        } catch (MMLException ex) {
            throw new ProtocolException("Server sent a corrupt MML String.");
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

        socket.out.writeInt(type.getAsInt());

        for (int i = 0; i < hashes.length; i++) {
            socket.out.writeUTF(hashes[i].getHashName());

            socket.out.writeShort(hashes[i].getHashLength());

            byte[] byteArray = hashes[i].getBytes();

            socket.out.write(byteArray, 0, byteArray.length);
        }

        socket.out.writeUTF("");

        return FileTypeList.mergePunctuation(socket.in.readUTF());
    }

    public static void checkProtocol(DataInputStream in) throws IOException,
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
        // try {
        socket.out.writeInt(2);
        socket.in.read();
        // } catch (IOException ex) {}

        try {
            socket.close();
        } catch (Exception ex) {
            // nothing
        }
    }

    public static String[] getFileList(MysterSocket socket, MysterType type) throws IOException {
        socket.out.writeInt(78);
        checkProtocol(socket.in);

        socket.out.writeInt(type.getAsInt());

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
        } catch (Exception ex) {
            // nothing
        }

        try {
            socket.close();
        } catch (Exception ex) {
            // nothing
        }
    }

}