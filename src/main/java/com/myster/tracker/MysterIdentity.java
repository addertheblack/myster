package com.myster.tracker;

import java.security.PublicKey;
import java.util.Optional;

public interface MysterIdentity {
    // hashCode()
    // equals()
    // toString()
    
    public static Optional<PublicKey> extractPublicKey(MysterIdentity i) {
        if (i instanceof PublicKeyIdentity pk) {
            return Optional.of(pk.getPublicKey());
        }
        return Optional.empty();
    }
}