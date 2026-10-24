
package com.myster.tracker;

import com.myster.net.MysterAddress;

/** Identifies a server by its network address when no public-key identity is available. */
public class MysterAddressIdentity implements MysterIdentity {
    private final MysterAddress address;
    
    public MysterAddressIdentity(MysterAddress address) {
        this.address = address;
    }
    
    @Override
    public int hashCode() {
        return address.hashCode();
    }

    @Override
    public boolean equals(Object obj) {
        if (obj instanceof MysterAddressIdentity mysterAddressIdentity) {
            return address.equals(mysterAddressIdentity.address);
        } else {
            return false;
        }
    }
    
    @Override
    public String toString() {
        return address.toString();
    }
}
