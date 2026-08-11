package com.myster.net.client;

import java.security.PublicKey;
import java.util.Optional;

import com.myster.net.MysterAddress;
import com.myster.tracker.MysterIdentity;

/**
 * Immutable parameters for a datagram call. A target address or identity may
 * be accompanied by an explicit expected server key. The expected key proves a
 * one-off address/key association without adding it to the tracker cache.
 */
public class ParamBuilder {
    private final Optional<MysterAddress> address;
    private final Optional<MysterIdentity> identity;
    private final Optional<PublicKey> expectedServerPublicKey;
    private final boolean forceEncryption;
    private final boolean forceUnencrypted;
    
    public ParamBuilder() {
        this(Optional.empty(), Optional.empty(), Optional.empty(), false, false);
    }
    
    public ParamBuilder(MysterAddress address) {
        this(Optional.of(address), Optional.empty(), Optional.empty(), false, false);
    }
    
    private ParamBuilder(Optional<MysterAddress> address,
                         Optional<MysterIdentity> identity,
                         Optional<PublicKey> expectedServerPublicKey,
                         boolean forceEncryption,
                         boolean forceUnencrypted) {
        this.address = address;
        this.identity = identity;
        this.expectedServerPublicKey = expectedServerPublicKey;
        this.forceEncryption = forceEncryption;
        this.forceUnencrypted = forceUnencrypted;
    }
    
    public ParamBuilder withIdentity(MysterIdentity identity) {
        return new ParamBuilder(Optional.empty(), Optional.of(identity), this.expectedServerPublicKey, this.forceEncryption, this.forceUnencrypted);
    }
    
    public ParamBuilder withAddress(MysterAddress address) {
        return new ParamBuilder(Optional.of(address), Optional.empty(), this.expectedServerPublicKey, this.forceEncryption, this.forceUnencrypted);
    }

    /**
     * Requires a request to be encrypted to this public key, independently of
     * any address-to-identity association already cached by the tracker.
     *
     * <p>This is the 3DNS proof hook: the address is preserved and the key is
     * not inserted into trusted pool state merely by constructing parameters.
     *
     * @param publicKey expected key of the server at {@link #getAddress()}
     * @return new parameters containing the same target address and expected key
     */
    public ParamBuilder withExpectedServerPublicKey(PublicKey publicKey) {
        return new ParamBuilder(this.address,
                                this.identity,
                                Optional.of(publicKey),
                                this.forceEncryption,
                                this.forceUnencrypted);
    }
    
    public ParamBuilder forceEncryption() {
        return new ParamBuilder(this.address, this.identity, this.expectedServerPublicKey, true, false);
    }
    
    // Getter methods
    public Optional<MysterAddress> getAddress() {
        return address;
    }
    
    public Optional<MysterIdentity> getIdentity() {
        return identity;
    }

    public Optional<PublicKey> getExpectedServerPublicKey() {
        return expectedServerPublicKey;
    }
    
    public boolean isForceEncryption() {
        return forceEncryption;
    }
    
    public boolean isForceUnencrypted() {
        return forceUnencrypted;
    }
}
