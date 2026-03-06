package com.myster.type.ui;

import java.util.Optional;
import java.util.function.BiConsumer;

import com.myster.identity.Cid128;
import com.myster.tracker.MysterServer;

/**
 * The subset of server-pool operations needed by the Type Manager GUI.
 *
 * <p>Implemented by an adapter in {@link com.myster.Myster} that wraps
 * {@link com.myster.tracker.MysterServerPool}. Keeping this narrow interface here means the GUI
 * has no compile-time dependency on the full pool and its transitive tracker graph.
 *
 * <p>Two operations are needed:
 * <ol>
 *   <li>{@link #forEachServer} — enumerate servers that have a known {@link Cid128}, for use
 *       in {@link ServerPickerDialog}. Only servers whose identity is cryptographically known
 *       are yielded — the adapter handles the {@code PublicKeyIdentity} filtering.</li>
 *   <li>{@link #resolveDisplayName} — resolve a {@link Cid128} to a human-readable server name
 *       for the Members tab in {@link TypeEditorPanel}.</li>
 * </ol>
 */
public interface TypeEditorServerSource {

    /**
     * Calls {@code consumer} once for every server that has a derivable {@link Cid128}.
     * Servers whose identity cannot be resolved to a public key are excluded.
     *
     * @param consumer receives each eligible server and its derived {@link Cid128}; must not block
     */
    void forEachServer(BiConsumer<MysterServer, Cid128> consumer);

    /**
     * Resolves a {@link Cid128} to a human-readable server name.
     *
     * <p>Returns {@link Optional#empty()} if the server is not in the pool or its identity is
     * not known.
     *
     * @param cid the 128-bit identity hash of the target server
     * @return the server's display name, or empty if not resolvable
     */
    Optional<String> resolveDisplayName(Cid128 cid);
}
