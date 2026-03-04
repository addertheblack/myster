package com.myster.client.ui;

import java.io.IOException;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

import com.general.thread.PromiseFutures;
import com.myster.access.AccessList;
import com.myster.net.MysterAddress;
import com.myster.net.client.MysterStream;
import com.myster.type.MysterType;

/**
 * Per-{@code ClientWindow} transient cache of display names fetched from remote nodes.
 *
 * <p>When a type is not in the local {@link com.myster.type.TypeDescriptionList}, this cache
 * fetches its access list from the connected server to display a human-readable name instead
 * of the raw hex string. Nothing is written to disk — the cache is discarded when the window
 * closes or reconnects.
 *
 * <p>A failed or in-progress lookup is represented by an empty-string sentinel so that only
 * one network fetch is ever fired per type per session.
 *
 * <p>All public methods are thread-safe. {@link #resolveAsync} fires its {@code onResolved}
 * callback on the background thread — EDT dispatch is the caller's responsibility.
 * {@code TypeListerThread}'s constructor wrapper handles this automatically.
 */
public class TypeMetadataCache {

    /**
     * Abstraction over the blocking fetch operation, used to inject a test double in unit tests.
     */
    interface Fetcher {
        Optional<AccessList> fetch(MysterAddress address, MysterType type) throws IOException;
    }

    private final Fetcher fetcher;
    private final ConcurrentHashMap<MysterType, String> cache = new ConcurrentHashMap<>();

    /**
     * Production constructor — fetches via the standard stream suite.
     *
     * @param stream the {@code MysterStream} from {@code protocol.getStream()}
     */
    public TypeMetadataCache(MysterStream stream) {
        this.fetcher = stream::getAccessList;
    }

    /** Testing constructor — accepts a custom fetcher. */
    TypeMetadataCache(Fetcher fetcher) {
        this.fetcher = fetcher;
    }

    /**
     * Returns the cached display name for a type, or its hex string if not yet resolved.
     *
     * @param type the type to look up
     * @return the human-readable name, or {@code type.toHexString()} if unknown
     */
    public String getDisplayName(MysterType type) {
        String v = cache.get(type);
        return (v != null && !v.isEmpty()) ? v : type.toHexString();
    }

    /**
     * Returns {@code true} if a fetch has already been attempted for this type (regardless of
     * whether it succeeded). Used to prevent duplicate fetches.
     *
     * @param type the type to check
     * @return true if a fetch was already started
     */
    public boolean hasAttempted(MysterType type) {
        return cache.containsKey(type);
    }

    /**
     * Starts a background fetch for a type whose name is not yet known. No-op if a fetch has
     * already been attempted for this type.
     *
     * <p>On completion (success or failure), fires {@code onResolved} on the background thread.
     * The caller is responsible for dispatching to the EDT — {@code TypeListerThread}'s
     * {@code Util.invokeLater} wrapper handles this automatically.
     *
     * @param type       the type to resolve
     * @param from       the server address to fetch from
     * @param onResolved callback fired when the fetch completes (on background thread)
     */
    public void resolveAsync(MysterType type, MysterAddress from, Runnable onResolved) {
        if (cache.putIfAbsent(type, "") != null) {
            return; // sentinel already set — another fetch is in progress or completed
        }
        PromiseFutures.execute(() -> {
            try {
                fetcher.fetch(from, type).ifPresent(al -> {
                    String name = al.getState().getName();
                    cache.put(type, (name != null && !name.isBlank()) ? name : type.toHexString());
                });
            } catch (Exception ignored) {
                // sentinel stays as ""; getDisplayName falls back to hex
            }
            onResolved.run(); // NOT on EDT — TypeListerThread's wrapper dispatches it
            return null;
        });
    }
}
