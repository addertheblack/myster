package com.myster.client.stream.msdownload;

import static com.myster.client.stream.msdownload.MultiSourceDownload.toIoFile;

import java.awt.EventQueue;

/**
 * This class is here to encapsulate all the information related to a Myster
 * multi source download resumable download block file.
 *  
 */

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.util.List;

import com.general.util.AnswerDialog;
import com.myster.client.stream.msdownload.MultiSourceDownload.FileMover;
import com.myster.hash.FileHash;
import com.myster.hash.SimpleFileHash;
import com.myster.mml.MMLException;
import com.myster.mml.RobustMML;
import com.myster.search.HashCrawlerManager;
import com.myster.type.MysterType;
import com.myster.ui.MysterFrameContext;
import com.myster.util.FileProgressWindow;

public class MSPartialFile implements AutoCloseable {
    public static final String FILE_ENDING = ".p";

    //////////// STATIC SUB SYSTEM \\\\\\\\\\\\\\\\
    public static MSPartialFile recreate(File file) throws IOException {
        if (!file.exists())
            throw new IOException("File does not exist");

        RandomAccessFile maskFile = new RandomAccessFile(file, "rw");

        RobustMML mml;
        try {
            mml = new RobustMML(maskFile.readUTF());
        } catch (MMLException ex) {
            maskFile.close();
            throw new IOException("MML Meta data was badly formed. This file is corrupt.");
        }

        return new MSPartialFile(file, maskFile, new PartialFileHeader(mml, (int) maskFile
                .getFilePointer()));
    }

    public static MSPartialFile create(String filename, File path, MysterType type, int blockSize,
            FileHash[] hashes, long fileLength) throws IOException {
        File fileReference = new File(MultiSourceUtilities.getIncomingDirectory(), filename + FILE_ENDING);

        if (fileReference.exists()) {
            if (!fileReference.delete()) {
                throw new IOException(
                        "There's a file (" + fileReference + ") in the way and I can't delete it!");
            }
        }
        
        RandomAccessFile maskFile;
        
        try {
            maskFile = new RandomAccessFile(fileReference, "rw");
        } catch (IOException ex) {
            throw new IOException("File \"" + fileReference + "\" appears to be read only.");
        }
        PartialFileHeader header = new PartialFileHeader(filename, path, type, blockSize, hashes,
                fileLength);

        maskFile.write(header.toBytes());

        return new MSPartialFile(fileReference, maskFile, header);
    }

    public static MSPartialFile[] list() throws IOException {
        File incomingDir = MultiSourceUtilities.getIncomingDirectory();

        String[] file_list = incomingDir.list((File dir, String name) -> {
            if (!name.endsWith(".p"))
                return false;

            File file = new File(dir, name);

            if (file.isDirectory())
                return false;

            return true;
        });

        MSPartialFile[] msPartialFiles = new MSPartialFile[file_list.length];

        for (int i = 0; i < file_list.length; i++) {
            msPartialFiles[i] = recreate(new File(incomingDir, file_list[i]));
        }

        return msPartialFiles;
    }

    public static void restartDownloads(HashCrawlerManager crawlerManager, MysterFrameContext c) throws IOException {
        MSPartialFile[] files = list();

        for (int i = 0; i < files.length; i++) {
            try {
                startDownload(files[i], crawlerManager, c);
            } catch (IOException ex) {
                ex.printStackTrace();
            }
        }
    }

    private static FileProgressWindow showProgres(MysterFrameContext c, final String filename) {
        final FileProgressWindow progress = new FileProgressWindow(c);
        progress.setTitle("Downloading " + filename);
        progress.show();
        return progress;
    }

