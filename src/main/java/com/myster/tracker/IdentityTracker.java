
package com.myster.tracker;

import static com.myster.tracker.MysterServerImplementation.computeNodeNameFromIdentity;
import static com.myster.tracker.TrackerUtils.INVOKER;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Optional;
import java.util.logging.Logger;

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
public class IdentityTracker implements IdentityProvider {
    private static final Logger LOGGER = Logger.getLogger(IdentityTracker.class.getName());

    private static final long REFRESH_MS = 10 * 60 * 1000;

    private final Map<ExternalName, MysterIdentity> externalNameToIdentity = new HashMap<>();
    private final Map<MysterAddress, AddressState> addressStates = new HashMap<>();
    private final Map<MysterAddress, MysterIdentity> addressToIdentity = new HashMap<>();
    private final Map<MysterIdentity, List<MysterAddress>> identityToAddresses = new HashMap<>();

    private final Pinger pinger;
    
    private Timer timer;
    
    /**
     * @param pinger is responsible for "pinging" servers to check if they are up or down
     */
    public IdentityTracker(Pinger pinger) {
        this.pinger = pinger;
    }
    
    @Override
    public synchronized boolean exists(MysterAddress address) {
        return addressStates.containsKey(address);
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
            return -1;
        }
        
        if (addressState.lastPingTime == -1) {
            return -1;
        }
        
        return addressState.up ? addressState.lastPingTime : -2;
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
            // most likely to be reachable if our laptop is not longer on the LAN
            candidates = Util.filter(addresses, a -> !a.getInetAddress().isLoopbackAddress() && !ServerUtils.isLanAddress(a.getInetAddress()));
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
        
        return candidates.size() == 0 ? Optional.empty(): Optional.of(candidates.get(0));
    }
    

    @Override
    public synchronized MysterAddress[] getAddresses(MysterIdentity identity) {
        List<MysterAddress> addresses = identityToAddresses.get(identity);
        
        if (addresses == null) {
            return new MysterAddress[] {};
        }
        
        return addresses.toArray(new MysterAddress[] {});
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

        if (identityToAddresses.containsKey(key)) {
            List<MysterAddress> addresses = identityToAddresses.get(key);

            addresses.remove(address);
            
            if(addresses.size()==0) {
                externalNameToIdentity.remove(computeNodeNameFromIdentity(key));
                identityToAddresses.remove(key);
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
        var refreshTime = REFRESH_MS;

        if (refreshTime < System.currentTimeMillis() - addressState.lastPingTime) {
            addressState.pingProcess.ifPresent(PromiseFuture::cancel);
            
            addressState.pingProcess = Optional.of(pinger.ping(address).clearInvoker()
                    .setInvoker(INVOKER).addStandardExceptionHandler().addResultListener(e -> {
                        updateState(addressState, e);
                    }));
        }
    }
    
    // Package protected for unit test
    synchronized  void waitForPing(MysterAddress address) {
        AddressState addressState = addressStates.get(address);
        
        addressState.pingProcess.ifPresent(f -> Util.callAndWaitNoThrows(f::get));
    }

    private void updateState(AddressState addressState, PingResponse pingResponse) {
        addressState.lastPingTime = pingResponse.pingTimeMs();
        addressState.up = !pingResponse.isTimeout();
    }
     
    static class AddressState {
        public int lastPingTime = -1;
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
