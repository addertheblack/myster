package com.myster.access;

import java.io.IOException;
import java.util.Optional;
import java.util.logging.Logger;

import com.myster.identity.Cid128;
import com.myster.type.MysterType;

/**
 * Central access-enforcement decision point for private Myster types.
 *
 * <p>The single public method {@link #isAllowed} encodes the full allow/deny policy:
 * <ol>
 *   <li>No access list for this type → <b>allow</b> (type is effectively public).</li>
 *   <li>{@link Policy#isListFilesPublic()} is {@code true} → <b>allow</b>.</li>
 *   <li>Caller identity is unknown (empty {@code callerCid}) → <b>deny</b>. Identity cannot
 *       be verified on a plaintext connection.</li>
 *   <li>Caller's {@link Cid128} is in the members map (admins are always members) → <b>allow</b>.</li>
 *   <li>Otherwise → <b>deny</b>.</li>
 * </ol>
 *
 * <p>On any {@link IOException} from the reader the method fails open (returns {@code true}) and
 * logs a WARNING — a corrupt or temporarily unreadable access list must not take down the serving
 * thread.
 *
 * @see AccessListReader
 * @see AccessListState#isMember(Cid128)
 */
public class AccessEnforcementUtils {
    private static final Logger log = Logger.getLogger(AccessEnforcementUtils.class.getName());

    private AccessEnforcementUtils() {}

    /**
     * Determines whether the caller identified by {@code callerCid} is allowed to access files
     * of {@code type}.
     *
     * @param type       the Myster type being accessed
     * @param callerCid  the verified identity of the caller; empty if the connection is
     *                   plaintext or the identity could not be determined
     * @param reader     source of access-list data for the type
     * @return {@code true} if access is permitted, {@code false} if it must be denied
     */
    public static boolean isAllowed(MysterType type,
                                    Optional<Cid128> callerCid,
                                    AccessListReader reader) {
        Optional<AccessList> listOpt;
        try {
            listOpt = reader.loadAccessList(type);
        } catch (IOException e) {
            log.warning("Failed to load access list for type " + type + " — failing open: " + e.getMessage());
            return true;
        }

        if (listOpt.isEmpty()) {
            return true;
        }

        AccessListState state = listOpt.get().getState();

        if (state.getPolicy().isListFilesPublic()) {
            return true;
        }

        if (callerCid.isEmpty()) {
            return false;
        }

        return state.isMember(callerCid.get());
    }
}

