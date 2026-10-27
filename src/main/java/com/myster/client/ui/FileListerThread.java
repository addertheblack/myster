package com.myster.client.ui;

import java.io.IOException;
import java.security.PublicKey;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import com.general.thread.Invoker;
import com.myster.mml.MessagePak;
import com.myster.net.MysterAddress;
import com.myster.net.MysterSocket;
import com.myster.net.client.MysterStream;
import com.myster.net.client.ParamBuilder;
import com.myster.net.stream.client.StandardSuiteStream;
import com.myster.net.stream.client.StandardSuiteStream.FileCallback;
import com.myster.net.stream.client.StandardSuiteStream.NamedMetaData;
import com.myster.type.MysterType;
import com.myster.util.MysterThread;
import com.myster.util.Sayable;

/** Fetches file rows off the EDT, publishing batches and percentage progress with terminal callbacks. */
public class FileListerThread extends MysterThread {
    public record FileRecord(String file, MessagePak metaData) {}

    public interface ItemListListener {
        void addItemsToFileList(FileRecord[] files);
        void finish();
        default void failed(IOException error) {}
    }

    interface LookupTypeDescription { String lookup(MysterType type); }

    private final MysterStream stream;
    private final Optional<PublicKey> expectedKey;
    private final ItemListListener listener;
    private final Sayable msg;
    private final String ip;
    private final MysterType type;
    private final LookupTypeDescription lookup;

    public FileListerThread(MysterStream stream,
                            Optional<PublicKey> expectedKey,
                            ItemListListener listener,
                            Sayable msg,
                            String ip,
                            MysterType type,
                            LookupTypeDescription lookup) {
        this.stream = stream;
        this.expectedKey = expectedKey;
        this.listener = listener;
        this.msg = msg;
        this.ip = ip;
        this.type = type;
        this.lookup = lookup;
    }

    @Override
    public void run() {
        if (endFlag || type == null) {
            return;
        }

        try {
            ParamBuilder params = new ParamBuilder(MysterAddress.createMysterAddress(ip));

            if (expectedKey.isPresent()) {
                params = params.withExpectedServerPublicKey(expectedKey.get());
            }

            try (MysterSocket opened = stream.makeStreamConnection(params)) {
                if (endFlag) {
                    return;
                }

                List<FileRecord> records = new ArrayList<>();

                StandardSuiteStream.getAllFilesAndMetadata(opened, type, new FileCallback() {
                    private int totalFiles;
                    private int receivedFiles;
                    private long lastPublicationTime = System.currentTimeMillis();

                    public void numberOfFiles(int count) {
                        totalFiles = count;
                    }

                    public void file(NamedMetaData file) {
                        if (endFlag) {
                            return;
                        }

                        records.add(new FileRecord(file.name(), file.pak()));
                        receivedFiles++;

                        if (System.currentTimeMillis() - lastPublicationTime >= 1000) {
                            publish(records);
                            lastPublicationTime = System.currentTimeMillis();
                        }

                        // Keep progress visible while rows are buffered for the next GUI batch.
                        if (totalFiles > 0 && (receivedFiles == 1 || receivedFiles % 10 == 0
                                || receivedFiles == totalFiles)) {
                            int percent = (int) (receivedFiles * 100L / totalFiles);
                            onEdt(() -> msg.say("Downloading file metadata: " + lookup.lookup(type)
                                    + " " + percent + "%"));
                        }
                    }
                });

                publish(records);
            }

            onEdt(() -> {
                msg.say("File List: " + lookup.lookup(type) + " Complete.");
                listener.finish();
            });
        } catch (IOException exception) {
            onEdt(() -> {
                msg.say("Could not load the file list: " + exception.getMessage());
                listener.failed(exception);
            });
        }
    }

    private void publish(List<FileRecord> records) {
        if (records.isEmpty()) {
            return;
        }

        FileRecord[] batch = records.toArray(FileRecord[]::new);
        records.clear();

        onEdt(() -> listener.addItemsToFileList(batch));
    }

    private void onEdt(Runnable callback) {
        Invoker.EDT.invoke(() -> { if (!endFlag) callback.run(); });
    }

    public void flagToEnd() {
        endFlag = true;
        interrupt();
    }

    public void end() { flagToEnd(); }
}
