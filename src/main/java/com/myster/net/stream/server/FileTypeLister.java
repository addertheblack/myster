/*
 * 
 * Title: Myster Open Source Author: Andrew Trumper Description: Generic Myster
 * Code
 * 
 * This code is under GPL
 * 
 * Copyright Andrew Trumper 2000-2001
 */

package com.myster.net.stream.server;

import java.io.IOException;
import java.util.List;

import com.general.util.Util;
import com.myster.access.AccessEnforcementUtils;
import com.myster.access.AccessListReader;
import com.myster.net.server.ConnectionContext;
import com.myster.type.MysterType;

/**
 * Section 74 — returns the list of file types served by this node.
 *
 * <p>Private types (those with an access list whose policy is not public) are filtered out for
 * callers that are not members. Membership is verified via {@link AccessEnforcementUtils}.
 */
public class FileTypeLister extends ServerStreamHandler {
    public static final int NUMBER = 74;

    private final AccessListReader accessListReader;

    public FileTypeLister(AccessListReader accessListReader) {
        this.accessListReader = accessListReader;
    }

    public int getSectionNumber() {
        return NUMBER;
    }

    public void section(ConnectionContext context) throws IOException {
        MysterType[] allTypes = context.fileManager().getFileTypeListing();

        List<MysterType> filtered = Util.filter(
                java.util.Arrays.asList(allTypes),
                t -> AccessEnforcementUtils.isAllowed(t, context.callerCid(), accessListReader));

        context.socket().out.writeInt(filtered.size());
        for (MysterType t : filtered) {
            context.socket().out.writeType(t);
        }
    }
}

