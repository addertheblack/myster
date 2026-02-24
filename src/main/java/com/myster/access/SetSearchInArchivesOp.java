package com.myster.access;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;

/**
 * Operation to set whether the type should search inside archive files.
 */
public class SetSearchInArchivesOp implements BlockOperation {
    private final boolean searchInArchives;

    public SetSearchInArchivesOp(boolean searchInArchives) {
        this.searchInArchives = searchInArchives;
    }

    public boolean isSearchInArchives() {
        return searchInArchives;
    }

    @Override
    public OpType getType() {
        return OpType.SET_SEARCH_IN_ARCHIVES;
    }

    @Override
    public void serializePayload(DataOutputStream out) throws IOException {
        out.writeBoolean(searchInArchives);
    }

    static SetSearchInArchivesOp deserializePayload(DataInputStream in) throws IOException {
        return new SetSearchInArchivesOp(in.readBoolean());
    }

    @Override
    public String toString() {
        return "SetSearchInArchivesOp{searchInArchives=" + searchInArchives + "}";
    }
}

