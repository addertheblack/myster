
package com.myster.tracker;

record ExternalName(String name) {
    @Override
    public final String toString() {
        return name();
    }
}
