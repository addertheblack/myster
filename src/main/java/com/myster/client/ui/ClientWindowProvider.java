package com.myster.client.ui;

import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.net.UnknownHostException;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.logging.Logger;

import com.myster.net.MysterAddress;
import com.myster.net.client.MysterProtocol;
import com.myster.net.server.ServerPreferences;
import com.myster.search.HashCrawlerManager;
import com.myster.tracker.MysterIdentity;
import com.myster.tracker.Tracker;
import com.myster.type.TypeDescriptionList;
import com.myster.ui.MysterFrameContext;

import static com.general.util.Util.ensureEventDispatchThread;

/**
 * Manages ClientWindow instances, ensuring that only one window exists per MysterIdentity.
 * When a window for a given identity is requested, either brings the existing window to front
 * or creates a new one.
 */
public class ClientWindowProvider {
    private static final Logger log = Logger.getLogger(ClientWindowProvider.class.getName());

    private MysterFrameContext context;
    private final IdentityResolver identityResolver;
    private final MysterProtocol protocol;
    private final HashCrawlerManager hashManager;
    private final Tracker tracker;
    private final ServerPreferences serverPreferences;
    private final TypeDescriptionList typeDescriptionList;

    // Map from MysterIdentity to active ClientWindow
    private final Map<MysterIdentity, ClientWindow> activeWindows = new HashMap<>();

    public ClientWindowProvider(IdentityResolver identityResolver,
                                MysterProtocol protocol,
                                HashCrawlerManager hashManager,
                                Tracker tracker,
                                ServerPreferences serverPreferences,
                                TypeDescriptionList typeDescriptionList) {
        ensureEventDispatchThread();

        this.identityResolver = identityResolver;
        this.protocol = protocol;
        this.hashManager = hashManager;
        this.tracker = tracker;
        this.serverPreferences = serverPreferences;
        this.typeDescriptionList = typeDescriptionList;
    }

    /**
     * Sets the context after construction. This is needed to break circular dependency.
     */
    public void setContext(MysterFrameContext context) {
        ensureEventDispatchThread();

        this.context = context;
    }

    /**
     * Gets or creates a ClientWindow for the given data.
     * If the data contains an IP address that resolves to a known identity,
     * and a window already exists for that identity, brings that window to front.
     * Otherwise, creates a new window.
     *
     * @param data the initial data for the window
     * @return the ClientWindow (either existing or newly created)
     */
    public ClientWindow getOrCreateWindow(ClientWindow.ClientWindowData data) {
        ensureEventDispatchThread();

        // Try to resolve the IP to an identity
        Optional<MysterIdentity> identity = data.ip().flatMap(ip -> {
            try {
                MysterAddress address = MysterAddress.createMysterAddress(ip);
                return identityResolver.resolve(address);
            } catch (UnknownHostException e) {
                log.warning("Could not resolve address: " + ip);
                return Optional.empty();
            }
        });

        // If we have an identity and an existing window for it, bring it to front
        if (identity.isPresent()) {
            ClientWindow existingWindow = activeWindows.get(identity.get());
            if (existingWindow != null) {
                log.info("Bringing existing ClientWindow to front for identity: " + identity.get());
                existingWindow.toFront();
                existingWindow.requestFocus();
                return existingWindow;
            }
        }

        // Create a new window
        ClientWindow window = new ClientWindow(context, data, protocol, hashManager, tracker,
                                                serverPreferences, typeDescriptionList);

        // If we have an identity, track this window
        identity.ifPresent(id -> {
            activeWindows.put(id, window);
            log.info("Created new ClientWindow for identity: " + id);
        });

        window.addComponentListener(new ComponentAdapter() {
            @Override
            public void componentHidden(ComponentEvent e) {
                identity.ifPresent(id -> {
                    activeWindows.remove(id);
                    log.info("Removed ClientWindow for identity: " + id);
                });
            }
        });

        // Add listener to remove from map when window closes
        window.addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                identity.ifPresent(id -> {
                    activeWindows.remove(id);
                    log.info("Removed ClientWindow for identity: " + id);
                });
            }

            @Override
            public void windowClosed(WindowEvent e) {
                identity.ifPresent(id -> {
                    activeWindows.remove(id);
                    log.info("Removed ClientWindow for identity: " + id);
                });
            }
        });

        return window;
    }

    /**
     * Creates a new ClientWindow with no initial data.
     * This window will not be tracked by identity until it connects to a server.
     *
     * @return a new ClientWindow
     */
    public ClientWindow createNewWindow() {
        return new ClientWindow(context, protocol, hashManager, tracker, serverPreferences, typeDescriptionList);
    }
}

