package com.myster.threedns;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import com.myster.identity.Cid128;

/**
 * Immutable structural snapshot of one local 3DNS target slot.
 * <p>
 * This is a UI/debug view of the retained slot state. It copies the left and
 * right entry lists, but the entries still reference the live {@code MysterServer}
 * objects held by the tracker pool.
 */
public record ThreeDnsTargetSlotSnapshot(
        int bitIndex,
        Cid128 targetCid,
        List<ThreeDnsFingerEntry> left,
        List<ThreeDnsFingerEntry> right
) {
    public ThreeDnsTargetSlotSnapshot {
        Objects.requireNonNull(targetCid);
        left = List.copyOf(left);
        right = List.copyOf(right);
    }

    /**
     * Returns retained entries with left-side entries first, then right-side entries.
     */
    public List<ThreeDnsFingerEntry> entries() {
        List<ThreeDnsFingerEntry> entries = new ArrayList<>(left.size() + right.size());
        entries.addAll(left);
        entries.addAll(right);
        return List.copyOf(entries);
    }
}