    //Resumable multisource driver.
    public static void startDownload(MSPartialFile partialFile, HashCrawlerManager crawlerManager, MysterFrameContext c) throws IOException {
        final String finalFileName = partialFile.getFilename() + ".i";
        final String pathToType = com.myster.filemanager.FileTypeListManager.getInstance()
                .getPathFromType(partialFile.getType());
        final FileProgressWindow progress = showProgres(c, partialFile.getFilename());
        String incompleteFilename = partialFile.getFilename() + ".i";
        File dir = partialFile.getPath();
        File file = new File(dir, incompleteFilename);

        //Humm, path to file does not exist.. check inside download folder.
        if (!file.exists()) {
            dir = new File(pathToType);
            file = new File(dir, incompleteFilename);
        }
        
        MultiSourceUtilities.debug("GMResuming:" + file);

        for (int loopCounter = 0; (loopCounter < 3)
                && ((!dir.exists()) || (!dir.isDirectory()) || (!file.exists()) || (!file.isFile())); loopCounter++) {
            final String DIALOG_PROMPT = "Where is the file " + partialFile.getFilename() + "?";

            final java.awt.FileDialog dialog =
                    new java.awt.FileDialog(progress, DIALOG_PROMPT, java.awt.FileDialog.LOAD);

            dialog.setVisible(true);

            if (dialog.getFile() == null)
                userCancelled(progress, partialFile); //always
            // throws
            // exception !

            if (!dialog.getFile().equals(finalFileName)) {
                final String YES_ANSWER = "Yes", NO_ANSWER = "No", CANCEL_ANSWER = "Cancel";
                String response = AnswerDialog.simpleAlert(
                        progress,
                        "The file name \n\n\""
                                + dialog.getFile()
                                + "\"\n\n is not the same name as \n\n\""
                                + finalFileName
                                + "\n\n. If this is not the right file, it will be "
                                + "rendered unusable. Are you sure you want to resume this download"
                                + " with this file?", new String[] { YES_ANSWER, NO_ANSWER,
                                CANCEL_ANSWER });

                if (response.equals(NO_ANSWER)) {
                    continue;
                } else if (response.equals(CANCEL_ANSWER)) {
                    userCancelled(progress, partialFile); //always
                    // throws
                    // exception !
                }
            }

            dir = new File(dialog.getDirectory());
            file = new File(dialog.getDirectory(), dialog.getFile());
        }

        FileMover fileMover = (f) -> {
            EventQueue.invokeLater(() -> {
                MultiSourceUtilities.moveFileToFinalDestination(f, progress);
            });
        };

        // TODO move this into MSDownload
        RandomAccessFile randomAccessFile = new RandomAccessFile(file, "rw");

        final MultiSourceDownload download =
                new MultiSourceDownload(toIoFile(randomAccessFile, file),
                                        crawlerManager,
                                        new MSDownloadHandler(progress),
                                        fileMover,
                                        partialFile);

        //there are no exceptions after this so that is why we
        // can
        // get away
        // with it.

        progress.addWindowListener(new java.awt.event.WindowAdapter() {
            public void windowClosing(java.awt.event.WindowEvent e) {
                if (!MultiSourceUtilities.confirmCancel(progress))
                    return;

                download.cancel();

                progress.setVisible(false);
            }
        });
        download.start();
    }

    private static class UserCanceledException extends IOException {
        public UserCanceledException() {
            super("User Cancelled");
        }
    }

    private static void userCancelled(FileProgressWindow progress, MSPartialFile file)
            throws UserCanceledException {
        progress.setVisible(false);
        file.done();
        throw new UserCanceledException();
    }

    ///////////////// OBJECT SYSTEM \\\\\\\\\\\\\\

    //private static final String filePath;
    private long offset = 0;

    private final File fileReference;
    private final RandomAccessFile maskFile;
    private final PartialFileHeader header;

    private MSPartialFile(File fileReference, RandomAccessFile maskFile, PartialFileHeader header) {
        this.maskFile = maskFile;
        this.header = header;
        this.fileReference = fileReference;

        this.offset = header.getOffset();
    }

    public RobustMML getCopyOfMetaData() {
        return new RobustMML(header.toMML());
    }

    public long getBlockSize() {
        return header.getBlockSize();
    }

    public FileHash[] getFileHashes() {
        return header.getFileHashes();
    }

    public FileHash getHash(String hash) {
        return header.getHash(hash);
    }

    public String getFilename() {
        return header.getFilename();
    }

    public MysterType getType() {
        return header.getType();
    }

