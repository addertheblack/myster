package com.myster.access;

import java.io.IOException;
import java.util.Optional;

import com.myster.type.MysterType;

/**
 * Narrow read-only view of an access-list store. The only operation exposed is loading the
 * access list for a given type.
 *
 * <p>This interface exists so that enforcement components ({@link AccessEnforcementUtils} and
 * the eight TCP section handlers) depend only on what they actually need, rather than on the
 * full {@link AccessListManager}. {@code AccessListManager} implements this interface and is
 * passed at the injection sites in {@code Myster.java}.
 *
 * @see AccessListManager
 * @see AccessEnforcementUtils
 */
@FunctionalInterface
public interface AccessListReader {

    /**
     * Loads the access list for {@code type}.
     *
     * @param type the type whose access list is requested
     * @return the access list, or empty if none exists for this type
     * @throws IOException if an I/O error occurs while reading the access list
     */
    Optional<AccessList> loadAccessList(MysterType type) throws IOException;
}

