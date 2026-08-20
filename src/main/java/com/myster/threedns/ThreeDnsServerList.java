package com.myster.threedns;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Optional;
import java.util.StringTokenizer;
import java.util.logging.Logger;
import java.util.prefs.BackingStoreException;
import java.util.prefs.Preferences;

import com.myster.cid.ServerCid;
import com.myster.net.MysterAddress;
import com.myster.tracker.ExternalName;
import com.myster.tracker.IdentityNeighborSet;
import com.myster.tracker.MysterIdentity;
import com.myster.tracker.MysterServer;
import com.myster.tracker.MysterServerPool;
import com.myster.tracker.PublicKeyIdentity;

/**
 * Tracker-owned 3DNS retention list. It is not a type-shaped server list; it
 * retains small left/right public-key server sets around the local node's
 * positive exponential CID targets. Persisted entries are restored as server
 * references and runtime methods filter for currently usable/up servers where
 * that contract is required.
 */
public class ThreeDnsServerList {
    public static final int DEFAULT_PER_SIDE_LIMIT = 2;
    public static final int TARGET_COUNT = ServerCid.LENGTH * Byte.SIZE;

    private static final Logger log = Logger.getLogger(ThreeDnsServerList.class.getName());
    private static final String PREF_NODE = "ThreeDns";

    private final ServerCid localCid;
    private final MysterServerPool pool;
    private final Preferences preferences;
    private final Runnable listChanged;
    private final List<TargetSlot> targets = new ArrayList<>();

    public ThreeDnsServerList(ServerCid localCid,
                              MysterServerPool pool,
                              Preferences preferences,
                              Runnable listChanged) {
        this.localCid = localCid;
        this.pool = pool;
        this.preferences = preferences.node(PREF_NODE);
        this.listChanged = listChanged;

        for (int bitIndex = 0; bitIndex < TARGET_COUNT; bitIndex++) {
            targets.add(new TargetSlot(bitIndex, localCid.plusPowerOfTwo(bitIndex)));
        }

        load();
    }

    /**
     * Reconsiders a refreshed server for every retained target. Only public-key
     * servers that are currently up and have at least one up address can enter
     * live retention slots.
     */
    public synchronized void consider(MysterServer server) {
        if (!isUsable(server)) {
            return;
        }

        boolean changed = false;
        long now = System.currentTimeMillis();
        for (TargetSlot target : targets) {
            changed |= addLiveEntry(target, ThreeDnsFingerEntry.Side.LEFT, server, now);
            changed |= addLiveEntry(target, ThreeDnsFingerEntry.Side.RIGHT, server, now);
        }

        if (changed) {
            saveAndNotify();
        }
    }

    public synchronized void serverPing(MysterAddress address, boolean isUp) {
        pool.getCachedMysterServer(address).ifPresent(server -> {
            if (isUp) {
                consider(server);
            } else {
                removeIdentity(server.getIdentity());
            }
        });
    }

    public synchronized void removeIdentity(MysterIdentity identity) {
        boolean changed = false;
        List<TargetSlot> changedTargets = new ArrayList<>();
        for (TargetSlot target : targets) {
            if (target.remove(identity)) {
                changed = true;
                changedTargets.add(target);
            }
        }

        for (TargetSlot target : changedTargets) {
            changed |= refill(target);
        }

        if (changed) {
            saveAndNotify();
        }
    }

    /**
     * Returns unique currently usable public-key identities from retained
     * entries, preserving snapshot order.
     */
    public synchronized List<PublicKeyIdentity> seeds(int limit) {
        if (limit <= 0) {
            return List.of();
        }

        LinkedHashMap<PublicKeyIdentity, PublicKeyIdentity> seeds = new LinkedHashMap<>();
        for (ThreeDnsFingerEntry entry : snapshot()) {
            if (seeds.size() >= limit) {
                break;
            }

            if (isUsable(entry.server())) {
                seeds.putIfAbsent(entry.identity(), entry.identity());
            }
        }

        return List.copyOf(seeds.values());
    }

    /**
     * Returns currently usable retained identities closest to an arbitrary
     * target. This searches the retained finger cache, not the entire pool.
     */
    public synchronized IdentityNeighborSet forTarget(ServerCid target, int perSideLimit) {
        int limit = normalizeLimit(perSideLimit);
        List<ThreeDnsFingerEntry> usable = snapshot().stream()
                .filter(entry -> isUsable(entry.server()))
                .toList();

        Optional<PublicKeyIdentity> exact = usable.stream()
                .filter(entry -> entry.serverCid().equals(target))
                .map(ThreeDnsFingerEntry::identity)
                .findFirst();

        List<PublicKeyIdentity> left = closest(usable,
                                               target,
                                               limit,
                                               exact,
                                               target::comparePredecessorDistance);
        List<PublicKeyIdentity> right = closest(usable,
                                                target,
                                                limit,
                                                exact,
                                                target::compareSuccessorDistance);

        return new IdentityNeighborSet(exact, left, right);
    }