    public long getFileLength() {
        return header.getFileLength();
    }

    /**
     * @return the directory the file was last seen in or the current directory.
     */
    private File getPath() {
        return header.getPath();
    }

    /**
     * @return the location in the file being downloaded of the first undownloaded block
     */
    public long getFirstUndownloadedBlock() throws IOException {
        maskFile.seek(offset);

        final int blockSize = 64 * 1024;

        final byte[] buffer = new byte[blockSize];

        int numberOfBlocks = (int) ((maskFile.length() - offset) / buffer.length);

        for (long blockCounter = 0; blockCounter <= numberOfBlocks; blockCounter++) {
            int currentBlockSize = (int) (blockCounter >= numberOfBlocks ? (maskFile.length() - offset)
                    % buffer.length
                    : buffer.length); //UNSAFE CAST

            if (currentBlockSize == 0)
                break;

            maskFile.readFully(buffer, 0, currentBlockSize);

            for (int i = 0; i < currentBlockSize; i++) {
                if (buffer[i] != ((byte) 0xFF))
                    return (8 * (i + (blockCounter * blockSize)));
            }
        }

        return 8 * (maskFile.length() - offset);
    }

    //    private boolean getBit(long bit) throws IOException {
    //        maskFile.seek(getSeek(bit));
    //        return (maskFile.read() & getMask(bit)) != 0;
    //    }

    public void setBit(long bit) throws IOException {
        long seek = getSeek(bit);

        maskFile.seek(seek);

        int myData = maskFile.read();

        if (myData == -1)
            myData = 0; // end of file yeah yeah yeah...

        maskFile.seek(seek);

        maskFile.write(myData | getMask(bit));
    }

    /**
     * @param blockNumber to lookup
     * @return get the bytes position to seek to in the MSPartial file of the block number
     */
    private long getSeek(long blockNumber) {
        return offset + (blockNumber / 8);
    }

    /**
     * @return get the mask to use to mask away everything except the bit position
     *      corresponding to the bit number assuming the seek has already been used
     * @see MSPartialFile#getSeek(long)
     */
    private static int getMask(long blockNumber) {
        return 0x80 >> (blockNumber % 8);
    }

    protected void finalize() throws Throwable {
        try {
            dispose();
        } finally {
            super.finalize();
        }
    }

    @Override
    public void close() throws IOException {
        maskFile.close();
    }

    public void dispose() {
        try {
            close();
        } catch (IOException ex) {
            // nothing
        }
    }

    public void done() {
        dispose();

        if (!fileReference.delete()) {
            MultiSourceUtilities.debug("Could not delete partial file.");
        }
    }

    private static class PartialFileHeader {
        private static final String FILENAME_PATH = "/Filename";
        private static final String BLOCK_SIZE_PATH = "/Block Size Path";
        private static final String HASHES_PATH = "/Hashes/";
        private static final String TYPE = "/Type";
        private static final String FILE_LENGTH = "/File Length";
        private static final String PATH = "/File Path";

        private final String filename;
        private final MysterType type;
        private final long blockSize;
        private final FileHash[] hashes;
        private final long fileLength;
        private final File path;
        private final int headerOffset;

        PartialFileHeader(RobustMML mml, int offset) throws IOException {
            this.headerOffset = offset;
            try {
                filename = mml.get(FILENAME_PATH);
                String string_blockSize = mml.get(BLOCK_SIZE_PATH);
                String string_length = mml.get(FILE_LENGTH);
                String string_type = mml.get(TYPE);
                String string_path = mml.get(PATH);

                assertNotNull(filename); //throws IOException on null
                assertNotNull(string_blockSize);
                assertNotNull(string_length);
                assertNotNull(string_type);

                blockSize = Integer.parseInt(string_blockSize);
                hashes = getHashesFromHeader(mml, HASHES_PATH);
                fileLength = Long.parseLong(string_length);
                type = new MysterType(Integer.parseInt(string_type));
                path = new File(string_path == null ? "" : string_path);
            } catch (NumberFormatException ex) {
                throw new IOException("" + ex);
            }
        }

