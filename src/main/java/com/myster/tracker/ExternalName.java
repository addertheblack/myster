
package com.myster.tracker;

public record ExternalName(String name) {
    @Override
    public final String toString() {
        return name();
    }
}