    /**
     * Returns all retained entries, including restored entries that may not
     * currently be up.
     */
    public synchronized List<ThreeDnsFingerEntry> snapshot() {
        List<ThreeDnsFingerEntry> snapshot = new ArrayList<>();
        for (TargetSlot target : targets) {
            snapshot.addAll(target.left);
            snapshot.addAll(target.right);
        }
        return snapshot;
    }

    /**
     * Returns immutable snapshots of every local target slot in bit-index order.
     * Restored entries that are not currently up remain visible in these snapshots;
     * callers that need only usable nodes should use {@link #seeds(int)} or
     * {@link #forTarget(ServerCid, int)}.
     */
    public synchronized List<ThreeDnsTargetSlotSnapshot> snapshotTargetSlots() {
        List<ThreeDnsTargetSlotSnapshot> snapshot = new ArrayList<>(targets.size());
        for (TargetSlot target : targets) {
            snapshot.add(new ThreeDnsTargetSlotSnapshot(target.bitIndex,
                                                        target.targetCid,
                                                        target.left,
                                                        target.right));
        }
        return List.copyOf(snapshot);
    }

    private List<PublicKeyIdentity> closest(List<ThreeDnsFingerEntry> entries,
                                            ServerCid target,
                                            int limit,
                                            Optional<PublicKeyIdentity> exact,
                                            DistanceComparator distanceComparator) {
        LinkedHashMap<PublicKeyIdentity, PublicKeyIdentity> results = new LinkedHashMap<>();
        entries.stream()
                .sorted((a, b) -> compareEntries(target, distanceComparator, a, b))
                .forEach(entry -> {
                    if (results.size() >= limit) {
                        return;
                    }

                    PublicKeyIdentity identity = entry.identity();
                    if (exact.filter(identity::equals).isEmpty()) {
                        results.putIfAbsent(identity, identity);
                    }
                });

        return List.copyOf(results.values());
    }

    private int compareEntries(ServerCid target,
                               DistanceComparator distanceComparator,
                               ThreeDnsFingerEntry a,
                               ThreeDnsFingerEntry b) {
        int distance = distanceComparator.compare(a.serverCid(), b.serverCid());
        return distance != 0 ? distance : a.serverCid().compareTo(b.serverCid());
    }

    private boolean addLiveEntry(TargetSlot target,
                                 ThreeDnsFingerEntry.Side side,
                                 MysterServer server,
                                 long now) {
        Optional<ThreeDnsFingerEntry> entry = createEntry(target.targetCid, side, server, now);
        return entry.filter(e -> target.add(e, DEFAULT_PER_SIDE_LIMIT)).isPresent();
    }

    private boolean addRestoredEntry(TargetSlot target, ThreeDnsFingerEntry.Side side, MysterServer server) {
        Optional<ThreeDnsFingerEntry> entry = createEntry(target.targetCid, side, server, 0);
        return entry.filter(e -> target.add(e, DEFAULT_PER_SIDE_LIMIT)).isPresent();
    }

    private Optional<ThreeDnsFingerEntry> createEntry(ServerCid target,
                                                     ThreeDnsFingerEntry.Side side,
                                                     MysterServer candidateServer,
                                                     long updateTimeMs) {
        if (!(candidateServer.getIdentity() instanceof PublicKeyIdentity identity)) {
            return Optional.empty();
        }

        ServerCid candidateServerCid = ServerCid.fromPublicKey(identity.getPublicKey());
        if (candidateServerCid.equals(localCid)) { // is this me?
            return Optional.empty();
        }

        return candidateServer.getBestAddress()
                .map(address -> new ThreeDnsFingerEntry(target,
                                                        candidateServerCid,
                                                        candidateServer,
                                                        address,
                                                        side,
                                                        updateTimeMs));
    }

    private boolean refill(TargetSlot target) {
        boolean changed = false;
        IdentityNeighborSet neighbors = pool.findClosestByCid(target.targetCid, DEFAULT_PER_SIDE_LIMIT);

        long now = System.currentTimeMillis();
        for (PublicKeyIdentity identity : neighbors.left()) {
            changed |= addIdentityCandidate(target, ThreeDnsFingerEntry.Side.LEFT, identity, now);
        }
        for (PublicKeyIdentity identity : neighbors.right()) {
            changed |= addIdentityCandidate(target, ThreeDnsFingerEntry.Side.RIGHT, identity, now);
        }
        if (neighbors.exact().isPresent()) {
            changed |= addIdentityCandidate(target, ThreeDnsFingerEntry.Side.RIGHT, neighbors.exact().get(), now);
        }

        return changed;
    }