        PartialFileHeader(String filename, File path, MysterType type, long blockSize,
                FileHash[] hashes, long fileLength) {
            this.filename = filename;
            this.type = type;
            this.blockSize = blockSize;
            this.hashes = hashes;
            this.fileLength = fileLength;
            this.path = path;
            this.headerOffset = toBytes().length;
        }

        public long getBlockSize() {
            return blockSize;
        }

        public String getFilename() {
            return filename;
        }

        public File getPath() {
            return path;
        }

        public MysterType getType() {
            return type;
        }

        public long getFileLength() {
            return fileLength;
        }

        public FileHash[] getFileHashes() {
            FileHash[] temp_hashes = new FileHash[hashes.length];

            for (int i = 0; i < temp_hashes.length; i++) {
                temp_hashes[i] = hashes[i];
            }

            return temp_hashes;
        }

        private void assertNotNull(Object o) throws IOException {
            if (o == null)
                throw new IOException("Unexpect null object");
        }

        /**
         * Gets the requested hash type. If it doesn't exist it returns null.
         */
        public FileHash getHash(String hashType) {
            FileHash[] hashes = getFileHashes();

            for (int i = 0; i < hashes.length; i++) {
                if (hashes[i].getHashName().equalsIgnoreCase(hashType))
                    return hashes[i];
            }

            MultiSourceUtilities.debug("Could not find hash of type " + hashType);
            
            return null;
        }

        public com.myster.mml.MML toMML() {
            RobustMML mml = new RobustMML();

            mml.put(FILENAME_PATH, filename);
            mml.put(BLOCK_SIZE_PATH, "" + blockSize);
            mml.put(FILE_LENGTH, "" + fileLength);
            mml.put(TYPE, "" + type.getAsInt()); //! is encoded as an int
            mml.put(PATH, "" + path.getAbsolutePath());
            // instead of a string because
            // the string encoding is not
            // exactly equivalent

            addHashesToHeader(hashes, mml, HASHES_PATH);

            return mml;
        }

        public byte[] toBytes() {
            ByteArrayOutputStream b_out = new ByteArrayOutputStream();

            DataOutputStream out = new DataOutputStream(b_out);

            try {
                out.writeUTF(toMML().toString());
            } catch (IOException ex) {
                throw new com.general.util.UnexpectedException("This line should not throw an error.");
            }

            return b_out.toByteArray(); // lots of "to" methods here.
        }

        /**
         * @return the number of bytes that the MML header information takes up
         *         in the mask file. Start reading the mask data from this
         *         point.
         */
        public int getOffset() {
            return headerOffset;
        }
    }

    //Hash encoding/decoding
    static final String HASH_NAME_PATH = "Hash Name";

    static final String HASH_AS_STRING = "Hash Value";

    private static void addHashesToHeader(FileHash[] hashes, RobustMML mml, String path) {
        for (int i = 0; i < hashes.length; i++) {
            FileHash hash = hashes[i];

            String hashName = hash.getHashName();
            String hashAsString = SimpleFileHash.asHex(hash.getBytes());

            String workingPath = path + i + "/";

            mml.put(workingPath + HASH_NAME_PATH, hashName);
            mml.put(workingPath + HASH_AS_STRING, hashAsString);
        }
    }

    private static FileHash[] getHashesFromHeader(RobustMML mml, String path) throws IOException {
        List<String> itemsToDecode = mml.list(path);
        if (itemsToDecode == null)
            throwIOException("itemsToDecode is null");

        FileHash[] hashes = new FileHash[itemsToDecode.size()];

        for (int i = 0; i < itemsToDecode.size(); i++) {
            String workingPath = path + i + "/";

            String hashName = mml.get(workingPath + HASH_NAME_PATH);
            String hashAsString = mml.get(workingPath + HASH_AS_STRING);

            if (hashName == null || hashAsString == null)
                throwIOException("Could not find a hash name or value");

            hashes[i] = SimpleFileHash.buildFromHexString(hashName, hashAsString);
        }

        return hashes;
    }

    private static void throwIOException(String errorMessage) throws IOException {
        throw new IOException(errorMessage);
    }

}