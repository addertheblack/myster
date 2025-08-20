
package com.myster.tracker;

import static com.myster.tracker.MysterServer.DOWN;
import static com.myster.tracker.MysterServer.UNTRIED;
import static com.myster.tracker.MysterServerImplementation.computeNodeNameFromIdentity;
import static com.myster.tracker.TrackerUtils.INVOKER;

import java.net.InetAddress;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Optional;
import java.util.Set;
import java.util.function.Consumer;
import java.util.logging.Logger;
import java.util.stream.Collectors;

import com.general.thread.PromiseFuture;
import com.general.util.Timer;
import com.general.util.Util;
import com.myster.client.datagram.PingResponse;
import com.myster.net.MysterAddress;
import com.myster.server.ServerUtils;

/**
 * This is responsible for tracking which internet addresses (MysterAddress) map to which server identities
 * (MysterIdentity).
 */
class IdentityTracker implements IdentityProvider {
    private static final Logger LOGGER = Logger.getLogger(IdentityTracker.class.getName());

    private static final long REFRESH_MS = 10 * 60 * 1000;

    private final Map<ExternalName, MysterIdentity> externalNameToIdentity = new HashMap<>();
    private final Map<MysterAddress, AddressState> addressStates = new HashMap<>();
    private final Map<MysterAddress, MysterIdentity> addressToIdentity = new HashMap<>();
    private final Map<InetAddress, Set<MysterAddress>> ipToServerAddresses = new HashMap<>();
    private final Map<MysterIdentity, List<MysterAddress>> identityToAddresses = new HashMap<>();
    
    private final Consumer<PingResponse> pingListener;
    private final Consumer<MysterIdentity> deadServerListener;

    private final Pinger pinger;
    
    private Timer timer;
    
    /**
     * @param pingListener add a pingLister to get notified when a server has been pinged.
     *          You can use this to update your GUI! Note: NOT ON THE EVENT THREAD!
     * @param pinger
     *            is responsible for "pinging" servers to check if they are up
     *            or down
     */
    public IdentityTracker(Pinger pinger,
                           Consumer<PingResponse> pingListener,
                           Consumer<MysterIdentity> deadListener) {
        this.pinger = pinger;
        this.pingListener = pingListener;
        this.deadServerListener = deadListener;
    }
    
    @Override
    public synchronized boolean exists(MysterAddress address) {
        return addressStates.containsKey(address);
    }
    
    @Override
    public synchronized Set<MysterAddress> getServerAddressesForAddress(InetAddress ip) {
        if (!ipToServerAddresses.containsKey(ip)) {
            // return an immutable empty collection
            return Set.of();
        }
        
        // return immutable copy of ipToServerAddresses.get(ip)
        return Set.copyOf(ipToServerAddresses.get(ip));
    }

    @Override
    public synchronized boolean existsMysterIdentity(MysterIdentity identity) {
        var addresses = identityToAddresses.get(identity);
        if (addresses == null) {
            return false;
        }

        if (addresses.isEmpty()) {
            return false;
        }

        return true;
    }

    @Override
    public synchronized MysterIdentity getIdentity(MysterAddress address) {
        return addressToIdentity.get(address);
    }
    
    @Override
    public synchronized MysterIdentity getIdentityFromExternalName(ExternalName name) {
        return externalNameToIdentity.get(name);
    }
    
    @Override
    public synchronized boolean isUp(MysterAddress address) {
        AddressState addressState = addressStates.get(address);
        if(addressState == null) {
            return false;
        }
        
        return addressState.up;
    }
    
    @Override
    public synchronized int getPing(MysterAddress address) {
        AddressState addressState = addressStates.get(address);
        if(addressState == null) {
            return UNTRIED;
        }
        
        if (addressState.lastPingDurationMs == UNTRIED) {
            return UNTRIED;
        }
        
        return addressState.up ? addressState.lastPingDurationMs : DOWN;
    }
    
    @Override
    public synchronized Optional<MysterAddress> getBestAddress(MysterIdentity identity) {
        List<MysterAddress> addresses = identityToAddresses.get(identity);
        
        if (addresses == null) {
            return Optional.empty();
        }
        
        List<MysterAddress> candidates = Util.filter(addresses, a -> {
            AddressState addressState = addressStates.get(a);
            if (addressState == null) {
                return false;
            }
            
            return addressState.up;
        });
        
        if (candidates.isEmpty()) {
            // If the server is down we should only check the public address since that is the
            // most likely to be reachable if our laptop is not longer on the LAN (or loopback)
            candidates = Util.filter(addresses, a -> !TrackerUtils.isLanAddress(a.getInetAddress()));
            
            // this is for the edge case where we only have the LAN address for this server
            if (candidates.isEmpty()) {
                candidates = addresses;
            }
        }
        
        for (MysterAddress mysterAddress : candidates) {
            if (mysterAddress.getInetAddress().isLoopbackAddress()) {
                return Optional.of(mysterAddress);
            }
        }
        
        for (MysterAddress mysterAddress : candidates) {
            if (ServerUtils.isLanAddress(mysterAddress.getInetAddress())) {
                return Optional.of(mysterAddress);
            }
        }
        
        if(candidates.isEmpty()) {
            return Optional.empty();
        }
        
        return Optional.of(candidates.get(0));
    }
    

