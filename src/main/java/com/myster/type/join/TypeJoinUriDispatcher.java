package com.myster.type.join;

import java.awt.Window;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashSet;
import java.util.Set;
import java.util.function.Supplier;

import javax.swing.SwingUtilities;

import com.myster.type.ui.JoinPrivateTypeDialog;

/** Keeps native, command-line, and manual invitation activation on one bounded EDT dialog path. */
public final class TypeJoinUriDispatcher {
    private static final int MAX_QUEUED_LINKS = 8;

    private final TypeJoinCoordinator coordinator;
    private final Supplier<Window> ownerSupplier;
    private final Deque<String> queuedLinks = new ArrayDeque<>();
    private final Set<String> queuedSet = new HashSet<>();
    private JoinPrivateTypeDialog activeDialog;
    private String activeLink;

    public TypeJoinUriDispatcher(TypeJoinCoordinator coordinator, Supplier<Window> ownerSupplier) {
        this.coordinator = java.util.Objects.requireNonNull(coordinator);
        this.ownerSupplier = java.util.Objects.requireNonNull(ownerSupplier);
    }

    /** Opens the shared dialog without a prefilled URI for the Type Manager toolbar. */
    public void openManual() {
        onEdt(() -> openNow(""));
    }

    /**
     * Queues one valid complete {@code myster:} URI. Malformed and duplicate activation arguments
     * are ignored, and at most eight distinct links wait behind the active dialog.
     */
    public void dispatch(String text) {
        if (text == null || text.length() > TypeJoinUri.MAX_URI_CHARS
                || !text.regionMatches(true, 0, "myster:", 0, "myster:".length())) {
            return;
        }
        final String canonical;
        try {
            canonical = TypeJoinUri.parse(text).toString();
        } catch (java.io.IOException exception) {
            return;
        }
        onEdt(() -> enqueueOrOpen(canonical));
    }

    private void enqueueOrOpen(String link) {
        if (activeDialog == null) {
            openNow(link);
            return;
        }
        if (!link.equals(activeLink)
                && queuedLinks.size() < MAX_QUEUED_LINKS && queuedSet.add(link)) {
            queuedLinks.addLast(link);
        }
        activeDialog.setVisible(true);
        activeDialog.toFront();
    }

    private void openNow(String link) {
        activeLink = link;
        activeDialog = new JoinPrivateTypeDialog(ownerSupplier.get(), coordinator, link);
        activeDialog.setVisible(true);
        activeDialog = null;
        activeLink = null;
        if (!queuedLinks.isEmpty()) {
            String next = queuedLinks.removeFirst();
            queuedSet.remove(next);
            SwingUtilities.invokeLater(() -> openNow(next));
        }
    }

    private static void onEdt(Runnable runnable) {
        if (SwingUtilities.isEventDispatchThread()) {
            runnable.run();
        } else {
            SwingUtilities.invokeLater(runnable);
        }
    }
}
