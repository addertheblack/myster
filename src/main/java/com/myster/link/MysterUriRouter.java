package com.myster.link;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.Objects;
import java.util.function.Consumer;

import com.general.util.Util;

/** Shared native/CLI router. Invitation parsing and its manual dialog remain invitation-owned. */
public final class MysterUriRouter {
    private final Consumer<String> invitations;
    private final Consumer<MysterLinkUri> resources;
    private final Consumer<IOException> errors;

    private IOException pendingError;
    private boolean showingError;
    private boolean errorScheduled;

    public MysterUriRouter(Consumer<String> invitations,
                           Consumer<MysterLinkUri> resources,
                           Consumer<IOException> errors) {
        this.invitations = Objects.requireNonNull(invitations);
        this.resources = Objects.requireNonNull(resources);
        this.errors = Objects.requireNonNull(errors);
    }

    /** Routes a complete argument; malformed/unsupported resource actions never become browsing. */
    public void dispatch(String text) {
        Util.invokeNowOrLater(() -> route(text));
    }

    private void route(String text) {
        if (text == null) {
            return;
        }

        try {
            if (text.length() > MysterLinkUri.MAX_URI_CHARS) {
                throw new IOException("Myster link is too long.");
            }

            URI uri = new URI(text);
            if (!"myster".equalsIgnoreCase(uri.getScheme())) {
                throw new IOException("Unsupported URL scheme.");
            }

            if ("join-type".equals(uri.getRawAuthority())) {
                invitations.accept(text);
            } else if ("link".equals(uri.getRawAuthority())) {
                resources.accept(MysterLinkUri.parse(text));
            } else {
                throw new IOException("Unsupported Myster link action.");
            }
        } catch (URISyntaxException exception) {
            report(new IOException("Invalid Myster link.", exception));
        } catch (IOException exception) {
            report(exception);
        }
    }

    /** Coalesces bad-input batches, including arrivals during Swing modal event loops. */
    public void report(IOException exception) {
        Util.invokeNowOrLater(() -> {
            pendingError = exception;

            if (!showingError && !errorScheduled) {
                errorScheduled = true;
                javax.swing.SwingUtilities.invokeLater(this::showError);
            }
        });
    }

    private void showError() {
        errorScheduled = false;
        IOException error = pendingError;
        if (error == null) { return; }
        pendingError = null;
        showingError = true;
        try {
            errors.accept(error);
        } finally {
            showingError = false;
            if (pendingError != null && !errorScheduled) {
                errorScheduled = true;
                javax.swing.SwingUtilities.invokeLater(this::showError);
            }
        }
    }
}
