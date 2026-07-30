package com.myster.net.stream.client.msdownload;

/**
 * The download could not start because Myster could not contact or communicate
 * with the server.
 */
public class DownloadServerConnectionException extends DownloadStartException {
    private final String server;

    public DownloadServerConnectionException(String server, Throwable cause) {
        super("Could not connect to server: " + server, cause);
        this.server = server;
    }

    public String server() {
        return server;
    }
}
