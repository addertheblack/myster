/*
 * 
 * Title: Myster Open Source Author: Andrew Trumper Description: Generic Myster
 * Code
 * 
 * This code is under GPL
 * 
 * Copyright Andrew Trumper 2000-2001
 */

package com.myster.client.ui;

import java.io.IOException;
import java.net.UnknownHostException;
import java.util.concurrent.ExecutionException;

import com.general.util.Util;
import com.myster.net.MysterAddress;
import com.myster.net.MysterSocket;
import com.myster.net.client.MysterProtocol;
import com.myster.net.client.ParamBuilder;
import com.myster.net.stream.client.MysterSocketFactory;
import com.myster.type.MysterType;
import com.myster.type.TypeDescriptionList;
import com.myster.util.MysterThread;
import com.myster.util.Sayable;

/**
 * Background thread that fetches the list of {@link MysterType}s available on a remote server.
 *
 * <p>Tries UDP first; falls back to TCP if UDP fails. After listing types, optionally resolves
 * display names for unknown types via {@link TypeMetadataCache} — each unrecognised type triggers
 * a background access-list fetch, with the result updating the UI row in-place once it arrives.
 *
 * <p>All {@link TypeListener} callbacks are dispatched to the EDT via {@code Util.invokeLater}.
 * The {@code onResolved} callback passed to {@link TypeMetadataCache#resolveAsync} is fired on the
 * background thread; the internal listener wrapper handles EDT dispatch automatically.
 */
public class TypeListerThread extends MysterThread {

    /**
     * Callback interface for type-listing events. All methods are called on the EDT.
     */
    public interface TypeListener {
        void addItemToTypeList(MysterType s);
        void refreshIP(MysterAddress address);

        /**
         * Called when a transient display name has been resolved for a type that was previously
         * shown as a hex string. The UI should update the existing row in-place.
         *
         * @param type the type whose display name is now available
         */
        void refreshTypeDisplay(MysterType type);
    }
    

    private final MysterProtocol protocol;
    private final TypeListener listener;
    private final Sayable msg;
    private final String ip;
    private final TypeDescriptionList tdList;
    private final TypeMetadataCache cache;

    private MysterSocket socket;

    /**
     * Creates a type lister thread without metadata resolution (for callers that do not need it).
     *
     * @param protocol the protocol suite
     * @param listener receives type-listing callbacks on the EDT
     * @param msg      receives status messages on the EDT
     * @param ip       the server address string
     */
    public TypeListerThread(MysterProtocol protocol,
                            TypeListener listener,
                            Sayable msg,
                            String ip) {
        this(protocol, listener, msg, ip, null, null);
    }

    /**
     * Creates a type lister thread with optional metadata resolution.
     *
     * <p>When {@code tdList} and {@code cache} are non-null, types not found in {@code tdList}
     * will have their names resolved asynchronously and {@link TypeListener#refreshTypeDisplay}
     * called once the name is available.
     *
     * @param protocol the protocol suite
     * @param listener receives type-listing callbacks on the EDT
     * @param msg      receives status messages on the EDT
     * @param ip       the server address string
     * @param tdList   the local type registry (used to skip already-known types); may be null
     * @param cache    the per-window transient name cache; may be null
     */
    public TypeListerThread(MysterProtocol protocol,
                            TypeListener listener,
                            Sayable msg,
                            String ip,
                            TypeDescriptionList tdList,
                            TypeMetadataCache cache) {
        this.protocol = protocol;
        this.tdList = tdList;
        this.cache = cache;
        this.listener = new TypeListener() {
            public void addItemToTypeList(MysterType s) {
                Util.invokeLater(() -> {
                    if (endFlag) return;
                    listener.addItemToTypeList(s);
                });
            }

            public void refreshIP(MysterAddress address) {
                Util.invokeLater(() -> {
                    if (endFlag) return;
                    listener.refreshIP(address);
                });
            }

            public void refreshTypeDisplay(MysterType type) {
                Util.invokeLater(() -> {
                    if (endFlag) return;
                    listener.refreshTypeDisplay(type);
                });
            }
        };
        this.msg = (String s) -> Util.invokeLater(() -> msg.say(s));
        this.ip = ip;
    }

    public void run() {
        MysterAddress mysterAddress;
        try {
            mysterAddress = MysterAddress.createMysterAddress(ip);
        } catch (UnknownHostException e) {
            msg.say("Unknown host: " + ip);
            return;
        }

        try {
            msg.say("Requested Type List (UDP)...");

            if (endFlag) return;

            listener.refreshIP(mysterAddress);
            MysterType[] types = protocol.getDatagram().getTypes(new ParamBuilder(mysterAddress)).get();
            if (endFlag) return;

            for (MysterType t : types) {
                listener.addItemToTypeList(t);
            }
            resolveUnknownTypes(types, mysterAddress);
            msg.say("Idle...");
        } catch (ExecutionException exp) {
            if (!(exp.getCause() instanceof IOException)) {
                throw new IllegalStateException("Unexpected Exception", exp);
            }
            
            msg.say("Connecting to server...");
            try (MysterSocket socket =
                    MysterSocketFactory.makeStreamConnection(mysterAddress)) {
                if (endFlag) return;

                msg.say("Requesting File Type List...");

                MysterType[] typeList = protocol.getStream().getTypes(socket);

                msg.say("Adding Items...");
                for (MysterType t : typeList) {
                    listener.addItemToTypeList(t);
                }
                resolveUnknownTypes(typeList, mysterAddress);
                msg.say("Idle...");
            } catch (IOException ex) {
                msg.say("Could not get File Type List from specified server.");
            }
        } catch (InterruptedException exception) {
            return;
        }
    }

    /**
     * For each type not already in {@code tdList} and not yet attempted by the cache, kicks off
     * an async name resolution. The callback fires on the background thread; the listener wrapper
     * dispatches it to the EDT.
     */
    private void resolveUnknownTypes(MysterType[] types, MysterAddress address) {
        if (tdList == null || cache == null) return;
        for (MysterType type : types) {
            if (tdList.get(type).isEmpty() && !cache.hasAttempted(type)) {
                cache.resolveAsync(type, address, () -> listener.refreshTypeDisplay(type));
            }
        }
    }

    public void flagToEnd() {
        endFlag = true;
        try { socket.close(); } catch (Exception _) {}
        interrupt();
    }
    
    public void end() {
        flagToEnd();
        try {
            join();
        } catch (InterruptedException ex) {
            //nothing..
        }
    }
}