    @Override
    public synchronized MysterAddress[] getAddresses(MysterIdentity identity) {
        List<MysterAddress> addresses = identityToAddresses.get(identity);
        
        if (addresses == null) {
            return new MysterAddress[] {};
        }
        
        return addresses.toArray(MysterAddress[]::new);
    }
    
    @Override
    public synchronized void repingNow(MysterAddress address) {
        AddressState addressState = addressStates.get(address);
        if (addressState == null) {
            // this can happen due to a race condition where the address is removed
            // but it's incredibly unlikely
            LOGGER.warning("Tried to reping an address that doesn't exist: " + address);
            return;
        }


        // because of the tendency of servers to spaz out and send ping floods
        // we ignore them if it's been like 10 secs.
        // this can mean we might miss an event but meh, I prefer not to set
        // myself up to dos something.
        if (System.currentTimeMillis() - addressState.timeOfLastPing < 10000) {
            return;
        }

        // reset the time so that it will be pinged immediately
        addressState.timeOfLastPing = 0;
        refreshElementIfNeeded(address, addressState);        
    }

    /**
     * This is so that you can suggest that an address should be pinged. This
     * only works for "down" addresses. Up addresses are skipped.
     * 
     * The use case is for when you have an address that we know about, the
     * address is marked "down" but you start receiving traffic from the
     * address. Why am I receiving traffic for a "down" address? Suggest the
     * address to force a check again. This method protects against being called
     * too many times.
     * 
     * @param identity
     *            to scan for down addresses to check.
     */
    public synchronized void suggestPing(MysterAddress a) {
        AddressState state = addressStates.get(a);
        if (state == null || state.up) {
            return;
        }
        
        long timeMillis = System.currentTimeMillis();
        if (REFRESH_MS < timeMillis - state.timeOfLastSuggestPing) {
            state.timeOfLastSuggestPing = timeMillis;
            state.timeOfLastPing = 0;

            LOGGER.fine("Trying suggested ping for " + a);
            refreshElementIfNeeded(a, state);
        }
    }

    /**
     * Associates the key with the address and vice versa
     */
    public synchronized void addIdentity(MysterIdentity key, MysterAddress address) {
        if (timer == null) {
            resetTimer();
        }

        if (addressToIdentity.containsKey(address)) {
            if (addressToIdentity.get(address).equals(key)) {
                return;
            }

            removeIdentity(addressToIdentity.get(address), address);
        }
        
        ipToServerAddresses.putIfAbsent(address.getInetAddress(), new HashSet<MysterAddress>());
        ipToServerAddresses.get(address.getInetAddress()).add(address);

        addressToIdentity.put(address, key);
        externalNameToIdentity.put(computeNodeNameFromIdentity(key), key);

        if (!identityToAddresses.containsKey(key)) {
            identityToAddresses.put(key, new ArrayList<>());
        }
        
        List<MysterAddress> addresses = identityToAddresses.get(key);

        if (addresses.contains(address)) {
            LOGGER.warning("address was already in the data MysterAddressTracker but it shouldn't be at this point "
                    + address);
        } else {
            addresses.add(address);
        }

        if (addressStates.containsKey(address)) {
            LOGGER.warning("address was already in the data MysterAddressTracker but it shouldn't be at this point "
                    + address);
            return;
        }

        AddressState addressState = new AddressState();
        addressStates.put(address, addressState);

        refreshElementIfNeeded(address, addressState);
    }

    private void resetTimer() {
        if (timer != null) {
            timer.cancelTimer();
        }
        
        timer = new Timer(() -> INVOKER.invoke(IdentityTracker.this::update), REFRESH_MS);
    }

