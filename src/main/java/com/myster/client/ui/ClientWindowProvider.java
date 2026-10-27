package com.myster.client.ui;

import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.awt.BorderLayout;
import java.awt.Dialog;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;

import javax.swing.JButton;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JProgressBar;
import javax.swing.WindowConstants;

import com.general.thread.Invoker;
import com.general.thread.PromiseFuture;
import com.general.util.AnswerDialog;

import com.myster.net.MysterAddress;
import com.myster.net.client.MysterProtocol;
import com.myster.search.HashCrawlerManager;
import com.myster.tracker.PublicKeyIdentity;
import com.myster.tracker.Tracker;
import com.myster.type.TypeDescriptionList;
import com.myster.type.MysterType;
import com.myster.ui.MysterFrameContext;

import static com.general.util.Util.ensureEventDispatchThread;

/** Creates and reuses windows only after their immutable public-key identity is known. */
public class ClientWindowProvider {
    /* pseudo final */
    private MysterFrameContext context;

    private final IdentityResolver identityResolver;

    /** abstraction for unit tests */
    private final Function<ClientWindow.ClientWindowData, ClientWindow> factory;

    /** registry of active windows */
    private final Map<PublicKeyIdentity, ClientWindow> activeWindows = new HashMap<>();

    public ClientWindowProvider(IdentityResolver identityResolver, MysterProtocol protocol,
                                HashCrawlerManager hashManager, Tracker tracker,
                                TypeDescriptionList typeDescriptionList) {
        ensureEventDispatchThread();

        this.identityResolver = identityResolver;
        this.factory = data -> new ClientWindow(context,
                                                                 protocol,
                                                                 hashManager,
                                                                 tracker,
                                                                 typeDescriptionList,
                                                                 data.ip(),
                                                                 data.identity());
    }

    /** Package protected for unit tests */
    ClientWindowProvider(IdentityResolver resolver, Function<ClientWindow.ClientWindowData, ClientWindow> factory) {
        this.identityResolver = resolver;
        this.factory = factory;
    }

    public void setContext(MysterFrameContext context) {
        ensureEventDispatchThread();

        this.context = context;
    }

    /**
     * Opens a known identity immediately, or discovers it before constructing any window.
     * The address-only fallback shows temporary progress and reports failure without registering
     * a provisional ClientWindow. A caller that already knows the key must pass it through.
     */
    public PromiseFuture<ClientWindow> openWindow(MysterAddress address,
                                                  Optional<PublicKeyIdentity> requestedIdentity,
                                                  Optional<MysterType> type,
                                                  Optional<String> fileName) {
        ensureEventDispatchThread();

        Optional<PublicKeyIdentity> identity = requestedIdentity.or(() -> identityResolver.getCached(address));
        if (identity.isPresent()) {
            return PromiseFuture.newPromiseFuture(getOrCreateWindow(
                    new ClientWindow.ClientWindowData(address, identity.get(), type, fileName)));
        }

        PromiseFuture<ClientWindow> opening = context.knownServerSource().resolveServer(address).mapAsync(server -> {
            if (!(server.getIdentity() instanceof PublicKeyIdentity keyIdentity)) {
                return PromiseFuture.newPromiseFutureException(
                        new IOException("The server did not provide a public-key identity."));
            }
            try {
                var serverAddress = server.getBestAddress().orElse(address);
                return PromiseFuture.newPromiseFuture(getOrCreateWindow(new ClientWindow.ClientWindowData(
                        serverAddress, keyIdentity, type, fileName)));
            } catch (Exception exception) {
                return PromiseFuture.newPromiseFutureException(exception);
            }
        }, Invoker.EDT);

        Runnable closeProgress = showLookupProgress(address, opening::cancel);
        opening.withInvoker(Invoker.EDT)
                .addFinallyListener(closeProgress)
                .addExceptionListener(failure -> showLookupFailure(address, failure));

        return opening;
    }

    /** Applies navigation to a new or reused window of exactly the supplied identity. */
    public ClientWindow getOrCreateWindow(ClientWindow.ClientWindowData data) {
        ensureEventDispatchThread();

        PublicKeyIdentity identity = data.identity();
        Optional<ClientWindow> existing = Optional.ofNullable(activeWindows.get(identity));

        boolean createdWindow = existing.isEmpty();
        final ClientWindow window = createdWindow ? factory.apply(data) : existing.orElseThrow();
        window.setInitialData(data);

        activeWindows.put(identity, window);

        if (createdWindow) {
            window.addComponentListener(new ComponentAdapter() {
                @Override public void componentHidden(ComponentEvent event) { removeWindow(identity, window); }
            });
            window.addWindowListener(new WindowAdapter() {
                @Override public void windowClosed(WindowEvent event) { removeWindow(identity, window); }
                @Override public void windowClosing(WindowEvent event) { removeWindow(identity, window); }
            });
        }

        window.setVisible(true);
        window.toFrontAndUnminimize();

        return window;
    }

    private void removeWindow(PublicKeyIdentity identity, ClientWindow window) {
        // A late closed notification from an old window must not remove its replacement.
        activeWindows.remove(identity, window);
    }

    /** Package protected so tests can replace the native progress UI. */
    Runnable showLookupProgress(MysterAddress address, Runnable cancel) {
        JDialog dialog = new JDialog(context.windowManager().getFrontMostWindow(),
                "Contacting server", Dialog.ModalityType.MODELESS);
        JPanel content = new JPanel(new BorderLayout(10, 10));
        content.setBorder(javax.swing.BorderFactory.createEmptyBorder(15, 15, 15, 15));
        content.add(new JLabel("Contacting " + address + "…"), BorderLayout.NORTH);
        JProgressBar progress = new JProgressBar();
        progress.setIndeterminate(true);
        content.add(progress, BorderLayout.CENTER);
        JButton cancelButton = new JButton("Cancel");
        cancelButton.addActionListener(_ -> cancel.run());
        content.add(cancelButton, BorderLayout.SOUTH);
        dialog.setContentPane(content);
        dialog.setDefaultCloseOperation(WindowConstants.DO_NOTHING_ON_CLOSE);
        dialog.addWindowListener(new WindowAdapter() {
            @Override public void windowClosing(WindowEvent event) { cancel.run(); }
        });
        dialog.pack();
        dialog.setLocationRelativeTo(dialog.getOwner());
        dialog.setVisible(true);
        return dialog::dispose;
    }

    /** Package protected so tests can observe failures without displaying a dialog. */
    void showLookupFailure(MysterAddress address, Throwable failure) {
        AnswerDialog.simpleAlert(context.windowManager().getFrontMostWindow(),
                "Could not contact " + address + ": " + failure.getMessage());
    }
}
