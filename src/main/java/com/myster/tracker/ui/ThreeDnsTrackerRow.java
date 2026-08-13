package com.myster.tracker.ui;

import java.util.Objects;

import com.myster.cid.ServerCid;
import com.myster.net.MysterAddress;
import com.myster.threedns.ThreeDnsFingerEntry;
import com.myster.tracker.MysterServer;

/**
 * Denormalized row model for TrackerWindow's 3DNS table.
 * <p>
 * The row keeps one live {@link MysterServer} for server actions and refreshed
 * status columns, plus immutable copies of the 3DNS target-slot facts needed
 * by the table.
 */
public record ThreeDnsTrackerRow(
        MysterServer server,
        ServerCid targetCid,
        ServerCid serverCid,
        int bitIndex,
        ThreeDnsFingerEntry.Side side,
        MysterAddress retainedAddress,
        long updateTimeMs
) {
    public ThreeDnsTrackerRow {
        Objects.requireNonNull(server);
        Objects.requireNonNull(targetCid);
        Objects.requireNonNull(serverCid);
        Objects.requireNonNull(side);
        Objects.requireNonNull(retainedAddress);
    }
}
