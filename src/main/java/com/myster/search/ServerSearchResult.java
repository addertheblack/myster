package com.myster.search;

import java.util.Objects;

import com.myster.net.MysterAddress;
import com.myster.tracker.MysterIdentity;

/** Immutable metadata for one server encountered in a type-scoped search. Ping uses MysterServer sentinels. */
public record ServerSearchResult(MysterIdentity identity,
                                 MysterAddress address,
                                 String name,
                                 String description,
                                 int files,
                                 int ping) {
    public ServerSearchResult {
        Objects.requireNonNull(identity);
        Objects.requireNonNull(address);
        name = Objects.requireNonNullElse(name, "");
        description = Objects.requireNonNullElse(description, "");
    }

    /** A display fallback only; unnamed servers do not match queries for their address. */
    public String displayName() {
        return name.isBlank() ? address.toString() : name;
    }
}