    public synchronized void removeIdentity(MysterIdentity key, MysterAddress address) {
        if (addressToIdentity.containsKey(address)) {
            if (addressToIdentity.get(address).equals(key)) {
                addressToIdentity.remove(address);
                addressStates.remove(address);
            }
        }
        
        ipToServerAddresses.putIfAbsent(address.getInetAddress(), new HashSet<MysterAddress>());
        ipToServerAddresses.get(address.getInetAddress()).remove(address);

        if (identityToAddresses.containsKey(key)) {
            List<MysterAddress> addresses = identityToAddresses.get(key);

            addresses.remove(address);
            
            if(addresses.size()==0) {
                externalNameToIdentity.remove(computeNodeNameFromIdentity(key));
                identityToAddresses.remove(key);

                var l = deadServerListener;
                if (l != null) {
                    INVOKER.invoke(() -> l.accept(key));
                }
            }
        }
    }
    
    private synchronized void update() {
        if (!INVOKER.isInvokerThread()) {
            throw new IllegalStateException("Should be called on the invoker thread");
        }
        
        for (Entry<MysterAddress, AddressState> entry : addressStates.entrySet()) {
            var address = entry.getKey();
            var addressState = entry.getValue();
            
            refreshElementIfNeeded(address, addressState);
        }

        resetTimer();
    }

    private void refreshElementIfNeeded(MysterAddress address, AddressState addressState) {
        if (REFRESH_MS > System.currentTimeMillis() - addressState.timeOfLastPing) {
            return;
        }
        
        addressState.pingProcess.ifPresent(PromiseFuture::cancel);

        addressState.pingProcess = Optional.of(pinger.ping(address).clearInvoker()
                .setInvoker(INVOKER).addStandardExceptionHandler().addResultListener(e -> {
                    Consumer<PingResponse> l;
                    synchronized (IdentityTracker.this) {
                        updateState(addressState, e);
                        l = pingListener;
                    }
                    l.accept(e);
                }));

        addressState.timeOfLastPing = System.currentTimeMillis();
    }

    // Package protected for unit test
    void waitForPing(MysterAddress address) throws InterruptedException {
        synchronized (this) {
            AddressState addressState = addressStates.get(address);

            addressState.pingProcess.ifPresent(f -> Util.callAndWaitNoThrows(f::get));
        }
        
        INVOKER.waitForThread();
    }

    private void updateState(AddressState addressState, PingResponse pingResponse) {
        addressState.lastPingDurationMs = pingResponse.isTimeout() ? DOWN : pingResponse.pingTimeMs();
        addressState.up = !pingResponse.isTimeout();
    }
    
    public synchronized void cleanUpOldAddresses(MysterIdentity key) {
        if (!Thread.holdsLock(IdentityTracker.this)) {
            throw new IllegalStateException("Must hold lock");
        }
        
        List<MysterAddress> addresses = identityToAddresses.get(key);
        if (addresses == null) {
            return; // that's weird but possible
        }
        
        enum AddressType {
            PUBLIC, LAN, OTHER
        }
        
        Map<AddressType, List<MysterAddress>> foo = addresses.stream().collect(Collectors.groupingBy((MysterAddress i) -> {
            if (i.getInetAddress().isLoopbackAddress() ) {
                return AddressType.OTHER;
            } else if (TrackerUtils.isLanAddress(i.getInetAddress())) {
                return AddressType.LAN;
            } else {
                return AddressType.PUBLIC;
            }
        }));
        
        List<MysterAddress> lanAddresses = foo.get(AddressType.LAN);
        if (lanAddresses != null) {
            deleteDownAddresses(key, lanAddresses);
        }
        
        List<MysterAddress> publicAddresses = foo.get(AddressType.PUBLIC);
        if (publicAddresses != null) {
            deleteDownAddresses(key, publicAddresses);
        }
    }

    private void deleteDownAddresses(MysterIdentity key, List<MysterAddress> lanAddresses) {
        List<MysterAddress> upAddresses =  lanAddresses.stream().filter(a -> {
            AddressState state = addressStates.get(a);
            if (state == null) {
                return false;
            }
            
            return state.up;
        }).toList();
        
        if (upAddresses.size()==0) {
            return;
        }
        
        List<MysterAddress> downAddresses =  lanAddresses.stream().filter(a -> {
            AddressState state = addressStates.get(a);
            if (state == null) {
                return false;
            }
            
            if (state.lastPingDurationMs == UNTRIED) {
                return false;
            }
            
            return !state.up;
        }).toList();
        
        for (MysterAddress a: downAddresses) {
            removeIdentity(key, a);
        }
    }
    
     
    private static class AddressState {
        public long timeOfLastSuggestPing = 0;
        public long timeOfLastPing = 0;
        public int lastPingDurationMs = UNTRIED;
        public boolean up = false;
        public Optional<PromiseFuture<PingResponse>> pingProcess = Optional.empty();
    }
    
    public interface Pinger {
        public PromiseFuture<PingResponse> ping(MysterAddress address);
    }

    public synchronized void close() {
        if (timer != null) {
            timer.cancelTimer();
        }
    }
}
