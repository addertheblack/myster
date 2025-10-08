package com.myster.net.client;
import java.util.Optional;

import com.myster.net.MysterAddress;
import com.myster.tracker.MysterIdentity;

/**
 * This is a builder for the params we need to make a MysterDatagram related call.
 * Fields include MysterAddress (or MysterIdentity.. one or the other), and whether to force encryption or force not encrypted (one or the other)
 */
public class ParamBuilder {
    private final Optional<MysterAddress> address;
    private final Optional<MysterIdentity> identity;
    private final boolean forceEncryption;
    private final boolean forceUnencrypted;
    
    public ParamBuilder() {
        this(Optional.empty(), Optional.empty(), false, false);
    }
    
    public ParamBuilder(MysterAddress address) {
        this(Optional.of(address), Optional.empty(), false, false);
    }
    
    private ParamBuilder(Optional<MysterAddress> address, Optional<MysterIdentity> identity, boolean forceEncryption, boolean forceUnencrypted) {
        this.address = address;
        this.identity = identity;
        this.forceEncryption = forceEncryption;
        this.forceUnencrypted = forceUnencrypted;
    }
    
    public ParamBuilder withIdentity(MysterIdentity identity) {
        return new ParamBuilder(Optional.empty(), Optional.of(identity), this.forceEncryption, this.forceUnencrypted);
    }
    
    public ParamBuilder withAddress(MysterAddress address) {
        return new ParamBuilder(Optional.of(address), Optional.empty(), this.forceEncryption, this.forceUnencrypted);
    }
    
    public ParamBuilder forceEncryption() {
        return new ParamBuilder(this.address, this.identity, true, false);
    }
    
    // Getter methods
    public Optional<MysterAddress> getAddress() {
        return address;
    }
    
    public Optional<MysterIdentity> getIdentity() {
        return identity;
    }
    
    public boolean isForceEncryption() {
        return forceEncryption;
    }
    
    public boolean isForceUnencrypted() {
        return forceUnencrypted;
    }
}