
package com.myster.tracker;

import java.security.PublicKey;

import com.myster.identity.Util;

class PublicKeyIdentity implements MysterIdentity {
    private final PublicKey key;
    
    public PublicKeyIdentity(PublicKey key) {
        this.key = key;
    }
    
    @Override
    public int hashCode() {
        return key.hashCode();
    }

    @Override
    public boolean equals(Object obj) {
        if (obj instanceof PublicKeyIdentity k) {
            return key.equals(k.key);
        } else {
            return false;
        }
    }
    
    @Override
    public String toString() {
        return Util.keyToString(key);
    }
    
    PublicKey getPublicKey() {
        return key;
    }
}