    private boolean addIdentityCandidate(TargetSlot target,
                                         ThreeDnsFingerEntry.Side side,
                                         PublicKeyIdentity identity,
                                         long now) {
        return pool.getCachedMysterServer(identity)
                .filter(this::isUsable)
                .flatMap(server -> createEntry(target.targetCid, side, server, now))
                .filter(entry -> target.add(entry, DEFAULT_PER_SIDE_LIMIT))
                .isPresent();
    }

    private boolean isUsable(MysterServer server) {
        return server.getIdentity() instanceof PublicKeyIdentity
                && server.isUp()
                && server.getUpAddresses().length > 0;
    }

    private void load() {
        for (TargetSlot target : targets) {
            loadSide(target, ThreeDnsFingerEntry.Side.LEFT);
            loadSide(target, ThreeDnsFingerEntry.Side.RIGHT);
        }
    }

    /**
     * Restores external-name entries from flat keys under the 3DNS preferences
     * node. Restored servers are retained even if they are not currently up;
     * usable result methods apply current up filtering.
     */
    private void loadSide(TargetSlot target, ThreeDnsFingerEntry.Side side) {
        StringTokenizer tokenizer = new StringTokenizer(preferences.get(key(target.bitIndex, side), ""));
        while (tokenizer.hasMoreTokens()) {
            ExternalName externalName = new ExternalName(tokenizer.nextToken());
            pool.lookupIdentityFromName(externalName)
                    .filter(PublicKeyIdentity.class::isInstance)
                    .flatMap(pool::getCachedMysterServer)
                    .ifPresentOrElse(
                            server -> addRestoredEntry(target, side, server),
                            () -> log.warning("3DNS retained server does not exist in pool: " + externalName)
                    );
        }
    }

    /**
     * Saves retained entries as external-name strings grouped by target bit and
     * side. These are references into the normal server pool, not assertions
     * that the retained servers are currently reachable.
     */
    private synchronized void save() {
        for (TargetSlot target : targets) {
            preferences.put(key(target.bitIndex, ThreeDnsFingerEntry.Side.LEFT), externalNames(target.left));
            preferences.put(key(target.bitIndex, ThreeDnsFingerEntry.Side.RIGHT), externalNames(target.right));
        }

        try {
            preferences.flush();
        } catch (BackingStoreException exception) {
            log.warning("Failed to save 3DNS retained server list: " + exception);
        }
    }

    private String externalNames(List<ThreeDnsFingerEntry> entries) {
        StringBuilder builder = new StringBuilder();
        for (ThreeDnsFingerEntry entry : entries) {
            builder.append(entry.server().getExternalName()).append(" ");
        }
        return builder.toString().trim();
    }

    private void saveAndNotify() {
        save();
        listChanged.run();
    }

    private static String key(int bitIndex, ThreeDnsFingerEntry.Side side) {
        return "bit." + bitIndex + "." + side.name().toLowerCase();
    }

    private static int normalizeLimit(int perSideLimit) {
        if (perSideLimit <= 0) {
            return DEFAULT_PER_SIDE_LIMIT;
        }

        return perSideLimit;
    }

    private final class TargetSlot {
        private final int bitIndex;
        private final ServerCid targetCid;
        private final List<ThreeDnsFingerEntry> left = new ArrayList<>();
        private final List<ThreeDnsFingerEntry> right = new ArrayList<>();

        private TargetSlot(int bitIndex, ServerCid targetCid) {
            this.bitIndex = bitIndex;
            this.targetCid = targetCid;
        }

        private boolean add(ThreeDnsFingerEntry candidateEntry, int limit) {
            List<ThreeDnsFingerEntry> entries = entries(candidateEntry.side());
            entries.removeIf(e -> e.server().getIdentity().equals(candidateEntry.server().getIdentity()));
            entries.add(candidateEntry);
            sort(entries, candidateEntry.side());

            while (entries.size() > limit) {
                entries.remove(entries.size() - 1);
            }

            return entries.contains(candidateEntry);
        }

        private boolean remove(MysterIdentity identity) {
            return left.removeIf(entry -> entry.server().getIdentity().equals(identity))
                    | right.removeIf(entry -> entry.server().getIdentity().equals(identity));
        }

        private List<ThreeDnsFingerEntry> entries(ThreeDnsFingerEntry.Side side) {
            return switch (side) {
                case LEFT -> left;
                case RIGHT -> right;
            };
        }

        private void sort(List<ThreeDnsFingerEntry> entries, ThreeDnsFingerEntry.Side side) {
            Comparator<ThreeDnsFingerEntry> comparator = switch (side) {
                case LEFT -> (a, b) -> compareEntries(targetCid, targetCid::comparePredecessorDistance, a, b);
                case RIGHT -> (a, b) -> compareEntries(targetCid, targetCid::compareSuccessorDistance, a, b);
            };
            entries.sort(comparator);
        }
    }

    @FunctionalInterface
    private interface DistanceComparator {
        int compare(ServerCid a, ServerCid b);
    }
}
